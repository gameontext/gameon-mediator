package net.wasdev.gameon.mediator.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_EMPTY)
public class Site {

    @JsonProperty("_id")
    String id;

    RoomInfo info;
    Exits exits;
    String owner;

    String type;

    public Site() {}

    public Site(Exit exit) {
        this.id = exit.id;
        this.info = new RoomInfo(exit);
    }

    public RoomInfo getInfo() {
        return info;
    }

    public void setInfo(RoomInfo roomInfo) {
        this.info = roomInfo;
    }

    public Exits getExits() {
        return exits;
    }

    public void setExits(Exits exits) {
        this.exits = exits;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    @JsonProperty("_id")
    public String getId() {
        return id;
    }

    @JsonProperty("_id")
    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

}
