/*******************************************************************************
 * Copyright (c) 2015 IBM Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package net.wasdev.gameon.mediator.room;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import net.wasdev.gameon.mediator.Constants;
import net.wasdev.gameon.mediator.Log;
import net.wasdev.gameon.mediator.MapClient;
import net.wasdev.gameon.mediator.PlayerClient;
import net.wasdev.gameon.mediator.PlayerConnectionMediator;
import net.wasdev.gameon.mediator.RoomMediator;
import net.wasdev.gameon.mediator.RoutedMessage;
import net.wasdev.gameon.mediator.models.ConnectionDetails;
import net.wasdev.gameon.mediator.models.Exit;
import net.wasdev.gameon.mediator.models.Exits;
import net.wasdev.gameon.mediator.models.Site;

/**
 *
 */
public class FirstRoom implements RoomMediator {

    public static final String TELEPORT = "teleport";
    public static final String FIRST_ROOM_DESC = "You've entered a vaguely squarish room, with walls of an indeterminate color.";
    public static final String FIRST_ROOM_EXTENDED = "\n\nYou are alone at the moment, and have a strong suspicion that "
            + " you're in a place that requires '/' before commands and is picky about syntax. You notice "
            + " buttons at the top right of the screen, and a button at the bottom left to help with that"
            + " leading slash.\n\nYou feel a strong temptation to try the buttons.";

    public static final String FIRST_ROOM_INV = "Sadly, there is nothing here.";

    public static final String FIRST_ROOM_POCKETS = "You do not appear to be carrying anything.";
    public static final String FIRST_ROOM_POCKETS_EXTENDED = "\n\nBut you will be eventually! As you explore, use /TAKE"
            + " to pick things up. Some things will remain with the room when you leave, others might stay"
            + " in your pocket for a little longer. Nothing is guaranteed to stay with you indefinitely. "
            + " Sneaky characters and self-healing rooms will foil most hoarder's plans.";

    PlayerConnectionMediator session = null;
    private AtomicInteger counter = new AtomicInteger(0);
    boolean newbie = false;
    boolean inventory = false;

    MapClient mapClient = null;

    final String playerJwt;
    final PlayerClient playerClient;

    public FirstRoom(String playerJwt, PlayerClient playerClient, MapClient mapClient) {
        this(playerJwt, playerClient, mapClient, false);
    }

    public FirstRoom(String playerJwt, PlayerClient playerClient, MapClient mapClient, boolean newbie) {
        Log.log(Level.FINEST, this, "New First Room, new player {0}", newbie);
        this.newbie = newbie;
        this.mapClient = mapClient;
        this.playerJwt = playerJwt;
        this.playerClient = playerClient;
    }

    @Override
    public String getId() {
        return Constants.FIRST_ROOM;
    }

    @Override
    public String getName() {
        return Constants.FIRST_ROOM;
    }

    @Override
    public String getFullName() {
        return "The First Room";
    }

    @Override
    public ConnectionDetails getConnectionDetails() {
        return null;
    }

    @Override
    public Exits getExits() {
        return mapClient.getFirstRoomExits();
    }

    @Override
    public Exit getExit(String direction) {
        Exits currentExits = getExits();
        return currentExits == null ? null : currentExits.getExit(direction);
    }

    @Override
    public JsonObject listExits() {
        Exits exits = getExits();
        return exits.toJson();
    }

    @Override
    public boolean connect() {
        return true;
    }

    @Override
    public boolean subscribe(PlayerConnectionMediator playerSession, long lastmessage) {
        this.session = playerSession;
        if (newbie) {
            JsonObjectBuilder builder = Json.createObjectBuilder();
            builder.add(Constants.BOOKMARK, counter.incrementAndGet());
            buildLocationResponse(builder);

            session.sendToClient(
                    RoutedMessage.createMessage(Constants.PLAYER, playerSession.getUserId(), builder.build()));
        }
        return true;
    }

    @Override
    public void unsubscribe(PlayerConnectionMediator playerSession) {
    }

    @Override
    public void disconnect() {
    }

    /**
     * "Send to the room"
     *
     * The First room is different because this is the room. So here, we take
     * the pseudo-sent messages and provide coverage for the most basic commands
     * to ensure they get handled' and provide some help and context.
     *
     * @see net.wasdev.gameon.mediator.RoomMediator#send(net.wasdev.gameon.mediator.RoutedMessage)
     */
    @Override
    public void send(RoutedMessage message) {
        Log.log(Level.FINEST, this, "TheFirstRoom received: {0}", message);

        JsonObject sourceMessage = message.getParsedBody();

        JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add(Constants.BOOKMARK, counter.incrementAndGet());
        switch (message.getFlowTarget()) {
            case Constants.ROOM_HELLO:
                // First room doesn't usually see roomHello,
                // but may in the case of a botched transition
                buildLocationResponse(builder);
                break;
            case Constants.ROOM_GOODBYE:
                // no response for roomGoodbye
                return;
            default:
                parseCommand(sourceMessage, builder);
                break;
        }

        JsonObject response = builder.build();

        String target = Constants.PLAYER;
        if (response.containsKey(RoomUtils.EXIT_ID)) {
            target = Constants.PLAYER_LOCATION;
        }
        session.sendToClient(RoutedMessage.createMessage(target, sourceMessage.getString(RoomUtils.USER_ID), response));
    }

    protected void parseCommand(JsonObject sourceMessage, JsonObjectBuilder responseBuilder) {
        String content = sourceMessage.getString(RoomUtils.CONTENT);
        String contentToLower = content.toLowerCase();

        // The First Room will just look for the leading / with a few verbs.
        // Other rooms may go for more complicated grammar (though leading slash
        // will be prevalent).
        if (contentToLower.startsWith("/look")) {
            buildLocationResponse(responseBuilder);
        } else if (contentToLower.startsWith("/exits")) {
            responseBuilder.add(RoomUtils.TYPE, Constants.ROOM_EXITS).add(RoomUtils.CONTENT, listExits());
        } else if (contentToLower.startsWith("/go")) {
            String exitDirection = RoomUtils.getDirection(contentToLower);
            if (exitDirection == null) {
                responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT).add(RoomUtils.CONTENT,
                        RoomUtils.buildContentResponse("Hmm. That direction didn't make sense. Try again?"));
            } else {
                responseBuilder.add(RoomUtils.TYPE, RoomUtils.EXIT).add(RoomUtils.EXIT_ID, exitDirection)
                .add(RoomUtils.CONTENT, "You've found a way out, well done!");
            }
        } else if (contentToLower.startsWith("/inventory")) {
            responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT).add(RoomUtils.CONTENT, buildInventoryResponse());
        } else if (contentToLower.startsWith("/examine")) {
            responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT).add(RoomUtils.CONTENT,
                    RoomUtils.buildContentResponse("You don't see anything of interest."));
        } else if (contentToLower.startsWith("/help")) {
            responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT).add(RoomUtils.CONTENT, buildHelpResponse());
        } else if (contentToLower.startsWith("/listmyrooms")) {
            processListMyRoomsCommand(sourceMessage, responseBuilder);
        } else if (contentToLower.startsWith("/listmyrooms")) {
            processListSystemRoomsCommand(sourceMessage, responseBuilder);
        } else if (contentToLower.startsWith("/deleteroom ")) {
            System.out.println("calling delete.. ");
            processDeleteRoomCommand(contentToLower, sourceMessage, responseBuilder);
        } else if (contentToLower.startsWith("/teleport ")) {
            if (contentToLower.length() > "/teleport ".length()) {
                String target = contentToLower.substring("/teleport ".length());

                // TODO: use sommat friendlier than room id as the teleport
                // argument.. needs update to listmyrooms above to tell it how
                // to

                responseBuilder.add(RoomUtils.TYPE, RoomUtils.EXIT).add(RoomUtils.EXIT_ID, target)
                .add(FirstRoom.TELEPORT, true).add(RoomUtils.CONTENT,
                        "You punch the coordinates into the console, a large tube appears from above you, and you are sucked into a maze of piping.");
            } else {
                responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT).add(RoomUtils.CONTENT,
                        RoomUtils.buildContentResponse(
                                "You concentrate really hard, and teleport from your current location, to your current location.. Magic!!"));
            }
        } else if (contentToLower.startsWith("/")) {
            responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT).add(RoomUtils.CONTENT,
                    RoomUtils.buildContentResponse("This room is a basic model. It doesn't understand that command."));
        } else {
            responseBuilder.add(Constants.USERNAME, sourceMessage.getString(Constants.USERNAME))
            .add(RoomUtils.CONTENT, content).add(RoomUtils.TYPE, RoomUtils.CHAT);
        }
    }

    private void processDeleteRoomCommand(String contentToLower, JsonObject sourceMessage,
            JsonObjectBuilder responseBuilder) {

        System.out.println("Processing delete command.. " + contentToLower);

        String userid = sourceMessage.getString(RoomUtils.USER_ID);

        if (contentToLower.length() > "/deleteroom ".length()) {
            String target = contentToLower.substring("/deleteroom ".length());

            // obtain the users shared secret using their jwt..
            String secret = playerClient.getSharedSecret(userid, playerJwt);
            System.out.println("Got key for user of " + String.valueOf(secret));
            if (secret == null) {
                responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT).add(RoomUtils.CONTENT,
                        RoomUtils.buildContentResponse(
                                "Sqork. The Internal Cogs Rumble, but refuse to move, I'm not sure who you are."));
                return;
            }

            if (mapClient.deleteSite(target, userid, secret)) {
                responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT).add(RoomUtils.CONTENT,
                        RoomUtils.buildContentResponse("The room has varnished. Like an old oak table."));
            } else {
                responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT).add(RoomUtils.CONTENT,
                        RoomUtils.buildContentResponse("The room stubbornly refuses to be deleted.. "));
            }
        } else {
            responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT).add(RoomUtils.CONTENT,
                    RoomUtils.buildContentResponse("But how can you delete that, if you have no fingers?"));
            return;
        }

    }

    private void processListMyRoomsCommand(JsonObject sourceMessage, JsonObjectBuilder responseBuilder) {
        // TODO: add cache / rate limit.
        String userid = sourceMessage.getString(RoomUtils.USER_ID);
        List<Site> rooms = mapClient.getRoomsByOwner(userid);

        StringBuffer roomSummary = new StringBuffer();
        if (rooms != null && !rooms.isEmpty()) {
            roomSummary.append("You have registered the following rooms... \n");
            for (Site room : rooms) {
                if (room.getInfo() != null) {
                    roomSummary.append(" - '" + room.getInfo().getFullName() + "' with id " + room.getId() + "\n");
                }
            }
            roomSummary.append("\nYou can go directly to your own rooms using /teleport <roomid>");
        } else {
            roomSummary.append("You have no rooms registered!");
        }

        responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT).add(RoomUtils.CONTENT,
                RoomUtils.buildContentResponse(roomSummary.toString()));
    }

    private void processListSystemRoomsCommand(JsonObject sourceMessage, JsonObjectBuilder responseBuilder) {
        // TODO: add cache / rate limit.
        List<Site> rooms = mapClient.getRoomsByOwner(Constants.SYSTEM_ID);

        StringBuffer roomSummary = new StringBuffer();
        if (rooms != null && !rooms.isEmpty()) {
            roomSummary.append("There are the following system rooms... \n");
            for (Site room : rooms) {
                if (room.getInfo() != null) {
                    roomSummary.append(" - '" + room.getInfo().getFullName() + "' with id " + room.getId() + "\n");
                }
            }
            roomSummary.append("\nYou can go directly to a system room using /teleport <roomid>");
        } else {
            roomSummary.append("There are no system rooms registered!");
        }

        responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT).add(RoomUtils.CONTENT,
                RoomUtils.buildContentResponse(roomSummary.toString()));
    }

    private void buildLocationResponse(JsonObjectBuilder responseBuilder) {
        responseBuilder.add(RoomUtils.TYPE, RoomUtils.LOCATION);
        responseBuilder.add(Constants.NAME, Constants.FIRST_ROOM);
        responseBuilder.add(Constants.FULL_NAME, getFullName());
        responseBuilder.add(Constants.ROOM_EXITS, listExits());
        responseBuilder.add(Constants.COMMANDS, buildHelpResponse());

        if (newbie) {
            responseBuilder.add(RoomUtils.DESCRIPTION, FIRST_ROOM_DESC + FIRST_ROOM_EXTENDED);
            newbie = false;
        } else {
            responseBuilder.add(RoomUtils.DESCRIPTION, FIRST_ROOM_DESC);
        }
    }

    protected JsonObject buildInventoryResponse() {
        if (inventory)
            return RoomUtils.buildContentResponse(FIRST_ROOM_POCKETS);

        inventory = true;
        return RoomUtils.buildContentResponse(FIRST_ROOM_POCKETS + FIRST_ROOM_POCKETS_EXTENDED);
    }

    protected JsonObject buildHelpResponse() {
        JsonObjectBuilder content = Json.createObjectBuilder();
        content.add("/listmyrooms", "List all of your rooms");
        content.add("/teleport", "Teleport to the specified room, e.g. `/teleport room-id`");
        return content.build();
    }

}
