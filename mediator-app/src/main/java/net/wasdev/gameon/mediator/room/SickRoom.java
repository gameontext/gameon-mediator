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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import net.wasdev.gameon.mediator.Log;
import net.wasdev.gameon.mediator.MapClient;
import net.wasdev.gameon.mediator.MediatorNexus;
import net.wasdev.gameon.mediator.RoutedMessage;
import net.wasdev.gameon.mediator.RoutedMessage.FlowTarget;
import net.wasdev.gameon.mediator.models.Site;

public class SickRoom extends AbstractRoomMediator {

    // %s will be replaced by the room's full name
    static final List<String> SICK_DESCRIPTIONS = Collections.unmodifiableList(Arrays.asList(
            "A hasty message has been taped to the wall, `Not feeling well, I've gone to lie down -- %s`"
            ));

    static final List<String> SICK_COMPLAINTS = Collections.unmodifiableList(Arrays.asList(
            "There is a distinctly green tinge to this room. It doesn't seem like itself at all.",
            "The room is strangely warm, expressing the malaise that comes with a fever as well as a room can.",
            "The room emits a low and rhythmic rumble, like a congested chest. Is it breathing?",
            "How odd. The room has a stretched tense feeling, like it is desperately trying not to sneeze."
            ));
    
    final RemoteRoomProxy proxy;
    final ScheduledExecutorService scheduledExecutor;
    ScheduledFuture<?> pendingAttempt;
    int attempts = 0;

    /** Associated user id (if not a multiplexed/shared connection) */
    final String targetUser;

    
    public SickRoom(RemoteRoomProxy proxy, MapClient mapClient, ScheduledExecutorService scheduledExecutor, Site site, String userId, MediatorNexus.View nexus) {
        super(nexus, mapClient, site);
        this.proxy = proxy;
        this.targetUser = userId == null ? "*" : userId;
        this.scheduledExecutor = scheduledExecutor;

        Log.log(Level.FINEST, this, "Created Sick Room for " + targetUser + " in " + site.getId());
       
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

    /**
     * Called whenever site information has been updated but the delegate stays
     * the same. Only one update in progress at a time, see {@link RemoteRoomProxy#updateInformation(Site)}
     * 
     * @see net.wasdev.gameon.mediator.room.AbstractRoomMediator#updateInformation(net.wasdev.gameon.mediator.models.Site)
     */
    @Override
    public void updateInformation(Site site) {
        // update exits and room information
        super.updateInformation(site);
        
        Log.log(Level.FINEST, this, "Updated Sick Room for " + targetUser + " in " + site.getId());

        // cough.
        sendToClients(RoutedMessage.createSimpleEventMessage(FlowTarget.player, targetUser, complaint() + "(" + attempts + ")"));

        // schedule a retry after an increasing interval
        pendingAttempt = scheduledExecutor.schedule(() -> {
            // attempt to reconnect
            proxy.updateInformation(null);
        }, attempts++, TimeUnit.SECONDS);
    }
    
    public String complaint() {
        int index = RoomUtils.random.nextInt(SICK_COMPLAINTS.size());
        return SICK_COMPLAINTS.get(index);
    }

    @Override
    public void disconnect() {
        if ( pendingAttempt != null ) {
            pendingAttempt.cancel(true);
        }
    }
    
}
