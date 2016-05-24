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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.json.JsonObject;

import org.junit.Test;
import org.junit.runner.RunWith;

import mockit.Expectations;
import mockit.Mocked;
import mockit.integration.junit4.JMockit;
import net.wasdev.gameon.mediator.mocks.MockedPlayerSession;
import net.wasdev.gameon.mediator.models.Site;
import net.wasdev.gameon.mediator.room.FirstRoom;

@RunWith(JMockit.class)
public class TeleportTest {

    @Test
    public void testTeleportNoRooms(@Mocked MapClient mapClient) {
        PlayerClient playerClient = new PlayerClient();
        String playerJwt = "testUser";

        FirstRoom firstRoom = new FirstRoom(playerJwt, playerClient, mapClient);
        MockedPlayerSession playerSession = new MockedPlayerSession();
        assertTrue("The subscription should be successful", firstRoom.subscribe(playerSession, 0));
        assertNull("A subscription for a non-newbie should not trigger a message",
                playerSession.getLastClientMessage());

        RoutedMessage routedMessage = RoutedMessage.createMessage("room", "firstRoom",
                "{\"username\":\"DevUser\",\"userId\":\"dummy.DevUser\",\"content\":\"/teleport steve\"}");

        new Expectations() {
            {
                mapClient.getSite("steve"); returns(null);
                mapClient.getRoomsByRoomName("steve");
                returns(Collections.emptyList());
            }
        };
        firstRoom.send(routedMessage);
        RoutedMessage lastClientMessage = playerSession.getLastClientMessage();

        checkMessageHeaders(lastClientMessage, "player", "dummy.DevUser", "event");

        checkMessageContentAsObject(lastClientMessage,
                "You don't appear to have a room with that id to teleport to.. maybe you should check `/listmyrooms`");
    }

    @Test
    public void testTeleportOneRoom(@Mocked MapClient mapClient) {
        PlayerClient playerClient = new PlayerClient();
        String playerJwt = "testUser";

        FirstRoom firstRoom = new FirstRoom(playerJwt, playerClient, mapClient);
        MockedPlayerSession playerSession = new MockedPlayerSession();
        assertTrue("The subscription should be successful", firstRoom.subscribe(playerSession, 0));
        assertNull("A subscription for a non-newbie should not trigger a message",
                playerSession.getLastClientMessage());

        RoutedMessage routedMessage = RoutedMessage.createMessage("room", "firstRoom",
                "{\"username\":\"DevUser\",\"userId\":\"dummy.DevUser\",\"content\":\"/teleport steve\"}");

        List<Site> returnedSites = new ArrayList<Site>();
        Site singleReturnedRoom = new Site();
        singleReturnedRoom.setId("steve");
        returnedSites.add(singleReturnedRoom);
        new Expectations() {
            {
                mapClient.getSite("steve"); returns(null);
                mapClient.getRoomsByRoomName("steve");
                returns(returnedSites);
            }
        };
        firstRoom.send(routedMessage);
        RoutedMessage lastClientMessage = playerSession.getLastClientMessage();

        checkMessageHeaders(lastClientMessage, "playerLocation", "dummy.DevUser", "exit");
        checkMessageContentAsString(lastClientMessage,
                "You punch the coordinates into the console, a large tube appears from above you, and you are sucked into a maze of piping.");

    }
    
    @Test
    public void testTeleportById(@Mocked MapClient mapClient) {
        PlayerClient playerClient = new PlayerClient();
        String playerJwt = "testUser";

        FirstRoom firstRoom = new FirstRoom(playerJwt, playerClient, mapClient);
        MockedPlayerSession playerSession = new MockedPlayerSession();
        assertTrue("The subscription should be successful", firstRoom.subscribe(playerSession, 0));
        assertNull("A subscription for a non-newbie should not trigger a message",
                playerSession.getLastClientMessage());

        RoutedMessage routedMessage = RoutedMessage.createMessage("room", "firstRoom",
                "{\"username\":\"DevUser\",\"userId\":\"dummy.DevUser\",\"content\":\"/teleport steve\"}");

        List<Site> returnedSites = new ArrayList<Site>();
        Site singleReturnedRoom = new Site();
        singleReturnedRoom.setId("steve");
        returnedSites.add(singleReturnedRoom);
        new Expectations() {
            {
                mapClient.getSite("steve"); returns(singleReturnedRoom);
            }
        };
        firstRoom.send(routedMessage);
        RoutedMessage lastClientMessage = playerSession.getLastClientMessage();

        checkMessageHeaders(lastClientMessage, "playerLocation", "dummy.DevUser", "exit");
        checkMessageContentAsString(lastClientMessage,
                "You punch the coordinates into the console, a large tube appears from above you, and you are sucked into a maze of piping.");

    }

    @Test
    public void testTeleportTwoRooms(@Mocked MapClient mapClient) {
        PlayerClient playerClient = new PlayerClient();
        String playerJwt = "testUser";

        FirstRoom firstRoom = new FirstRoom(playerJwt, playerClient, mapClient);
        MockedPlayerSession playerSession = new MockedPlayerSession();
        assertTrue("The subscription should be successful", firstRoom.subscribe(playerSession, 0));
        assertNull("A subscription for a non-newbie should not trigger a message",
                playerSession.getLastClientMessage());

        RoutedMessage routedMessage = RoutedMessage.createMessage("room", "firstRoom",
                "{\"username\":\"DevUser\",\"userId\":\"dummy.DevUser\",\"content\":\"/teleport steve\"}");

        List<Site> returnedSites = new ArrayList<Site>();
        Site room1 = new Site();
        room1.setId("siteIdForRoom1");
        returnedSites.add(room1);
        Site room2 = new Site();
        room2.setId("siteIdForRoom2");
        returnedSites.add(room2);
        new Expectations() {
            {
                mapClient.getSite("steve"); returns(null);
                mapClient.getRoomsByRoomName("steve");
                returns(returnedSites);
            }
        };
        firstRoom.send(routedMessage);
        RoutedMessage lastClientMessage = playerSession.getLastClientMessage();

        checkMessageHeaders(lastClientMessage, "player", "dummy.DevUser", "event");
        checkMessageContentAsObject(lastClientMessage,
                "/teleport siteIdForRoom1\n - /teleport siteIdForRoom2\n");

    }

    private void checkMessageHeaders(RoutedMessage messageToCheck, String expectedFlowTarget,
            String expectedDestination, String expectedContentType) {
        assertNotNull("The client should return a message", messageToCheck);
        assertEquals("The message should be directed to the correct flow target", expectedFlowTarget,
                messageToCheck.getFlowTarget());
        assertEquals("The message should be directed to the correct destination", expectedDestination,
                messageToCheck.getDestination());
        JsonObject actualMessageBody = messageToCheck.getParsedBody();
        assertNotNull("There should be a message body in the returned message", actualMessageBody);
        String actualContentType = actualMessageBody.getString("type");
        assertEquals("The type should be an event type", expectedContentType, actualContentType);

    }

    private void checkMessageContentAsString(RoutedMessage messageToCheck, String expectedContent) {
        JsonObject actualMessageBody = messageToCheck.getParsedBody();
        assertNotNull("There should be a message body in the returned message", actualMessageBody);
        String actualContent = actualMessageBody.getString("content");
        assertNotNull("There should be content in the message body", actualContent);
        assertTrue("The message to should contain [" + expectedContent + "], but got [" + actualContent + "]",
                actualContent.contains(expectedContent));
    }

    private void checkMessageContentAsObject(RoutedMessage messageToCheck, String expectedMessageToAll) {
        JsonObject actualMessageBody = messageToCheck.getParsedBody();
        assertNotNull("There should be a message body in the returned message", actualMessageBody);
        JsonObject actualContent = actualMessageBody.getJsonObject("content");
        assertNotNull("The message content should exist", actualContent);
        String actualMessageToAll = actualContent.getString("*");
        assertNotNull("There should be a message to all", actualMessageToAll);
        assertTrue("The message to should contain [" + expectedMessageToAll + "], but got [" + actualMessageToAll + "]",
                actualMessageToAll.contains(expectedMessageToAll));
    }

}
