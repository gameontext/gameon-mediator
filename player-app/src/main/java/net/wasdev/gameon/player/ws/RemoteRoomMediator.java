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

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.logging.Level;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;

import net.wasdev.gameon.player.ws.ConciergeClient.RoomEndpointList;
import net.wasdev.gameon.player.ws.ConnectionUtils.Drain;

public class RemoteRoomMediator implements RoomMediator {

	private final RoomEndpointList endpointInfo;
	private final ConnectionUtils connectionUtils;

	private Session roomSession;

	private volatile PlayerConnectionMediator playerSession;
	private Drain drainToRoom = null;

	/** Queue of messages destined for the client device */
	private final LinkedBlockingDeque<RoutedMessage> toRoom = new LinkedBlockingDeque<RoutedMessage>();

	/**
	 * @param threadFactory
	 */
	public RemoteRoomMediator(RoomEndpointList roomEndpointList, ConnectionUtils connectionUtils) {
		this.endpointInfo = roomEndpointList;
		this.connectionUtils = connectionUtils;
	}

	@Override
	public String getId() {
		return endpointInfo.getRoomId();
	}

	@Override
	public String getName() {
		return endpointInfo.getRoomName();
	}

	/**
	 * Attempt to establish the connection to the remote room (if not already established)
	 * @see net.wasdev.gameon.player.ws.RoomMediator#connect()
	 */
	@Override
	public boolean connect() {
		if ( roomSession != null && roomSession.isOpen() ) {
			return true;
		}

		Log.log(Level.FINE, this, "Creating connection to room {0}", endpointInfo);
		final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create()
				.decoders(Arrays.asList(RoutedMessageDecoder.class))
				.encoders(Arrays.asList(RoutedMessageEncoder.class))
				.build();

		for(String urlString : endpointInfo.getEndpoints() ) {
			// try each in turn, return as soon as we successfully connect
			URI uriServerEP = URI.create(urlString);

			try {
				// Create the new outbound session with a programmatic endpoint
				Session s = connectionUtils.connectToServer(new Endpoint() {

					@Override
					public void onOpen(Session session, EndpointConfig config) {
						// let the room mediator know the connection was opened
						connectionOpened(session);

						// Add message handler
						session.addMessageHandler(new MessageHandler.Whole<RoutedMessage>() {
							@Override
							public void onMessage(RoutedMessage message) {
								Log.log(Level.FINEST, session, "received from room {0}: {1}", getId(), message);
								if ( playerSession != null )
									playerSession.sendToClient(message);
							}
						});
					}

					@Override
					public void onClose(Session session, CloseReason closeReason) {
						// let the room mediator know the connection was closed
						connectionClosed(closeReason);
					}

					@Override
					public void onError(Session session, Throwable thr) {
						Log.log(Level.FINEST, this, "BADNESS " + session.getUserProperties(), thr);

						connectionUtils.tryToClose(session,
								new CloseReason(CloseReason.CloseCodes.UNEXPECTED_CONDITION, thr.toString()));
					}
				}, cec, uriServerEP);
				Log.log(Level.FINEST, s, "CONNECTED to room {0}", endpointInfo.getRoomId());

				return true;
			} catch (DeploymentException e) {
				Log.log(Level.FINER, this, "Deployment exception creating connection to room " + endpointInfo.getRoomId(), e);
			} catch (IOException e) {
				Log.log(Level.FINER, this, "I/O exception creating connection to room " + endpointInfo.getRoomId(), e);
			}
		}

		return false;
	}

	/**
	 * Subscribe to the room: both open the websocket AND start routing incoming
	 * messages to the player session.
	 *
	 * @param playerSession
	 */
	@Override
	public boolean subscribe(PlayerConnectionMediator playerSession, long lastMessage) {
		this.playerSession = playerSession;
		return true;
	}

	/**
	 * @param playerSession
	 */
	@Override
	public void unsubscribe(PlayerConnectionMediator playerSession) {
		this.playerSession = null;
	}

	/**
	 * Stop the writer, and close the WebSocket connection to the room
	 * @see net.wasdev.gameon.player.ws.RoomMediator#disconnect(net.wasdev.gameon.player.ws.PlayerConnectionMediator)
	 */
	@Override
	public void disconnect() {
		connectionUtils.tryToClose(roomSession);
		toRoom.clear();
	}

	/**
	 * Send a message on to the room
	 * @see net.wasdev.gameon.player.ws.RoomMediator#send(net.wasdev.gameon.player.ws.RoutedMessage)
	 */
	@Override
	public void send(RoutedMessage message) {
		// make sure we're only dealing with messages for the room,
		if ( message.isForRoom(this) ){
			// TODO: Capacity?
			toRoom.offer(message);
		} else {
			Log.log(Level.FINEST, this, "send -- Dropping message {0}", message);
		}
	}

	/**
	 * Called when the connection to the room has been established
	 */
	private void connectionOpened(Session roomSession) {
		Log.log(Level.FINER, this, "ROOM CONNECTION OPEN {0}: {1}", endpointInfo.getRoomId());

		this.roomSession = roomSession;

		// set up delivery thread to send messages to the room as they arrive
		drainToRoom = connectionUtils.drain("TO ROOM[" + endpointInfo.getRoomId() + "]", toRoom, roomSession);
	}

	/**
	 * Called when the connection to the room has closed.
	 * If the connection closed badly, try to open again.
	 */
	private void connectionClosed(CloseReason reason) {
		Log.log(Level.FINER, this, "ROOM CONNECTION CLOSED {0}: {1}", endpointInfo.getRoomId(), reason);

		if ( drainToRoom != null)
			drainToRoom.stop();

		if ( playerSession != null && !reason.getCloseCode().equals(CloseCodes.NORMAL_CLOSURE) ) {
			connect();
		}
	}

	@Override
	public String toString() {
		return this.getClass().getName() + "[roomId=" + endpointInfo.getRoomId() +"]";
	}
}
