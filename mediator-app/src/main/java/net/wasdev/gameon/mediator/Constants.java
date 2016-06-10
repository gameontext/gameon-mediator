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

import java.util.UUID;

import javax.json.Json;
import javax.json.JsonObject;

/**
 * Protocol and message constants
 *
 */
public interface Constants {

    /**
     * The id of the first room (special, provided by the mediator itself).
     */
    String FIRST_ROOM = "firstroom";

    String KEY_ROOM_ID = "roomId";

    /** Room name (for ack, room location events) */
    String KEY_ROOM_NAME = "name";

    /** Full room name (for ack, room location events) */
    String KEY_ROOM_FULLNAME = "fullName";

    /** Room exits (ack, room location events) */
    String KEY_ROOM_EXITS = "exits";

    /** Commands (for ack, room location events) */
    String KEY_COMMANDS = "commands";
    String KEY_BOOKMARK = "bookmark";
    String KEY_USERNAME = "username";
    String KEY_MEDIATOR_ID = "mediatorId";
    String KEY_ROOM_INVENTORY = "roomInventory";

    String VALID_JWT = "{\"type\": \"joinpart\",\"content\": \"connected: validating JWT\"}";
    String FINDROOM = "{\"type\": \"joinpart\",\"content\": \"%s: knock, knock\"}";
    String CONNECTING = "{\"type\": \"joinpart\",\"content\": \"connecting to %s\"}";
    String JOIN = "{\"type\": \"joinpart\",\"content\": \"enter %s\"}";
    String PART = "{\"type\": \"joinpart\",\"content\": \"exit %s\"}";

    String EVENT_HELLO = "{\"type\": \"event\",\"content\": {\"*\": \"%s is here\",\"%s\": \"%s\"},\"bookmark\": \"go-%d\"}";
    String EVENT_GOODBYE =  "{\"type\": \"event\",\"content\": {\"*\": \"%s has gone\",\"%s\": \"%s\"},\"bookmark\": \"g-%d\"}";

    String EVENT_GENERIC = "{\"type\": \"event\",\"content\": {\"%s\": \"%s\"}}";

    String EVENTMSG_INVALID_JWT = "Your JWT is invalid, try [logging in again](/#/login).";
    String EVENTMSG_FINDROOM_FAIL = "Oh dear. That door led nowhere.";
    String EVENTMSG_NO_ROOMS = "Sad news. There isn't a room or site with the id you've requested. The room may have been deleted, or the map may be down. Routing you (back) to First Room.";
    String EVENTMSG_SPLINCH_RECOVERY = "Ow! You were splinched! After a brief jolt (getting unsplinched isn't comfortable), all instances of you should hopefully be in the same room.";
    String EVENTMSG_BAD_RIDE = "There is a sudden jerk, and you feel as though a hook somewhere behind your navel was yanking you ... somewhere.";
    String EVENTMSG_REJOIN_ADVENTURE = "... Your adventure is already in progress on another device, joining ... ";
    String EVENTMSG_MOVING = "You tried to leave when you'd already left! Sadly, it meant going nowhere new.";
    String EVENTMSG_ALREADY_THERE = "You're already right where you wanted to be.";

    String EXIT_ELECTRIC_THUMB = "{\"type\": \"exit\",\"content\": \"In a desperate plea for rescue, you stick out your [Electric Thumb](http://hitchhikers.wikia.com/wiki/Electronic_Thumb) and hope for the best.\"}";

    /**
     * List of common/always present commands
     */
    JsonObject COMMON_COMMANDS = Json.createObjectBuilder()
            .add("/look", "Look at the room")
            .add("/examine", "Examine an item, e.g. `/examine` or `/examine item`")
            .add("/inventory", "List your inventory (will vary by room)")
            .add("/exits", "List room exits")
            .add("/go", "Exit the room using the specified door, e.g. `/go N`")
            .add("/sos", "Emergency rescue: will return you to First Room")
            .add("/help", "List available commands")
            .build();

    String MEDIATOR_UUID = UUID.randomUUID().toString(); // there is a liberty server one?

}
