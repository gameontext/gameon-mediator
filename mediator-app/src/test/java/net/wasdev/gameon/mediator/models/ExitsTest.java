package net.wasdev.gameon.mediator.models;

import javax.json.JsonObject;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ExitsTest {

    Exit n = new Exit();
    Exit s = new Exit();
    Exit e = new Exit();
    Exit w = new Exit();
    Exit u = new Exit();
    Exit d = new Exit();

    Exits exits = new Exits();

    @Before
    public void before() {
        n.setDoor("North");
        s.setDoor("South");
        e.setDoor("East");
        w.setDoor("West");
        u.setDoor("Up");
        d.setDoor("Down");

        exits.setN(n);
        exits.setS(s);
        exits.setE(e);
        exits.setW(w);
        exits.setU(u);
        exits.setD(d);
    }

    @Test
    public void testGetExit() {
        System.out.println(exits);

        Assert.assertSame(n, exits.getExit("N"));
        Assert.assertSame(s, exits.getExit("S"));
        Assert.assertSame(e, exits.getExit("E"));
        Assert.assertSame(w, exits.getExit("W"));
        Assert.assertSame(u, exits.getExit("U"));
        Assert.assertSame(d, exits.getExit("D"));
        Assert.assertNull(exits.getExit("Other"));
    }

    @Test
    public void testToJson() {
        JsonObject obj = exits.toSimpleJsonList();

        Assert.assertEquals("North", obj.getJsonString("N").getString());
        Assert.assertEquals("South", obj.getJsonString("S").getString());
        Assert.assertEquals("East", obj.getJsonString("E").getString());
        Assert.assertEquals("West", obj.getJsonString("W").getString());
        Assert.assertEquals("Up", obj.getJsonString("U").getString());
        Assert.assertEquals("Down", obj.getJsonString("D").getString());
    }

    @Test
    public void testEmptyList() {
        Exits exits = new Exits();

        JsonObject obj = exits.toSimpleJsonList();

        Assert.assertNull("North", obj.getJsonString("N"));
        Assert.assertNull("South", obj.getJsonString("S"));
        Assert.assertNull("East", obj.getJsonString("E"));
        Assert.assertNull("West", obj.getJsonString("W"));
        Assert.assertNull("Up", obj.getJsonString("U"));
        Assert.assertNull("Down", obj.getJsonString("D"));
    }
}
