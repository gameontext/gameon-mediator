package net.wasdev.gameon.mediator.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Exit {
    @JsonProperty("_id")
    String id;
    String name;
    String fullName;
    String token;
    String door = null;
    ConnectionDetails connectionDetails = null;

    public Exit() {}

    public Exit(Site targetSite, String direction) {
        this.id = targetSite.getId();

        if ( targetSite.getInfo() != null ) {
            this.name = targetSite.getInfo().getName();
            this.fullName = targetSite.getInfo().getFullName();
            this.connectionDetails = targetSite.getInfo().getConnectionDetails();
            this.token = targetSite.getInfo().getToken();

            // Note the direction flip. Assume we're in a room,
            // and there is a room to the North:
            // To build the North _EXIT_ (of the South room), we're
            // getting the South _DOOR_ (of the North room).
            switch(direction) {
                case "N" :
                    this.door = targetSite.getInfo().getDoors().getS();
                    break;
                case "S" :
                    this.door = targetSite.getInfo().getDoors().getN();
                    break;
                case "E" :
                    this.door = targetSite.getInfo().getDoors().getW();
                    break;
                case "W" :
                    this.door = targetSite.getInfo().getDoors().getE();
                    break;
            }

            // Really generic. They gave us nothing interesting.
            if ( this.door == null )
                this.door = "A door";

            // This won't be the prettiest. ew.
            if ( this.fullName == null )
                this.fullName = this.name;

        } else {
            // Empty/placeholder room. Still navigable if very unclear.
            this.name = "Nether space";
            this.fullName = "Nether space";
            this.door = "Tenuous doorway filled with gray fog";
        }
    }

    @JsonProperty("_id")
    public String getId() {
        return id;
    }

    @JsonProperty("_id")
    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getDoor() {
        return door;
    }

    public void setDoor(String door) {
        this.door = door;
    }

    public ConnectionDetails getConnectionDetails() {
        return connectionDetails;
    }

    public void setConnectionDetails(ConnectionDetails connectionDetails) {
        this.connectionDetails = connectionDetails;
    }
    
    public String getToken() {
        return token;
    }
}
