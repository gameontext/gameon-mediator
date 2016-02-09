package net.wasdev.gameon.mediator;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import net.wasdev.gameon.mediator.models.ConnectionDetails;
import net.wasdev.gameon.mediator.models.Exit;
import net.wasdev.gameon.mediator.models.Exits;
import net.wasdev.gameon.mediator.models.Site;

public class EmptyRoom implements RoomMediator {


    final MapClient mapClient;

    final String id;
    final String name;
    final String fullName;

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
        this.name = "emptyRoom";
        this.fullName = "Empty Room";

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
        return name;
    }

    @Override
    public String getFullName() {
        return fullName;
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

        JsonObjectBuilder content = Json.createObjectBuilder();
        content.add("N", exits.getN().getDoor());
        content.add("S", exits.getS().getDoor());
        content.add("E", exits.getE().getDoor());
        content.add("W", exits.getW().getDoor());
        if ( exits.getU() != null )
            content.add("U", exits.getU().getDoor());
        if ( exits.getD() != null )
            content.add("D", exits.getD().getDoor());

        return content.build();
    }

    @Override
    public boolean connect() {
        return true;
    }

    @Override
    public boolean subscribe(PlayerConnectionMediator playerSession, long lastmessage) {
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
        // TODO Auto-generated method stub

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
}
