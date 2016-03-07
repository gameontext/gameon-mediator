package net.wasdev.gameon.mediator.room;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import net.wasdev.gameon.mediator.Constants;
import net.wasdev.gameon.mediator.Log;
import net.wasdev.gameon.mediator.MapClient;
import net.wasdev.gameon.mediator.PlayerConnectionMediator;
import net.wasdev.gameon.mediator.RoomMediator;
import net.wasdev.gameon.mediator.RoutedMessage;
import net.wasdev.gameon.mediator.models.ConnectionDetails;
import net.wasdev.gameon.mediator.models.Exit;
import net.wasdev.gameon.mediator.models.Exits;
import net.wasdev.gameon.mediator.models.Site;

public class EmptyRoom implements RoomMediator {

    public static final String EMPTY_ROOMNAME = "emptyRoom";
    public static final String EMPTY_FULLNAME = "Empty Room";

    static final List<String> EMTPY_ROOMS = Collections.unmodifiableList(Arrays.asList(
            "Said your name, in an empty room",
            "Is that... padding on the walls?",
            "The center of the room is completely empty",
            "Nothing even remotely interesting is happening in here"));

    static final String NO_COMMANDS = "There is nothing here that can do that.";

    final Random random = new Random();
    final MapClient mapClient;
    final String id;

    Site site = null;
    Exits exits = null;

    boolean isEmpty = true;
    long lastCheck = 0;

    PlayerConnectionMediator session = null;

    /**
     * Create a placeholder empty room based on exit info.
     * The room will then figure out its other exits.
     *
     * @param mapClient Client for discovering/maintaining exit information
     * @param exitInfo Exit information (for creating this room)
     */
    public EmptyRoom(MapClient mapClient, String id, RoomMediator returnRoom) {
        this.mapClient = mapClient;

        // We're going to mock up a site here, just in case the lookup fails.
        // Means we'll at least be able to get back to how we got here
        this.id = id;

        // At the least, make sure we can get back to where we came from
        exits = createExits(id, returnRoom);

        // Now try to get Exits (we have a fallback if it fails)
        getExits();
    }

    /**
     * @return true if this empty room is not empty any more
     */
    public boolean notEmpty() {
        return !isEmpty;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return EMPTY_ROOMNAME;
    }

    @Override
    public String getFullName() {
        return EMPTY_FULLNAME;
    }
    
    @Override
    public String getToken() {
        return null;
    }

    @Override
    public ConnectionDetails getConnectionDetails() {
        return null;
    }

    @Override
    public Exits getExits() {
        Exits currentExits = exits;
        long now = System.nanoTime();
        if ( lastCheck == 0 || now - lastCheck > TimeUnit.SECONDS.toNanos(30) ) {
            try {
                site = mapClient.getSite(id);
                currentExits = exits = site.getExits();
                lastCheck = now;
                isEmpty = site.getInfo() == null;
            } catch(Exception e) {
                Log.log(Level.WARNING, this, "Unable to retrieve exits for room ["+id+"], will continue with old values", e);
            }
        }

        return currentExits;
    }

    @Override
    public Exit getExit(String direction) {
        Exits currentExits = getExits();
        return currentExits == null ? null : currentExits.getExit(direction);
    }

    @Override
    public JsonObject listExits() {
        Exits exits = getExits();
        return exits.toJson();
    }

    @Override
    public boolean connect() {
        return true;
    }

    @Override
    public boolean subscribe(PlayerConnectionMediator playerSession, long lastmessage) {
        this.session = playerSession;
        return true;
    }

    @Override
    public void unsubscribe(PlayerConnectionMediator playerSession) {
    }

    @Override
    public void disconnect() {
    }

    @Override
    public void send(RoutedMessage message) {
        Log.log(Level.FINEST, this, "TheFirstRoom received: {0}", message);

        JsonObject sourceMessage = message.getParsedBody();

        JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add(Constants.BOOKMARK, 0);
        switch (message.getFlowTarget()) {
            case Constants.ROOM_HELLO:
                buildLocationResponse(builder);
                break;
            case Constants.ROOM_GOODBYE:
                // no response for roomGoodbye
                return;
            default:
                parseCommand(sourceMessage, builder);
                break;
        }

        JsonObject response = builder.build();

        String target = Constants.PLAYER;
        if (response.containsKey(RoomUtils.EXIT_ID)) {
            target = Constants.PLAYER_LOCATION;
        }

        session.sendToClient(RoutedMessage.createMessage(target, sourceMessage.getString(RoomUtils.USER_ID), response));
    }

    protected void parseCommand(JsonObject sourceMessage, JsonObjectBuilder responseBuilder) {
        String content = sourceMessage.getString(RoomUtils.CONTENT);
        String userName = sourceMessage.getString(Constants.USERNAME);

        if ( content.startsWith("/go") ) {
            String exitDirection = RoomUtils.getDirection(content.toLowerCase());
            if ( exitDirection == null ) {
                responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT)
                .add(RoomUtils.CONTENT, RoomUtils.buildContentResponse(userName, "Hmm. That direction didn't make sense. Try again?"));
            } else if ( "u".equals(exitDirection) ) {
                responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT)
                .add(RoomUtils.CONTENT, RoomUtils.buildContentResponse(userName, "There might be a ceiling, or perhaps just clouds. Certainly no doors."));
            } else if ( "d".equals(exitDirection) ) {
                responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT)
                .add(RoomUtils.CONTENT, RoomUtils.buildContentResponse(userName, "Ooof. Yep. That's a floor."));
            } else {
                responseBuilder.add(RoomUtils.TYPE, RoomUtils.EXIT).add(RoomUtils.EXIT_ID, exitDirection)
                .add(RoomUtils.CONTENT, "You head " + longDirection(exitDirection));
            }
        } else if ( content.charAt(0) == '/' ) {
            responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT)
            .add(RoomUtils.CONTENT, RoomUtils.buildContentResponse(userName, NO_COMMANDS));
        } else {
            responseBuilder
            .add(Constants.USERNAME, userName)
            .add(RoomUtils.CONTENT, content)
            .add(RoomUtils.TYPE, RoomUtils.CHAT);
        }
    }

    /** Build a location response */
    private void buildLocationResponse(JsonObjectBuilder responseBuilder) {
        responseBuilder.add(RoomUtils.TYPE, RoomUtils.LOCATION);
        responseBuilder.add(Constants.NAME, EMPTY_ROOMNAME);
        responseBuilder.add(Constants.FULL_NAME, EMPTY_FULLNAME);
        responseBuilder.add(Constants.ROOM_EXITS, listExits());
        responseBuilder.add(RoomUtils.DESCRIPTION, randomDescription());
    }

    private String randomDescription() {
        int index = random.nextInt(EMTPY_ROOMS.size());
        return EMTPY_ROOMS.get(index);
    }

    /**
     * Assign information about a door from the targetSite into
     * the exit information. Note that orientation will flip here:
     * for the current room, the North Exit will be populated with
     * information about the South door of the adjacent/target room.
     *
     * @param returnRoom room to turn into an exit
     */
    public Exits createExits(String sourceId, RoomMediator returnRoom) {
        Exits newExits = new Exits();

        Exit exit = new Exit();
        exit.setId(returnRoom.getId());
        exit.setName(returnRoom.getName());
        exit.setFullName(returnRoom.getFullName());
        exit.setConnectionDetails(returnRoom.getConnectionDetails());
        exit.setDoor("Back where you came from");

        Exits exits = returnRoom.getExits();
        if ( exits.getN() != null && sourceId.equals(exits.getN().getId()) ) {
            newExits.setS(exit);
        } else if ( exits.getS() != null && sourceId.equals(exits.getS().getId()) ) {
            newExits.setN(exit);
        } else if ( exits.getE() != null && sourceId.equals(exits.getE().getId()) ) {
            newExits.setW(exit);
        } else if ( exits.getW() != null && sourceId.equals(exits.getW().getId()) ) {
            newExits.setE(exit);
        }

        return newExits;
    }

    private String longDirection(String lowerLetter) {
        switch(lowerLetter) {
            case "n" : return "North";
            case "s" : return "South";
            case "e" : return "East";
            case "w" : return "West";
        }
        return "off... ";
    }

    @Override
    public long getSelectedProtocolVersion() {
        return 1;
    }
}
