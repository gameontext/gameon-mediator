package org.gameontext.mediator.room;

import org.gameontext.mediator.models.Exit;
import org.gameontext.mediator.models.Exits;
import org.gameontext.mediator.room.RoomMediator;
import org.gameontext.mediator.room.RoomUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import mockit.Mocked;
import mockit.integration.junit4.JMockit;

@RunWith(JMockit.class)
public class RoomUtilsTest {

    @Test
    public void testGetDirection() {
        assertDirection(null);
        assertDirection("");
        assertDirection("Something else");
        assertDirection("/go to foreign land");
        assertDirection("/go North", "n");
        assertDirection("/go Somewhere", "s");
        assertDirection("/go E", "e");
        assertDirection("/go w", "w");
        assertDirection("/go U", "u");
        assertDirection("/go d", "d");
    }

    private void assertDirection(String input, String direction) {
        Assert.assertEquals("Parsed direction should be " + direction + ". Input=" + input, direction, RoomUtils.getDirection(input));
    }


    private void assertDirection(String input) {
        Assert.assertNull("Invalid direction string should return null. Input=" + input, RoomUtils.getDirection(input));
    }

    @Test
    public void testBuildContentResponseString() {
        Assert.assertEquals("{\"*\":\"response\"}", RoomUtils.buildContentResponse("response").toString());
        Assert.assertEquals("{\"*\":\"\"}", RoomUtils.buildContentResponse("").toString());

        Assert.assertEquals("{\"\":\"\"}", RoomUtils.buildContentResponse("", "").toString());
        Assert.assertEquals("{\"userA\":\"\"}", RoomUtils.buildContentResponse("userA", "").toString());
    }

    @Test
    public void testCreateExits(@Mocked RoomMediator returnRoom) {
        Exits exits;

        exits = RoomUtils.createFallbackExit(returnRoom.getEmergencyReturnExit(), "n");
        assertExits(exits, "Create fallback exits going north", false, true, false, false);

        exits = RoomUtils.createFallbackExit(returnRoom.getEmergencyReturnExit(), "s");
        assertExits(exits, "Create fallback exits going south", true, false, false, false);

        exits = RoomUtils.createFallbackExit(returnRoom.getEmergencyReturnExit(), "e");
        assertExits(exits, "Create fallback exits going east", false, false, false, true);

        exits = RoomUtils.createFallbackExit(returnRoom.getEmergencyReturnExit(), "w");
        assertExits(exits, "Create fallback exits going west", false, false, true, false);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testCreateExitsOther(@Mocked RoomMediator returnRoom) {
        RoomUtils.createFallbackExit(returnRoom.getEmergencyReturnExit(), "");
    }

    private void assertExits(Exits exits, String description, boolean n, boolean s, boolean e, boolean w) {
        assertExitExists(description + ": n exit ", exits.getN(), n);
        assertExitExists(description + ": s exit ", exits.getS(), s);
        assertExitExists(description + ": e exit ", exits.getE(), e);
        assertExitExists(description + ": w exit ", exits.getW(), w);
        assertExitExists(description + ": u exit ", exits.getU(), false);
        assertExitExists(description + ": d exit ", exits.getD(), false);
    }

    private void assertExitExists(String description, Exit exit, boolean exists) {
        if ( exists ) {
            Assert.assertNotNull(description + "should exist", exit);
        } else {
            Assert.assertNull(description + "should not exist", exit);
        }
    }

}
