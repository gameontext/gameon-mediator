package org.gameontext.mediator.room;

import java.util.List;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import org.gameontext.mediator.Constants;
import org.gameontext.mediator.MapClient;
import org.gameontext.mediator.MediatorNexus;
import org.gameontext.mediator.PlayerClient;
import org.gameontext.mediator.models.Exit;
import org.gameontext.mediator.models.Exits;
import org.gameontext.mediator.models.RoomInfo;
import org.gameontext.mediator.models.Site;

public class GuidedFirstRoom extends AbstractRoomMediator {

    public static final String FIRST_ROOM_FULL = "The Guided First Room";
    static final String FIRST_ROOM_DESC = "You've entered a vaguely squarish room, with walls of an indeterminate color. There is a single exit to the North.";
    static final String FIRST_ROOM_EXTENDED = "\n\nWelcome to Game On!\n\n"
            + "* Commands start with '/'.\n"
            + "* Use `/help` to list all available commands. The list will change from room to room.\n"
            + "* Use `/exits` to list all available exits.\n"
            + "* Use `/go north` to begin your adventure.\n"
            + "* Rooms might try to fool you, but these three commands will always work.";

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

    final String playerJwt;
    final PlayerClient playerClient;
    final MapClient mapClient;
    final String targetId;

    public GuidedFirstRoom(MediatorNexus.View nexus, String playerJwt, PlayerClient playerClient, MapClient mapClient, Site site, String targetId ) {
        super(nexus, mapClient, site);
        this.playerJwt = playerJwt;
        this.playerClient = playerClient;
        this.mapClient = mapClient;
        this.targetId = targetId;
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
        
        if (contentToLower.startsWith("/")) {
            responseBuilder.add(RoomUtils.TYPE, RoomUtils.EVENT).add(RoomUtils.CONTENT,
                    RoomUtils.buildContentResponse("This room is a basic model. It doesn't understand that command."));
        }

        return userId;
    }

    @Override
    protected void buildLocationResponse(JsonObjectBuilder responseBuilder) {
        super.buildLocationResponse(responseBuilder);
            responseBuilder.add(RoomUtils.DESCRIPTION, FIRST_ROOM_DESC + FIRST_ROOM_EXTENDED);
    }
    
    @Override
    protected void addCommands(JsonObjectBuilder responseBuilder) {
        JsonObjectBuilder content = Json.createObjectBuilder();
        responseBuilder.add(Constants.KEY_COMMANDS, content.build());
    }

    @Override
    protected void addRoomItems(JsonObjectBuilder responseBuilder) {
        JsonArrayBuilder content = Json.createArrayBuilder();
        responseBuilder.add(Constants.KEY_ROOM_INVENTORY, content.build());
    }

    @Override
    public Exits getExits() {
      Exits e = new Exits();
      
      //resolve the story id to a room. 
      List<Site> s = mapClient.getRoomsByRoomName(this.targetId);
      if(s!=null && !s.isEmpty()) {
          //if there are multiple with the same id, accept the first (for now). 
          String siteId = s.get(0).getId();
          Exit n = new Exit();
          n.setId(siteId);
          n.setName("Your Story");
          n.setFullName("A door leading to adventure!");
          e.setN(n);
      }
     
      return e;
    }


}
