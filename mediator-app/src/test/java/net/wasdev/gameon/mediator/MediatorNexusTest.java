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
package net.wasdev.gameon.mediator;

import java.text.MessageFormat;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import mockit.Expectations;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import mockit.Verifications;
import mockit.integration.junit4.JMockit;
import net.wasdev.gameon.mediator.MediatorNexus.ClientMediatorPod;
import net.wasdev.gameon.mediator.room.RoomMediator;

@RunWith(JMockit.class)
public class MediatorNexusTest {

    static final String signedJwt = "testJwt";
    static final String userId = "dummy.DevUser";
    static final String userName = "DevUser";

    static final String roomId = "roomId";
    static final String roomName = "roomName";
    static final String roomFullName = "roomFullName";

    @Mocked MediatorBuilder builder;

    @Rule
    public TestName testName = new TestName();
    
    @Before
    public void before() {
        System.out.println("-- " + testName.getMethodName() + " --------------------------------------");
        
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
            
            builder.findMediatorForRoom(client1, roomId); result = room1;
            builder.findMediatorForRoom(client2, roomId); result = room1;
        }};

        MediatorNexus nexus = new MediatorNexus();
        nexus.setBuilder(builder);

        Map<String, ClientMediatorPod> map = nexus.clientMap;

        System.out.println("Join client1: " + client1);
        nexus.join(client1, roomId, "");
        assertMapSize("Should have 1 element", 1, map);
        Assert.assertTrue("clientMediators should contain client1", map.get("client1").clientMediators.contains(client1));

        System.out.println("Join client1: " + client1);
        nexus.join(client1, roomId, ""); // double-add -- call to setMediator, but no structure change
        assertMapSize("Should have 1 element", 1, map);
        Assert.assertTrue("clientMediators should contain client1", map.get("client1").clientMediators.contains(client1));

        System.out.println("Join client1a: " + client1a);
        nexus.join(client1a, roomId, "");
        assertMapSize("Should have 1 element", 1, map);
        assertSetSize("Sessions should have 2 elements", 2, map.get("client1").clientMediators);
        Assert.assertTrue("clientMediators should contain client1", map.get("client1").clientMediators.contains(client1));
        Assert.assertTrue("clientMediators should contain client1a", map.get("client1").clientMediators.contains(client1a));

        System.out.println("Join client1b: " + client1b);
        nexus.join(client1b, roomId, "");
        assertMapSize("Should have 1 element", 1, map);
        assertSetSize("client 1 clientMediators should have 3 elements", 3, map.get("client1").clientMediators);
        Assert.assertTrue("clientMediators should contain client1", map.get("client1").clientMediators.contains(client1));
        Assert.assertTrue("clientMediators should contain client1a", map.get("client1").clientMediators.contains(client1a));
        Assert.assertTrue("clientMediators should contain client1b", map.get("client1").clientMediators.contains(client1b));

        System.out.println("Join client2: " + client2);
        nexus.join(client2, roomId, "");
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
            builder.findMediatorForRoom(client1, roomId); times = 1;
            builder.findMediatorForRoom(client2, roomId); times = 1;

            client1.setRoomMediator(room1, false); times = 2; // double add
            client1a.setRoomMediator(room1, false); times = 1;
            client1b.setRoomMediator(room1, false); times = 1;
            client2.setRoomMediator(room1, false); times = 1;
            
            room1.join(client1, ""); times = 1; // first add of client1
            room1.join(client2, ""); times = 1; // first add of client2
            
            room1.part(client1a); times = 1; // called when the last session is removed
            room1.part(client2); times = 1;  // second part (double removal) does not call to room
        }};
    }
    
    @Test
    public void testJoinEmptyString(@Mocked ClientMediator client1,
                                              @Mocked RoomMediator room1) {

        new Expectations() {{
            client1.getUserId(); result = "client1";         
            room1.getId(); result = Constants.FIRST_ROOM;
            builder.findMediatorForRoom(client1, Constants.FIRST_ROOM); result = room1;
        }};

        MediatorNexus nexus = new MediatorNexus();
        nexus.setBuilder(builder);

        nexus.join(client1, "", "");
        nexus.join(client1, "", "");
        
        new Verifications() {{
            // mediators created only once per client
            builder.findMediatorForRoom(client1, Constants.FIRST_ROOM); times = 1; 

            room1.join(client1, ""); times = 1; // first add of client1
            client1.setRoomMediator(room1, false); times = 2; // both calls to join 
            
            room1.goodbye(client1); times = 0;  // should not see goodbye!
        }};
    }
    
    @Test
    public void testTransitionNull(@Mocked ClientMediator client1,
                                  @Mocked RoomMediator room1) {
        
        new Expectations() {{
            client1.getUserId(); result = "client1";         
            room1.getId(); result = Constants.FIRST_ROOM;
            builder.findMediatorForRoom(client1, Constants.FIRST_ROOM); result = room1;
        }};

        MediatorNexus nexus = new MediatorNexus();
        nexus.setBuilder(builder);
        
        // This is effectively a join.
        nexus.transition(client1, null);
        
        new Verifications() {{
            // mediators created only once per client
            builder.findMediatorForRoom(client1, Constants.FIRST_ROOM); times = 1; 

            room1.join(client1, ""); times = 1; // first add of client1
            client1.setRoomMediator(room1, false); times = 1; // both calls to join 
            
            room1.goodbye(client1); times = 0;  // should not see goodbye!
        }};
    }

    @Test
    public void testTransitionSameRoom(@Mocked ClientMediator client1,
                                       @Mocked RoomMediator room1) {

        new Expectations() {{
            client1.getUserId(); result = "client1";         
            room1.getId(); result = roomId;
            builder.findMediatorForRoom(client1, roomId); result = room1;
        }};

        MediatorNexus nexus = new MediatorNexus();
        nexus.setBuilder(builder);

        // put client1 in a room
        nexus.join(client1, roomId, "");

        // transition to the same room id, should short-circuit
        nexus.transition(client1, roomId);
        
        new Verifications() {{
            // mediators created only once per client
            builder.findMediatorForRoom(client1, roomId); times = 1; 

            room1.join(client1, ""); times = 1; // first add of client1
            client1.setRoomMediator(room1, false); times = 1; // join
            
            room1.goodbye(client1); times = 0;  // should not see goodbye!
        }};
    }
    
    @Test
    public void testTransitionSameRoomNull(@Mocked ClientMediator client1,
                                       @Mocked RoomMediator room1) {

        new Expectations() {{
            client1.getUserId(); result = "client1";         
            room1.getId(); result = Constants.FIRST_ROOM;
            builder.findMediatorForRoom(client1, Constants.FIRST_ROOM); result = room1;
        }};

        MediatorNexus nexus = new MediatorNexus();
        nexus.setBuilder(builder);

        // put client1 in a room
        nexus.join(client1, null, "");

        // transition to the same room id, should short-circuit
        nexus.transition(client1, null);
        
        new Verifications() {{
            builder.findMediatorForRoom(client1, Constants.FIRST_ROOM); times = 1; 
            room1.join(client1, ""); times = 1; // first add of client1
            client1.setRoomMediator(room1, false); times = 1; // join
            
            room1.goodbye(client1); times = 0;  // should not see goodbye!
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
            
            builder.findMediatorForRoom(client1, roomId); result = room1;
            builder.findMediatorForRoom(client1, roomId2); result = room2;
        }};

        MediatorNexus nexus = new MediatorNexus();
        nexus.setBuilder(builder);

        // put client1 in room1
        nexus.join(client1, roomId, "");

        // transition to the same room id, should short-circuit
        nexus.transition(client1, roomId2);
        
        new Verifications() {{
            room1.join(client1, ""); times = 1; // first add of client1
            builder.findMediatorForRoom(client1, roomId); times = 1; 
            client1.setRoomMediator(room1, false); times = 1; // join
            
            // transition
            room1.goodbye(client1);
            builder.findMediatorForRoom(client1, roomId2); times = 1;
            room2.hello(client1, false);
            client1.setRoomMediator(room2, false); times = 1; // join
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
            builder.findMediatorForRoom(client1, Constants.FIRST_ROOM); result = room1;
            builder.findMediatorForExit(client1, room1, "N"); result = room2;
        }};
       
        MediatorNexus nexus = new MediatorNexus();
        nexus.setBuilder(builder);

        // put client1 in room1
        nexus.join(client1, null, "");
        nexus.join(client1a, null, "");

        // transition to the same room id, should short-circuit
        nexus.transitionViaExit(client1, "N");

        new Verifications() {{
            // Join (only with client1: client1a is just added to pod)
            builder.findMediatorForRoom(client1, Constants.FIRST_ROOM); times = 1; 
            room1.join(client1, ""); times = 1; // first add of client1
            client1.setRoomMediator(room1, false); times = 1; // join

            // client1a is added to pod
            client1a.setRoomMediator(room1, false); times = 1; // join

            // transition, updates both clients in the pod
            room1.goodbye(client1); times = 1;  
            builder.findMediatorForExit(client1, room1, "N"); times = 1;
            room2.hello(client1, false); times = 1; 
            client1.setRoomMediator(room2, false); times = 1; 
            client1a.setRoomMediator(room2, false); times = 1; 
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
