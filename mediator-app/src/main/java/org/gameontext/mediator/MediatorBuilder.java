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
package org.gameontext.mediator;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.enterprise.concurrent.ManagedScheduledExecutorService;
import javax.enterprise.concurrent.ManagedThreadFactory;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.websocket.Session;

import org.gameontext.mediator.MediatorNexus.ClientMediatorPod;
import org.gameontext.mediator.MediatorNexus.UserView;
import org.gameontext.mediator.RoutedMessage.FlowTarget;
import org.gameontext.mediator.models.Exit;
import org.gameontext.mediator.models.Exits;
import org.gameontext.mediator.models.RoomInfo;
import org.gameontext.mediator.models.Site;
import org.gameontext.mediator.room.ConnectingRoom;
import org.gameontext.mediator.room.EmptyRoom;
import org.gameontext.mediator.room.FirstRoom;
import org.gameontext.mediator.room.GuidedFirstRoom;
import org.gameontext.mediator.room.RemoteRoom;
import org.gameontext.mediator.room.RemoteRoomProxy;
import org.gameontext.mediator.room.RoomMediator;
import org.gameontext.mediator.room.RoomMediator.Type;
import org.gameontext.mediator.room.RoomUtils;
import org.gameontext.mediator.room.SickRoom;
import org.gameontext.mediator.room.UnknownRoom;
import org.gameontext.signed.SignedJWT;

import io.jsonwebtoken.Claims;

@ApplicationScoped
public class MediatorBuilder {

    public enum UpdateType {
        HELLO,
        JOIN,
        RECONNECT
    }

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
      
    @Resource(lookup = "systemId")
    String SYSTEM_ID;

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
    public ClientMediator buildClientMediator(String userId, Session session, SignedJWT clientJwt, String serverJwt) {
        WSDrain drain = new WSDrain(userId, session);
        drain.setThread(threadFactory.newThread(drain));

        // Send a keep-alive to the client.
        drain.setFuture(scheduledExecutor.scheduleAtFixedRate(() -> {
            drain.send(RoutedMessage.PING_MSG);
        }, 50, 2, TimeUnit.SECONDS));

        ClientMediator clientMediator = new ClientMediator(nexus, drain, userId, clientJwt, serverJwt);
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
    public RoomMediator findMediatorForRoom(ClientMediatorPod pod, String roomId) {
        Log.log(Level.FINEST, pod, "findMediatorForRoom: builder={0}, roomId={1}", Log.getHexHash(this), roomId);

        if ( roomId == null || roomId.isEmpty() || Constants.FIRST_ROOM.equals(roomId) ) {
            return getFirstRoomMediator(pod);
        }

        Site site = mapClient.getSite(roomId);
        if ( site == null ) {
            pod.send(RoutedMessage.createSimpleEventMessage(FlowTarget.player, pod.getUserId(),
                    Constants.EVENTMSG_NO_ROOMS));

            return getFirstRoomMediator(pod);
        }

        return new RemoteRoomProxy(this, pod, roomId);
    }

    /**
     * Find a new mediator for the given room id
     *
     * @param clientMediator
     * @param roomId
     *
     * @return a new room mediator. Never null.
     */
    public RoomMediator findMediatorForExit(ClientMediatorPod pod, RoomMediator startingRoom, String direction) {
        Log.log(Level.FINEST, pod, "findMediatorForExit: builder={0}, startingRoom={1}, direction={2}", Log.getHexHash(this), startingRoom, direction);

        Exits exits = startingRoom.getExits();
        Exit target = exits.getExit(direction);

        if (target == null ) {
            pod.send(RoutedMessage.createSimpleEventMessage(FlowTarget.player, pod.getUserId(),
                    Constants.EVENTMSG_FINDROOM_FAIL));

            return startingRoom;
        } else if ( Constants.FIRST_ROOM.equals(target.getId())) {
            return getFirstRoomMediator(pod);
        }

        Site site = mapClient.getSite(target.getId());
        if ( site == null )
            site = new Site(target, RoomUtils.createFallbackExit(startingRoom.getEmergencyReturnExit(), direction));

        return new RemoteRoomProxy(this, pod, site.getId());
    }

    public RoomMediator getFirstRoomMediator(ClientMediatorPod pod) {
        Log.log(Level.FINEST, pod, "getFirstRoomMediator: builder={0}, new={1}", Log.getHexHash(this), pod.room == null);

        boolean newbie = pod.room == null;
        Site site = mapClient.getSite(Constants.FIRST_ROOM);
        if ( site == null ) {
            site = FirstRoom.getFallbackSite();
        }
        
        String playerMode = "default";
        String targetId = null;
        
        SignedJWT clientJwt = pod.getParsedClientJwt();
        if(clientJwt!=null) {
            Claims c = clientJwt.getClaims();
            if(c.containsKey("playerMode")) {
                playerMode = c.get("playerMode", String.class);
                targetId = c.get("story", String.class);
            }
        }
        
        if("guided".equals(playerMode)) {
          return new GuidedFirstRoom(nexus.getMultiUserView(Constants.FIRST_ROOM),
              pod.getEncodedServerJwt(),
              playerClient,
              mapClient,
              site,
              targetId);
        } else {
          return new FirstRoom(nexus.getMultiUserView(Constants.FIRST_ROOM),
              pod.getEncodedServerJwt(),
              playerClient,
              mapClient,
              site,
              newbie);
        }
    }

    /**
     * Create a new "Connecting" delegate for a proxy.
     * This is done as a callback to keep all delegate construction mechanics here.
     * This will also schedule an executor to attempt the outbound client connection,
     * which will then replace the delegate if/when it is done.
     *
     * hello will be called on the delegates returned from this method.
     *
     * @param proxy
     * @param user
     * @param roomId
     * @return
     */
    public RoomMediator createDelegate(RemoteRoomProxy proxy, UserView user, String roomId) {
        Log.log(Level.FINEST, this, "Create connecting delegate: proxy={0}, userId={1}, site={2}", Log.getHexHash(proxy), user, roomId);
        Site site = mapClient.getSite(roomId);

        if ( site == null ) {
            return new UnknownRoom(mapClient, roomId, nexus.getMultiUserView(roomId));
        } else if ( site.getInfo() == null ) {
            return new EmptyRoom(mapClient, site, user.getUserId(), nexus.getMultiUserView(roomId));
        }

        return new ConnectingRoom(proxy, mapClient, site, user.getUserId(),
                nexus.getFilteredMultiUserView(roomId, RoomMediator.Type.CONNECTING));
    }

    /**
     * Try to make connection to remote room. Replace delegate IFF that is successful called by
     * {@link RemoteRoomProxy#reconnect()} and {@link RemoteRoomProxy#updateInformation(Site)}
     * @param updateType TODO
     * @param proxy
     * @param currentDelegate
     * @param site null when reconnecting after a connection failure, otherwise updated site information
     * @param user
     */
    public void updateDelegate(UpdateType updateType, RemoteRoomProxy proxy, RoomMediator currentDelegate, Site site, UserView user) {
        Log.log(Level.FINEST, this, "updateDelegate proxy={0}, user={1}, delegate={2}/{3}, site={4}",
                Log.getHexHash(proxy), user, Log.getHexHash(currentDelegate), currentDelegate.getType(), site);

        // try updating the delegate with the new information. It might return the same delegate
        RoomMediator newDelegate = internalUpdateDelegate(updateType, proxy, currentDelegate, site, user);

        // always complete the update operation on the proxy
        proxy.updateComplete(newDelegate);

        Log.log(Level.FINEST, this, "updateDelegate AFTER: proxy={0}, user={1}, delegate={2}/{3}",
                Log.getHexHash(proxy), user, Log.getHexHash(currentDelegate), currentDelegate.getType());
    }


    public RoomMediator internalUpdateDelegate(UpdateType updateType, RemoteRoomProxy proxy, RoomMediator currentDelegate, Site site, UserView user) {
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
                    return tryRemoteDelegate(updateType, proxy, currentDelegate, targetSite, user);
                default :
                    // refresh exits or descriptions, otherwise stick with what we have.
                    currentDelegate.updateInformation(targetSite);
                    return currentDelegate;
            }
        } else if ( localInfo == null ) {
            return createUpdateLocalDelegate(Type.EMPTY, proxy, currentDelegate, targetSite, user, null);
        } else {
            // try connecting to the remote room
            return tryRemoteDelegate(updateType, proxy, currentDelegate, targetSite, user);
        }
    }

    private RoomMediator tryRemoteDelegate(UpdateType updateType, RemoteRoomProxy proxy, RoomMediator currentDelegate, Site site, UserView user) {
        Log.log(Level.FINEST, this, "tryRemoteDelegate: proxy={0}, userId={1}, delegate={2}/{3}, site={4}, user={5}",
                Log.getHexHash(proxy), user, Log.getHexHash(currentDelegate), currentDelegate.getType(), site, user);

        String roomId = site.getId();
        WSDrain drain = new WSDrain(roomId);
        drain.setThread(threadFactory.newThread(drain));
        
        String reason = null;

        try {
            RemoteRoom room = new RemoteRoom(proxy, mapClient, scheduledExecutor, site, drain, nexus.getSingleUserView(roomId, user));
            switch(updateType) {
                case HELLO:
                    room.hello(user);
                    break;
                case JOIN:
                    room.join(user);
                    break;
                case RECONNECT:
                    room.hello(user);
                    break;
            }

            return room;
        } catch(Exception e) {
            Log.log(Level.FINEST, this, "tryRemoteDelegate FAILED: proxy={0}, userId={1}, exception={2}",
                    Log.getHexHash(proxy), user, e);
            
            reason = Instant.now().toString()+" "+e.getMessage();
        }

        return createUpdateLocalDelegate(Type.SICK, proxy, currentDelegate, site, user, reason);
    }

    private RoomMediator createUpdateLocalDelegate(Type type, RemoteRoomProxy proxy, RoomMediator currentDelegate, Site site, UserView user, String reason) {
        Log.log(Level.FINEST, this, "createUpdateLocalDelegate: proxy={0}, newType={1}, delegate={2}/{3}, site={4}, userId={5}",
                Log.getHexHash(proxy), type, Log.getHexHash(currentDelegate), currentDelegate.getType(), site, user);

        if ( currentDelegate.getType() == type ) {
            currentDelegate.updateInformation(site);
            if( type == Type.SICK){
                ((SickRoom)currentDelegate).updateReason(reason);
            }
            return currentDelegate;
        }

        RoomMediator mediator;
        if ( type == Type.EMPTY ) {
            mediator = new EmptyRoom(mapClient, site, user.getUserId(), nexus.getMultiUserView(site.getId()));
        } else {
            mediator = new SickRoom(proxy, mapClient, scheduledExecutor, site, user.getUserId(), SYSTEM_ID,
                                nexus.getFilteredMultiUserView(site.getId(), RoomMediator.Type.SICK), reason);
        }
        mediator.hello(user);
        return mediator;
    }

    public void execute(Runnable r) {
        this.scheduledExecutor.execute(r);
    }
}
