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

import java.util.logging.Level;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.enterprise.concurrent.ManagedScheduledExecutorService;
import javax.enterprise.concurrent.ManagedThreadFactory;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.websocket.Session;

import net.wasdev.gameon.mediator.RoutedMessage.FlowTarget;
import net.wasdev.gameon.mediator.models.Exit;
import net.wasdev.gameon.mediator.models.Exits;
import net.wasdev.gameon.mediator.models.RoomInfo;
import net.wasdev.gameon.mediator.models.Site;
import net.wasdev.gameon.mediator.room.ConnectingRoom;
import net.wasdev.gameon.mediator.room.EmptyRoom;
import net.wasdev.gameon.mediator.room.FirstRoom;
import net.wasdev.gameon.mediator.room.RemoteRoom;
import net.wasdev.gameon.mediator.room.RemoteRoomProxy;
import net.wasdev.gameon.mediator.room.RoomMediator;
import net.wasdev.gameon.mediator.room.RoomMediator.Type;
import net.wasdev.gameon.mediator.room.RoomUtils;
import net.wasdev.gameon.mediator.room.SickRoom;
import net.wasdev.gameon.mediator.room.UnknownRoom;

@ApplicationScoped
public class MediatorBuilder {

    /** CDI injection of client for Map */
    @Inject
    MapClient mapClient;

    /** CDI injection of client for Map */
    @Inject
    PlayerClient playerClient;

    @Inject
    MediatorNexus nexus;

    /** CDI injection of Java EE7 Managed thread factory */
    @Resource
    protected ManagedThreadFactory threadFactory;

    @Resource
    ManagedScheduledExecutorService scheduledExecutor;

    @PostConstruct
    public void postConstruct() {
        // They need each other, it's cute
        nexus.setBuilder(this);
    }

    /**
     * Create a new client mediator
     *
     * @param userId
     * @param session
     * @param serverJwt
     * @return
     */
    public ClientMediator buildClientMediator(String userId, Session session, String serverJwt) {
        WSDrain drain = new WSDrain(userId, session);
        drain.setThread(threadFactory.newThread(drain));

        ClientMediator clientMediator = new ClientMediator(nexus, drain, userId, serverJwt);
        return clientMediator;
    }

    /**
     * Find a new mediator for the given room id
     *
     * @param clientMediator
     * @param roomId
     *
     * @return a new room mediator. Never null.
     */
    public RoomMediator findMediatorForRoom(ClientMediator clientMediator, String roomId) {
        Log.log(Level.FINEST, clientMediator.getSource(), "findMediatorForRoom: builder={0}, roomId={1}", Log.getHexHash(this), roomId);

        if ( roomId == null || roomId.isEmpty() || Constants.FIRST_ROOM.equals(roomId) ) {
            return getFirstRoomMediator(clientMediator);
        }

        Site site = mapClient.getSite(roomId);
        if ( site == null ) {
             clientMediator.sendToClient(RoutedMessage.createSimpleEventMessage(FlowTarget.player, clientMediator.getUserId(),
                    Constants.EVENTMSG_NO_ROOMS));

            return getFirstRoomMediator(clientMediator);
        }

        return new RemoteRoomProxy(this, clientMediator.getUserId(), roomId);
    }

    /**
     * Find a new mediator for the given room id
     *
     * @param clientMediator
     * @param roomId
     *
     * @return a new room mediator. Never null.
     */
    public RoomMediator findMediatorForExit(ClientMediator clientMediator, RoomMediator startingRoom, String direction) {
        Log.log(Level.FINEST, clientMediator.getSource(), "findMediatorForExit: builder={0}, startingRoom={1}, direction={2}", Log.getHexHash(this), startingRoom, direction);

        Exits exits = startingRoom.getExits();
        Exit target = exits.getExit(direction);

        if (target == null ) {
            clientMediator.sendToClient(RoutedMessage.createSimpleEventMessage(FlowTarget.player, clientMediator.getUserId(),
                    Constants.EVENTMSG_FINDROOM_FAIL));

            return startingRoom;
        } else if ( Constants.FIRST_ROOM.equals(target.getId())) {
            return getFirstRoomMediator(clientMediator);
        }

        Site site = mapClient.getSite(target.getId());
        if ( site == null )
            site = new Site(target, RoomUtils.createFallbackExit(startingRoom.getEmergencyReturnExit(), direction));

        return new RemoteRoomProxy(this, clientMediator.getUserId(), site.getId());
    }

    public RoomMediator getFirstRoomMediator(ClientMediator clientMediator) {
        Log.log(Level.FINEST, clientMediator.getSource(), "getFirstRoomMediator: builder={0}, ", Log.getHexHash(this));

        boolean newbie = clientMediator.getRoomMediator() == null;
        Site site = mapClient.getSite(Constants.FIRST_ROOM);
        if ( site == null ) {
            site = FirstRoom.getFallbackSite();
        }

        return new FirstRoom(nexus.getMultiUserView(Constants.FIRST_ROOM),
                clientMediator.getUserJwt(),
                playerClient,
                mapClient,
                site,
                newbie);
    }

    /**
     * Create a new "Connecting" delegate for a proxy.
     * This is done as a callback to keep all delegate construction mechanics here.
     * This will also schedule an executor to attempt the outbound client connection,
     * which will then replace the delegate if/when it is done.
     *
     * @param proxy
     * @param userId
     * @param roomId
     * @return
     */
    public RoomMediator createDelegate(RemoteRoomProxy proxy, String userId, String roomId) {
        Log.log(Level.FINEST, this, "Create connecting delegate: proxy={0}, userId={1}, site={2}", Log.getHexHash(proxy), userId, roomId);
        Site site = mapClient.getSite(roomId);

        if ( site == null ) {
            return new UnknownRoom(mapClient, roomId, nexus.getMultiUserView(roomId));
        } else if ( site.getInfo() == null ) {
            return new EmptyRoom(mapClient, site, userId, nexus.getMultiUserView(roomId));
        }

        return new ConnectingRoom(proxy, mapClient, site, userId, 
                nexus.getFilteredMultiUserView(roomId, RoomMediator.Type.CONNECTING));
    }

    /**
     * Try to make connection to remote room. Replace delegate IFF that is successful called by
     * {@link RemoteRoomProxy#reconnect()} and {@link RemoteRoomProxy#updateInformation(Site)}
     *
     * @param proxy
     * @param currentDelegate
     * @param userId
     * @param site null when reconnecting after a connection failure, otherwise updated site information
     */
    public void updateDelegate(RemoteRoomProxy proxy, RoomMediator currentDelegate, String userId, Site site) {
        Log.log(Level.FINEST, this, "updateDelegate proxy={0}, userId={1}, delegate={2}/{3}, site={4}",
                Log.getHexHash(proxy), userId, Log.getHexHash(currentDelegate), currentDelegate.getType(), site);

        // try updating the delegate with the new information. It might return the same delegate
        RoomMediator newDelegate = internalUpdateDelegate(proxy, currentDelegate, userId, site);

        // always complete the update operation on the proxy
        proxy.updateComplete(newDelegate);

        Log.log(Level.FINEST, this, "updateDelegate AFTER: proxy={0}, userId={1}, delegate={2}/{3}",
                Log.getHexHash(proxy), userId, Log.getHexHash(currentDelegate), currentDelegate.getType());
    }

    public RoomMediator internalUpdateDelegate(RemoteRoomProxy proxy, RoomMediator currentDelegate, String userId, Site site) {

        Site targetSite = site;
        String roomId = currentDelegate.getId();

        if ( targetSite == null ) {
            targetSite = mapClient.getSite(roomId);
            if ( targetSite == null ) {
                if ( currentDelegate.getType() == Type.UNKNOWN )
                    return currentDelegate;

                return new UnknownRoom(mapClient, roomId, nexus.getMultiUserView(roomId));
            }
        }

        RoomInfo localInfo = targetSite.getInfo();

        if ( currentDelegate.sameConnectionDetails(localInfo) ) {
            // room connection information hasn't changed...
            switch(currentDelegate.getType()) {
                case SICK :
                case UNKNOWN :
                case CONNECTING :
                    // try connecting to the remote room
                    return tryRemoteDelegate(proxy, currentDelegate, userId, targetSite);
                default :
                    // refresh exits or descriptions, otherwise stick with what we have.
                    currentDelegate.updateInformation(targetSite);
                    return currentDelegate;
            }
        } else if ( localInfo == null ) {
            return createUpdateLocalDelegate(Type.EMPTY, proxy, currentDelegate, targetSite, null);
        } else {
            // try connecting to the remote room
            return tryRemoteDelegate(proxy, currentDelegate, userId, targetSite);
        }
    }

    private RoomMediator tryRemoteDelegate(RemoteRoomProxy proxy, RoomMediator currentDelegate, String userId, Site site) {
        Log.log(Level.FINEST, this, "tryRemoteDelegate: proxy={0}, userId={1}, delegate={2}/{3}, site={4}",
                Log.getHexHash(proxy), userId, Log.getHexHash(currentDelegate), currentDelegate.getType(), site);

        String roomId = site.getId();
        WSDrain drain = new WSDrain(roomId);
        drain.setThread(threadFactory.newThread(drain));

        try {
            return new RemoteRoom(proxy, mapClient, scheduledExecutor, site, drain, nexus.getSingleUserView(roomId, userId));
        } catch(Exception e) {
            Log.log(Level.FINEST, this, "tryRemoteDelegate FAILED: proxy={0}, userId={1}, exception={2}",
                    Log.getHexHash(proxy), userId, e);
        }

        return createUpdateLocalDelegate(Type.SICK, proxy, currentDelegate, site, userId);
    }

    private RoomMediator createUpdateLocalDelegate(Type type, RemoteRoomProxy proxy, RoomMediator currentDelegate, Site site, String userId) {
        Log.log(Level.FINEST, this, "createUpdateLocalDelegate: proxy={0}, newType={1}, delegate={2}/{3}, site={4}, userId={5}",
                Log.getHexHash(proxy), type, Log.getHexHash(currentDelegate), currentDelegate.getType(), site, userId);

        if ( currentDelegate.getType() == type ) {
            currentDelegate.updateInformation(site);
            return currentDelegate;
        }

        if ( type == Type.EMPTY ) {
            return new EmptyRoom(mapClient, site, userId, nexus.getMultiUserView(site.getId()));
        } else {
            return new SickRoom(proxy, mapClient, scheduledExecutor, site, userId,
                    nexus.getFilteredMultiUserView(site.getId(), RoomMediator.Type.SICK));
        }
    }
}
