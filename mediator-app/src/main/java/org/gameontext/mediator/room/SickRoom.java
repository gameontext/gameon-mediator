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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import org.gameontext.mediator.Constants;
import org.gameontext.mediator.Log;
import org.gameontext.mediator.MapClient;
import org.gameontext.mediator.MediatorNexus;
import org.gameontext.mediator.RoutedMessage;
import org.gameontext.mediator.RoutedMessage.FlowTarget;
import org.gameontext.mediator.models.Site;

public class SickRoom extends AbstractRoomMediator {

    // %s will be replaced by the room's full name
    static final List<String> SICK_DESCRIPTIONS = Collections.unmodifiableList(Arrays.asList(
            "A hasty message has been taped to the wall: \n\n> Not feeling well, I've gone to lie down\n> \n> -- %s"
            + "\n\nThis room is [not healthy](https://book.gameontext.org/microservices/#_sick_room)."
            ));

    static final List<String> SICK_COMPLAINTS = Collections.unmodifiableList(Arrays.asList(
            "There is a distinctly green tinge to this room. It doesn't seem like itself at all.",
            "The room is strangely warm, expressing the malaise that comes with a fever as well as a room can.",
            "The room emits a low and rhythmic rumble, like a congested chest. Is it breathing?",
            "How odd. The room has a stretched tense feeling, like it is desperately trying not to sneeze."
            ));

    final RemoteRoomProxy proxy;
    final ScheduledExecutorService scheduledExecutor;
    final String SYSTEM_ID;

    /** Associated user id (if not a multiplexed/shared connection) */
    final String targetUser;

    ScheduledFuture<?> pendingAttempt;
    int attempts = 0;

    volatile boolean connected = true;
    volatile boolean characterMessages = true;
    volatile long retryInterval = 2;
    volatile String reason;

    public SickRoom(RemoteRoomProxy proxy, 
            MapClient mapClient, 
            ScheduledExecutorService scheduledExecutor, 
            Site site, 
            String userId, 
            String systemId, 
            MediatorNexus.View nexus,
            String reason) {
        super(nexus, mapClient, site);
        this.proxy = proxy;
        this.targetUser = userId == null ? "*" : userId;
        this.scheduledExecutor = scheduledExecutor;
        this.SYSTEM_ID = systemId;
        this.reason = reason;
        
        //use game like messages if the user is not the owner,
        //otherwise default to detailed messages.
        characterMessages = !userId.equals(proxy.getOwnerId());
        
        Log.log(Level.FINEST, this, "Created Sick Room for {0} in {1}", userId, site.getId());

        this.updateInformation(site);
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
        int index = RoomUtils.random.nextInt(SICK_DESCRIPTIONS.size());
        return String.format(SICK_DESCRIPTIONS.get(index), roomInfo.getFullName());
    }

    @Override
    public Type getType() {
        return Type.SICK;
    }
    
    @Override
    public void goodbye(MediatorNexus.UserView user) {
        super.goodbye(user);
        disconnect();
    }

    public void updateReason(String reason){
        this.reason = reason;
    }

    /**
     * Called whenever site information has been updated but the delegate stays
     * the same. Only one update in progress at a time, see {@link RemoteRoomProxy#updateInformation(Site)}
     * This method is effectively single threaded: contained w/in the remote
     * proxy update
     *
     * @see org.gameontext.mediator.room.AbstractRoomMediator#updateInformation(org.gameontext.mediator.models.Site)
     */
    @Override
    public void updateInformation(Site site) {
        // update exits and room information
        super.updateInformation(site);

        if ( connected ) {
            ++attempts;

            // small numbers, but doing this with TimeUnit in seconds..
            retryInterval = (retryInterval * 2) + RoomUtils.random.nextInt(3);

            // cough.
            sendToClients(RoutedMessage.createSimpleEventMessage(FlowTarget.player, targetUser, complaint()));


            Log.log(Level.FINEST, this, "Update {0} of Sick Room for {1} in {2}, next retry attempt in {3} seconds",
                    attempts, targetUser, roomId, retryInterval);

            // schedule a retry after an increasing interval
            pendingAttempt = scheduledExecutor.schedule(() -> {
                // attempt to reconnect
                proxy.updateInformation(null);
            }, retryInterval, TimeUnit.SECONDS);
        }
    }

    public String complaint() {
        if ( characterMessages ) {
            int index = RoomUtils.random.nextInt(SICK_COMPLAINTS.size());
            return SICK_COMPLAINTS.get(index) + " (" + attempts + ")";
        } else {
            return "Failed attempt "  + attempts + ". Reconnecting in " + retryInterval + " seconds.";
        }
    }

    @Override
    public void disconnect() {
        connected = false;

        if ( pendingAttempt != null ) {
            pendingAttempt.cancel(true);
        }
        Log.log(Level.FINEST, this, "Sick Room for {0}/{1} disconnected: {2}",
                targetUser, roomId, pendingAttempt);
    }

    @Override
    protected void buildLocationResponse(JsonObjectBuilder responseBuilder) {
        super.buildLocationResponse(responseBuilder);

        JsonArrayBuilder objs = Json.createArrayBuilder();
        objs.add("Monitor");
        objs.add("Chart");
        responseBuilder.add(Constants.KEY_ROOM_INVENTORY, objs.build());
    }

    @Override
    protected String parseCommand(String userId, String userName, JsonObject sourceMessage, JsonObjectBuilder responseBuilder) {

        String targetUser = userId;
        String content = sourceMessage.getString(RoomUtils.CONTENT);
        String contentToLower = content.trim().toLowerCase();

        if (contentToLower.startsWith("/examine") || contentToLower.startsWith("/look") ) {
            JsonObject contentResponse;

            if ( contentToLower.contains(" monitor") ) {
                if ( characterMessages ) {
                    contentResponse = RoomUtils.buildContentResponse(userId,
                            "You squint at the teensy screen and try to make sense of the text scrolling by.");
                } else {
                    contentResponse = RoomUtils.buildContentResponse(userId,
                            "The screen flickers sickeningly. You look away.");
                }

                // now switch modes
                characterMessages = !characterMessages;
            } else if ( contentToLower.contains(" chart") ) {
                if ( characterMessages ) {
                    contentResponse = RoomUtils.buildContentResponse(userId,
                            "You skim the page. You don't have to be a doctor to tell this room isn't doing so well.");
                } else {
                    String response = "Patient: **" + getFullName() + "**\n\n"
                            + "* Connection attempts: " + attempts + "\n"
                            + "* Retry interval: " + retryInterval + " seconds";
                    
                    //add extra info if the player is the rooms owner.. 
                    if(userId.equals(getOwnerId()) || getOwnerId().equals(SYSTEM_ID)){
                        response += "\n"
                                 + "* Connection details target: "+roomInfo.getConnectionDetails().getTarget()+"\n"
                                 + "* Connection details hasToken?: "+(roomInfo.getConnectionDetails().getToken()!=null)+"\n"
                                 + "* Last failure: "+String.valueOf(reason);
                    }
                    
                    contentResponse = RoomUtils.buildContentResponse(userId,response);          
                }
            } else {
                contentResponse = RoomUtils.buildContentResponse(userId, "It doesn't look interesting.");
            }

            responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT)
                 .add(RoomUtils.CONTENT, contentResponse);
        } else {
            targetUser = super.parseCommand(userId, userName, sourceMessage, responseBuilder);
        }

        return targetUser;
    }
}
