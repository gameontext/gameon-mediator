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

import java.util.ConcurrentModificationException;
import java.util.logging.Level;

import net.wasdev.gameon.mediator.MediatorNexus.UserView;
import net.wasdev.gameon.mediator.RoutedMessage.FlowTarget;
import net.wasdev.gameon.mediator.room.FirstRoom;
import net.wasdev.gameon.mediator.room.RoomMediator;
import net.wasdev.gameon.mediator.room.RoomMediator.Type;

/**
 * The ClientMediator: mediates the inbound connection from the client device. Handles
 * room transitions.
 */
public class ClientMediator implements UserView {

    /**
     * The player's userId. The room will broadcast to all connected clientMediators in
     * the event that two player devices are connected (e.g.). The mediator will
     * filter messages, and only relay those directed to all sessionPods or to the
     * specific player.
     */
    private final String userId;

    /** Session/Room coordination */
    private final MediatorNexus nexus;

    /** Drain for messages headed to client */
    private final Drain toClient;

    /** Signed JWT for outbound requests on behalf of this session */
    private String signedJwt;

    /**
     * The player's username. Sent to the room with playerHello and
     * playerGoodbye (which are mediator-initiated messages).
     */
    private String userName;

    /** The mediator for the connected room */
    private volatile RoomMediator roomMediator = null;

    public ClientMediator(MediatorNexus nexus, Drain drain, String userId, String signedJwt) {
        this.nexus = nexus;
        this.userId = userId;
        this.toClient = drain;
        this.signedJwt = signedJwt;

        toClient.start();
    }

    @Override
    public String getUserId() {
        return userId;
    }

    @Override
    public String getUserName() {
        return userName;
    }

    public String getUserJwt() {
        return signedJwt;
    }

    public RoomMediator getRoomMediator() {
        return roomMediator;
    }

    /**
     * Called by the Nexus to change the mediator for the session.
     * Join/part indications should be sent to _each client session_
     * @param splinched
     * @param newMediator
     */
    public void setRoomMediator(RoomMediator targetRoom, boolean splinched) {
        Log.log(Level.FINEST, toClient, "setRoomMediator -- {0} {1}", splinched, targetRoom);

        if ( roomMediator == null || ! roomMediator.getId().equals(targetRoom.getId()) ) {
            if ( splinched ) {
                sendToClient(RoutedMessage.createSimpleEventMessage(FlowTarget.player, userId, Constants.EVENTMSG_SPLINCH_RECOVERY));
            } else {
                // if actually changing rooms, send messages to client to show room transition
                if ( roomMediator != null ) {
                    sendToClient(RoutedMessage.createMessage(FlowTarget.player, userId, String.format(Constants.PART, roomMediator.getFullName())));
                }

                sendToClient(RoutedMessage.createMessage(FlowTarget.player, userId, String.format(Constants.JOIN, targetRoom.getFullName())));
            }
        }

        // always pick up the new mediator instance, refresh client cached information
        roomMediator = targetRoom;
    }

    /**
     * Called from onMessage when the ready message is received
     * @param message
     */
    public void ready(RoutedMessage message) {
        userName = message.getString(Constants.KEY_USERNAME, "anonymous");

        String roomId = message.getString(Constants.KEY_ROOM_ID);
        String lastmessage = message.getString(Constants.KEY_BOOKMARK, "");

        // Join a room: this will come back via setRoomMediator
        nexus.join(this, roomId, lastmessage);
    }

    public void handleMessage(RoutedMessage message) {
        Log.log(Level.FINEST, toClient, "handleMessage -- {0}", message);
        if ( roomMediator != null ) {
            if ( message.isSOS() ) {
                switchRooms(message);
            } else {
                roomMediator.sendToRoom(message);
            }
        }
    }

    public void switchRooms(RoutedMessage message) {
        Log.log(Level.FINER, toClient, "SWITCH ROOMS", message, roomMediator);

        boolean teleport = false;
        String exitId = null;

        try {
            if ( message.isSOS() ) {
                // we don't look for an exitId in the case of an SOS.
                sendToClient(RoutedMessage.createMessage(FlowTarget.player, userId, Constants.EXIT_ELECTRIC_THUMB));

                // coordinate across instances of player, call #setRoomMediator
                nexus.transition(this, Constants.FIRST_ROOM);
            } else {
                // If we are properly exiting a room, we have the direction we should
                // go (or the room id for teleport) in the payload of the playerLocation message
                exitId = message.getString("exitId");

                // If the room is firstRoomInfo.. we might be teleporting..
                // (moving to a room directly without looking up an exit)
                if (roomMediator.getType() == Type.FIRST_ROOM) {
                    teleport = message.getBoolean(FirstRoom.TELEPORT, false);
                }

                if (teleport) {
                    // when we are teleporting, the exitId is the destination room id.
                    // coordinate across instances of player, call #setRoomMediator
                    nexus.transition(this, exitId);
                } else {
                    if (exitId == null) {
                        // coordinate across instances of player, call #setRoomMediator
                        nexus.transition(this, Constants.FIRST_ROOM);
                    } else {
                        // coordinate across instances of player, call #setRoomMediator
                        nexus.transitionViaExit(this, exitId);
                    }
                }
            }
        } catch(ConcurrentModificationException cem) {
            // hmm ... we tried to move, but something/someone else moved us first
            toClient.send(RoutedMessage.createSimpleEventMessage(FlowTarget.player, userId,
                    Constants.EVENTMSG_MOVING));
        }
    }

    public void destroy() {
        Log.log(Level.FINER, toClient, "DESTROY");
        nexus.part(this);
        toClient.stop();
    }

    /**
     * @param message
     */
    public void sendToClient(RoutedMessage message) {
        // make sure we're only dealing with messages for everyone,
        // or messages for this user (ignore all others)
        if (message.isForUser(userId)) {
            toClient.send(message);
        } else {
            Log.log(Level.FINEST, toClient, "sendToClient -- Dropping message {0}", message);
        }
    }

    public Object getSource() {
        return toClient;
    }

    @Override
    public String toString() {
        return "ClientMediator[userId="+userId+", userName="+userName+", instance="+Log.getHexHash(toClient)+"]";
    }
}
