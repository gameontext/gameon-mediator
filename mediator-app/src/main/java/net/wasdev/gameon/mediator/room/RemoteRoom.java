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

import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;

import net.wasdev.gameon.mediator.Drain;
import net.wasdev.gameon.mediator.Log;
import net.wasdev.gameon.mediator.MapClient;
import net.wasdev.gameon.mediator.MediatorNexus;
import net.wasdev.gameon.mediator.RoutedMessage;
import net.wasdev.gameon.mediator.models.ConnectionDetails;
import net.wasdev.gameon.mediator.models.Site;

public class RemoteRoom extends AbstractRoomMediator {

    interface Connection {
        void connect() throws Exception;
        void disconnect();
        void sendToRoom(RoutedMessage message);
        long version();
    }

    final Connection connection;
    final RemoteRoomProxy proxy;
    final ScheduledExecutorService scheduledExecutor;

    public RemoteRoom(RemoteRoomProxy proxy, MapClient mapClient, ScheduledExecutorService scheduledExecutor, Site site, Drain drain, MediatorNexus.View nexusView) throws Exception {
        super(nexusView, mapClient, site);
        this.proxy = proxy;
        this.scheduledExecutor = scheduledExecutor;

        // Try to connect to the remote room, throw exception on failure.
        Log.log(Level.FINE, this, "Creating connection to room {0}", site.getId());

        ConnectionDetails details = site.getInfo().getConnectionDetails();
        if ( "websocket".equals(details.getType())) {
            connection = new WebSocketClientConnection(proxy, nexusView, drain, site);
        } else {
            throw new UnsupportedOperationException(details.getType() + " is not a supported transport type");
        }

        connection.connect();
    }

    @Override
    public Type getType() {
        return Type.REMOTE;
    }

    @Override
    public String getName() {
        return roomInfo.getName();
    }

    @Override
    public String getFullName() {
        return roomInfo.getFullName();
    }

    @Override
    public String getDescription() {
        return roomInfo.getDescription();
    }

    @Override
    public void hello(MediatorNexus.UserView user, boolean recovery) {
        // Say hello to the new room!
        Log.log(Level.FINER, this, "REMOTE HELLO {0}", getId());

        connection.sendToRoom(RoutedMessage.createHello(connection.version(), roomId, user));
    }

    @Override
    public void goodbye(MediatorNexus.UserView user) {
        // Say hello to the new room!
        Log.log(Level.FINER, this, "REMOTE GOODBYE {0}", getId());

        connection.sendToRoom(RoutedMessage.createGoodbye(roomId, user));
        connection.disconnect(); // will work w/ connection type to clean up after send
    }

    @Override
    public void join(MediatorNexus.UserView user) {
        if ( connection.version() > 1) {
            // Say hello to the new room!
            Log.log(Level.FINER, this, "REMOTE JOIN {0}", getId());

            connection.sendToRoom(RoutedMessage.createJoin(connection.version(), roomId, user));
        } else {
            // strip remove bookmark from v1 sessions, as they were ints instead of strings
            hello(user, false);
        }
    }

    @Override
    public void part(MediatorNexus.UserView user) {
        if ( connection.version() > 1) {
            // Say hello to the new room!
            Log.log(Level.FINER, this, "REMOTE PART {0}", getId());

            connection.sendToRoom(RoutedMessage.createPart(roomId, user));
            connection.disconnect(); // will work with connection type to clean up after send
        } else {
            goodbye(user);
        }
    }

    @Override
    public void sendToRoom(RoutedMessage message) {
        connection.sendToRoom(message);
    }

    @Override
    public synchronized void disconnect() {
        Log.log(Level.FINE, this, "DISCONNECT... ");
        connection.disconnect();
    }

    @Override
    public String toString() {
        return this.getClass().getName() + "[roomId=" + super.getId() + "]";
    }
}
