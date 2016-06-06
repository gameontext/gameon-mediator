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

import net.wasdev.gameon.mediator.ClientMediator;
import net.wasdev.gameon.mediator.MapClient;
import net.wasdev.gameon.mediator.MediatorNexus;
import net.wasdev.gameon.mediator.RoutedMessage;
import net.wasdev.gameon.mediator.RoutedMessage.FlowTarget;
import net.wasdev.gameon.mediator.models.Site;

/**
 * Placeholder while we connect to a remote room.
 * Connection is triggered by roomHello/roomJoin
 *
 */
public class ConnectingRoom extends AbstractRoomMediator {

    final RemoteRoomProxy proxy;
    
    public ConnectingRoom(RemoteRoomProxy proxy, MediatorNexus.View nexus, MapClient mapClient, Site site) {
        super(nexus, mapClient, site);
        this.proxy = proxy;
        roomInfo = site.getInfo();
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
    public void hello(ClientMediator playerSession, boolean recovery) {
        // there might be a few instances of the player around.. 
        // they should all have moved together, so we use the broadcast flag to send
        // to all of them.
        sendToClients(RoutedMessage.createSimpleEventMessage(FlowTarget.player, playerSession.getUserId(), 
                getDescription()));
        
        // Attempt to connect to the real deal.
        proxy.connectRemote(true, "");
     }

    @Override
    public void join(ClientMediator playerSession, String lastMessage) {
        super.join(playerSession, lastMessage);
        
        // Attempt to connect to the real deal.
        proxy.connectRemote(false, lastMessage);
    }
}
