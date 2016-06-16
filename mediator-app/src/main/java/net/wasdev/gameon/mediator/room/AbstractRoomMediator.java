/*******************************************************************************
 * Copyright (c) 2016 IBM Corp.
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import net.wasdev.gameon.mediator.Constants;
import net.wasdev.gameon.mediator.Log;
import net.wasdev.gameon.mediator.MapClient;
import net.wasdev.gameon.mediator.MediatorNexus;
import net.wasdev.gameon.mediator.MediatorNexus.UserView;
import net.wasdev.gameon.mediator.RoutedMessage;
import net.wasdev.gameon.mediator.RoutedMessage.FlowTarget;
import net.wasdev.gameon.mediator.models.Exit;
import net.wasdev.gameon.mediator.models.Exits;
import net.wasdev.gameon.mediator.models.RoomInfo;
import net.wasdev.gameon.mediator.models.Site;

public abstract class AbstractRoomMediator implements RoomMediator {

    static final String NO_COMMANDS = "There is nothing here that can do that.";

    static final List<String> GOODBYES = Collections.unmodifiableList(Arrays.asList(
            "Faithless is he that says farewell when the road darkens.", // Tolkein
            "Not all those who wander are lost.", // Tolkein
            "Little by little, one travels far", // Tolkein
            "Courage is found in unlikely places.", // Tolkein
            "Farewell, fair cruelty.", // Shakespeare
            "So long, and thanks for all the fish.", // Adams
            ""));


    final String roomId;
    final MediatorNexus.View nexusView;
    final MapClient mapClient;

    protected Exits exits;
    protected RoomInfo roomInfo;
    protected AtomicInteger bookmark = new AtomicInteger(0);

    public AbstractRoomMediator(MediatorNexus.View nexusView, MapClient mapClient, Site site) {
        this.roomId = site.getId();
        this.nexusView = nexusView;
        this.mapClient = mapClient;
        this.exits = site.getExits();
        this.roomInfo = site.getInfo();
    }

    @Override
    public String getId() {
        return roomId;
    }

    @Override
    public MediatorNexus.View getNexusView() {
        return nexusView;
    }

    @Override
    public abstract String getName();

    @Override
    public abstract String getFullName();

    @Override
    public abstract String getDescription();

    @Override
    public abstract Type getType();

    @Override
    public JsonObject listExits() {
        return exits.toSimpleJsonList();
    }

    @Override
    public Exits getExits() {
        Exits currentExits = exits;
        Site s = mapClient.getSite(roomId);
        if ( s != null ) {
            roomInfo = s.getInfo();
            currentExits = exits = s.getExits();
        }

        return currentExits;
    }

    @Override
    public boolean sameConnectionDetails(RoomInfo info) {
        if ( roomInfo == null ) {
            return info == null;
        }
        if ( roomInfo.getConnectionDetails() == null ) {
            return info != null && info.getConnectionDetails() == null;
        }
        if ( info == null || info.getConnectionDetails() == null )
            return false;

        return roomInfo.getConnectionDetails().equals(info.getConnectionDetails());
    }

    @Override
    public void updateInformation(Site site) {
        this.exits = site.getExits();
        this.roomInfo = site.getInfo();
    }

    @Override
    public Exit getEmergencyReturnExit() {
        Exit exit = new Exit();
        exit.setId(roomId);
        exit.setName(getName());
        exit.setFullName(getFullName());
        exit.setDoor("Back where you came from");
        return exit;
    }

    @Override
    public void hello(MediatorNexus.UserView user, boolean recovery) {
        // Say hello to..
        sendToClients(RoutedMessage.createMessage(FlowTarget.player, "*",
                String.format(Constants.EVENT_HELLO, user.getUserName(), user.getUserId(), helloMessage(), bookmark.incrementAndGet())));

        // type=location message
        sendToClients(getLocationEventMessage(user));
     }

    @Override
    public void goodbye(MediatorNexus.UserView user) {
        sendToClients(RoutedMessage.createMessage(FlowTarget.player, "*",
                String.format(Constants.EVENT_GOODBYE, user.getUserName(), user.getUserId(), goodbyeMessage(), bookmark.incrementAndGet())));
    }

    @Override
    public void join(MediatorNexus.UserView user) {
        // joins happen _quite_ frequently. No action.
    }

    @Override
    public void part(MediatorNexus.UserView user){
        // parts happen _quite_ frequently. No action.
    }

    @Override
    public void disconnect() {}

    @Override
    public void sendToRoom(RoutedMessage message) {
        Log.log(Level.FINEST, this, "{0}/{1} received: {2}", getName(), getType(), message);

        JsonObject sourceMessage = message.getParsedBody();
        String userId = sourceMessage.getString(RoomUtils.USER_ID);

        JsonObjectBuilder builder = Json.createObjectBuilder();
        String targetUserId = parseMessage(userId, sourceMessage, builder);

        // build response (local-only room)
        JsonObject response = builder.build();

        FlowTarget target = FlowTarget.player;
        if (response.containsKey(RoomUtils.EXIT_ID)) {
            target = FlowTarget.playerLocation;
        }

        // send response back to clients...
        sendToClients(RoutedMessage.createMessage(target, targetUserId, response));
    }

    @Override
    public void sendToClients(RoutedMessage message) {
        nexusView.sendToClients(message);
    }

    @Override
    public RoutedMessage getLocationEventMessage(UserView user) {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        buildLocationResponse(builder);
        builder.add(Constants.KEY_BOOKMARK, "go" + getType() + ":" + bookmark.incrementAndGet());

        return RoutedMessage.createMessage(FlowTarget.player, user.getUserId(), builder.build());
    }

    protected String parseMessage(String userId, JsonObject sourceMessage, JsonObjectBuilder responseBuilder) {
        String content = sourceMessage.getString(RoomUtils.CONTENT).trim();
        String userName = sourceMessage.getString(Constants.KEY_USERNAME);
        String targetId = userId;

        if ( content.charAt(0) == '/' ) {
            String contentToLower = content.toLowerCase();

            if ( contentToLower.startsWith("/go") ) {
                String exitDirection = RoomUtils.getDirection(contentToLower);
                if ( exitDirection == null ) {
                    responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT)
                        .add(RoomUtils.CONTENT, RoomUtils.buildContentResponse(userId, "Hmm. That direction didn't make sense. Try again?"));
                } else if ( "u".equals(exitDirection) ) {
                    responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT)
                        .add(RoomUtils.CONTENT, RoomUtils.buildContentResponse(userId, "There might be a ceiling, or perhaps just clouds. Certainly no doors."));
                } else if ( "d".equals(exitDirection) ) {
                    responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT)
                        .add(RoomUtils.CONTENT, RoomUtils.buildContentResponse(userId, "Ooof. Yep. That's a floor."));
                } else {
                    responseBuilder.add(RoomUtils.TYPE, RoomUtils.EXIT)
                        .add(RoomUtils.EXIT_ID, exitDirection)
                        .add(RoomUtils.CONTENT, "You head " + RoomUtils.longDirection(exitDirection));
                }
            } else if ( "/look".equals(contentToLower) ) {
                buildLocationResponse(responseBuilder);
            } else {
                targetId = parseCommand(userId, userName, sourceMessage, responseBuilder);
            }
        } else {
            // chat is broadcast
            targetId = "*";
            responseBuilder.add(Constants.KEY_USERNAME, userName)
                .add(RoomUtils.CONTENT, content)
                .add(Constants.KEY_BOOKMARK, "go-" + bookmark.incrementAndGet())
                .add(RoomUtils.TYPE, RoomUtils.CHAT);
        }

        responseBuilder.add(Constants.KEY_BOOKMARK, "go-" + bookmark.incrementAndGet());

        return targetId;
    }

    /** Process the text of a command:
     * @return user id of target user, or '*' for all
     */
    protected String parseCommand(String userId, String userName, JsonObject sourceMessage, JsonObjectBuilder responseBuilder) {
        responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT)
            .add(RoomUtils.CONTENT, RoomUtils.buildContentResponse(userId, NO_COMMANDS));
        return userId;
    }

    protected void buildLocationResponse(JsonObjectBuilder responseBuilder) {
        responseBuilder.add(RoomUtils.TYPE, RoomUtils.LOCATION);
        responseBuilder.add(Constants.KEY_ROOM_NAME, getName());
        responseBuilder.add(Constants.KEY_ROOM_FULLNAME, getFullName());
        responseBuilder.add(Constants.KEY_ROOM_EXITS, exits.toSimpleJsonList());
        responseBuilder.add(RoomUtils.DESCRIPTION, getDescription());
        addCommands(responseBuilder);
    }
    
    protected void addCommands(JsonObjectBuilder responseBuilder) {
        // no-op.
    }

    protected String goodbyeMessage() {
        int index = RoomUtils.random.nextInt(GOODBYES.size());
        return GOODBYES.get(index);
    }

    protected String helloMessage() {
        return "Welcome to " + getFullName();
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName()
                + "[id=" + roomId
                + ", name="+getName()
                + ", full="+getFullName()
                + "]";
    }

    public String toFullString() {
        return this.getClass().getSimpleName()
                + "[name="+getName()
                + ", full="+getFullName()
                + ", info="+roomInfo
                + ", exits="+exits
                + "]";
    }

}
