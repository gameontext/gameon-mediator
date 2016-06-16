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
package net.wasdev.gameon.mediator.room;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.logging.Level;

import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

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
import net.wasdev.gameon.mediator.Constants;
import net.wasdev.gameon.mediator.Log;
import net.wasdev.gameon.mediator.MapClient;
import net.wasdev.gameon.mediator.MediatorNexus;
import net.wasdev.gameon.mediator.PlayerClient;
import net.wasdev.gameon.mediator.RoutedMessage;
import net.wasdev.gameon.mediator.RoutedMessage.FlowTarget;
import net.wasdev.gameon.mediator.models.RoomInfo;
import net.wasdev.gameon.mediator.models.Site;
import net.wasdev.gameon.mediator.room.RoomMediator.Type;

@RunWith(JMockit.class)
public class FirstRoomTest {

    @Mocked MediatorNexus.View nexus;
    @Mocked MapClient mapClient;
    @Mocked PlayerClient playerClient;

    String playerJwt = "testJwt";
    String userId = "dummy.DevUser";
    String userName = "DevUser";

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
    public void testBasics(@Mocked Site site,
                           @Mocked RoomInfo info,
                           @Mocked JsonObjectBuilder builder) {

        new Expectations() {{
            site.getInfo(); returns(info);
        }};

        FirstRoom firstRoom = new FirstRoom(nexus, playerJwt, playerClient, mapClient, site, true);

        Assert.assertTrue(firstRoom.newbie);
        Assert.assertEquals(Type.FIRST_ROOM, firstRoom.getType());
        Assert.assertEquals(Constants.FIRST_ROOM, firstRoom.getName());
        Assert.assertEquals(FirstRoom.FIRST_ROOM_FULL, firstRoom.getFullName());
        Assert.assertFalse("room info should not be the same when null", firstRoom.sameConnectionDetails(null));

        String description = firstRoom.getDescription();
        Assert.assertEquals("get description should return the constant (avoid confusion)", FirstRoom.FIRST_ROOM_DESC, description);

        firstRoom.buildLocationResponse(builder);
        firstRoom.buildLocationResponse(builder);

        new Verifications() {{
            info.getName(); times = 0;
            info.getFullName(); times = 0;
            builder.add(RoomUtils.TYPE, RoomUtils.LOCATION);  times = 2;
            builder.add(Constants.KEY_ROOM_NAME, Constants.FIRST_ROOM); times = 2;
            builder.add(Constants.KEY_ROOM_FULLNAME, FirstRoom.FIRST_ROOM_FULL); times = 2;
            builder.add(Constants.KEY_ROOM_EXITS, (JsonObject) any); times = 2;
            builder.add(Constants.KEY_ROOM_EXITS, (JsonObject) any); times = 2;
            builder.add(Constants.KEY_COMMANDS, (JsonObject) any); times = 2;
            builder.add(RoomUtils.DESCRIPTION, FirstRoom.FIRST_ROOM_DESC + FirstRoom.FIRST_ROOM_EXTENDED); times = 1;
            builder.add(RoomUtils.DESCRIPTION, FirstRoom.FIRST_ROOM_DESC); times = 3;
        }};
    }

    @Test
    public void testTeleportNoRooms(@Mocked Site site) {

        new Expectations() {{
            mapClient.getSite("steve"); returns(null);
            mapClient.getRoomsByRoomName("steve"); returns(Collections.emptyList());
        }};

        FirstRoom firstRoom = new FirstRoom(nexus, playerJwt, playerClient, mapClient, site);
        RoutedMessage routedMessage = RoutedMessage.createMessage(FlowTarget.room, "firstRoom",
                "{\"username\":\"DevUser\",\"userId\":\"dummy.DevUser\",\"content\":\"/teleport steve\"}");

        firstRoom.sendToRoom(routedMessage);

        new Verifications() {{
            RoutedMessage result;

            nexus.sendToClients(result = withCapture()); times = 1;
            System.out.println(result);

            Assert.assertEquals("Message should flow to the player", FlowTarget.player, result.getFlowTarget());
            Assert.assertEquals("Message should be specific to the user", userId, result.getDestination());
            Assert.assertEquals("Should be an event message", "event", getMessageType(result));
            Assert.assertEquals(FirstRoom.TELEPORT_NO_ROOMS, getContentForUserId(result, userId));
        }};
    }

    @Test
    public void testTeleportOneRoom(@Mocked Site site, @Mocked Site site1) {

        new Expectations() {{
            mapClient.getSite("steve"); returns(null);
            mapClient.getRoomsByRoomName("steve"); returns(Arrays.asList(site1));
            site1.getId(); result = "steve";
        }};

        FirstRoom firstRoom = new FirstRoom(nexus, playerJwt, playerClient, mapClient, site);
        RoutedMessage routedMessage = RoutedMessage.createMessage(FlowTarget.room, "firstRoom",
                "{\"username\":\"DevUser\",\"userId\":\"dummy.DevUser\",\"content\":\"/teleport steve\"}");

        firstRoom.sendToRoom(routedMessage);

        new Verifications() {{
            RoutedMessage result;

            nexus.sendToClients(result = withCapture()); times = 1;
            System.out.println(result);

            Assert.assertEquals("Message should be a player location change", FlowTarget.playerLocation, result.getFlowTarget());
            Assert.assertEquals("Message should be specific to the user", userId, result.getDestination());
            Assert.assertEquals("Should be an exit message", "exit", getMessageType(result));

            // we have bonus random appended text that might show up, have to go with startsWith instead of equals
            Assert.assertTrue(getContent(result).startsWith(FirstRoom.TELEPORT_GO));
        }};
    }

    @Test
    public void testTeleportById(@Mocked Site site, @Mocked Site site1) {

        new Expectations() {{
            mapClient.getSite("steve"); returns(site1);
            site1.getId(); returns("steve");
        }};

        FirstRoom firstRoom = new FirstRoom(nexus, playerJwt, playerClient, mapClient, site);
        RoutedMessage routedMessage = RoutedMessage.createMessage(FlowTarget.room, "firstRoom",
                "{\"username\":\"DevUser\",\"userId\":\"dummy.DevUser\",\"content\":\"/teleport steve\"}");

        firstRoom.sendToRoom(routedMessage);

        new Verifications() {{
            RoutedMessage result;

            nexus.sendToClients(result = withCapture()); times = 1;
            System.out.println(result);

            Assert.assertEquals("Message should be a player location change", FlowTarget.playerLocation, result.getFlowTarget());
            Assert.assertEquals("Message should be specific to the user", userId, result.getDestination());
            Assert.assertEquals("Should be an exit message", "exit", getMessageType(result));

            String messageContent = getContent(result);
            Assert.assertNotNull("message should be specific for user", messageContent);

            // we have bonus random appended text that might show up, have to go with startsWith instead of equals
            Assert.assertTrue("Message should start with [" + FirstRoom.TELEPORT_GO+ "], but is ["+messageContent+"]",
                    messageContent.startsWith(FirstRoom.TELEPORT_GO));
        }};
    }

    @Test
    public void testTeleportTwoRooms(@Mocked Site site, @Mocked Site site1, @Mocked Site site2) {
        new Expectations() {{
            site1.getId(); result = "siteIdForRoom1";
            site2.getId(); result = "siteIdForRoom2";
            mapClient.getSite("steve"); returns(null);
            mapClient.getRoomsByRoomName("steve"); returns(Arrays.asList(site1, site2));
        }};

        FirstRoom firstRoom = new FirstRoom(nexus, playerJwt, playerClient, mapClient, site);
        RoutedMessage routedMessage = RoutedMessage.createMessage(FlowTarget.room, "firstRoom",
                "{\"username\":\"DevUser\",\"userId\":\"dummy.DevUser\",\"content\":\"/teleport steve\"}");

        firstRoom.sendToRoom(routedMessage);

        new Verifications() {{
            RoutedMessage result;

            nexus.sendToClients(result = withCapture()); times = 1;

            Assert.assertEquals("Message should flow to the player: " + result, FlowTarget.player, result.getFlowTarget());
            Assert.assertEquals("Message should be specific to the user: " + result, userId, result.getDestination());
            Assert.assertEquals("Should be an event message: " + result, "event", getMessageType(result));

            String messageContent = getContentForUserId(result, userId);
            Assert.assertNotNull("message should be specific for user: " + result, messageContent);

            String expectedValue = String.format(FirstRoom.TELEPORT_MANY_ROOMS, "steve");
            // List of rooms & ids will follow, so use startsWith
            Assert.assertTrue("Message should start with [" + expectedValue + "], but is ["+messageContent+"]",
                    messageContent.startsWith(expectedValue));
        }};
    }


    String getMessageType(RoutedMessage message) {
        return message.getParsedBody().getString(RoomUtils.TYPE);
    }

    String getContent(RoutedMessage message) {
        String content = message.getParsedBody().getString(RoomUtils.CONTENT);
        return content;
    }

    String getContentForUserId(RoutedMessage message, String userId) {
        JsonObject content = message.getParsedBody().getJsonObject(RoomUtils.CONTENT);
        return content.getString(userId,"");
    }
}
