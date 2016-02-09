package net.wasdev.gameon.mediator.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
    public String toJsonString() {
        ObjectMapper mapper = new ObjectMapper();

        try {
            return mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

}
