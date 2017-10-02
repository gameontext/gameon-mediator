/*******************************************************************************
 * Copyright (c) 2016 IBM Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package org.gameontext.mediator;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import javax.json.Json;
import javax.json.JsonObject;

import org.gameontext.mediator.MediatorNexus.ClientMediatorPod;
import org.gameontext.mediator.MediatorNexus.UserView;
import org.gameontext.mediator.events.EventSubscription;
import org.gameontext.mediator.events.MediatorEvents;
import org.gameontext.mediator.events.MediatorEvents.PlayerEventHandler;
import org.gameontext.mediator.room.FirstRoom;
import org.gameontext.mediator.room.RoomMediator;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import mockit.Deencapsulation;
import mockit.Expectations;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import mockit.Verifications;
import mockit.integration.junit4.JMockit;

@RunWith(JMockit.class)
public class MediatorNexusTest {

    static final String signedJwt = "testJwt";
    static final String userId = "dummy.DevUser";
    static final String userName = "DevUser";

    static final String roomId = "roomId";
    static final String roomName = "roomName";
    static final String roomFullName = "roomFullName";
    static final JsonObject roomExits = Json.createObjectBuilder().build();

    @Mocked MediatorBuilder builder;
    
    @Mocked MediatorEvents events;
    @Mocked EventSubscription subscription;
    
    @Mocked PlayerClient playerClient;

    @Rule
    public TestName testName = new TestName();

    @Before
    public void before() {
        System.out.println("-- " + testName.getMethodName() + " --------------------------------------");

        new Expectations() {{ events.subscribeToPlayerEvents((String)any, (PlayerEventHandler)any); result = subscription; }};
        
        new MockUp<Log>()
        {
            @Mock
            public void log(Level level, Object source, String msg, Object[] params) {
                System.out.println("Log: " + MessageFormat.format(msg, params));
            }

            @Mock
            public void log(Level level, Object source, String msg, Throwable thrown) {
                System.out.println("Log: " + msg + ": " + thrown.getMessage());
                thrown.printStackTrace(System.out);
            }
        };
    }

    @Test
    public void testStructureCleanup(@Mocked ClientMediator client1,
            @Mocked ClientMediator client1a,
            @Mocked ClientMediator client1b,
            @Mocked ClientMediator client2,
            @Mocked RoomMediator room1) {

        new Expectations() {{
            client1.getUserId(); result = "client1";
            client1a.getUserId(); result = "client1";
            client1b.getUserId(); result = "client1";
            client2.getUserId(); result = "client2";

            room1.getId(); result = roomId;
            room1.getName(); result = roomName;
            room1.getFullName(); result = roomFullName;
            room1.listExits(); result = roomExits;

            builder.findMediatorForRoom((ClientMediatorPod) any, roomId); result = room1;
        }};

        MediatorNexus nexus = new MediatorNexus();
        nexus.events = events;
        nexus.setBuilder(builder);

        Map<String, ClientMediatorPod> map = nexus.clientMap;

        System.out.println("Join client1: " + client1);
        nexus.join(client1, roomId, "previous");
        assertMapSize("Should have 1 element", 1, map);
        Assert.assertTrue("clientMediators should contain client1", map.get("client1").clientMediators.contains(client1));

        System.out.println("Join client1: " + client1);
        nexus.join(client1, roomId, "previous"); // double-add -- call to setMediator, but no structure change
        assertMapSize("Should have 1 element", 1, map);
        Assert.assertTrue("clientMediators should contain client1", map.get("client1").clientMediators.contains(client1));

        System.out.println("Join client1a: " + client1a);
        nexus.join(client1a, roomId, "previous");
        assertMapSize("Should have 1 element", 1, map);
        assertSetSize("Sessions should have 2 elements", 2, map.get("client1").clientMediators);
        Assert.assertTrue("clientMediators should contain client1", map.get("client1").clientMediators.contains(client1));
        Assert.assertTrue("clientMediators should contain client1a", map.get("client1").clientMediators.contains(client1a));

        System.out.println("Join client1b: " + client1b);
        nexus.join(client1b, roomId, "previous");
        assertMapSize("Should have 1 element", 1, map);
        assertSetSize("client 1 clientMediators should have 3 elements", 3, map.get("client1").clientMediators);
        Assert.assertTrue("clientMediators should contain client1", map.get("client1").clientMediators.contains(client1));
        Assert.assertTrue("clientMediators should contain client1a", map.get("client1").clientMediators.contains(client1a));
        Assert.assertTrue("clientMediators should contain client1b", map.get("client1").clientMediators.contains(client1b));

        System.out.println("Join client2: " + client2);
        nexus.join(client2, roomId, "previous");
        assertMapSize("Should have 2 elements", 2, map);
        assertSetSize("client 1 clientMediators should have 3 elements", 3, map.get("client1").clientMediators);
        assertSetSize("client 2 clientMediators should have 1 element", 1, map.get("client2").clientMediators);
        Assert.assertTrue("clientMediators should contain client2", map.get("client2").clientMediators.contains(client2));

        // Tear down!
        System.out.println("Part client2: " + client2 + ", " + map);
        nexus.part(client2);
        assertMapSize("Should have 1 elements", 1, map);
        assertSetSize("client 1 clientMediators should have 3 elements", 3, map.get("client1").clientMediators);
        Assert.assertNull("client2 pod should have been removed", map.get("client2"));

        System.out.println("Part client2: " + client2);
        nexus.part(client2); // double-remove -- do nothing
        assertMapSize("Should have 1 elements", 1, map);
        Assert.assertNull("client2 pod should have been removed", map.get("client2"));

        System.out.println("Part client1: " + client1);
        nexus.part(client1);
        assertMapSize("Should have 1 element", 1, map);
        assertSetSize("client 1 clientMediators should have 2 elements", 2, map.get("client1").clientMediators);
        assertSetDoesNotContain("clientMediators should NOT contain client1", map.get("client1").clientMediators, client1);
        assertSetContains("clientMediators should contain client1a", map.get("client1").clientMediators, client1a);
        assertSetContains("clientMediators should contain client1b", map.get("client1").clientMediators, client1b);

        System.out.println("Part client1b: " + client1b);
        nexus.part(client1b);
        assertMapSize("Should have 1 element", 1, map);
        assertSetSize("client 1 clientMediators should have 1 element", 1, map.get("client1").clientMediators);
        assertSetDoesNotContain("clientMediators should NOT contain client1", map.get("client1").clientMediators, client1);
        assertSetContains("clientMediators should contain client1a", map.get("client1").clientMediators, client1a);
        assertSetDoesNotContain("clientMediators should NOT contain client1b", map.get("client1").clientMediators, client1b);

        System.out.println("Part client1b: " + client1b);
        nexus.part(client1b); // double remove -- do nothing
        assertMapSize("Should have 1 element", 1, map);
        assertSetSize("client 1 clientMediators should have 1 element", 1, map.get("client1").clientMediators);
        Assert.assertFalse("clientMediators should NOT contain client1", map.get("client1").clientMediators.contains(client1));
        assertSetContains("clientMediators should contain client1a", map.get("client1").clientMediators, client1a);
        Assert.assertFalse("clientMediators should NOT contain client1b", map.get("client1").clientMediators.contains(client1b));

        // remove client1a: removal of client1a
        System.out.println("Part client1a: " + client1a);
        nexus.part(client1a);
        Assert.assertTrue("structure should be empty", map.isEmpty());

        new Verifications() {{
            // mediators created only once per client
            builder.findMediatorForRoom((ClientMediatorPod) any, roomId); times = 2;

            client1.setRoomMediator(room1, false); times = 2; // double add
            client1a.setRoomMediator(room1, false); times = 1;
            client1b.setRoomMediator(room1, false); times = 1;
            client2.setRoomMediator(room1, false); times = 1;

            List<UserView> joins = new ArrayList<>();
            room1.join(withCapture(joins)); times = 2; // only TWO joins. One for each client id
            Assert.assertEquals("Joins: " + joins, "client1", joins.get(0).getUserId());
            Assert.assertEquals("Joins: " + joins, "client2", joins.get(1).getUserId());

            List<UserView> parts = new ArrayList<>();
            room1.part(withCapture(parts)); times = 2; // only TWO parts. One for each client id
            Assert.assertEquals("Parts: " + parts, "client2", parts.get(0).getUserId());
            Assert.assertEquals("Parts: " + parts, "client1", parts.get(1).getUserId());
        }};
    }

    @Test
    public void testJoinEmptyRoomString(@Mocked ClientMediator client1,
                                        @Mocked RoomMediator room1) {

        new Expectations() {{
            client1.getUserId(); result = "client1";
            room1.getId(); result = Constants.FIRST_ROOM;
            room1.getName(); result = Constants.FIRST_ROOM;
            room1.getFullName(); result = FirstRoom.FIRST_ROOM_FULL;
            room1.listExits(); result = roomExits;
            builder.findMediatorForRoom((ClientMediatorPod) any, Constants.FIRST_ROOM); result = room1;
        }};

        MediatorNexus nexus = new MediatorNexus();
        nexus.events = events;
        nexus.setBuilder(builder);

        nexus.join(client1, "", "previous");
        nexus.join(client1, "", "previous");

        new Verifications() {{
            // mediators created only once per client
            builder.findMediatorForRoom((ClientMediatorPod) any, Constants.FIRST_ROOM); times = 1;

            UserView join;
            room1.join(join = withCapture()); times = 1;
            Assert.assertEquals("join user = " + join , "client1", join.getUserId());
            client1.setRoomMediator(room1, false); times = 2; // both calls to join

            room1.hello((UserView) any); times = 0;  // should not see hello!
            room1.goodbye((UserView) any); times = 0;  // should not see goodbye!
            room1.part((UserView) any); times = 0;  // should not see part!
        }};
    }


    @Test
    public void testJoinNullBookmark(@Mocked ClientMediator client1,
                                   @Mocked RoomMediator room1) {

        new Expectations() {{
            client1.getUserId(); result = "client1";
            room1.getId(); result = Constants.FIRST_ROOM;
            room1.getName(); result = Constants.FIRST_ROOM;
            room1.getFullName(); result = FirstRoom.FIRST_ROOM_FULL;
            room1.listExits(); result = roomExits;
            builder.findMediatorForRoom((ClientMediatorPod) any, Constants.FIRST_ROOM); result = room1;
        }};

        MediatorNexus nexus = new MediatorNexus();
        nexus.events = events;
        nexus.setBuilder(builder);

        // This is effectively a roomHello via join.
        nexus.join(client1, null, null);

        new Verifications() {{
            // mediators created only once per client
            builder.findMediatorForRoom((ClientMediatorPod) any, Constants.FIRST_ROOM); times = 1;

            UserView hello;
            room1.hello(hello = withCapture()); times = 1;
            Assert.assertEquals("join user = " + hello , "client1", hello.getUserId());

            client1.setRoomMediator(room1, false); times = 1;

            room1.join((UserView) any); times = 0;  // should not see join!
            room1.goodbye((UserView) any); times = 0;  // should not see goodbye!
            room1.part((UserView) any); times = 0;  // should not see part!
        }};
    }

    @Test
    public void testJoinEmptyBookmark(@Mocked ClientMediator client1,
                                   @Mocked RoomMediator room1) {

        new Expectations() {{
            client1.getUserId(); result = "client1";
            room1.getId(); result = Constants.FIRST_ROOM;
            room1.getName(); result = Constants.FIRST_ROOM;
            room1.getFullName(); result = FirstRoom.FIRST_ROOM_FULL;
            room1.listExits(); result = roomExits;
            builder.findMediatorForRoom((ClientMediatorPod) any, Constants.FIRST_ROOM); result = room1;
        }};

        MediatorNexus nexus = new MediatorNexus();
        nexus.events = events;
        nexus.setBuilder(builder);

        // This is effectively a roomHello via join.
        nexus.join(client1, null, "");

        new Verifications() {{
            // mediators created only once per client
            builder.findMediatorForRoom((ClientMediatorPod) any, Constants.FIRST_ROOM); times = 1;

            UserView hello;
            room1.hello(hello = withCapture()); times = 1;
            Assert.assertEquals("join user = " + hello , "client1", hello.getUserId());

            client1.setRoomMediator(room1, false); times = 1;

            room1.join((UserView) any); times = 0;  // should not see join!
            room1.goodbye((UserView) any); times = 0;  // should not see goodbye!
            room1.part((UserView) any); times = 0;  // should not see part!
        }};
    }

    @Test
    public void testJoinZeroBookmark(@Mocked ClientMediator client1,
                                   @Mocked RoomMediator room1) {

        new Expectations() {{
            client1.getUserId(); result = "client1";
            room1.getId(); result = Constants.FIRST_ROOM;
            room1.getName(); result = Constants.FIRST_ROOM;
            room1.getFullName(); result = FirstRoom.FIRST_ROOM_FULL;
            room1.listExits(); result = roomExits;
            builder.findMediatorForRoom((ClientMediatorPod) any, Constants.FIRST_ROOM); result = room1;
        }};

        MediatorNexus nexus = new MediatorNexus();
        nexus.events = events;
        nexus.setBuilder(builder);

        // This is effectively a roomHello via join.
        nexus.join(client1, null, "0");

        new Verifications() {{
            // mediators created only once per client
            builder.findMediatorForRoom((ClientMediatorPod) any, Constants.FIRST_ROOM); times = 1;

            UserView hello;
            room1.hello(hello = withCapture()); times = 1;
            Assert.assertEquals("join user = " + hello , "client1", hello.getUserId());

            client1.setRoomMediator(room1, false); times = 1;

            room1.join((UserView) any); times = 0;  // should not see join!
            room1.goodbye((UserView) any); times = 0;  // should not see goodbye!
            room1.part((UserView) any); times = 0;  // should not see part!
        }};
    }

    @Test
    public void testJoinConflict(@Mocked ClientMediator client1,
                                 @Mocked RoomMediator room1) {

        new Expectations() {{
            client1.getUserId(); result = "client1";
            room1.getId(); result = roomId;
            room1.getName(); result = roomName;
            room1.getFullName(); result = roomFullName;
            room1.listExits(); result = roomExits;

            builder.findMediatorForRoom((ClientMediatorPod) any, roomId); result = room1;
        }};

        MediatorNexus nexus = new MediatorNexus();
        nexus.events = events;
        nexus.setBuilder(builder);

        // initial join
        nexus.join(client1, roomId, "previous");

        // subsequent join: player trying to resume to different room
        nexus.join(client1, "otherRoom", "previous");

        new Verifications() {{
            UserView join;
            room1.join(join = withCapture()); times = 1;
            Assert.assertEquals("join user = " + join , "client1", join.getUserId());
            client1.setRoomMediator(room1, false); times = 1; // initial call
            client1.setRoomMediator(room1, true); times = 1; // splinch repair

            room1.hello((UserView) any); times = 0;  // should not see hello!
            room1.goodbye((UserView) any); times = 0;  // should not see goodbye!
            room1.part((UserView) any); times = 0;  // should not see part!
        }};
    }

    @Test
    public void testTransitionNull(@Mocked ClientMediator client1,
                                  @Mocked RoomMediator room1) {

        new Expectations() {{
            client1.getUserId(); result = "client1";
            room1.getId(); result = Constants.FIRST_ROOM;
            room1.getName(); result = Constants.FIRST_ROOM;
            room1.getFullName(); result = FirstRoom.FIRST_ROOM_FULL;
            room1.listExits(); result = roomExits;
            builder.findMediatorForRoom((ClientMediatorPod) any, Constants.FIRST_ROOM); result = room1;
        }};

        MediatorNexus nexus = new MediatorNexus();
        nexus.events = events;
        nexus.setBuilder(builder);

        // This is effectively a roomHello via join.
        nexus.transition(client1, null);

        new Verifications() {{
            // mediators created only once per client
            builder.findMediatorForRoom((ClientMediatorPod) any, Constants.FIRST_ROOM); times = 1;

            UserView hello;
            room1.hello(hello = withCapture()); times = 1;
            Assert.assertEquals("join user = " + hello , "client1", hello.getUserId());

            client1.setRoomMediator(room1, false); times = 1;

            room1.join((UserView) any); times = 0;  // should not see join!
            room1.goodbye((UserView) any); times = 0;  // should not see goodbye!
            room1.part((UserView) any); times = 0;  // should not see part!
        }};
    }

    @Test
    public void testTransitionEmpty(@Mocked ClientMediator client1,
                                  @Mocked RoomMediator room1) {

        new Expectations() {{
            client1.getUserId(); result = "client1";
            room1.getId(); result = Constants.FIRST_ROOM;
            room1.getName(); result = Constants.FIRST_ROOM;
            room1.getFullName(); result = FirstRoom.FIRST_ROOM_FULL;
            room1.listExits(); result = roomExits;
            builder.findMediatorForRoom((ClientMediatorPod) any, Constants.FIRST_ROOM); result = room1;
        }};

        MediatorNexus nexus = new MediatorNexus();
        nexus.events = events;
        nexus.setBuilder(builder);

        // This is effectively a hello via join.
        nexus.transition(client1, "");

        new Verifications() {{
            // mediators created only once per client
            builder.findMediatorForRoom((ClientMediatorPod) any, Constants.FIRST_ROOM); times = 1;

            UserView hello;
            room1.hello(hello = withCapture()); times = 1;
            Assert.assertEquals("hello user = " + hello , "client1", hello.getUserId());

            client1.setRoomMediator(room1, false); times = 1;

            room1.join((UserView) any); times = 0;  // should not see join!
            room1.goodbye((UserView) any); times = 0;  // should not see goodbye!
            room1.part((UserView) any); times = 0;  // should not see part!
        }};
    }


    @Test
    public void testTransitionSameRoom(@Mocked ClientMediator client1,
                                       @Mocked RoomMediator room1) {

        new Expectations() {{
            client1.getUserId(); result = "client1";
            room1.getId(); result = roomId;
            room1.getName(); result = roomName;
            room1.getFullName(); result = roomFullName;
            room1.listExits(); result = roomExits;
            builder.findMediatorForRoom((ClientMediatorPod) any, roomId); result = room1;
        }};

        MediatorNexus nexus = new MediatorNexus();
        nexus.events = events;
        nexus.setBuilder(builder);

        // put client1 in a room
        nexus.join(client1, roomId, "previous");

        // transition to the same room id, should short-circuit
        nexus.transition(client1, roomId);

        new Verifications() {{
            // mediators created only once per client
            builder.findMediatorForRoom((ClientMediatorPod) any, roomId); times = 1;

            UserView join;
            room1.join(join = withCapture()); times = 1;
            Assert.assertEquals("join user = " + join , "client1", join.getUserId());

            client1.setRoomMediator(room1, false); times = 1;

            room1.hello((UserView) any); times = 0;  // should not see join!
            room1.goodbye((UserView) any); times = 0;  // should not see goodbye!
            room1.part((UserView) any); times = 0;  // should not see part!
        }};
    }

    @Test
    public void testTransitionSameRoomNull(@Mocked ClientMediator client1,
                                       @Mocked RoomMediator room1) {

        new Expectations() {{
            client1.getUserId(); result = "client1";
            room1.getId(); result = Constants.FIRST_ROOM;
            room1.getName(); result = Constants.FIRST_ROOM;
            room1.getFullName(); result = FirstRoom.FIRST_ROOM_FULL;
            room1.listExits(); result = roomExits;
            builder.findMediatorForRoom((ClientMediatorPod) any, Constants.FIRST_ROOM); result = room1;
        }};

        MediatorNexus nexus = new MediatorNexus();
        nexus.events = events;
        nexus.setBuilder(builder);

        // put client1 in a room
        nexus.join(client1, null, "previous");

        // transition to the same (null) room id, should short-circuit
        nexus.transition(client1, null);

        new Verifications() {{
            builder.findMediatorForRoom((ClientMediatorPod) any, Constants.FIRST_ROOM); times = 1;

            UserView join;
            room1.join(join = withCapture()); times = 1;
            Assert.assertEquals("join user = " + join , "client1", join.getUserId());

            client1.setRoomMediator(room1, false); times = 1;

            room1.hello((UserView) any); times = 0;  // should not see hello!
            room1.goodbye((UserView) any); times = 0;  // should not see goodbye!
            room1.part((UserView) any); times = 0;  // should not see part!
        }};
    }

    @Test
    public void testTransitionToRoom(@Mocked ClientMediator client1,
                                     @Mocked RoomMediator room1,
                                     @Mocked RoomMediator room2) {

        String roomId2 = "room2";

        new Expectations() {{
            client1.getUserId(); result = "client1";
            client1.getRoomMediator(); returns(room1, room2);

            room1.getId(); result = roomId;
            room1.getName(); result = roomName;
            room1.getFullName(); result = roomFullName;
            room1.listExits(); result = roomExits;

            room2.getId(); result = roomId2;
            room2.getName(); result = roomName;
            room2.getFullName(); result = roomFullName;
            room2.listExits(); result = roomExits;

            builder.findMediatorForRoom((ClientMediatorPod) any, roomId); result = room1;
            builder.findMediatorForRoom((ClientMediatorPod) any, roomId2); result = room2;
        }};

        MediatorNexus nexus = new MediatorNexus();
        nexus.events = events;
        nexus.setBuilder(builder);
        Deencapsulation.setField(nexus,playerClient);

        // put client1 in room1
        nexus.join(client1, roomId, "previous");

        nexus.transition(client1, roomId2);

        new Verifications() {{
            UserView join;
            room1.join(join = withCapture()); times = 1;
            Assert.assertEquals("join user = " + join , "client1", join.getUserId());

            builder.findMediatorForRoom((ClientMediatorPod) any, roomId); times = 1;
            client1.setRoomMediator(room1, false); times = 1;

            // transition
            UserView goodbye;
            room1.goodbye(goodbye = withCapture()); times = 1;
            Assert.assertEquals("goodbye user = " + goodbye , "client1", goodbye.getUserId());

            builder.findMediatorForRoom((ClientMediatorPod) any, roomId2); times = 1;
            client1.setRoomMediator(room2, false); times = 1;
            
            playerClient.updatePlayerLocation("client1",(String)any,roomId,roomId2); times = 1;

            UserView hello;
            room2.hello(hello = withCapture()); times = 1;
            Assert.assertEquals("hello user = " + hello , "client1", hello.getUserId());

            room1.hello((UserView) any); times = 0;  // should not see hello!
            room1.part((UserView) any); times = 0;  // should not see part!

            room2.join((UserView) any); times = 0;  // should not see join!
            room2.goodbye((UserView) any); times = 0;  // should not see goodbye!
            room2.part((UserView) any); times = 0;  // should not see part!
        }};
    }


    @Test
    public void testTransitionToRoomConflict(@Mocked ClientMediator client1,
                                             @Mocked RoomMediator room1) {

        new Expectations() {{
            client1.getUserId(); result = "client1";
            room1.getId(); result = roomId;
            room1.getName(); result = roomName;
            room1.getFullName(); result = roomFullName;
            room1.listExits(); result = roomExits;

            builder.findMediatorForRoom((ClientMediatorPod) any, roomId); result = room1;
        }};

        MediatorNexus nexus = new MediatorNexus();
        nexus.events = events;
        nexus.setBuilder(builder);

        // initial join
        nexus.join(client1, roomId, "previous");

        // we have to cheat here, because there is little to no chance
        // of getting this threading window right.
        ClientMediatorPod pod = nexus.clientMap.get("client1");
        Assert.assertNotNull(pod);

        // CHEATING: call nested inner directly
        try {
            Deencapsulation.invoke(pod, "transition", client1, "otherRoom", Constants.FIRST_ROOM, false);
            Assert.fail("Expected concurrent modification exception");
        } catch(ConcurrentModificationException ex) {
            //YAY!!
        }

        new Verifications() {{
            UserView join;
            room1.join(join = withCapture()); times = 1;
            Assert.assertEquals("join user = " + join , "client1", join.getUserId());
            client1.setRoomMediator(room1, false); times = 1; // initial call
            client1.setRoomMediator(room1, true); times = 1; // splinch repair

            room1.hello((UserView) any); times = 0;  // should not see hello!
            room1.goodbye((UserView) any); times = 0;  // should not see goodbye!
            room1.part((UserView) any); times = 0;  // should not see part!
        }};
    }

    @Test
    public void testTransitionViaExit(@Mocked ClientMediator client1,
            @Mocked ClientMediator client1a,
            @Mocked RoomMediator room1,
            @Mocked RoomMediator room2) {

        new Expectations() {{
            client1.getUserId(); result = "client1";
            client1.getRoomMediator(); returns(room1, room2);
            client1a.getUserId(); result = "client1";

            room1.getId(); result = Constants.FIRST_ROOM;
            room1.getName(); result = Constants.FIRST_ROOM;
            room1.getFullName(); result = FirstRoom.FIRST_ROOM_FULL;
            room1.listExits(); result = roomExits;

            room2.getId(); result = roomId;
            room2.getName(); result = roomName;
            room2.getFullName(); result = roomFullName;
            room2.listExits(); result = roomExits;

            builder.findMediatorForRoom((ClientMediatorPod) any, Constants.FIRST_ROOM); result = room1;
            builder.findMediatorForExit((ClientMediatorPod) any, room1, "N"); result = room2;
        }};

        MediatorNexus nexus = new MediatorNexus();
        nexus.events = events;
        nexus.setBuilder(builder);
        Deencapsulation.setField(nexus,playerClient);

        // put client1 AND client1a in room1
        nexus.join(client1, null, "previous");
        nexus.join(client1a, null, "previous");

        // transition to the same room id, should short-circuit
        nexus.transitionViaExit(client1, "N");

        new Verifications() {{
            // Join (only with client1: client1a is just added to pod)
            builder.findMediatorForRoom((ClientMediatorPod) any, Constants.FIRST_ROOM); times = 1;

            UserView join;
            room1.join(join = withCapture()); times = 1;
            Assert.assertEquals("join user = " + join , "client1", join.getUserId());

            client1.setRoomMediator(room1, false); times = 1;

            // client1a is added to pod
            client1a.setRoomMediator(room1, false); times = 1;

            // transition, updates both clients in the pod
            UserView goodbye;
            room1.goodbye(goodbye = withCapture()); times = 1;
            Assert.assertEquals("goodbye user = " + goodbye , "client1", goodbye.getUserId());

            builder.findMediatorForExit((ClientMediatorPod) any, room1, "N"); times = 1;
            
            playerClient.updatePlayerLocation("client1",(String)any,Constants.FIRST_ROOM,roomId); times = 1;

            UserView hello;
            room2.hello(hello = withCapture()); times = 1;
            Assert.assertEquals("hello user = " + hello , "client1", hello.getUserId());

            // Both mediators should have new room mediator set
            client1.setRoomMediator(room2, false); times = 1;
            client1a.setRoomMediator(room2, false); times = 1;

            room1.hello((UserView) any); times = 0;  // should not see hello!
            room1.part((UserView) any); times = 0;  // should not see part!

            room2.join((UserView) any); times = 0;  // should not see join!
            room2.goodbye((UserView) any); times = 0;  // should not see goodbye!
            room2.part((UserView) any); times = 0;  // should not see part!
        }};
    }


    @Test
    public void testTransitionViaNullExit(@Mocked ClientMediator client1,
                                          @Mocked RoomMediator room1) {

        new Expectations() {{
            client1.getUserId(); result = "client1";
            room1.getId(); result = Constants.FIRST_ROOM;
            room1.getName(); result = Constants.FIRST_ROOM;
            room1.getFullName(); result = FirstRoom.FIRST_ROOM_FULL;
            room1.listExits(); result = roomExits;
            builder.findMediatorForRoom((ClientMediatorPod) any, Constants.FIRST_ROOM); result = room1;
        }};

        MediatorNexus nexus = new MediatorNexus();
        nexus.events = events;
        nexus.setBuilder(builder);

        // This is effectively a join.
        nexus.transition(client1, null);

        new Verifications() {{
            // mediators created only once per client
            builder.findMediatorForRoom((ClientMediatorPod) any, Constants.FIRST_ROOM); times = 1;

            UserView hello;
            room1.hello(hello = withCapture()); times = 1;
            Assert.assertEquals("join user = " + hello , "client1", hello.getUserId());

            client1.setRoomMediator(room1, false); times = 1;

            room1.join((UserView) any); times = 0;  // should not see join!
            room1.goodbye((UserView) any); times = 0;  // should not see goodbye!
            room1.part((UserView) any); times = 0;  // should not see part!
        }};
    }

    @Test
    public void testTransitionViaExitNullRoom(@Mocked ClientMediator client1,
                                              @Mocked RoomMediator room1) {

        new Expectations() {{
            client1.getUserId(); result = "client1";
            client1.getRoomMediator(); result = null;
            room1.getId(); result = roomId;
            room1.getName(); result = roomName;
            room1.getFullName(); result = roomFullName;
            room1.listExits(); result = roomExits;
            builder.findMediatorForRoom((ClientMediatorPod) any, Constants.FIRST_ROOM); result = room1;
        }};

        MediatorNexus nexus = new MediatorNexus();
        nexus.events = events;
        nexus.setBuilder(builder);
        Deencapsulation.setField(nexus,playerClient);

        // This is effectively a join.
        nexus.transitionViaExit(client1, "N");

        new Verifications() {{
            // mediators created only once per client
            builder.findMediatorForRoom((ClientMediatorPod) any, Constants.FIRST_ROOM); times = 1;
            playerClient.updatePlayerLocation("client1",(String)any,Constants.FIRST_ROOM,roomId); 

            UserView hello;
            room1.hello(hello = withCapture()); times = 1;
            Assert.assertEquals("join user = " + hello , "client1", hello.getUserId());

            client1.setRoomMediator(room1, false); times = 1;

            room1.join((UserView) any); times = 0;  // should not see join!
            room1.goodbye((UserView) any); times = 0;  // should not see goodbye!
            room1.part((UserView) any); times = 0;  // should not see part!
        }};
    }


    @Test
    public void testTransitionViaExitConflict(@Mocked ClientMediator client1,
                                             @Mocked RoomMediator room1) {

        new Expectations() {{
            client1.getUserId(); result = "client1";
            room1.getId(); result = roomId;
            room1.getName(); result = roomName;
            room1.getFullName(); result = roomFullName;
            room1.listExits(); result = roomExits;

            builder.findMediatorForRoom((ClientMediatorPod) any, roomId); result = room1;
        }};

        MediatorNexus nexus = new MediatorNexus();
        nexus.events = events;
        nexus.setBuilder(builder);

        // initial join
        nexus.join(client1, roomId, "previous");

        // we have to cheat here, because there is little to no chance
        // of getting this threading window right.
        ClientMediatorPod pod = nexus.clientMap.get("client1");
        Assert.assertNotNull(pod);

        // CHEATING: call nested inner directly
        try {
            Deencapsulation.invoke(pod, "transitionViaExit", client1, "otherRoom", "N");
            Assert.fail("Expected concurrent modification exception");
        } catch(ConcurrentModificationException ex) {
            //YAY!!
        }

        new Verifications() {{
            UserView join;
            room1.join(join = withCapture()); times = 1;
            Assert.assertEquals("join user = " + join , "client1", join.getUserId());
            client1.setRoomMediator(room1, false); times = 1; // initial call
            client1.setRoomMediator(room1, true); times = 1; // splinch repair

            room1.hello((UserView) any); times = 0;  // should not see hello!
            room1.goodbye((UserView) any); times = 0;  // should not see goodbye!
            room1.part((UserView) any); times = 0;  // should not see part!
        }};
    }
    
    @Test
    public void testLocationCallback(@Mocked ClientMediator client1,
            @Mocked RoomMediator room1,
            @Mocked RoomMediator room2){
        String userid = "client1";
        
        new Expectations() {{
            client1.getUserId(); result = userid;
            room1.getId(); result = roomId;
            room1.getName(); result = roomName;
            room1.getFullName(); result = roomFullName;
            room1.listExits(); result = roomExits;
            events.subscribeToPlayerEvents((String)any, (PlayerEventHandler)any);

            builder.findMediatorForRoom((ClientMediatorPod) any, roomId); result = room1;
        }};

        MediatorNexus nexus = new MediatorNexus();
        nexus.events = events;
        nexus.setBuilder(builder);

        //start by placing user in a room, to prime the mediator pod.
        nexus.join(client1, roomId, "previous");

        new Verifications() {{
            //capture the callback..
            PlayerEventHandler peh;
            events.subscribeToPlayerEvents(userid, peh = withCapture());
            
            new Expectations() {{
                room1.getId(); result = roomId;
                room1.getName(); result = roomName;
                room2.getId(); result = Constants.FIRST_ROOM;
                room2.getName(); result = Constants.FIRST_ROOM;
                room2.getFullName(); result = FirstRoom.FIRST_ROOM_FULL;
                builder.findMediatorForRoom((ClientMediatorPod) any, Constants.FIRST_ROOM); result = room2;
            }};
            
            //join complete, trigger the callback..
            //we'll claim the user has moved to first room. 
            //the expectations block above preps builder to 
            //return room2 for that id.
            peh.locationUpdated(userid, Constants.FIRST_ROOM);
            
            //check we are now in room2.
            RoomMediator result = client1.getRoomMediator();
            Assert.assertEquals(room2, result);
        }};
    }

    void assertMapSize(String prefix, int size, Map<?, ?> map) {
        Assert.assertEquals(prefix + ": " + map, size, map.size());
    }

    void assertSetSize(String prefix, int size, Set<?> set) {
        Assert.assertEquals(prefix + ": " + set, size, set.size());
    }

    void assertSetContains(String prefix, Set<?> set, Object element) {
        Assert.assertTrue(prefix + ": " + set, set.contains(element));
    }

    void assertSetDoesNotContain(String prefix, Set<?> set, Object element) {
        Assert.assertFalse(prefix + ": " + set, set.contains(element));
    }

}
