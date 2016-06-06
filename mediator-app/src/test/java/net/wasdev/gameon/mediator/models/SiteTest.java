package net.wasdev.gameon.mediator.models;

import org.junit.Assert;
import org.junit.Test;

public class SiteTest {

    @Test
    public void testExitSite() {
        Exit e = new Exit();
        e.setId("id");

        Site s = new Site(e);
        Assert.assertEquals(s.getId(), e.id);
        Assert.assertNotNull("RoomInfo should be created based on Exit information", s.getInfo());
        Assert.assertNull("Site created from an exit should not have exits", s.getExits());
        Assert.assertNull("Site created from an exit should not have a type", s.getType());
        Assert.assertNull("Site created from an exit should not have an owner", s.getOwner());
    }

}
