package org.gameontext.mediator.room;

import java.util.List;
import java.util.Random;
import java.util.logging.Level;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import org.gameontext.mediator.Constants;
import org.gameontext.mediator.Log;
import org.gameontext.mediator.MapClient;
import org.gameontext.mediator.MediatorNexus;
import org.gameontext.mediator.PlayerClient;
import org.gameontext.mediator.models.Exits;
import org.gameontext.mediator.models.RoomInfo;
import org.gameontext.mediator.models.Site;

public class FirstRoom extends AbstractRoomMediator {

    public static final String TELEPORT = "teleport";

    public static final String FIRST_ROOM_FULL = "The First Room";
    static final String FIRST_ROOM_DESC = "You've entered a vaguely squarish room, with walls of an indeterminate color. A note is pinned to the wall.";
    static final String FIRST_ROOM_EXTENDED = "\n\nTL;DR README (The extended edition is [here](https://book.gameontext.org/)): \n\n"
            + "* Commands start with '/'.\n"
            + "* Use `/help` to list all available commands. The list will change from room to room.\n"
            + "* Use `/exits` to list all available exits.\n"
            + "* Use `/sos` to return to First Room if you're stuck.\n"
            + "* Rooms might try to fool you, but these three commands will always work.";

    static final String FIRST_ROOM_INV = "Sadly, there is nothing here.";
 
    static final String FIRST_ROOM_POCKETS = "You do not appear to be carrying anything.";
    static final String FIRST_ROOM_POCKETS_EXTENDED = "\n\n Individual rooms "
            + " may or may not support the notion of items. So whether or not you have things in your pockets"
            + " will change from room to room, as will how long they stay there.";

    static final String TELEPORT_GO = "You punch the coordinates into the console, a large tube appears from above you, and you are sucked into a maze of piping.";
    static final String TELEPORT_NO_ROOMS = "There isn't a room with that id to teleport to, maybe you should check `/listmyrooms`";
    static final String TELEPORT_MANY_ROOMS = "There are multiple rooms named %s. To get to the correct room, use one of the following long IDs (you may need to ask the room owner which ID is for their room):\n";

    public static Site getFallbackSite() {
        Site site = new Site(Constants.FIRST_ROOM);

        // Connection to the map must be down. :( Fake something
        RoomInfo info = new RoomInfo();
        info.setName(Constants.FIRST_ROOM);
        info.setFullName(FIRST_ROOM_FULL);

        site.setInfo(info);
        site.setExits(new Exits());
        return site;
    }

    boolean newbie = false;
    boolean inventory = false;

    final String playerJwt;
    final PlayerClient playerClient;

    public FirstRoom(MediatorNexus.View nexus, String playerJwt, PlayerClient playerClient, MapClient mapClient, Site site) {
        this(nexus, playerJwt, playerClient, mapClient, site, false);
    }

    public FirstRoom(MediatorNexus.View nexus, String playerJwt, PlayerClient playerClient, MapClient mapClient, Site site, boolean newbie) {
        super(nexus, mapClient, site);
        this.playerJwt = playerJwt;
        this.playerClient = playerClient;
        this.newbie = newbie;
    }

    @Override
    public String getName() {
        return Constants.FIRST_ROOM;
    }

    @Override
    public String getFullName() {
        return FIRST_ROOM_FULL;
    }

    @Override
    public String getDescription() {
        return FIRST_ROOM_DESC;
    }

    @Override
    public Type getType() {
        return Type.FIRST_ROOM;
    }

    /** Process the text of a command */
    @Override
    protected String parseCommand(String userId, String userName, JsonObject sourceMessage, JsonObjectBuilder responseBuilder) {
        String content = sourceMessage.getString(RoomUtils.CONTENT);
        String contentToLower = content.toLowerCase();
        
        if ( contentToLower.startsWith("/look ") || (contentToLower.startsWith("/examine"))) {
            JsonObject contentResponse;
            if ( contentToLower.contains(" note") ) {
                contentResponse = RoomUtils.buildContentResponse(userId,
                        "Welcome to Game On!\n\n"
                        + "Please take some time to explore the game and wander from room to room (`/go N`). "
                        + "Each room is a separate microservice provided by developers like you. "
                        + "We hope you feel encouraged to explore microservices by building your own room.\n\n"
                        + "* Command behavior will vary from room to room, as different people write different things\n"
                        + "* The Mediator (the service providing First Room) connects to rooms on your behalf using pre-defined websocket endpoints.\n"
                        + "* Status updates on the right side show the Mediator's progress as it coordinates switching rooms.");
                
                responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT).add(RoomUtils.CONTENT, contentResponse);
            } else if ( contentToLower.contains(" room") ) {
                buildLocationResponse(responseBuilder);
            } else {
                contentResponse = RoomUtils.buildContentResponse(userId, "Not sure what you're trying to examine.");                
                responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT).add(RoomUtils.CONTENT, contentResponse);
            }
        } else if (contentToLower.startsWith("/inventory")) {
            responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT).add(RoomUtils.CONTENT, buildInventoryResponse());

        } else if (contentToLower.startsWith("/listmyrooms")) {
            processListMyRoomsCommand(userId, responseBuilder);

        } else if (contentToLower.startsWith("/listsystemrooms")) {
            processListSystemRoomsCommand(responseBuilder);

        } else if (contentToLower.startsWith("/deleteroom")) {
            processDeleteRoomCommand(userId, content, contentToLower, responseBuilder);

        } else if (contentToLower.startsWith("/teleport")) {
            processTeleportCommand(userId, userName, content, contentToLower, responseBuilder);

        } else if (contentToLower.startsWith("/")) {
            responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT).add(RoomUtils.CONTENT,
                    RoomUtils.buildContentResponse("This room is a basic model. It doesn't understand that command."));
        }

        return userId;
    }

    @Override
    protected void buildLocationResponse(JsonObjectBuilder responseBuilder) {
        super.buildLocationResponse(responseBuilder);

        if (newbie) {
            responseBuilder.add(RoomUtils.DESCRIPTION, FIRST_ROOM_DESC + FIRST_ROOM_EXTENDED);
            newbie = false;
        } else {
            responseBuilder.add(RoomUtils.DESCRIPTION, FIRST_ROOM_DESC);
        }
    }
    
    @Override
    protected void addCommands(JsonObjectBuilder responseBuilder) {
        JsonObjectBuilder content = Json.createObjectBuilder();
        content.add("/inventory", "Check your pockets");
        content.add("/listmyrooms", "List all of your rooms");
        content.add("/teleport", "Teleport to the specified room, e.g. `/teleport room-id`");
        content.add("/deleteroom", "Deregisters a room you have registered. e.g. `/deleteroom room-id`");

        responseBuilder.add(Constants.KEY_COMMANDS, content.build());
    }

    @Override
    protected void addRoomItems(JsonObjectBuilder responseBuilder) {
        JsonArrayBuilder content = Json.createArrayBuilder();
        content.add("Note");
        responseBuilder.add(Constants.KEY_ROOM_INVENTORY, content.build());
    }

    protected JsonObject buildInventoryResponse() {
        if (inventory)
            return RoomUtils.buildContentResponse(FIRST_ROOM_POCKETS);

        inventory = true;
        return RoomUtils.buildContentResponse(FIRST_ROOM_POCKETS + FIRST_ROOM_POCKETS_EXTENDED);
    }

    private void processListMyRoomsCommand(String userId, JsonObjectBuilder responseBuilder) {
        List<Site> rooms = mapClient.getRoomsByOwner(userId);

        StringBuffer roomSummary = new StringBuffer();
        if (!rooms.isEmpty()) {
            roomSummary.append("You have registered the following rooms... \n");
            for (Site room : rooms) {
                if (room.getInfo() != null) {
                    roomSummary.append(" - '" + room.getInfo().getFullName() + "' with id " + room.getInfo().getName() + " (long id: " + room.getId() +")\n");
                }
            }
            roomSummary.append("\nYou can go directly to a room using /teleport <roomid>");
        } else {
            roomSummary.append("You have no rooms registered!");
        }

        responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT).add(RoomUtils.CONTENT,
                RoomUtils.buildContentResponse(roomSummary.toString()));
    }

    private void processListSystemRoomsCommand(JsonObjectBuilder responseBuilder) {
        List<Site> rooms = mapClient.getSystemRooms();

        StringBuffer roomSummary = new StringBuffer();
        if (!rooms.isEmpty()) {
            roomSummary.append("There are the following system rooms... \n");
            for (Site room : rooms) {
                if (room.getInfo() != null) {
                    roomSummary.append(" - '" + room.getInfo().getFullName() + "' with id " + room.getInfo().getName()
                            + " (teleport id " + room.getId() + ")\n");
                }
            }
            roomSummary.append("\nYou can go directly to a system room using /teleport <teleportid>");
        } else {
            roomSummary.append("There are no system rooms registered!");
        }

        responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT).add(RoomUtils.CONTENT,
                RoomUtils.buildContentResponse(roomSummary.toString()));
    }

    private void processDeleteRoomCommand(String userId, String content, String contentToLower, JsonObjectBuilder responseBuilder) {
        Log.log(Level.INFO, this, "Processing delete command.. {0}", contentToLower);

        if (contentToLower.length() > "/deleteroom ".length()) {
            String targetId = content.substring("/deleteroom ".length());

            List<Site> possibleCandidates = mapClient.getRoomsByOwnerAndRoomName(userId, targetId);
            if (possibleCandidates.isEmpty()) {
                responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT).add(RoomUtils.CONTENT,
                        RoomUtils.buildContentResponse(
                                "You don't appear to have a room with that id to delete. maybe you should check `/listmyrooms`"));
            } else {
                // obtain the users shared secret using their jwt..
                String secret = playerClient.getSharedSecret(userId, playerJwt);
                Log.log(Level.INFO, this, "Got key for user of (first4chars) {0}",
                        String.valueOf(secret).substring(0, 4));

                if (secret == null) {
                    responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT).add(RoomUtils.CONTENT,
                            RoomUtils.buildContentResponse(
                                    "Sqork. The Internal Cogs Rumble, but refuse to move, I'm not sure who you are."));

                    // EXIT EARLY
                    return;
                }

                if (mapClient.deleteSite(possibleCandidates.get(0).getId(), userId, secret)) {
                    responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT).add(RoomUtils.CONTENT,
                            RoomUtils.buildContentResponse("The room has varnished. Like an old oak table."));
                } else {
                    responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT).add(RoomUtils.CONTENT,
                            RoomUtils.buildContentResponse("The room stubbornly refuses to be deleted.. "));
                }
            }
        } else {
            responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT).add(RoomUtils.CONTENT,
                    RoomUtils.buildContentResponse("But how can you delete that, if you have no fingers?"));
        }
    }

    private void processTeleportCommand(String userId, String userName, String content, String contentToLower, JsonObjectBuilder responseBuilder) {
        if (contentToLower.length() > "/teleport ".length()) {
            String requestedTarget = content.substring("/teleport ".length());
            beamMeUp(userId, userName, requestedTarget, responseBuilder);
        } else {
            responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT).add(RoomUtils.CONTENT, RoomUtils.buildContentResponse(
                    "You concentrate really hard, and teleport from your current location, to your current location.. Magic!!"));
        }
    }

    private void beamMeUp(String userId, String userName, String targetId, JsonObjectBuilder responseBuilder){
        // First , see if we can find an exact match for this
        Site siteToBeamTo = findSite(userId, targetId, responseBuilder);

        if (siteToBeamTo != null) {
            String fly = "";
            if ((new Random()).nextInt(10) == 0) {
                fly = " Just before the teleporter engages, you hear a slight buzzing noise.. Good luck " + userName + "-Fly!";
            }
            responseBuilder.add(RoomUtils.TYPE, RoomUtils.EXIT).add(RoomUtils.EXIT_ID, siteToBeamTo.getId())
                    .add(FirstRoom.TELEPORT, true)
                    .add(RoomUtils.CONTENT, TELEPORT_GO + fly);
        }
    }

    private Site findSite(String userId, String targetId, JsonObjectBuilder responseBuilder) {
        Site singleSite = mapClient.getSite(targetId);

        if (singleSite != null) {
            return singleSite;
        }

        List<Site> possibleCandidates = mapClient.getRoomsByRoomName(targetId);

        if (possibleCandidates.isEmpty()) {
            responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT)
                .add(RoomUtils.CONTENT, RoomUtils.buildContentResponse(userId, FirstRoom.TELEPORT_NO_ROOMS));
            return null;
        }
        if (possibleCandidates.size() > 1) {
            StringBuilder returnedMessage = new StringBuilder();
            returnedMessage.append(String.format(TELEPORT_MANY_ROOMS, targetId));
            for (Site site : possibleCandidates) {
                returnedMessage.append(" - /teleport "+site.getId()+ " (owned by " + site.getOwner() + ")\n");
            }
            responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT).add(RoomUtils.CONTENT,
                    RoomUtils.buildContentResponse(userId, returnedMessage.toString()));
            return null;
        }
        return possibleCandidates.get(0);
    }
}
