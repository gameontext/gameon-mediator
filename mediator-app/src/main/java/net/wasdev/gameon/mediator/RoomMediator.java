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

/**
 * Encapsulates interactions between the Player and the (local or remote) room.
 */
public interface RoomMediator {

	/**
	 * @return The room's unique id.
	 */
	String getId();

	/**
	 * @return The room's pretty name
	 */
	String getName();

	/**
	 * Create the connection between the mediator and the remote room
	 * @return
	 */
	boolean connect();

	/**
	 * Subscribe the player mediator: the player mediator will start receiving room
	 * events.
	 * @param playerSession
	 * @param lastmessage
	 */
	boolean subscribe(PlayerConnectionMediator playerSession, long lastmessage);

	/**
	 * Unubscribe the player mediator: the player mediator will stop receiving room
	 * events.
	 * @param playerSession
	 */
	void unsubscribe(PlayerConnectionMediator playerSession);

	/**
	 * Disconnect from the room (stop sending messages to the room)
	 */
	void disconnect();

	/**
	 * Send message to room
	 * @param message RoutedMessage containing routing information to forward on to room
	 */
	void send(RoutedMessage message);
}
