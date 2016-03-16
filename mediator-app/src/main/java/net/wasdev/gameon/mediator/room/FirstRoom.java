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
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import javax.annotation.Resource;
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
    /**
     * The id under which system rooms are registered.
     */
    @Resource(lookup="systemId")
    String SYSTEM_ID;
    
    public static final String TELEPORT = "teleport";
    public static final String FIRST_ROOM_DESC = "You've entered a vaguely squarish room, with walls of an indeterminate color.";
    public static final String FIRST_ROOM_EXTENDED = "\n\nTL;DR README (The extended edition is [here](https://gameontext.gitbooks.io/gameon-gitbook/content/)): \n\n"
            + "* Commands start with '/'.\n"
            + "* Use `/help` to list all available commands. The list will change from room to room.\n"
            + "* Use `/exits` to list all available exits.\n"
            + "* Use `/sos` to return to First Room if you're stuck.\n"
            + "* Rooms might try to fool you, but these three commands will always work.";

    public static final String FIRST_ROOM_INV = "Sadly, there is nothing here.";

    public static final String FIRST_ROOM_POCKETS = "You do not appear to be carrying anything.";
    public static final String FIRST_ROOM_POCKETS_EXTENDED = "\n\nBut you might be eventually! Individual rooms "
            + " may or may not support the notion of items. So whether or not you have things in your pockets"
            + " will change from room to room, as will how long they stay there.";

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

        if( session != null)
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
        } else if (contentToLower.startsWith("/listsystemrooms")) {
            processListSystemRoomsCommand(sourceMessage, responseBuilder);
        } else if (contentToLower.startsWith("/deleteroom")) {
            processDeleteRoomCommand(content, contentToLower, sourceMessage, responseBuilder);
        } else if (contentToLower.startsWith("/teleport")) {
            String username = sourceMessage.getString(RoomUtils.USER_NAME);
            processTeleportCommand(content, contentToLower,username,sourceMessage, responseBuilder);
        } else if (contentToLower.startsWith("/xyzzy")) {
            String username = sourceMessage.getString(RoomUtils.USER_NAME);
            processSystemTeleportCommand(content, contentToLower,username,sourceMessage, responseBuilder);
        }else if (contentToLower.startsWith("/")) {
            responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT).add(RoomUtils.CONTENT,
                    RoomUtils.buildContentResponse("This room is a basic model. It doesn't understand that command."));
        } else {
            responseBuilder.add(Constants.USERNAME, sourceMessage.getString(Constants.USERNAME))
            .add(RoomUtils.CONTENT, content).add(RoomUtils.TYPE, RoomUtils.CHAT);
        }
    }

    private void processDeleteRoomCommand(String content, String contentToLower, JsonObject sourceMessage,
            JsonObjectBuilder responseBuilder) {

        Log.log(Level.INFO, this, "Processing delete command.. {0}" ,contentToLower);

        String userid = sourceMessage.getString(RoomUtils.USER_ID);

        if (contentToLower.length() > "/deleteroom ".length()) {
            String targetId = content.substring("/deleteroom ".length());
            
            List<Site> possibleCandidates = mapClient.getRoomsByOwnerAndRoomName(userid,targetId);            
            if(possibleCandidates.isEmpty()){
                responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT).add(RoomUtils.CONTENT,
                        RoomUtils.buildContentResponse(
                                "You don't appear to have a room with that id to delete. maybe you should check `/listmyrooms`"));
            }else{
                // obtain the users shared secret using their jwt..
                String secret = playerClient.getSharedSecret(userid, playerJwt);
                Log.log(Level.INFO, this, "Got key for user of (first4chars) {0}" ,String.valueOf(secret).substring(0,4));
                if (secret == null) {
                    responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT).add(RoomUtils.CONTENT,
                            RoomUtils.buildContentResponse(
                                    "Sqork. The Internal Cogs Rumble, but refuse to move, I'm not sure who you are."));
                    return;
                }
    
                if (mapClient.deleteSite(possibleCandidates.get(0).getId(), userid, secret)) {
                    responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT).add(RoomUtils.CONTENT,
                            RoomUtils.buildContentResponse("The room has varnished. Like an old oak table."));
                } else {
                    responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT).add(RoomUtils.CONTENT,
                            RoomUtils.buildContentResponse("The room stubbornly refuses to be deleted.. "));
                }
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
                    roomSummary.append(" - '" + room.getInfo().getFullName() + "' with id " + room.getInfo().getName() + "\n");
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
        List<Site> rooms = mapClient.getRoomsByOwner(SYSTEM_ID);

        StringBuffer roomSummary = new StringBuffer();
        if (rooms != null && !rooms.isEmpty()) {
            roomSummary.append("There are the following system rooms... \n");
            for (Site room : rooms) {
                if (room.getInfo() != null) {
                    roomSummary.append(" - '" + room.getInfo().getFullName() + "' with id " + room.getInfo().getName() + "\n");
                }
            }
            roomSummary.append("\nYou can go directly to a system room using /teleport <roomid>");
        } else {
            roomSummary.append("There are no system rooms registered!");
        }

        responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT).add(RoomUtils.CONTENT,
                RoomUtils.buildContentResponse(roomSummary.toString()));
    }
    
    private void processSystemTeleportCommand(String content, String contentToLower, String username, JsonObject sourceMessage, JsonObjectBuilder responseBuilder) {
        if (contentToLower.length() > "/xyzzy ".length()) {         
            String requestedTarget = content.substring("/xyzzy ".length());
            beamMeUp(SYSTEM_ID, username, requestedTarget, responseBuilder);
        } else {
            responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT).add(RoomUtils.CONTENT,
                    RoomUtils.buildContentResponse(
                            "This doesn't appear to be the correct way to use this command. Use the source, Fluke."));
        }
    }        
    
    private void processTeleportCommand(String content, String contentToLower, String username, JsonObject sourceMessage, JsonObjectBuilder responseBuilder) {
        String userid = sourceMessage.getString(RoomUtils.USER_ID);
        if (contentToLower.length() > "/teleport ".length()) {         
            String requestedTarget = content.substring("/teleport ".length());
            beamMeUp(userid, username, requestedTarget, responseBuilder);
        } else {
            responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT).add(RoomUtils.CONTENT,
                    RoomUtils.buildContentResponse(
                            "You concentrate really hard, and teleport from your current location, to your current location.. Magic!!"));
        }
    }
    
    private void beamMeUp(String userid, String userName, String targetId, JsonObjectBuilder responseBuilder){
            //teleport is only going to allow teleport to rooms owned by the player.
            List<Site> possibleCandidates = mapClient.getRoomsByOwnerAndRoomName(userid,targetId);            
            if(possibleCandidates.isEmpty()){
                responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT).add(RoomUtils.CONTENT,
                        RoomUtils.buildContentResponse(
                                "You don't appear to have a room with that id to teleport to.. maybe you should check `/listmyrooms`"));
            }else{
                String fly = "";
                if((new Random()).nextInt(10)==0){
                    fly = " Just before the teleporter engages, you hear a slight buzzing noise.. Good luck "+userName+"-Fly!";
                }
                responseBuilder.add(RoomUtils.TYPE, RoomUtils.EXIT).add(RoomUtils.EXIT_ID, possibleCandidates.get(0).getId())
                .add(FirstRoom.TELEPORT, true).add(RoomUtils.CONTENT,
                        "You punch the coordinates into the console, a large tube appears from above you, and you are sucked into a maze of piping."+fly);
            }

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
        content.add("/deleteroom", "Deregisters a room you have registered. e.g. `/deleteroom room-id`");
        return content.build();
    }
    
    @Override
    public long getSelectedProtocolVersion() {
        return 1;
    }
}
