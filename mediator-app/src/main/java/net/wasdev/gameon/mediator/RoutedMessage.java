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

import java.io.StringReader;
import java.util.ArrayList;

import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonString;

/**
 * Routed messages are intended to defer or avoid processing the bulk of the
 * JSON message for the instances when the mediator acts only as a pass-through.
 */
public class RoutedMessage {

    /**
     * Create a message without an explicit destination (usually a client-player
     * control message)
     *
     * @param flowTarget
     *            The prefix that identifies how the message should be routed
     * @param jsonData
     *            The Json payload for the message
     * @return a new RoutedMessage
     */
    public static RoutedMessage createMessage(String flowTarget, JsonObject jsonData) {
        return createMessage(flowTarget, "", jsonData);
    }

    /**
     * Create a new message
     *
     * @param flowTarget
     *            The prefix that identifies how the message should be routed
     * @param destination
     *            Specific room or player id (depending on direction)
     * @param jsonData
     *            The Json payload for the message
     * @return a new RoutedMessage
     */
    public static RoutedMessage createMessage(String flowTarget, String destination, JsonObject jsonData) {
        return new RoutedMessage(flowTarget, destination, jsonData);
    }

    /**
     * Create a new message
     *
     * @param flowTarget
     *            The prefix that identifies how the message should be routed
     * @param destination
     *            Specific room or player id (depending on direction)
     * @param messageData
     *            Pre-marshalled message data (usually from a constant)
     * @return a new RoutedMessage
     */
    public static RoutedMessage createMessage(String flowTarget, String destination, String messageData) {
        return new RoutedMessage(flowTarget, destination, messageData);
    }

    /**
     * For pass-through messages, keep the original value to resend.
     */
    private String wholeMessage;

    /**
     * Either player* if the message is flowing from room to player, or room* if
     * the message is flowing from player to room.
     */
    private final String flowTarget;

    /**
     * Either a wildcard (usually used to indicate a broadcast to all players),
     * or a specific player id or room id to allow a player or room to filter
     * out messages that aren't intended for them (more common with the player
     * service than with the room).
     */
    private final String destination;

    /**
     * String containing the payload of the message. Not set when the message is
     * constructed using a JsonObject
     */
    private String messageData = null;

    /**
     * JsonObject representing the payload of the message (beyond the routing
     * data). This field is set lazily for objects built by the decoder. We try
     * to avoid deserializing the JsonObject when the Player service doesn't
     * care what is in it.
     */
    private JsonObject jsonData = null;

    /**
     * Parse the source message to pull off the routing data. Routing data has
     * two or three parts:
     * <li>ready,{ ... }
     * <li>room,&lt;roomId&gt;,{...}
     * <li>sos,*,{ ... }
     * <li>player,*,{...}
     * <li>player,&lt;playerId&gt;,{...}
     * <li>playerLocation,&lt;playerId&gt;,{...}
     * <li>
     *
     * The Player service very rarely creates messages out of the blue (when it
     * does it uses the builder to make one with the right parameters in a way
     * that conveys the most information about what is being done).
     *
     * @param msg
     *            Text message pulled off the WebSocket
     */
    public RoutedMessage(String message) {
        this.wholeMessage = message;

        // this is getting parsed in a low-level/raw way, again to avoid doing
        // anything
        // with the Json payload unless/until we need to.
        // Also, we don't split on commas arbitrarily: there are commas in the
        // json payload,
        // which means unnecessary splitting and joining.
        ArrayList<String> list = new ArrayList<>(3);
        int brace = message.indexOf('{');
        int i = 0;
        int j = message.indexOf(',');
        while (j > 0 && j < brace) {
            list.add(message.substring(i, j).trim());
            i = j + 1;
            j = message.indexOf(',', i);
        }

        // stash all of the rest in the data field.
        this.messageData = message.substring(i).trim();

        // The flowTarget is always present.
        // The destination may or may not be present, but shouldn't return null.
        this.flowTarget = list.get(0);
        this.destination = list.size() > 1 ? list.get(1) : "";
    }

    /**
     *
     * @param flowTarget
     *            Indicator of which direction the message is flowing, along
     *            with a general indication of what kind of message it is.
     * @param destination
     *            Destination of message: either a wildcard or specific id.
     * @param message
     *            JSON payload
     */
    private RoutedMessage(String flowTarget, String destination, String message) {
        this.wholeMessage = null;
        this.flowTarget = flowTarget;
        this.destination = destination;
        this.messageData = message;
    }

    /**
     * @param flowTarget
     *            Indicator of which direction the message is flowing, along
     *            with a general indication of what kind of message it is.
     * @param destination
     *            Destination of message: either a wildcard or specific id.
     * @param message
     *            JSON payload
     */
    private RoutedMessage(String flowTarget, String destination, JsonObject jsonData) {
        this.wholeMessage = null;
        this.flowTarget = flowTarget;
        this.destination = destination;
        this.jsonData = jsonData;
    }

    /**
     * @return the routing portion of the original message (player*, room*,
     *         ready, ack, sos)
     */
    public String getFlowTarget() {
        return flowTarget;
    }

    /**
     * Parse and return the JsonObject for the payload
     *
     * @param a
     *            JsonObject for the parsed payload
     */
    public JsonObject getParsedBody() {
        // We do not worry about concurrency here: processing any given message
        // is single threaded unti queued (at which point we aren't looking
        // anymore)
        // We also are not using the more advanced streaming APIs, as the
        // messages
        // the player service unpacks tend to be short and focused.
        if (jsonData == null) {
            JsonReader jsonReader = Json.createReader(new StringReader(messageData));
            jsonData = jsonReader.readObject();
        }

        return jsonData;
    }

    /**
     * @return true if the message is a special route that should be intercepted
     *         by the mediator to redirect the user to a different room.
     */
    public boolean isSOS() {
        return flowTarget.equals(Constants.SOS);
    }

    /**
     * @return message destination (specific room, player, or wildcard for all
     *         players)
     */
    public String getDestination() {
        return destination;
    }

    /**
     * @param userId
     * @return true if this message should be sent to the specified user
     */
    public boolean isForUser(String userId) {
        if (flowTarget.startsWith(Constants.PLAYER))
            return "*".equals(destination) || destination.equals(userId);
        else
            return false;
    }

    /**
     * @return true if the message is flowing from room to client
     */
    public boolean isClientBound() {
        return flowTarget.startsWith(Constants.PLAYER);
    }

    /**
     * @param targetRoom
     * @return true if this message should be sent to the specified room
     */
    public boolean isForRoom(RoomMediator targetRoom) {
        return destination.equals(targetRoom.getId());
    }

    /**
     * Get optional long value out of the message json payload
     *
     * @param key
     *            Key to find value for
     * @param value
     *            Default value if key can't be found
     * @return value in object, or the provided default
     */
    public String getOptionalValue(String key, String defaultValue) {
        String result = defaultValue;
        JsonString value = getParsedBody().getJsonString(key);
        if (value != null)
            result = value.getString();

        return result;
    }

    /**
     * Get optional long value out of the message json payload
     *
     * @param key
     *            Key to find value for
     * @param value
     *            Default value if key can't be found
     * @return value in object, or the provided default
     */
    public long getOptionalValue(String key, long value) {
        long result = value;
        JsonNumber num = getParsedBody().getJsonNumber(key);
        if (num != null)
            result = num.longValue();

        return result;
    }

    @Override
    public String toString() {
        if (wholeMessage != null)
            return wholeMessage;

        StringBuilder result = new StringBuilder();
        result.append(flowTarget).append(',');

        if (!destination.isEmpty()) {
            result.append(destination).append(',');
        }

        if (messageData != null) {
            result.append(messageData);
        } else if (jsonData != null) {
            result.append(jsonData.toString());
        }

        wholeMessage = result.toString();

        return wholeMessage;
    }
}
