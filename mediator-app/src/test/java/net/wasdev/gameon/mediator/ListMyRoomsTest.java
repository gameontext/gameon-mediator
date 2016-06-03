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
import net.wasdev.gameon.mediator.models.RoomInfo;
import net.wasdev.gameon.mediator.models.Site;
import net.wasdev.gameon.mediator.room.FirstRoom;

@RunWith(JMockit.class)
public class ListMyRoomsTest {

    @Test
    public void testNoRooms(@Mocked MapClient mapClient) {
        PlayerClient playerClient = new PlayerClient();
        String playerJwt = "testUser";

        FirstRoom firstRoom = new FirstRoom(playerJwt, playerClient, mapClient);
        MockedPlayerSession playerSession = new MockedPlayerSession();
        assertTrue("The subscription should be successful", firstRoom.subscribe(playerSession, 0));
        assertNull("A subscription for a non-newbie should not trigger a message",
                playerSession.getLastClientMessage());

        RoutedMessage routedMessage = RoutedMessage.createMessage("room", "firstRoom",
                "{\"username\":\"DevUser\",\"userId\":\"testUser\",\"content\":\"/listmyrooms\"}");

        new Expectations() {{
            mapClient.getRoomsByOwner(playerJwt); returns(Collections.emptyList());
        }};

        firstRoom.send(routedMessage);
        RoutedMessage lastClientMessage = playerSession.getLastClientMessage();

        checkMessageHeaders(lastClientMessage, "player", "testUser", "event");

        checkMessageContentAsObject(lastClientMessage, "You have no rooms registered!");
    }

    @Test
    public void testOneRoom(@Mocked MapClient mapClient) {
        PlayerClient playerClient = new PlayerClient();
        String playerJwt = "testUser";

        FirstRoom firstRoom = new FirstRoom(playerJwt, playerClient, mapClient);
        MockedPlayerSession playerSession = new MockedPlayerSession();
        assertTrue("The subscription should be successful", firstRoom.subscribe(playerSession, 0));
        assertNull("A subscription for a non-newbie should not trigger a message",
                playerSession.getLastClientMessage());

        RoutedMessage routedMessage = RoutedMessage.createMessage("room", "firstRoom",
                "{\"username\":\"DevUser\",\"userId\":\"testUser\",\"content\":\"/listmyrooms\"}");

        List<Site> returnedSites = new ArrayList<Site>();
        Site singleReturnedRoom = new Site();
        singleReturnedRoom.setId("steve");
        RoomInfo roomInfo = new RoomInfo();
        roomInfo.setFullName("AnotherRoomForSteve");
        roomInfo.setName("stevesRoom");
        singleReturnedRoom.setInfo(roomInfo);
        returnedSites.add(singleReturnedRoom);

        new Expectations() {{
            mapClient.getRoomsByOwner(playerJwt); returns(returnedSites);
        }};

        firstRoom.send(routedMessage);
        RoutedMessage lastClientMessage = playerSession.getLastClientMessage();

        checkMessageHeaders(lastClientMessage, "player", playerJwt, "event");
        checkMessageContentAsObject(lastClientMessage,
                "You have registered the following rooms... \n"
                + " - '" + singleReturnedRoom.getInfo().getFullName() + "' with id "
                + singleReturnedRoom.getInfo().getName() + " (long id: " + singleReturnedRoom.getId() +")\n"
                + "\nYou can go directly to a room using /teleport <roomid>");
    }

    @Test
    public void testTwoRooms(@Mocked MapClient mapClient) {
        PlayerClient playerClient = new PlayerClient();
        String playerJwt = "testUser";

        FirstRoom firstRoom = new FirstRoom(playerJwt, playerClient, mapClient);
        MockedPlayerSession playerSession = new MockedPlayerSession();
        assertTrue("The subscription should be successful", firstRoom.subscribe(playerSession, 0));
        assertNull("A subscription for a non-newbie should not trigger a message",
                playerSession.getLastClientMessage());

        RoutedMessage routedMessage = RoutedMessage.createMessage("room", "firstRoom",
                "{\"username\":\"DevUser\",\"userId\":\"testUser\",\"content\":\"/listmyrooms\"}");

        List<Site> returnedSites = new ArrayList<Site>();
        Site room1 = new Site();
        room1.setId("siteIdForRoom1");
        RoomInfo roomInfo1 = new RoomInfo();
        roomInfo1.setFullName("site1FullName");
        roomInfo1.setName("site1");
        room1.setInfo(roomInfo1);
        returnedSites.add(room1);
        
        Site room2 = new Site();
        room2.setId("siteIdForRoom2");
        RoomInfo roomInfo2 = new RoomInfo();
        roomInfo2.setFullName("site1FullName");
        roomInfo2.setName("site1");
        room2.setInfo(roomInfo2);
        returnedSites.add(room2);

        new Expectations() {{
            mapClient.getRoomsByOwner(playerJwt); returns(returnedSites);
        }};

        firstRoom.send(routedMessage);
        RoutedMessage lastClientMessage = playerSession.getLastClientMessage();

        checkMessageHeaders(lastClientMessage, "player", "testUser", "event");
        checkMessageContentAsObject(lastClientMessage,
                "You have registered the following rooms... \n"
                + " - '" + room1.getInfo().getFullName() + "' with id "
                + room1.getInfo().getName() + " (long id: " + room1.getId() +")\n"
                + " - '" + room2.getInfo().getFullName() + "' with id "
                + room2.getInfo().getName() + " (long id: " + room2.getId() +")\n"
                + "\nYou can go directly to a room using /teleport <roomid>");

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

