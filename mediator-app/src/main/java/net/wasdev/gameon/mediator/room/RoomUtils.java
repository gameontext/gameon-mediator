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

import java.util.Random;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import net.wasdev.gameon.mediator.models.Exit;
import net.wasdev.gameon.mediator.models.Exits;

public class RoomUtils {

    /** Type of message indicating successful exit from a room */
    public static final String EXIT = "exit";

    /** Type of message for specifying details about a location */
    public static final String LOCATION = "location";

    /** Type of message to indicate room events */
    public static final String EVENT = "event";

    /** Type of message to indicate chat messages */
    public static final String CHAT = "chat";

    /** JSON element specifying the type of message. */
    public static final String TYPE = "type";

    /** Location: room description */
    public static final String DESCRIPTION = "description";

    /** JSON element specifying the content of message. */
    public static final String CONTENT = "content";

    /** JSON element specifying the user id. */
    public static final String USER_ID = "userId";

    /** JSON element specifying the content of message. */
    public static final String EXIT_ID = "exitId";

    public static final Random random = new Random();

    /**
     * Process a command string: '/go N', or '/go North' to return the direction identifier.
     * @param command
     * @return lower case string containing just the direction letter, e.g. 'n', or null if an unknown direction.
     */
    static String getDirection(String command) {
        if ( command == null || command.isEmpty() || !command.startsWith("/go ") )
            return null;

        String direction = command.substring(4,5).toLowerCase();

        switch(direction) {
            case "n":
            case "s":
            case "e":
            case "w":
            case "u":
            case "d":
                return direction;
            default:
                return null;
        }
    }

    static String longDirection(String lowerLetter) {
        switch(lowerLetter) {
            case "n" : return "North";
            case "s" : return "South";
            case "e" : return "East";
            case "w" : return "West";
            case "u" : return "Up";
            case "d" : return "Down";
            default  : return "off... ";
        }
    }


    public static JsonObject buildContentResponse(String userId, String message) {
        JsonObjectBuilder content = Json.createObjectBuilder();
        content.add(userId, message);
        return content.build();
    }

    public static JsonObject buildContentResponse(String message) {
        JsonObjectBuilder content = Json.createObjectBuilder();
        content.add("*", message);
        return content.build();
    }

    /**
     * Create a set of exits that provides a return path back
     * to the room the user came from.(If the user is going North
     * to enter the new room, the provided exit/return path will be south).
     *
     * @param returnRoom room to turn into an exit
     * @param direction Direction used to reach the room
     */
    public static Exits createFallbackExit(Exit fallbackExit, String direction) {
        Exits newExits = new Exits();

        switch(direction.toLowerCase()) {
            case "n":
                newExits.setS(fallbackExit);
                break;
            case "s":
                newExits.setN(fallbackExit);
                break;
            case "e":
                newExits.setW(fallbackExit);
                break;
            case "w":
                newExits.setE(fallbackExit);
                break;
            default:
                throw new IllegalArgumentException("Invalid direction for fallback exit: " + direction);
        }

        return newExits;
    }

}
