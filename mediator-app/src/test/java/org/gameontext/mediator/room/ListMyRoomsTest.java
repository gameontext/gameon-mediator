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
package org.gameontext.mediator.room;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.json.JsonObject;

import org.gameontext.mediator.MapClient;
import org.gameontext.mediator.PlayerClient;
import org.gameontext.mediator.RoutedMessage;
import org.gameontext.mediator.MediatorNexus.View;
import org.gameontext.mediator.RoutedMessage.FlowTarget;
import org.gameontext.mediator.models.RoomInfo;
import org.gameontext.mediator.models.Site;
import org.gameontext.mediator.room.FirstRoom;
import org.junit.Test;
import org.junit.runner.RunWith;

import mockit.Expectations;
import mockit.Mocked;
import mockit.Verifications;
import mockit.integration.junit4.JMockit;

@RunWith(JMockit.class)
public class ListMyRoomsTest {

    @Test
    public void testNoRooms(@Mocked MapClient mapClient,
                            @Mocked View nexusView,
                            @Mocked Site site) {
        PlayerClient playerClient = new PlayerClient();
        String playerJwt = "testUser";

        new Expectations() {{
            mapClient.getRoomsByOwner(playerJwt); returns(Collections.emptyList());
        }};

        FirstRoom firstRoom = new FirstRoom(nexusView, playerJwt, playerClient, mapClient, site);

        RoutedMessage routedMessage = RoutedMessage.createMessage(FlowTarget.room, "firstRoom",
                "{\"username\":\"DevUser\",\"userId\":\"testUser\",\"content\":\"/listmyrooms\"}");

        firstRoom.sendToRoom(routedMessage);

        new Verifications() {{
            List<RoutedMessage> responses = new ArrayList<>();

            // no message for a non-newbie: only the one for the command we issued
            nexusView.sendToClients(withCapture(responses)); times = 1;

            checkMessageHeaders(responses.get(0), FlowTarget.player, playerJwt, "event");
            checkMessageContentAsObject(responses.get(0), "You have no rooms registered!");

        }};
    }

    @Test
    public void testOneRoom(@Mocked MapClient mapClient,
            @Mocked View nexusView,
            @Mocked Site site) {

        PlayerClient playerClient = new PlayerClient();
        String playerJwt = "testUser";

        RoomInfo roomInfo = new RoomInfo();
        roomInfo.setFullName("AnotherRoomForSteve");
        roomInfo.setName("stevesRoom");

        Site singleReturnedRoom = new Site();
        singleReturnedRoom.setId("steve");
        singleReturnedRoom.setInfo(roomInfo);

        List<Site> returnedSites = new ArrayList<Site>();
        returnedSites.add(singleReturnedRoom);

        new Expectations() {{
            mapClient.getRoomsByOwner(playerJwt); returns(returnedSites);
        }};

        FirstRoom firstRoom = new FirstRoom(nexusView, playerJwt, playerClient, mapClient, site);

        RoutedMessage routedMessage = RoutedMessage.createMessage(FlowTarget.room, "firstRoom",
                "{\"username\":\"DevUser\",\"userId\":\"testUser\",\"content\":\"/listmyrooms\"}");


       firstRoom.sendToRoom(routedMessage);

       new Verifications() {{
           List<RoutedMessage> responses = new ArrayList<>();

           // no message for a non-newbie: only the one for the command we issued
           nexusView.sendToClients(withCapture(responses)); times = 1;

           checkMessageHeaders(responses.get(0), FlowTarget.player, playerJwt, "event");
           checkMessageContentAsObject(responses.get(0),
                   "You have registered the following rooms... \n"
                   + " - '" + singleReturnedRoom.getInfo().getFullName() + "' with id "
                   + singleReturnedRoom.getInfo().getName() + " (long id: " + singleReturnedRoom.getId() +")\n"
                   + "\nYou can go directly to a room using /teleport <roomid>");

       }};
    }

    @Test
    public void testTwoRooms(@Mocked MapClient mapClient,
            @Mocked View nexusView,
            @Mocked Site site) {
        PlayerClient playerClient = new PlayerClient();
        String playerJwt = "testUser";

        RoomInfo roomInfo1 = new RoomInfo();
        roomInfo1.setFullName("site1FullName");
        roomInfo1.setName("site1");

        Site room1 = new Site();
        room1.setId("siteIdForRoom1");
        room1.setInfo(roomInfo1);

        RoomInfo roomInfo2 = new RoomInfo();
        roomInfo2.setFullName("site2FullName");
        roomInfo2.setName("site2");

        Site room2 = new Site();
        room2.setId("siteIdForRoom2");
        room2.setInfo(roomInfo2);

        List<Site> returnedSites = new ArrayList<Site>();
        returnedSites.add(room1);
        returnedSites.add(room2);

        new Expectations() {{
            mapClient.getRoomsByOwner(playerJwt); returns(returnedSites);
        }};

        FirstRoom firstRoom = new FirstRoom(nexusView, playerJwt, playerClient, mapClient, site);

        RoutedMessage routedMessage = RoutedMessage.createMessage(FlowTarget.room, "firstRoom",
                "{\"username\":\"DevUser\",\"userId\":\"testUser\",\"content\":\"/listmyrooms\"}");

        firstRoom.sendToRoom(routedMessage);

        new Verifications() {{
            List<RoutedMessage> responses = new ArrayList<>();

            // no message for a non-newbie: only the one for the command we issued
            nexusView.sendToClients(withCapture(responses)); times = 1;

            checkMessageHeaders(responses.get(0), FlowTarget.player, playerJwt, "event");
            checkMessageContentAsObject(responses.get(0),
                    "You have registered the following rooms... \n"
                    + " - '" + room1.getInfo().getFullName() + "' with id "
                    + room1.getInfo().getName() + " (long id: " + room1.getId() +")\n"
                    + " - '" + room2.getInfo().getFullName() + "' with id "
                    + room2.getInfo().getName() + " (long id: " + room2.getId() +")\n"
                    + "\nYou can go directly to a room using /teleport <roomid>");

        }};
    }

    private void checkMessageHeaders(RoutedMessage messageToCheck, FlowTarget expectedFlowTarget,
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

