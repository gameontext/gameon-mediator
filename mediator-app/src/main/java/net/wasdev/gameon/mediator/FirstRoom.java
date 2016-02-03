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
package net.wasdev.gameon.mediator;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import net.wasdev.gameon.mediator.MapClient.Site;

/**
 *
 */
public class FirstRoom implements RoomMediator {

    public static final String CONTENT = "content";
    public static final String USER_ID = "userId";
    public static final String EXIT_ID = "exitId";
    public static final String MEDIATOR_ID = "mediatorId";
    public static final String TELEPORT = "teleport";
    public static final String EXITS = "exits";
    public static final String DESCRIPTION = "description";
    public static final String NAME = "name";
    public static final String LOCATION = "location";

    /** JSON element specifying the type of message. */
    public static final String TYPE = "type";
    public static final String CHAT = "chat";
    public static final String EXIT = "exit";
    public static final String EVENT = "event";

    public static final String FIRST_ROOM_DESC = "You've entered a vaguely squarish room, with walls of an indeterminate color.";
    public static final String FIRST_ROOM_EXTENDED = "<br /><br />You are alone at the moment, and have a strong suspicion that "
            + " you're in a place that requires '/' before commands and is picky about syntax. You notice "
            + " buttons at the top right of the screen, and a button at the bottom left to help with that"
            + " leading slash.<br /><br />You feel a strong temptation to try the buttons.";

    public static final String FIRST_ROOM_INV = "Sadly, there is nothing here.";

    public static final String FIRST_ROOM_POCKETS = "You do not appear to be carrying anything.";
    public static final String FIRST_ROOM_POCKETS_EXTENDED = "<br /><br />But you will be eventually! As you explore, use /TAKE"
            + " to pick things up. Some things will remain with the room when you leave, others might stay"
            + " in your pocket for a little longer. Nothing is guaranteed to stay with you indefinitely. "
            + " Sneaky characters and self-healing rooms will foil most hoarder's plans.";

    PlayerConnectionMediator session = null;
    private AtomicInteger counter = new AtomicInteger(0);
    boolean newbie = false;
    boolean inventory = false;
    MapClient mapClient = null;

    public FirstRoom(MapClient mapClient) {
        this(mapClient, false);
    }

    public FirstRoom(MapClient mapClient, boolean newbie) {
        Log.log(Level.FINEST, this, "New First Room, new player {0}", newbie);
        this.newbie = newbie;
        this.mapClient = mapClient;
    }

    @Override
    public String getId() {
        return "firstroom";
    }

    @Override
    public String getName() {
        return Constants.FIRST_ROOM;
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
        if (response.containsKey(FirstRoom.EXIT_ID)) {
            target = Constants.PLAYER_LOCATION;
        }
        session.sendToClient(RoutedMessage.createMessage(target, sourceMessage.getString(FirstRoom.USER_ID), response));
    }

    protected void parseCommand(JsonObject sourceMessage, JsonObjectBuilder responseBuilder) {
        String content = sourceMessage.getString(FirstRoom.CONTENT);
        String contentToLower = content.toLowerCase();

        // The First Room will just look for the leading / with a few verbs.
        // Other rooms may go for more complicated grammar (though leading slash
        // will be prevalent).
        if (contentToLower.startsWith("/look")) {
            buildLocationResponse(responseBuilder);
        } else if (contentToLower.startsWith("/exits")) {
            responseBuilder.add(FirstRoom.TYPE, FirstRoom.EXITS).add(FirstRoom.CONTENT, buildExitsResponse());
        } else if (contentToLower.startsWith("/exit") || contentToLower.startsWith("/go ")) {
            String exitDirection = "N";
            if(contentToLower.startsWith("/go ") && contentToLower.length()>"/go ".length()){
                exitDirection = (""+contentToLower.charAt(4)).toUpperCase();
            }     
            responseBuilder.add(FirstRoom.TYPE, FirstRoom.EXIT).add(FirstRoom.EXIT_ID, exitDirection).add(FirstRoom.CONTENT,
                    "You've found a way out, well done!");
        } else if (contentToLower.startsWith("/inventory")) {
            responseBuilder.add(FirstRoom.TYPE, FirstRoom.EVENT).add(FirstRoom.CONTENT, buildInventoryResponse());
        } else if (contentToLower.startsWith("/examine")) {
            responseBuilder.add(FirstRoom.TYPE, FirstRoom.EVENT).add(FirstRoom.CONTENT,
                    buildContentResponse("You don't see anything of interest."));
        } else if (contentToLower.startsWith("/help")) {
            responseBuilder.add(FirstRoom.TYPE, FirstRoom.EVENT).add(FirstRoom.CONTENT, buildContentResponse(
                    "Commands do and should start with '/' This room doesn't understand many commands, but others will."));
        } else if (contentToLower.startsWith("/listmyrooms")) {            
            //TODO: add cache / rate limit. 
            String userid = sourceMessage.getString(USER_ID);
            List<Site> rooms =  mapClient.getRoomsByOwner(userid);
            
            StringBuffer roomSummary = new StringBuffer();
            if(rooms!=null && !rooms.isEmpty()){
                roomSummary.append("You have registered the following rooms... <br />");
                for(Site room: rooms){
                    if(room.getInfo()!=null){
                        roomSummary.append(" - '"+room.getInfo().getFullName()+"' with id "+room.getId()+"<br />\n");
                    }
                }
                roomSummary.append("You can go directly to your own rooms using /teleport <roomid>");
            }else{
                roomSummary.append("You have no rooms registered!");
            }
            
            responseBuilder.add(FirstRoom.TYPE, FirstRoom.EVENT).add(FirstRoom.CONTENT, buildContentResponse(
                    roomSummary.toString()));
        } else if (contentToLower.startsWith("/teleport ")) {
            if(contentToLower.length()>"/teleport ".length()){
                String target = contentToLower.substring("/teleport ".length());
                
                //TODO: use sommat friendlier than room id as the teleport argument.. 
                //      needs update to listmyrooms above to tell it how to 
                
                responseBuilder.add(FirstRoom.TYPE, FirstRoom.EXIT).add(FirstRoom.EXIT_ID, target).add(FirstRoom.TELEPORT, true).add(FirstRoom.CONTENT,
                        "You punch the coordinates into the console, a large tube appears from above you, and you are sucked into a maze of piping.");
            }else{
                responseBuilder.add(FirstRoom.TYPE, FirstRoom.EVENT).add(FirstRoom.CONTENT, buildContentResponse(
                        "You concentrate really hard, and teleport from your current location, to your current location.. Magic!!"));
            }
        }else if (contentToLower.startsWith("/")) {
            responseBuilder.add(FirstRoom.TYPE, FirstRoom.EVENT).add(FirstRoom.CONTENT,
                    buildContentResponse("This room is a basic model. It doesn't understand that command."));
        } else {
            responseBuilder.add(Constants.USERNAME, sourceMessage.getString(Constants.USERNAME))
                    .add(FirstRoom.CONTENT, content).add(FirstRoom.TYPE, FirstRoom.CHAT);
        }
    }

    private JsonObject buildContentResponse(String message) {
        JsonObjectBuilder content = Json.createObjectBuilder();
        content.add("*", message);
        return content.build();
    }

    private void buildLocationResponse(JsonObjectBuilder responseBuilder) {
        responseBuilder.add(FirstRoom.TYPE, FirstRoom.LOCATION);
        responseBuilder.add(FirstRoom.NAME, Constants.FIRST_ROOM);
        responseBuilder.add(FirstRoom.EXITS, buildExitsResponse());

        if (newbie) {
            responseBuilder.add(FirstRoom.DESCRIPTION, FIRST_ROOM_DESC + FIRST_ROOM_EXTENDED);
            newbie = false;
        } else {
            responseBuilder.add(FirstRoom.DESCRIPTION, FIRST_ROOM_DESC);
        }
    }

    private JsonObject buildExitsResponse() {
        JsonObjectBuilder content = Json.createObjectBuilder();
        content.add("N", "Simple door");
        content.add("S", "Simple door");
        content.add("E", "Simple door");
        content.add("W", "Simple door");
        content.add("U", "Hatch in the ceiling");
        content.add("D", "Trap-door in the floor");

        return content.build();
    }

    protected JsonObject buildInventoryResponse() {
        if (inventory)
            return buildContentResponse(FIRST_ROOM_POCKETS);

        inventory = true;
        return buildContentResponse(FIRST_ROOM_POCKETS + FIRST_ROOM_POCKETS_EXTENDED);
    }

}
