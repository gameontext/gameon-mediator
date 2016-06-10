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
import java.util.List;
import java.util.logging.Level;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;

import net.wasdev.gameon.mediator.room.RoomMediator;

/**
 * Routed messages are intended to defer or avoid processing the bulk of the
 * JSON message for the instances when the mediator acts only as a pass-through.
 */
public class RoutedMessage {

    /** Routing: ClientMediator sends hello when a player enters the room (new room) */
    public static final String ROOM_HELLO = "roomHello";

    /** Routing: ClientMediator sends goodbye when a player leaves the room */
    public static final String ROOM_GOODBYE = "roomGoodbye";

    /** Routing: ClientMediator sends hello when a player enters the room (re-connect) */
    public static final String ROOM_JOIN = "roomJoin";

    /** Routing: ClientMediator sends goodbye when a player leaves the room (connection close) */
    public static final String ROOM_PART = "roomPart";


    /** Routing: Message directed from room to player */
    public static final String PLAYER = "player";

    public static final String ROOM = "room";

    /**
     * Routing: Message from room to mediator indicating that the player
     * successfully opened a door. This will tell the mediator to attempt a room
     * change.
     */
    public static final String PLAYER_LOCATION = "playerLocation";

    /**
     * Routing: sent by the player to attempt a "rescue" (a jump from one room
     * to a random other room if they get stuck (or feel like exploring).
     */
    public static final String SOS = "sos";
    public static final String ACK = "ack";
    public static final String READY = "ready";

    public static final String MSG_HELLO_JOIN = "{\"version\": %d,\"userId\": \"%s\",\"username\": \"%s\"}";
    public static final String MSG_RECOVERY_HELLO = "{\"version\": %d,\"userId\": \"%s\",\"username\": \"%s\",\"recovery\": true}";
    public static final String MSG_PROTOCOL = "{\"userId\": \"%s\",\"username\": \"%s\"}";

    public enum FlowTarget {
        ack(RoutedMessage.ACK),
        ready(RoutedMessage.READY),
        player(RoutedMessage.PLAYER),
        playerLocation(RoutedMessage.PLAYER_LOCATION),
        room(RoutedMessage.ROOM),
        roomHello(RoutedMessage.ROOM_HELLO),
        roomGoodbye(RoutedMessage.ROOM_GOODBYE),
        roomJoin(RoutedMessage.ROOM_JOIN),
        roomPart(RoutedMessage.ROOM_PART),
        sos(RoutedMessage.SOS);

        private final String name;

        FlowTarget(String name) {
            this.name = name;
        }

        public boolean forPlayer() {
            return name.startsWith(RoutedMessage.PLAYER);
        }

        public boolean forRoom() {
            return name.startsWith(RoutedMessage.ROOM);
        }
    }

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
    public static RoutedMessage createMessage(FlowTarget flowTarget, JsonObject jsonData) {
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
    public static RoutedMessage createMessage(FlowTarget flowTarget, String destination, JsonObject jsonData) {
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
    public static RoutedMessage createMessage(FlowTarget flowTarget, String destination, String messageData) {
        return new RoutedMessage(flowTarget, destination, messageData);
    }

    public static RoutedMessage createSimpleEventMessage(FlowTarget flowTarget, String playerId, String eventText) {
        return new RoutedMessage(flowTarget, playerId, String.format(Constants.EVENT_GENERIC, playerId, eventText));
    }

    public static RoutedMessage createHello(long version, String roomId, MediatorNexus.UserView user) {
        return new RoutedMessage(FlowTarget.roomHello, roomId,
                String.format(MSG_HELLO_JOIN, version, user.getUserId(), user.getUserName()));
    }

    public static RoutedMessage createRecoveryHello(long version, String roomId, MediatorNexus.UserView user) {
        return new RoutedMessage(FlowTarget.roomHello, roomId,
                String.format(MSG_RECOVERY_HELLO, version, user.getUserId(), user.getUserName()));
    }

    public static RoutedMessage createGoodbye(String roomId, MediatorNexus.UserView user) {
        return new RoutedMessage(FlowTarget.roomGoodbye, roomId,
                String.format(MSG_PROTOCOL, user.getUserId(), user.getUserName()));
    }

    /**
     * Only use the bookmark with v2 join messages: it is noted as a String.
     * @param version
     * @return
     */
    public static RoutedMessage createJoin(long version, String roomId, MediatorNexus.UserView user) {
        return new RoutedMessage(FlowTarget.roomJoin, roomId,
                String.format(MSG_HELLO_JOIN, version, user.getUserId(), user.getUserName()));
    }

    public static RoutedMessage createPart(String roomId, MediatorNexus.UserView user) {
        return new RoutedMessage(FlowTarget.roomPart, roomId,
                String.format(MSG_PROTOCOL, user.getUserId(), user.getUserName()));
    }

    /**
     * For pass-through messages, keep the original value to resend.
     */
    private String wholeMessage;

    /**
     * Either player* if the message is flowing from room to player, or room* if
     * the message is flowing from player to room.
     */
    private final FlowTarget flowTarget;

    /**
     * Either a wildcard (usually used to indicate a broadcast to all sessionPods),
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
        this.flowTarget = FlowTarget.valueOf(list.get(0));
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
    private RoutedMessage(FlowTarget flowTarget, String destination, String message) {
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
    private RoutedMessage(FlowTarget flowTarget, String destination, JsonObject jsonData) {
        this.wholeMessage = null;
        this.flowTarget = flowTarget;
        this.destination = destination;
        this.jsonData = jsonData;
    }

    /**
     * @return the routing portion of the original message (player*, room*,
     *         ready, ack, sos)
     */
    public FlowTarget getFlowTarget() {
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
        // is single threaded until queued (at which point we aren't looking
        // anymore)
        // We also are not using the more advanced streaming APIs, as the
        // messages the player service unpacks tend to be short and focused.
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
        return flowTarget == FlowTarget.sos;
    }

    /**
     * @return message destination (specific room, player, or wildcard for all
     *         sessionPods)
     */
    public String getDestination() {
        return destination;
    }

    /**
     * @param userId
     * @return true if this message should be sent to the specified user
     */
    public boolean isForUser(String userId) {
        if (flowTarget.forPlayer() ) {
            return "*".equals(destination) || destination.equals(userId);
        }
        return flowTarget == FlowTarget.ack;
    }

    /**
     * @return true if the message is flowing from room to client
     */
    public boolean isClientBound() {
        return flowTarget.forPlayer();
    }

    /**
     * @param targetRoom
     * @return true if this message should be sent to the specified room
     */
    public boolean isForRoom(RoomMediator targetRoom) {
        if ( flowTarget.forRoom() ) {
            return destination.equals(targetRoom.getId());
        }
        return false;
    }

    /**
     * Get optional long value out of the message json payload
     *
     * @param key
     *            Key to find value for
     * @return value in object or null
     */
    public String getString(String key) {
        String result = null;
        JsonObject obj = getParsedBody();
        JsonValue value = obj.get(key);
        if ( value != null ) {
            try {
                if ( value.getValueType() == ValueType.STRING) {
                    result = obj.getString(key);
                } else {
                    result = value.toString();
                }
            } catch (Exception e) { // class cast, etc
                Log.log(Level.FINER, this, "Exception parsing String: " + value, e);
                // fall through to return default value
            }
        }

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
    public String getString(String key, String defaultValue) {
        String result = getString(key);
        return result == null ? defaultValue : result;
    }

    /**
     * @param key
     * @return JsonObject value or null
     */
    public JsonObject getObject(String key) {
        JsonObject obj = getParsedBody();
        JsonValue value = obj.get(key);
        if ( value != null && value.getValueType() == ValueType.OBJECT) {
            return (JsonObject) value;
        }
        return null;
    }

    /**
     * @param key
     * @return JsonArray value or null
     */
    public List<Long> getLongArray(String key, List<Long> defaultValue) {
        JsonObject obj = getParsedBody();
        JsonValue arrayValue = obj.get(key);
        if ( arrayValue != null && arrayValue.getValueType() == ValueType.ARRAY ) {
            try {
                List<Long> result = new ArrayList<>();
                ((JsonArray) arrayValue).forEach(value -> result.add(((JsonNumber) value).longValue()));
                return result;
            } catch (Exception e) { // class cast, etc
                Log.log(Level.FINER, this, "Exception parsing JsonArray: " + arrayValue, e);
                // fall through to return default value
            }
        }
        return defaultValue;
    }

    /**
     * Get long value out of the message json payload
     *
     * @param key
     *            Key to find value for
     * @param defaultValue
     *            Default value if key can't be found
     * @return value in object, or the provided default
     */
    public long getLong(String key, long defaultValue) {
        JsonObject obj = getParsedBody();
        JsonValue value = obj.get(key);
        if ( value != null && value.getValueType() == ValueType.NUMBER ) {
            return ((JsonNumber) value).longValue();
        }
        return defaultValue;
    }

    /**
     * Get boolean value out of the message json payload
     *
     * @param key
     *            Key to find value for
     * @param defaultValue
     *            Default value if key can't be found
     * @return value in object, or the provided default
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        JsonObject obj = getParsedBody();
        JsonValue value = obj.get(key);
        if ( value != null ) {
            if ( value.getValueType() == ValueType.FALSE )
                return false;
            if ( value.getValueType() == ValueType.TRUE )
                return true;
        }
        return defaultValue;
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
