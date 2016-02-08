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

import java.util.UUID;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import javax.json.Json;
import javax.json.JsonObject;
import javax.websocket.Session;

import net.wasdev.gameon.mediator.ConnectionUtils.Drain;
import net.wasdev.gameon.mediator.MapClient.Exit;
import net.wasdev.gameon.mediator.MapClient.Site;

/**
 * The mediator: mediates the inbound connection from the player's device to the
 * connection to each individual room. Handles all room transitions and
 * failover.
 * <p>
 * The {@code PlayerServerEndpoint will interact with the
 * {@code PlayerSessionManager} to find the associated mediator.
 * </p>
 */
public class PlayerConnectionMediator {

    /**
     * The player's userId. The room will broadcast to all connected sessions in
     * the event that two player devices are connected (e.g.). The mediator will
     * filter messages, and only relay those directed to all players or to the
     * specific player.
     */
    private final String userId;

    /**
     * The player's username. Sent to the room with playerHello and
     * playerGoodbye (which are mediator-initiated messages).
     */
    private final String username;

    /** Player client (for updating player location */
    private final PlayerClient playerClient;

    /**
     * JWT used to sign messages.
     */
    private final String jwt;

    /**
     * Mediator id. Identifies _this_ mediator instance, which is sent back to
     * the client device. In the case that the websocket is interrupted, sticky
     * sessions should get the client routed back to the same mediator instance,
     * which means we should be able to cope/queue/smooth-over brief disconnects
     * from the client.
     */
    private final String id = UUID.randomUUID().toString();

    /**
     * Map client: used to navigate room to room. Provided by the
     * PlayerSessionMediator, as the owning CDI-managed bean.
     */
    private final MapClient mapClient;

    /**
     * Connection utilities: used to simplify managing websocket connections.
     * Provided by the PlayerSessionMediator, as the owning CDI-managed bean.
     */
    private final ConnectionUtils connectionUtils;

    /** The websocket session between this service and the client device */
    private Drain drainToClient = null;

    /** Queue of messages destined for the client device */
    private final LinkedBlockingDeque<RoutedMessage> toClient = new LinkedBlockingDeque<RoutedMessage>();

    /** The currently connected room */
    private RoomMediator currentRoom = null;

    /**
     * Session reaping happens in cycles: we allow a few cycles before the
     * session is actually destroyed. Session suspend.
     */
    private AtomicInteger suspendCount = new AtomicInteger(0);

    /** Room protocol: name sent from room to client (identifying the room) */
    public static final String ROOM_NAME = "roomName";

    /**
     * Handshake, player to mediator. Indicates the player is ready to start or
     * resume player. Includes information based on localstorage in the client
     * (which room the client thought it was in and the last message seen).
     */
    public static final String CLIENT_READY = "ready";

    /**
     * Handshake, mediator to player. Returns information to the client to
     * update the client's cache.
     */
    public static final String CLIENT_ACK = "ack";

    public static final String FINDROOM_FAIL = "{\"type\": \"joinpart\",\"content\": \"Oh dear. That is a door to Nowhere. Back to TheFirstRoom \",\"bookmark\": 0}";
    public static final String CONNECTING = "{\"type\": \"joinpart\",\"content\": \"...connecting to %s...\",\"bookmark\": 0}";
    public static final String FINDROOM = "{\"type\": \"joinpart\",\"content\": \"...asking map service for next room...\",\"bookmark\": 0}";
    public static final String PART = "{\"type\": \"joinpart\",\"content\": \"exit %s\",\"bookmark\": 0}";
    public static final String JOIN = "{\"type\": \"joinpart\",\"content\": \"enter %s\",\"bookmark\": 0}";
    public static final String HIBYE = "{\"username\": \"%s\",\"userId\": \"%s\"}";
    public static final String ELECTRIC_THUMB = "{\"type\": \"exit\",\"content\": \"In a desperate plea for rescue, you stick out your <a href='http://everything2.com/title/Electronic+Thumb' target='_blank'>Electric Thumb</a> and hope for the best.\",\"bookmark\": 0}";
    public static final String BAD_RIDE = "{\"type\": \"event\",\"content\": {\"*\": \"There is a sudden jerk, and you feel as though a hook somewhere behind your navel was yanking you ... somewhere. <br /><br />What just happened? Something bad, whatever it was, and now you notice you're in a different place than you were.\"},\"bookmark\": 0}";
    public static final String SPLINCHED = "{\"type\": \"event\",\"content\": {\"*\": \"Ow! You were splinched! After a brief jolt (getting unsplinched isn't comfortable), you're all back together again. At least, all instances of you are in the same room.\"},\"bookmark\": 0}";

    /**
     * Create a new PlayerSession for the user.
     *
     * @param userId
     *            Name of user for this session
     * @param playerClient
     */
    public PlayerConnectionMediator(String userId, String username, String jwt, MapClient mapClient,
            PlayerClient playerClient, ConnectionUtils connectionUtils) {
        this.userId = userId;
        this.username = username;
        this.jwt = jwt;
        this.mapClient = mapClient;
        this.connectionUtils = connectionUtils;
        this.playerClient = playerClient;

        Log.log(Level.FINEST, this, "playerConnectionMediator built. currentRoom should be null at the mo.. is it? "
                + (currentRoom == null));
    }

    /**
     * @return ID of this session
     */
    public String getId() {
        return id;
    }

    /**
     * @return User ID of this session
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Re-establish connection between the mediator and the player. Check the
     * facts, make sure we are safe to resume, otherwise warp the player back to
     * first room with no fuss.
     *
     * @param clientSession
     *            The client websocket session
     * @param roomId
     *            The room id
     * @param lastmessage
     *            the last message the client saw
     */
    public void connect(Session clientSession, String roomId, long lastmessage) {
        suspendCount.set(0); // resumed

        // set up delivery thread to send messages to the client as they arrive
        drainToClient = connectionUtils.drain("TO PLAYER[" + userId + "]", toClient, clientSession);

        // Find the required room (should keep existing on a reconnect, if
        // possible)
        // will fall back to FirstRoom if we don't already have a mediator, and
        // the map service can't find the room
        currentRoom = findMediatorForRoomId(roomId);

        if (currentRoom.connect()) {
            if (roomId != null && !currentRoom.getId().equals(roomId)) {
                // WARP from one room to the other (because the player is
                // actually in another room)
                Log.log(Level.FINE, this, "User {0} warped from {1} to {2}", userId, roomId, currentRoom.getId());
                sendToClient(RoutedMessage.createMessage(Constants.PLAYER, userId, PlayerConnectionMediator.SPLINCHED));
            }
        } else {
            Log.log(Level.FINE, this, "User {0} warped from {1} to FirstRoom due to inability to connect to {2}",
                    userId, roomId, currentRoom.getId());
            sendToClient(RoutedMessage.createMessage(Constants.PLAYER, userId, PlayerConnectionMediator.BAD_RIDE));
            currentRoom = new FirstRoom(mapClient);
        }

        // Double check that all is well and the client agrees with where we are
        // after possible bumpy rides or splinch repair
        sendClientAck();

        // Start flow of messages from room to player (if not previously
        // started)
        currentRoom.subscribe(this, lastmessage);
    }

    /**
     * Stop draining the client-bound queue
     */
    public void disconnect() {
        if (drainToClient != null)
            drainToClient.stop();
    }

    /**
     * Destroy/cleanup the session (expired)
     */
    public void destroy() {
        Log.log(Level.FINE, this, "session {0} destroyed", userId);
        // session expired.
        toClient.clear();

        Log.log(Level.FINER, this,
                "playerConnectionMediator for {1} unsubscribing from currentRoom {0} and setting it to null",
                currentRoom.getId(), userId);

        currentRoom.unsubscribe(this);
        currentRoom.disconnect();
        currentRoom = null;
    }

    /**
     * Coordinate a player switching rooms. Send messages to the client to
     * indicate the transition, connect to the new room, indicate to the client
     * that they've joined the new room, and then disconnect from the old room.
     *
     * @param message
     *            RoutedMessage that contains the updated/target room
     */
    private void switchRooms(RoutedMessage message) {
        Log.log(Level.FINER, this, "SWITCH ROOMS", currentRoom, message);

        RoomMediator oldRoom = currentRoom;
        String exitId = null;
        boolean teleport = false;

        if (message != null) {
            if (message.isSOS()) {
                // we don't look for an exitId in the case of an SOS.
                sendToClient(
                        RoutedMessage.createMessage(Constants.PLAYER, userId, PlayerConnectionMediator.ELECTRIC_THUMB));
            } else {
                JsonObject exitData = message.getParsedBody();
                // If the room is firstRoom.. we might be teleporting..
                // (moving to a room directly without looking up an exit)
                if (Constants.FIRST_ROOM.equals(oldRoom.getId())) {
                    teleport = exitData.getBoolean("teleport", false);
                }
                // If we are properly exiting a room, we have the new room in
                // the payload of the message from the old room
                exitId = exitData.getString("exitId");
            }
        }

        // Part the room
        Log.log(Level.FINER, this, "GOODBYE {0}", oldRoom.getId());
        sendToClient(RoutedMessage.createMessage(Constants.PLAYER, userId,
                String.format(PlayerConnectionMediator.PART, oldRoom.getId())));
        sendToRoom(oldRoom, RoutedMessage.createMessage(Constants.ROOM_GOODBYE, oldRoom.getId(),
                String.format(PlayerConnectionMediator.HIBYE, username, userId)));

        // allow room to close connection after receiving the roomGoodbye
        oldRoom.unsubscribe(this);
        oldRoom.disconnect();

        // Find the next room.
        RoomMediator newRoom = null;
        if (!teleport) {
            System.out.println("Room change requested.. going from " + oldRoom.getName()
                    + (exitId != null ? " via " + exitId : " to firstroom"));
            sendToClient(RoutedMessage.createMessage(Constants.PLAYER, userId,
                    String.format(PlayerConnectionMediator.FINDROOM, oldRoom.getId())));

            // if exitId is still null, its an SOS
            if (exitId != null) {
                newRoom = findMediatorForExitFromRoom(oldRoom, exitId);
            } else {
                newRoom = findMediatorForRoomId(Constants.FIRST_ROOM);
            }
        } else {
            // when we are teleporting, the exitId is the destination room id.
            newRoom = findMediatorForRoomId(exitId);
        }

        boolean bumpyRide = false;
        if (newRoom != null) {
            sendToClient(RoutedMessage.createMessage(Constants.PLAYER, userId,
                    String.format(PlayerConnectionMediator.CONNECTING, newRoom.getId())));

            if (newRoom.connect()) {
                Log.log(Level.FINER, this, "playerConnectionMediator just set room for {0} to be {1}", userId,
                        newRoom.getId());

                currentRoom = newRoom;
            } else {
                bumpyRide = true;
            }
        } else {
            bumpyRide = true;
        }
        if (bumpyRide) {
            // Bumpy ride! OW.
            sendToClient(RoutedMessage.createMessage(Constants.PLAYER, userId, PlayerConnectionMediator.BAD_RIDE));
            sendToClient(RoutedMessage.createMessage(Constants.PLAYER, userId,
                    String.format(PlayerConnectionMediator.CONNECTING, currentRoom.getId())));

            if (currentRoom.connect()) {
                // we were able to reconnect to the old room.
            } else {
                currentRoom = findMediatorForRoomId(null);
            }
        }

        // Tell the client we've changed rooms
        sendToClient(RoutedMessage.createMessage(Constants.PLAYER, userId,
                String.format(PlayerConnectionMediator.JOIN, currentRoom.getName())));

        // Update client's local storage information
        sendClientAck();

        // Start flow of messages from room to player (if not previously
        // started)
        currentRoom.subscribe(this, 0);

        // Say hello to the new room!
        Log.log(Level.FINER, this, "HELLO {0}", currentRoom.getId());
        sendToRoom(currentRoom, RoutedMessage.createMessage(Constants.ROOM_HELLO, currentRoom.getId(),
                String.format(PlayerConnectionMediator.HIBYE, username, userId)));
    }

    /**
     * Route the message to the current room
     *
     * @param message
     *            RoutedMessage containing routing information and payload
     */
    public void sendToRoom(RoutedMessage message) {
        this.sendToRoom(currentRoom, message);
    }

    /**
     * Send a message on to the target room. IFF the message is actually the
     * special /sos command, switch rooms instead.
     *
     * @param targetRoom
     *            Room to write to (like the old room)
     * @param message
     *            RoutedMessage containing routing information and payload
     */
    public void sendToRoom(RoomMediator targetRoom, RoutedMessage message) {
        if (message.isSOS()) {
            switchRooms(message);
        } else {
            targetRoom.send(message);
        }
    }

    /**
     * Compose an acknowledgement to send back to the client that contains the
     * mediator id and the current room id/name.
     *
     * @return ack message with mediator id
     */
    private void sendClientAck() {
        JsonObject ack = Json.createObjectBuilder()
                .add(Constants.MEDIATOR_ID, id)
                .add(Constants.ROOM_ID, currentRoom.getId())
                .add(Constants.COMMANDS, Constants.COMMON_COMMANDS).build();

        toClient.offer(RoutedMessage.createMessage(PlayerConnectionMediator.CLIENT_ACK, ack));
    }

    /**
     * Add message to queue for delivery to the client
     *
     * @param routing
     *            RoutedMessage containing routing information and payload
     */
    public void sendToClient(RoutedMessage message) {
        // make sure we're only dealing with messages for everyone,
        // or messages for this user (ignore all others)
        if (message.isForUser(userId)) {
            // TODO: Capacity?
            toClient.offer(message);

            // If we are additionally changing locations, ...
            if (Constants.PLAYER_LOCATION.equals(message.getFlowTarget())) {
                switchRooms(message);
            }
        } else {
            Log.log(Level.FINEST, this, "sendToClient -- Dropping message {0}", message);
        }
    }

    /**
     * Find the room for the specified room id.
     *
     * @param roomId
     *            Current room id to look up.
     * @return current room mediator if it matches, or a new one if not.
     */
    protected RoomMediator findMediatorForRoomId(String roomId) {
        Log.log(Level.FINEST, this, "findRoom  {0} {1}", roomId, currentRoom);

        if (currentRoom != null) {
            if (currentRoom.getId().equals(roomId)) {
                // Room session resume
                return currentRoom;
            } else {
                // The player moved rooms somewhere along the way
                // we need to make sure we detach the old session
                currentRoom.unsubscribe(this);
                currentRoom.disconnect();
            }
        }

        if (roomId == null || roomId.isEmpty() || Constants.FIRST_ROOM.equals(roomId)) {
            return new FirstRoom(mapClient, (roomId == null || roomId.isEmpty()));
        }

        return createMediator(mapClient.getSite(roomId));
    }

    /**
     * Find the room on the other side of the specified door
     *
     * @param currentRoom
     *            The current room
     * @param exit
     *            The id of the door to look behind (directional, e.g. 'N')
     * @return A new room mediator for the room on the other side of the door
     */
    protected RoomMediator findMediatorForExitFromRoom(RoomMediator currentRoom, String exit) {
        Exit nextExit = null;
        if (exit == null) {
            // allow nextExit to remain null, and we'll default it to first
            // room.
        } else {
            nextExit = mapClient.findIdentifedExitForRoom(currentRoom, exit);
        }
        // check for empty rooms...
        if (nextExit == null || nextExit.getConnectionDetails() == null) {
            return null;
        } else {
            return createMediator(nextExit);
        }
    }

    /**
     * Create the remote mediator for the specified Exit
     *
     * @param roomEndpoints
     *            Room and possible endpoints to reach that room
     * @return new mediator, or the FirstRoom if roomEndpoints is null
     */
    protected RoomMediator createMediator(Exit exit) {
        if (exit == null || Constants.FIRST_ROOM.equals(exit.getId())) {
            // safe fallback
            sendToClient(RoutedMessage.createMessage(Constants.PLAYER, userId,
                    String.format(PlayerConnectionMediator.FINDROOM_FAIL)));
            return new FirstRoom(mapClient);
        } else {
            return new RemoteRoomMediator(exit, connectionUtils);
        }
    }

    /**
     * Create the remote mediator for the specified Site
     *
     * @param roomEndpoints
     *            Room and possible endpoints to reach that room
     * @return new mediator, or the FirstRoom if roomEndpoints is null
     */
    protected RoomMediator createMediator(Site site) {
        if (site == null) {
            // safe fallback
            sendToClient(RoutedMessage.createMessage(Constants.PLAYER, userId,
                    String.format(PlayerConnectionMediator.FINDROOM_FAIL)));
            return new FirstRoom(mapClient);
        } else {
            return new RemoteRoomMediator(site, connectionUtils);
        }
    }

    @Override
    public String toString() {
        if (currentRoom == null) {
            return this.getClass().getName() + "[userId=" + userId + "]";
        } else {
            return this.getClass().getName() + "[userId=" + userId + ", roomId=" + currentRoom.getId()
                    + ", suspendCount=" + suspendCount.get() + "]";
        }
    }

    public int incrementAndGet() {
        return suspendCount.incrementAndGet();
    }
}
