package net.wasdev.gameon.mediator.room;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

public class RoomUtils {

    static String getDirection(String direction) {
        if ( direction == null || direction.isEmpty() || direction.length() < 5 )
            return null;

        char d = direction.charAt(4);

        switch(d) {
            case 'n':
            case 's':
            case 'e':
            case 'w':
            case 'u':
            case 'd':
                return "" + d;
            default:
                return null;
        }
    }

    static JsonObject buildContentResponse(String message) {
        JsonObjectBuilder content = Json.createObjectBuilder();
        content.add("*", message);
        return content.build();
    }

    static JsonObject buildContentResponse(String userId, String message) {
        JsonObjectBuilder content = Json.createObjectBuilder();
        content.add(userId, message);
        return content.build();
    }

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
    public static final String USER_ID = "userId";
    public static final String EXIT_ID = "exitId";


}
