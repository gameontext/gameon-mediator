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
package org.gameontext.mediator.events;

import java.io.IOException;
import java.util.logging.Level;

import javax.inject.Inject;

import org.gameontext.mediator.Log;
import org.gameontext.mediator.kafka.GameOnEvent;
import org.gameontext.mediator.kafka.KafkaRxJavaObservable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import rx.Subscription;

public class MediatorEvents {

    public interface PlayerEventHandler {
        public void playerUpdated(String userId, String userName, String favoriteColor);

        public void locationUpdated(String userId, String newLocation);
        // add additional methods as required for other event types...
    }

    @Inject
    KafkaRxJavaObservable kafka;

    public EventSubscription subscribeToPlayerEvents(String userId, PlayerEventHandler peh) {
        Subscription subscription = kafka.consume().filter(event -> "playerEvents".equals(event.getTopic()))
                .filter(event -> userId.equals(event.getKey())).subscribe(event -> handlePlayerEvent(event, peh));

        return new EventSubscription(subscription);
    }

    // Map events into player event handler callbacks.
    private void handlePlayerEvent(GameOnEvent goe, PlayerEventHandler peh) {
        ObjectMapper om = new ObjectMapper();
        JsonNode tree;
        try {
            // the value in the GameOnEvent is JSON, with a type field that
            // dictates the content.
            tree = om.readTree(goe.getValue());
            String type = tree.get("type").asText();
            // current known values for type.. may change if we start using more
            // refined events.
            switch (type) {
            case "UPDATE": {
                // update(_*) and create, have the player json as a value under
                // the key 'player' this may change, to at least obscure/remove 
                // restricted info like apikey but for now, this is ok while we 
                // figure out events, since messagehub is not webfacing.

                // get the player json, and parse it to a JsonNode
                JsonNode player = tree.get("player");
                // grab the name field from the json..
                String username = player.get("name").asText();
                String color = player.get("favoriteColor").asText();

                peh.playerUpdated(goe.getKey(), username, color);
                break;
            }
            case "DELETE": {
                // note JSON only has id field.. rest is already deleted.
                break;
            }
            case "UPDATE_LOCATION": {
                JsonNode player = tree.get("player");
                String location = player.get("location").asText();
                peh.locationUpdated(goe.getKey(), location);
                break;
            }
            case "UPDATE_APIKEY": {
                break;
            }
            case "CREATE": {
                break;
            }
            default:
                break;
            }
        } catch (IOException e) {
            Log.log(Level.SEVERE, this, "Error parsing event", e);
        }
    }
}
