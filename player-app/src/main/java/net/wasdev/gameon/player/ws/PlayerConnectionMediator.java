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
package net.wasdev.gameon.player.ws;

import java.util.UUID;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import javax.json.Json;
import javax.json.JsonObject;
import javax.websocket.Session;

import net.wasdev.gameon.player.ws.ConciergeClient.RoomEndpointList;
import net.wasdev.gameon.player.ws.ConnectionUtils.Drain;

/**
 * A session that buffers content destined for the client devices across
 * connect/disconnects.
 */
public class PlayerConnectionMediator {

	private final String userId;
	private final String username;
	private final String jwt;
	private final String id = UUID.randomUUID().toString();

	private final ConciergeClient concierge;
	private final ConnectionUtils connectionUtils;

	/** The websocket session between this service and the client device */
	private Drain drainToClient = null;

	/** Queue of messages destined for the client device */
	private final LinkedBlockingDeque<RoutedMessage> toClient = new LinkedBlockingDeque<RoutedMessage>();

	/** The currently connected room */
	private RoomMediator currentRoom = null;

	/**
	 * Session reaping happens in cycles: we allow a few cycles
	 * before the session is actually destroyed. Session suspend. */
	private AtomicInteger suspendCount = new AtomicInteger(0);

	/**
	 * Create a new PlayerSession for the user.
	 * @param userId Name of user for this session
	 * @param playerClient
	 */
	public PlayerConnectionMediator(String userId, String username, String jwt, ConciergeClient concierge, PlayerClient playerClient, ConnectionUtils connectionUtils) {
		this.userId = userId;
		this.username = username;
		this.jwt = jwt;
		this.concierge = concierge;
		this.connectionUtils = connectionUtils;

		Log.log(Level.FINEST, this, "playerConnectionMediator built. currentRoom should be null at the mo.. is it? "+(currentRoom==null));
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
	 * Re-establish connection between the mediator and the player.
	 * Check the facts, make sure we are safe to resume, otherwise warp the player
	 * back to first room with no fuss.
	 *
	 * @param clientSession The client websocket session
	 * @param roomId The room id
	 * @param lastmessage the last message the client saw
	 */
	public void connect(Session clientSession, String roomId, long lastmessage) {
		suspendCount.set(0); // resumed

		// set up delivery thread to send messages to the client as they arrive
		drainToClient = connectionUtils.drain("TO PLAYER[" + userId + "]", toClient, clientSession);

		// Find the required room (should keep existing on a reconnect, if possible)
		// will fall back to FirstRoom if we don't already have a mediator, and the concierge
		// can't find the room
		currentRoom = findRoom(roomId);

		if ( currentRoom.connect() ) {
			if ( roomId != null && !currentRoom.getId().equals(roomId) ) {
				// WARP from one room to the other (because the player is actually in another room)
				Log.log(Level.FINE, this, "User {0} warped from {1} to {2}", userId, roomId, currentRoom.getId());
				sendToClient(RoutedMessage.createMessage(Constants.PLAYER, userId, Constants.SPLINCHED));
			}
		} else {
			Log.log(Level.FINE, this, "User {0} warped from {1} to FirstRoom due to inability to connect to {2}", userId, roomId, currentRoom.getId());
			sendToClient(RoutedMessage.createMessage(Constants.PLAYER, userId, Constants.BAD_RIDE));
			currentRoom = new FirstRoom();
		}

		// Double check that all is well and the client agrees with where we are after possible bumpy rides or splinch repair
		sendClientAck();

		// Start flow of messages from room to player (if not previously started)
		currentRoom.subscribe(this, lastmessage);
	}

	/**
	 * Stop draining the client-bound queue
	 */
	public void disconnect() {
		if ( drainToClient != null)
			drainToClient.stop();
	}

	/**
	 * Destroy/cleanup the session (expired)
	 */
	public void destroy() {
		Log.log(Level.FINE, this, "session {0} destroyed", userId);
		// session expired.
		toClient.clear();

		Log.log(Level.FINER, this, "playerConnectionMediator for {1} unsubscribing from currentRoom {0} and setting it to null", currentRoom.getId(), userId);

		currentRoom.unsubscribe(this);
		currentRoom.disconnect();
		currentRoom = null;
	}

	/**
	 * Coordinate a player switching rooms. Send messages to the client
	 * to indicate the transition, connect to the new room, indicate to the
	 * client that they've joined the new room, and then disconnect from the old room.
	 *
	 * @param message RoutedMessage that contains the updated/target room
	 */
	private void switchRooms(RoutedMessage message) {
		Log.log(Level.FINER, this, "SWITCH ROOMS", currentRoom, message);

		RoomMediator oldRoom = currentRoom;
		String exitId = null;

		if ( message != null ) {
			if ( message.isSOS() ) {
				// we don't look for an exitId in the case of an SOS.
				sendToClient(RoutedMessage.createMessage(Constants.PLAYER, userId, Constants.ELECTRIC_THUMB));
			} else {
				// If we are properly exiting a room, we have the new room in the payload
				// of the message from the old room.
				JsonObject exitData = message.getParsedBody();
				exitId = exitData.getString("exitId");
			}
		}

		// Part the room
		Log.log(Level.FINER, this, "GOODBYE {0}", oldRoom.getId());
		sendToClient(RoutedMessage.createMessage(Constants.PLAYER, userId, String.format(Constants.PART, oldRoom.getId())));
		sendToRoom(oldRoom,
				RoutedMessage.createMessage(Constants.ROOM_GOODBYE,
						oldRoom.getId(),
						String.format(Constants.HIBYE, username, userId)));

		// allow room to close connection after receiving the roomGoodbye
		oldRoom.unsubscribe(this);
		oldRoom.disconnect();

		// Find the next room.
		sendToClient(RoutedMessage.createMessage(Constants.PLAYER, userId, String.format(Constants.FINDROOM, oldRoom.getId())));
		RoomMediator newRoom = findNextRoom(oldRoom, exitId);

		sendToClient(RoutedMessage.createMessage(Constants.PLAYER, userId, String.format(Constants.CONNECTING, newRoom.getId())));
		if ( newRoom.connect() ) {
			Log.log(Level.FINER, this, "playerConnectionMediator just set room for {0} to be {1}", userId, newRoom.getId());
			currentRoom = newRoom;
		} else {
			// Bumpy ride! OW.
			sendToClient(RoutedMessage.createMessage(Constants.PLAYER, userId, Constants.BAD_RIDE));
			sendToClient(RoutedMessage.createMessage(Constants.PLAYER, userId, String.format(Constants.CONNECTING, currentRoom.getId())));

			if ( currentRoom.connect() ) {
				// we were able to reconnect to the old room.
			} else {
				currentRoom = findRoom(null);
			}
		}

		sendToClient(RoutedMessage.createMessage(Constants.PLAYER, userId, String.format(Constants.JOIN, currentRoom.getName())));
		// Tell the client we've changed rooms
		sendClientAck();

		// Start flow of messages from room to player (if not previously started)
		currentRoom.subscribe(this, 0);

		Log.log(Level.FINER, this, "HELLO {0}", currentRoom.getId());
		sendToRoom(currentRoom,
				RoutedMessage.createMessage(Constants.ROOM_HELLO,
						currentRoom.getId(),
						String.format(Constants.HIBYE, username, userId)));
	}

	/**
	 * Route the message to the current room
	 * @param message RoutedMessage containing routing information and payload
	 */
	public void sendToRoom(RoutedMessage message) {
		this.sendToRoom(currentRoom, message);
	}

	/**
	 * Send a message on to the target room. IFF the message is actually the
	 * special /sos command, switch rooms instead.
	 * @param targetRoom Room to write to (like the old room)
	 * @param message RoutedMessage containing routing information and payload
	 */
	public void sendToRoom(RoomMediator targetRoom, RoutedMessage message) {
		if ( message.isSOS() ) {
			switchRooms(message);
		} else {
			targetRoom.send(message);
		}
	}

	/**
	 * Compose an acknowledgement to send back to the client that
	 * contains the mediator id and the current room id/name.
	 *
	 * @return ack message with mediator id
	 */
	private void sendClientAck() {
		JsonObject ack = Json.createObjectBuilder()
				.add(Constants.MEDIATOR_ID, id)
				.add(Constants.ROOM_ID, currentRoom.getId())
				.add(Constants.ROOM_NAME, currentRoom.getName())
				.build();

		toClient.offer(RoutedMessage.createMessage(Constants.CLIENT_ACK, ack));
	}

	/**
	 * Add message to queue for delivery to the client
	 * @param routing RoutedMessage containing routing information and payload
	 */
	public void sendToClient(RoutedMessage message) {
		// make sure we're only dealing with messages for everyone,
		// or messages for this user (ignore all others)
		if ( message.isForUser(userId) ){
			// TODO: Capacity?
			toClient.offer(message);

			// If we are additionally changing locations, ...
			if ( Constants.PLAYER_LOCATION.equals(message.getFlowTarget())) {
				switchRooms(message);
			}
		} else {
			Log.log(Level.FINEST, this, "sendToClient -- Dropping message {0}", message);
		}
	}

	/**
	 * Find the room for the specified room id.
	 *
	 * @param roomId Current room id to look up.
	 * @return current room mediator if it matches, or a new one if not.
	 */
	protected RoomMediator findRoom(String roomId) {
		Log.log(Level.FINEST, this, "findRoom  {0} {1}", roomId, currentRoom);

		if ( currentRoom != null ) {
			if ( currentRoom.getId().equals(roomId)) {
				// Room session resume
				return currentRoom;
			} else {
				// The player moved rooms somewhere along the way
				// we need to make sure we detach the old session
				currentRoom.unsubscribe(this);
				currentRoom.disconnect();
			}
		}

		if ( roomId == null || roomId.isEmpty() || Constants.FIRST_ROOM.equals(roomId) ) {
			return new FirstRoom((roomId == null || roomId.isEmpty()));
		}


		return createMediator(concierge.getRoomEndpoints(roomId));
	}

	/**
	 * Find the room on the other side of the specified door
	 * @param currentRoom The current room
	 * @param exit The id of the door to look behind (directional, e.g. 'N')
	 * @return A new room mediator for the room on the other side of the door
	 */
	protected RoomMediator findNextRoom(RoomMediator currentRoom, String exit) {
		if ( exit == null || Constants.FIRST_ROOM.equals(currentRoom.getId()) ) {
			return createMediator(concierge.findNextRoom(currentRoom, null));
		}
		return createMediator(concierge.findNextRoom(currentRoom, exit));
	}

	/**
	 * Create the remote mediator for the specified Room/EndpointList
	 * @param roomEndpoints Room and possible endpoints to reach that room
	 * @return new mediator, or the FirstRoom if roomEndpoints is null
	 */
	protected RoomMediator createMediator(RoomEndpointList roomEndpoints) {
		if ( roomEndpoints == null ) {
			// safe fallback
			sendToClient(RoutedMessage.createMessage(Constants.PLAYER, userId, String.format(Constants.FINDROOM_FAIL)));
			return new FirstRoom();
		} else {
			return new RemoteRoomMediator(roomEndpoints, connectionUtils);
		}
	}

	@Override
	public String toString() {
		if ( currentRoom == null ) {
			return this.getClass().getName()
					+ "[userId=" + userId +"]";
		} else {
			return this.getClass().getName()
					+ "[userId=" + userId
					+ ", roomId=" + currentRoom.getId()
					+ ", suspendCount=" + suspendCount.get() +"]";
		}
	}

	public int incrementAndGet() {
		return suspendCount.incrementAndGet();
	}
}
