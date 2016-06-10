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
package net.wasdev.gameon.mediator;

import java.util.ConcurrentModificationException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Level;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonObject;

import net.wasdev.gameon.mediator.RoutedMessage.FlowTarget;
import net.wasdev.gameon.mediator.room.RoomMediator;
import net.wasdev.gameon.mediator.room.RoomMediator.Type;

/**
 * Clients (Players) subscribed to listen to messages for
 * a given room.
 *
 */
@ApplicationScoped
public class MediatorNexus  {

    public interface View {
        void sendToClients(RoutedMessage message);

        boolean stillConnected();
    }

    public interface UserView {
        String getUserId();

        String getUserName();
    }

    @Inject
    PlayerClient playerClient;

    MediatorBuilder mediatorBuilder;

    // UserId to ClientMediators (client websocket clientMediators)
    protected final ConcurrentHashMap<String, ClientMediatorPod> clientMap = new ConcurrentHashMap<>();

    // Room Id to list of MediatorPods
    protected final ConcurrentHashMap<String, PodsByRoom> roomClients = new ConcurrentHashMap<>();

    /**
     * Set the builder used by the nexus.
     *
     * Ordering is important here: The nexus is injected into the builder
     * because the builder uses the nexus to create client mediators.
     * The builder then sets itself here, because the nexus needs the builder to create
     * room mediators.
     * @param builder
     */
    public void setBuilder(MediatorBuilder builder) {
        this.mediatorBuilder = builder;
    }

    /**
     * Have a new session join: if there are existing clientMediators, this may trigger
     * some yanking around.
     *
     * @param playerSession
     * @param newRoom
     * @return
     */
    public void join(ClientMediator playerSession, String newRoomId, String lastMessage) {
        ClientMediatorPod pod = getCreatePod(playerSession);

        Log.log(Level.FINER, playerSession.getSource(), "{0}: request join to {1} with lastmessage {2}", Log.getHexHash(pod), newRoomId, lastMessage);

        // See if there is already a mediator for this cluster of user clientMediators
        // and/or negotiate which room should be used.
        pod.join(playerSession, newRoomId, lastMessage);
    }

    /**
     * Transition the player from one location to the next
     *
     * @param playerSession
     * @param oldRoom
     * @param newRoom
     * @return true if location changed (hello/goodbye should be sent)
     * @throws ConcurrentModificationException if change can not be made
     */
    public void transition(ClientMediator playerSession, String toRoomId) {
        ClientMediatorPod pod = getCreatePod(playerSession);
        RoomMediator room = playerSession.getRoomMediator();

        String fromRoom = room == null ? null : room.getId(); // snapshot
        Log.log(Level.FINER, playerSession.getSource(), "{0}: request transition from expected={1}, new={2}",
                Log.getHexHash(pod), fromRoom, toRoomId);

        pod.transition(playerSession, fromRoom, toRoomId);
    }

    /**
     * Transition the player from one location to the next
     *
     * @param playerSession
     * @param oldRoom
     * @param newRoom
     * @return true if location changed (hello/goodbye should be sent)
     * @throws ConcurrentModificationException if change can not be made
     */
    public void transitionViaExit(ClientMediator playerSession, String direction) {
        ClientMediatorPod pod = getCreatePod(playerSession);
        RoomMediator room = playerSession.getRoomMediator();

        String fromRoom = room == null ? null : room.getId(); // snapshot
        Log.log(Level.FINER, playerSession.getSource(), "{0}: request transition from expected={1}, direction={2}",
                Log.getHexHash(pod), fromRoom, direction);

        pod.transitionViaExit(playerSession, fromRoom, direction);
    }

    /**
     * Remove the player session from tracking: no location change
     * @param playerSession
     */
    public void part(ClientMediator playerSession) {
        ClientMediatorPod pod = clientMap.get(playerSession.getUserId());
        if ( pod != null ) {
            Log.log(Level.FINER, playerSession.getSource(), "{0}: request part", Log.getHexHash(pod));

            pod.part(playerSession);
        }
    }

    private ClientMediatorPod getCreatePod(ClientMediator playerSession) {
        return clientMap.computeIfAbsent(playerSession.getUserId(), k -> new ClientMediatorPod(playerSession.getUserId()));
    }

    private PodsByRoom getCreatePlayerList(String roomId) {
        return roomClients.computeIfAbsent(roomId, k -> new PodsByRoom(roomId));
    }

    private PodsByRoom removeDeleteEmptyPlayerList(String roomId, ClientMediatorPod pod) {
        if ( roomId != null ) {
            return roomClients.computeIfPresent(roomId, (k,v) -> v.remove(pod));
        }
        return null;
    }


    /**
     * This maps a player (multiple clientMediators) with a room mediator
     *
     */
    public class ClientMediatorPod implements UserView {
        final String userId;
        final Set<ClientMediator> clientMediators;
        volatile String userName;
        volatile RoomMediator room;

        private ClientMediatorPod(String userId) {
            this.userId = userId;
            this.clientMediators = new CopyOnWriteArraySet<>();
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
            ClientMediator m = clientMediators.iterator().next();
            m.getUserJwt();
            return null;
        }

        /**
         * Send message to all connected client mediators
         * @param message
         */
        public void send(RoutedMessage message) {
            if (message.isForUser(userId)) {
                clientMediators.forEach(s -> s.sendToClient(message));

                // If we are additionally changing locations, ...
                if (message.getFlowTarget() == FlowTarget.playerLocation ) {
                    ClientMediator m = clientMediators.iterator().next();
                    m.switchRooms(message);
                }
            } else if ( !clientMediators.isEmpty() ){
                Log.log(Level.FINEST, this, "send -- Dropping message as not for user {0}, destination={0},{1}", userId, message.getFlowTarget(), message.getDestination());
            }
        }

        /**
         * Send message to all connected client mediators IFF the room type
         * is the same, which is significant for sick/connecting/empty
         * rooms
         *
         * @param message
         */
        public void filteredSend(Type roomType, RoutedMessage message) {
            if( room.getType() == roomType )
                send(message);
        }

        /**
         * Join/Add a new client session to the pod. The newRoomId may or may not match
         * the existing/connected roomId, in which case, negotiation may be required
         * @param playerSession
         * @param newRoomId
         * @param lastMessage
         */
        private synchronized void join(ClientMediator playerSession, String newRoomId, String lastMessage) {
            String targetId = newRoomId;
            boolean joinRoom = clientMediators.isEmpty(); // were we first?
            boolean helloInstead = joinRoom && isEmptyBookmark(lastMessage);

            clientMediators.add(playerSession);
            userName = playerSession.getUserName();

            Log.log(Level.FINER, playerSession.getSource(), "{0}: pre-join -- session added for {1}, target={2} ({3}, {4}): {5}",
                    Log.getHexHash(this), userId, targetId, joinRoom, helloInstead, clientMediators);

            if ( targetId == null || targetId.isEmpty() )
                targetId = Constants.FIRST_ROOM;

            if ( room == null ) {
                // create new room mediator: we'te the first in
                room = mediatorBuilder.findMediatorForRoom(this, targetId);
                playerSession.setRoomMediator(room, false);
                playerSession.sendToClient(clientAck());
            } else if ( targetId.equals(room.getId())) {
                // easy, no conflicts, just join the existing session
                playerSession.setRoomMediator(room, false);
                playerSession.sendToClient(clientAck());
            } else {
                // TODO & conflict: existing clientMediators are somewhere different than new
                // session: which is the right value? the one that came in or the (possibly older)
                // one? -- need to check w/ player service and sort it.

                // For now: just make sure everyone is in the same room, and don't worry about
                // the new arrival.
                RoutedMessage ack = clientAck(); // send to all
                clientMediators.forEach(s -> {
                    s.setRoomMediator(room, true);
                    s.sendToClient(ack);
                });

                Log.log(Level.FINER, playerSession.getSource(), "{0}: post-join -- splinch recovery for {1}, room={2} ({3}, {4}): {5}",
                        Log.getHexHash(this), userId, room.getId(), joinRoom, helloInstead, clientMediators);
                return;
            }

            // Update the players-by-room index to contain this player
            PodsByRoom newPlayers = getCreatePlayerList(targetId);
            newPlayers.add(this);

            // Specific to the joining session:
            if ( helloInstead ) {
                Log.log(Level.FINER, playerSession.getSource(), "HELLO {0} {1}", userId, room.getId());
                room.hello(this, false);
            } else if ( joinRoom ) {
                Log.log(Level.FINER, playerSession.getSource(), "JOIN {0} {1}", userId, room.getId());
                room.join(this);
            }

            Log.log(Level.FINER, playerSession.getSource(), "{0}: post-join -- session added for {1}, room={2} ({3}, {4}): {5}",
                    Log.getHexHash(this), userId, room.getId(), joinRoom, helloInstead, clientMediators);
        }

        private boolean isEmptyBookmark(String lastMessage) {
            return ( lastMessage == null || lastMessage.isEmpty() || "0".equals(lastMessage) );
        }

        private synchronized void transition(ClientMediator playerSession, String fromRoomId, String targetRoomId) {
            if ( room == null ) {
                join(playerSession, targetRoomId, "");
                return;
            }

            String toRoomId = targetRoomId;
            String currentId = room.getId();

            Log.log(Level.FINER, playerSession.getSource(), "{0}: pre-transition for {1} from pod={2}, expected={3}, new={4}. Connected: {5}",
                    Log.getHexHash(this), userId, currentId, fromRoomId, toRoomId, clientMediators);

            if ( toRoomId == null || toRoomId.isEmpty() )
                toRoomId = Constants.FIRST_ROOM;

            if ( currentId.equals(toRoomId) ) {
                // nothing to do
                playerSession.sendToClient(RoutedMessage.createSimpleEventMessage(FlowTarget.player, playerSession.getUserId(),
                        Constants.EVENTMSG_ALREADY_THERE));
            } else if ( currentId.equals(fromRoomId) ) {
                RoomMediator newRoom = mediatorBuilder.findMediatorForRoom(this, toRoomId);
                performSwitch(newRoom);
            } else  {
                Log.log(Level.WARNING, playerSession.getSource(), "{0}: {1} could not be moved from pod={2}, expected={3}, new={4}. Connected: {5}",
                        Log.getHexHash(this), userId, currentId, fromRoomId, toRoomId, clientMediators);

                // For now: Make sure the caller is in the right place
                playerSession.setRoomMediator(room, true);
                playerSession.sendToClient(clientAck());

                // if the clients aren't in the old location, and they aren't in the new location, bail
                // caller will catch
                throw new ConcurrentModificationException(userId + " could not be moved."
                        + " pod=" + currentId
                        + ", expected=" + fromRoomId
                        + ", new=" + toRoomId);
            }

            Log.log(Level.FINER, playerSession.getSource(), "{0}: post-transition for {1} now in {2}({3}). Connected: {4}",
                    Log.getHexHash(this), userId, room.getName(), room.getId(), clientMediators);
        }

        /**
         * @param playerSession
         * @param fromRoomId
         * @param direction
         */
        private synchronized void transitionViaExit(ClientMediator playerSession, String fromRoomId, String direction) {
            if ( room == null ) {
                join(playerSession, fromRoomId, "");
                return;
            }

            String currentId = room.getId();

            Log.log(Level.FINER, playerSession.getSource(), "{0}: pre-transition-via-exit for {1} from pod={2}, expected={3}, direction={4}. Connected: {5}",
                    Log.getHexHash(this), userId, currentId, fromRoomId, direction, clientMediators);

            if ( currentId.equals(fromRoomId) ) {
                RoomMediator newRoom = mediatorBuilder.findMediatorForExit(this, room, direction);
                performSwitch(newRoom);
            } else {
                Log.log(Level.WARNING, playerSession.getSource(), "{0}: post-transition-via-exit for {1} -- could not be moved from pod={2}, expected={3}, direction={4}. Connected: {5}",
                        Log.getHexHash(this), userId, currentId, fromRoomId, direction, clientMediators);

                // For now: Make sure the caller is in the right place
                playerSession.setRoomMediator(room, true);
                playerSession.sendToClient(clientAck());

                // the player isn't where we think they are..
                throw new ConcurrentModificationException(userId + " could not be moved."
                        + " pod=" + currentId
                        + ", expected=" + fromRoomId
                        + ", direction=" + direction);
            }

            Log.log(Level.FINER, playerSession.getSource(), "{0}: post-transition-via-exit for {1} now in {2}({3}). Connected: {4}",
                    Log.getHexHash(this), userId, room.getName(), room.getId(), clientMediators);
        }

        /**
         * Perform the actual transition between rooms: not synchronized, as all callers are.
         * @param newRoom
         */
        private void performSwitch(RoomMediator newRoom) {
            if ( newRoom != room ) {
                RoomMediator oldRoom = room;

                // Add this pod to the index with the new room id
                PodsByRoom newPlayers = getCreatePlayerList(newRoom.getId());
                newPlayers.add(this);

                // remove this pod from the old room index
                oldRoom.goodbye(this);
                removeDeleteEmptyPlayerList(oldRoom.getId(), this);

                // assign the new room
                room = newRoom;

                // Assign the new room mediator to each of the client mediators for this player
                RoutedMessage ack = clientAck();
                clientMediators.forEach(s -> {
                    s.setRoomMediator(newRoom, false);
                    s.sendToClient(ack);
                });

                // say hello to the room
                newRoom.hello(this, false);
            }
        }

        /**
         * Called when the client session is disconnected.
         * @param playerSession
         */
        private synchronized void part(ClientMediator playerSession) {

            if ( clientMediators.contains(playerSession) && clientMediators.size() == 1 ) {
                // we're the last session standing: part the room.
                room.part(this);

                // self-cleaning. This element is about to vanish, so remove it from the room-indexed list, too
                removeDeleteEmptyPlayerList(room.getId(), this);

                clientMap.remove(userId); // Auto-cleanup! We're empty!
            }

            // do this last, after room part
            clientMediators.remove(playerSession);

            Log.log(Level.FINEST, playerSession.getSource(), "{0}: session removed for {1}, connected: {2}",
                    Log.getHexHash(this), userId, clientMediators);
        }

        /**
         * Compose an acknowledgement to send back to the client that contains the
         * mediator id and information about the current room (to set up/refresh the
         * local cache).
         *
         * @return ack message with mediator id
         */
        public RoutedMessage clientAck() {
            JsonObject ack = Json.createObjectBuilder()
                    .add(Constants.KEY_MEDIATOR_ID, Constants.MEDIATOR_UUID)
                    .add(Constants.KEY_ROOM_ID, room.getId())
                    .add(Constants.KEY_ROOM_NAME, room.getName())
                    .add(Constants.KEY_ROOM_FULLNAME, room.getFullName())
                    .add(Constants.KEY_ROOM_EXITS, room.listExits())
                    .add(Constants.KEY_COMMANDS, Constants.COMMON_COMMANDS).build();

            return RoutedMessage.createMessage(FlowTarget.ack, ack);
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getOuterType().hashCode();
            result = prime * result + ((userId == null) ? 0 : userId.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            ClientMediatorPod other = (ClientMediatorPod) obj;
            if (!getOuterType().equals(other.getOuterType()))
                return false;
            if (userId == null) {
                if (other.userId != null)
                    return false;
            } else if (!userId.equals(other.userId))
                return false;
            return true;
        }

        @Override
        public String toString() {
            return this.getClass().getSimpleName()
                    + "[sessionsEmpty="+clientMediators.isEmpty()
                    + ", room=" + (room == null ? "null" : room.getName() + "(" + room.getId() + ")")
                    + "]";
        }

        private MediatorNexus getOuterType() {
            return MediatorNexus.this;
        }
    }

    /**
     * Local rooms need a general broadcast across all
     * users in the room (empty, sick, firstroom, .. )
     */
    public class MultiUserView implements View {
        final String roomId;

        private MultiUserView(String roomId) {
            this.roomId = roomId;
        }

        @Override
        public void sendToClients(RoutedMessage message) {

            if ("*".equals(message.getDestination()) ) {
                PodsByRoom list = roomClients.get(roomId);
                Log.log(Level.FINEST, this, "MUV-broadcast({0}): Send {1} to {2}",
                        list.sessionPods.isEmpty(), message);

                for( ClientMediatorPod cm : list.sessionPods ) {
                    cm.send(message);
                }
            } else {
                ClientMediatorPod p = clientMap.get(message.getDestination());
                Log.log(Level.FINEST, this, "MUV-send({0}): Send {1} to {2}",
                        stillConnected(), message, p);

                if ( p != null )
                    p.send(message);
            }
        }

        @Override
        public boolean stillConnected() {
            PodsByRoom list = roomClients.get(roomId);
            if ( list == null )
                return false;

            return list.sessionPods.isEmpty();
        }
    }

    public class FilteredMultiUserView implements View {
        final String roomId;
        final RoomMediator.Type roomType;

        private FilteredMultiUserView(String roomId, RoomMediator.Type type) {
            this.roomId = roomId;
            this.roomType = type;
        }

        @Override
        public void sendToClients(RoutedMessage message) {

            if ("*".equals(message.getDestination()) ) {
                PodsByRoom list = roomClients.get(roomId);
                Log.log(Level.FINEST, this, "FMUV-broadcast({0}/{1}): Send {2} to {3}",
                        roomType, list.sessionPods.isEmpty(), message, list);

                for( ClientMediatorPod cm : list.sessionPods ) {
                    cm.filteredSend(roomType, message);
                }
            } else {
                ClientMediatorPod p = clientMap.get(message.getDestination());
                Log.log(Level.FINEST, this, "FMUV-send({0}): Send {1} to {2}",
                        stillConnected(), message, p);

                if ( p != null )
                    p.send(message);
            }
        }

        @Override
        public boolean stillConnected() {
            PodsByRoom list = roomClients.get(roomId);
            if ( list == null )
                return false;

            return list.sessionPods.isEmpty();
        }
    }


    /**
     * Remote rooms have their own broadcast. The Remote room
     * should only send to the client mediator pod that is their connections.
     */
    public class SingleUserView implements View {
        final ClientMediatorPod connectedClients;

        private SingleUserView(ClientMediatorPod connectedClients) {
            this.connectedClients = connectedClients;
        }

        @Override
        public void sendToClients(RoutedMessage message) {
            Log.log(Level.FINEST, this, "Single-user view {0}: Send {1} to {2}",
                    stillConnected(), message, connectedClients);

            if ( stillConnected() ) {
                connectedClients.send(message);
            }
        }

        @Override
        public boolean stillConnected() {
            return !connectedClients.clientMediators.isEmpty();
        }
    }

    /**
     * This holds references to the ClientMediatorPods for each room.
     */
    private class PodsByRoom {
        final String roomId;
        final Set<ClientMediatorPod> sessionPods; // iteration by rooms to

        private PodsByRoom(String roomId) {
            this.roomId = roomId;
            this.sessionPods = ConcurrentHashMap.newKeySet();
        }

        private void add(ClientMediatorPod player) {
            Log.log(Level.FINEST, this, "PodsByRoom {0}: add {1}", roomId, player);
            sessionPods.add(player);
        }

        private PodsByRoom remove(ClientMediatorPod player) {
            Log.log(Level.FINEST, this, "PodsByRoom {0}: remove {1}", roomId, player);
            sessionPods.remove(player);

            if ( sessionPods.isEmpty() ) {
                Log.log(Level.FINEST, player, "PodsByRoom Element removed {0}", roomId);

                // return null to auto-remove the element from the containing map
                return null;
            }

            // this value is unchanged in the containing map
            return this;
        }

        @Override
        public String toString() {
            return this.getClass().getSimpleName()
                    + "[roomId=" + roomId
                    + ", sessionPods=" + sessionPods
                    + "]";
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getOuterType().hashCode();
            result = prime * result + ((roomId == null) ? 0 : roomId.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            PodsByRoom other = (PodsByRoom) obj;
            if (!getOuterType().equals(other.getOuterType()))
                return false;
            if (roomId == null) {
                if (other.roomId != null)
                    return false;
            } else if (!roomId.equals(other.roomId))
                return false;
            return true;
        }

        private MediatorNexus getOuterType() {
            return MediatorNexus.this;
        }
    }


    public View getMultiUserView(String roomId) {
        return new MultiUserView(roomId);
    }

    public View getFilteredMultiUserView(String roomId, RoomMediator.Type type) {
        return new FilteredMultiUserView(roomId, type);
    }

    public View getSingleUserView(String roomId, UserView user) {
        return new SingleUserView(clientMap.get(user.getUserId()));
    }
}
