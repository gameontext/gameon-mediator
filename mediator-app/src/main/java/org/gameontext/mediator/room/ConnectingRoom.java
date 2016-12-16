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
package org.gameontext.mediator.room;

import java.util.logging.Level;

import org.gameontext.mediator.Log;
import org.gameontext.mediator.MapClient;
import org.gameontext.mediator.MediatorNexus;
import org.gameontext.mediator.RoutedMessage;
import org.gameontext.mediator.RoutedMessage.FlowTarget;
import org.gameontext.mediator.models.Site;

/**
 * Placeholder while we connect to a remote room.
 * Connection is triggered by roomHello/roomJoin
 *
 */
public class ConnectingRoom extends AbstractRoomMediator {

    final RemoteRoomProxy proxy;

    /** Associated user id (if not a multiplexed/shared connection) */
    final String targetUser;

    public ConnectingRoom(RemoteRoomProxy proxy, MapClient mapClient, Site site, String userId, MediatorNexus.View nexus) {
        super(nexus, mapClient, site);
        this.proxy = proxy;
        this.targetUser = userId == null ? "*" : userId;

        Log.log(Level.FINEST, this, "Created Connecting Room for " + targetUser + " in " + site.getId());
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
        return "Connecting to "+roomInfo.getFullName()+". Please hold.";
    }

    @Override
    public Type getType() {
        return Type.CONNECTING;
    }

    @Override
    public void hello(MediatorNexus.UserView user) {
        // there might be a few instances of the player around..
        // they should all have moved together, so we use the broadcast flag to send
        // to all of them.
        sendToClients(RoutedMessage.createSimpleEventMessage(FlowTarget.player, user.getUserId(),
                getDescription()));

        // Attempt to connect to the real deal.
        proxy.connectRemote(true);
     }

    @Override
    public void join(MediatorNexus.UserView user) {
        super.join(user);

        // Attempt to connect to the real deal.
        proxy.connectRemote(false);
    }
}
