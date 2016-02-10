package net.wasdev.gameon.mediator.models;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_EMPTY)
public class Exits {
    Exit n;
    Exit s;
    Exit e;
    Exit w;
    Exit u;
    Exit d;

    public Exit getN() {
        return n;
    }

    public void setN(Exit n) {
        this.n = n;
    }

    public Exit getS() {
        return s;
    }

    public void setS(Exit s) {
        this.s = s;
    }

    public Exit getE() {
        return e;
    }

    public void setE(Exit e) {
        this.e = e;
    }

    public Exit getW() {
        return w;
    }

    public void setW(Exit w) {
        this.w = w;
    }

    public Exit getU() {
        return u;
    }

    public void setU(Exit u) {
        this.u = u;
    }

    public Exit getD() {
        return d;
    }

    public void setD(Exit d) {
        this.d = d;
    }

    @JsonIgnore
    public Exit getExit(String direction) {
        switch (direction.toLowerCase()) {
            case "n": {
                return getN();
            }
            case "s": {
                return getS();
            }
            case "e": {
                return getE();
            }
            case "w": {
                return getW();
            }
            case "u": {
                return getU();
            }
            case "d": {
                return getD();
            }
            default: {
                // unknown exit.. return null;
                return null;
            }
        }
    }

    @JsonIgnore
    public JsonObject toJson() {

        JsonObjectBuilder content = Json.createObjectBuilder();
        if ( n != null) {
            content.add("N", n.getDoor());
        }

        if ( s != null) {
            content.add("S", s.getDoor());
        }

        if ( e != null ) {
            content.add("E", e.getDoor());
        }

        if ( w != null ) {
            content.add("W", w.getDoor());
        }

        if ( u != null ) {
            content.add("U", u.getDoor());
        }

        if ( d != null ) {
            content.add("D", d.getDoor());
        }

        return content.build();
    }

}
