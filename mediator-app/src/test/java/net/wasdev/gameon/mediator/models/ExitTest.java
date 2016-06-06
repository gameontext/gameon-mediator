package net.wasdev.gameon.mediator.models;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ExitTest {

    Site site = new Site();
    RoomInfo roomInfo = new RoomInfo();
    ConnectionDetails details = new ConnectionDetails();

    @Before
    public void before() {
        site.setId("test");
    }

    @Test
    public void testCreateExitNoSiteInfo() {

        Exit exit = new Exit(site, "N");

        Assert.assertEquals("test", exit.getId());
        Assert.assertEquals("Nether space", exit.getName());
        Assert.assertEquals("Nether space", exit.getFullName());
        Assert.assertEquals("Tenuous doorway filled with gray fog", exit.getDoor());
        Assert.assertNull(exit.getConnectionDetails());
    }

    @Test
    public void testCreateExitNoDoors() {
        roomInfo.setDescription("description!");
        roomInfo.setFullName("FullName!");
        roomInfo.setName("Name!");
        site.setInfo(roomInfo);

        details.setTarget("Target!");
        details.setType("Type!");
        roomInfo.setConnectionDetails(details);

        Exit exit = new Exit(site, "N");
        Assert.assertSame(roomInfo, site.getInfo());
        Assert.assertSame(details, exit.getConnectionDetails());
        Assert.assertEquals("test", exit.getId());
        Assert.assertEquals("Name!", exit.getName());
        Assert.assertEquals("FullName!", exit.getFullName());
        Assert.assertEquals("A door", exit.getDoor());
    }

    @Test
    public void testCreateExit() {
        roomInfo.setDescription("description!");
        roomInfo.setName("Name!");
        site.setInfo(roomInfo);

        Doors doors = new Doors();
        doors.setN("North");
        doors.setS("South");
        doors.setE("East");
        doors.setW("West");
        doors.setU("Up");
        doors.setD("Down");
        roomInfo.setDoors(doors);

        Exit exit = new Exit(site, "S");
        Assert.assertEquals("South", exit.getDoor());
        Assert.assertSame(roomInfo, site.getInfo());
        Assert.assertNull(exit.getConnectionDetails());
        Assert.assertEquals("test", exit.getId());
        Assert.assertEquals("Name!", exit.getName());
        Assert.assertEquals("Name!", exit.getFullName());

        exit = new Exit(site, "N");
        Assert.assertEquals("North", exit.getDoor());

        exit = new Exit(site, "W");
        Assert.assertEquals("West", exit.getDoor());

        exit = new Exit(site, "E");
        Assert.assertEquals("East", exit.getDoor());

        exit = new Exit(site, "D");
        Assert.assertEquals("Down", exit.getDoor());

        exit = new Exit(site, "U");
        Assert.assertEquals("Up", exit.getDoor());
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void testCreateExitUnknown() {
        roomInfo.setDescription("description!");
        roomInfo.setName("Name!");
        site.setInfo(roomInfo);

        Doors doors = new Doors();
        roomInfo.setDoors(doors);

        new Exit(site, "Other");        
    }


}
