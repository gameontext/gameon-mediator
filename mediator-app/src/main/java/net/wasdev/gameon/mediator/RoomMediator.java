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

import javax.json.JsonObject;

import net.wasdev.gameon.mediator.models.ConnectionDetails;
import net.wasdev.gameon.mediator.models.Exit;
import net.wasdev.gameon.mediator.models.Exits;

/**
 * Interface for a (local or remote) room mediator.
 */
public interface RoomMediator {

    /**
     * @return The room's unique id.
     */
    String getId();

    /**
     * @return The room's name
     */
    String getName();

    /**
     * @return The room's pretty name
     */
    String getFullName();

    /**
     * @return connection details for room
     */
    ConnectionDetails getConnectionDetails();
    
    /**
     * @return the protocol version to use when talking to room
     */
    long getSelectedProtocolVersion();

    /**
     * @return a list of the room's exits
     */
    Exits getExits();

    /**
     * @param direction
     * @return The exit heading off in the specified direction
     */
    Exit getExit(String direction);

    /**
     * @return JsonObject list of exits as presented in the client {"N": "Door Description"}
     */
    JsonObject listExits();

    /**
     * Create the connection between the mediator and the remote room
     *
     * @return
     */
    boolean connect();

    /**
     * Subscribe the player mediator: the player mediator will start receiving
     * room events.
     *
     * @param playerSession
     * @param lastmessage
     */
    boolean subscribe(PlayerConnectionMediator playerSession, long lastmessage);

    /**
     * Unubscribe the player mediator: the player mediator will stop receiving
     * room events.
     *
     * @param playerSession
     */
    void unsubscribe(PlayerConnectionMediator playerSession);

    /**
     * Disconnect from the room (stop sending messages to the room)
     */
    void disconnect();

    /**
     * Send message to room
     *
     * @param message
     *            RoutedMessage containing routing information to forward on to
     *            room
     */
    void send(RoutedMessage message);
}
