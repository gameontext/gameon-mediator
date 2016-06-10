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

import java.io.StringReader;
import java.lang.reflect.Field;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

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
import net.wasdev.gameon.mediator.room.RoomMediator;
import net.wasdev.gameon.mediator.room.RoomMediator.Type;

@RunWith(JMockit.class)
public class ClientMediatorTest {

    @Mocked MediatorBuilder builder;
    @Mocked MediatorNexus nexus;
    @Mocked MapClient mapClient;
    @Mocked PlayerClient playerClient;
    @Mocked Drain drain;

    static final String signedJwt = "testJwt";
    static final String userId = "dummy.DevUser";
    static final String userName = "DevUser";

    static final String roomId = "roomId";
    static final String roomName = "roomName";
    static final String roomFullName = "roomFullName";

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
    public void testReadyNoSavedRoom(@Mocked RoomMediator room) {

        ClientMediator mediator = new ClientMediator(nexus, drain, userId, signedJwt);

        String msgTxt = "ready,{}";
        RoutedMessage message = new RoutedMessage(msgTxt);
        System.out.println(message);

        new Expectations() {{
            nexus.join(mediator, null, "");
        }};

        mediator.ready(message);

        new Verifications() {{
            nexus.join(mediator, null, ""); times = 1;
            drain.send((RoutedMessage) any); times = 0;
        }};
    }

    @Test
    public void testReadyUserName(@Mocked RoomMediator room) {

        ClientMediator mediator = new ClientMediator(nexus, drain, userId, signedJwt);

        String msgTxt = "ready,{\"username\":\"TinyJamFilledMuffin\"}";
        RoutedMessage message = new RoutedMessage(msgTxt);
        System.out.println(message);

        new Expectations() {{
            nexus.join(mediator, null, "");
        }};

        mediator.ready(message);

        new Verifications() {{
            nexus.join(mediator, null, ""); times = 1;
            drain.send((RoutedMessage) any); times = 0;
        }};
    }


    @Test
    public void testReadyZeroBookmark(@Mocked RoomMediator room) {

        ClientMediator mediator = new ClientMediator(nexus, drain, userId, signedJwt);

        String msgTxt = "ready,{\"bookmark\":0}";
        RoutedMessage message = new RoutedMessage(msgTxt);
        System.out.println(message);

        new Expectations() {{
            nexus.join(mediator, null, "0");
        }};

        mediator.ready(message);

        new Verifications() {{
            nexus.join(mediator, null, "0"); times = 1;
            drain.send((RoutedMessage) any); times = 0;
        }};
    }


    @Test
    public void testReadySavedRoom(@Mocked RoomMediator room) {

        ClientMediator mediator = new ClientMediator(nexus, drain, userId, signedJwt);

        String msgTxt = "ready,{\"roomId\": \"roomId\",\"bookmark\": \"id\"}";
        RoutedMessage message = new RoutedMessage(msgTxt);
        System.out.println(message);

        new Expectations() {{
            nexus.join(mediator, roomId, "id");
        }};

        mediator.ready(message);

        new Verifications() {{
            nexus.join(mediator, roomId, "id"); times = 1;
            drain.send((RoutedMessage) any); times = 0;
        }};
    }

    @Test
    public void testSwitchRooms(@Mocked RoomMediator room1,
                                @Mocked RoomMediator room2) throws Exception {

      Field field_roomMediator = ClientMediator.class.getDeclaredField("roomMediator");
      field_roomMediator.setAccessible(true);

      ClientMediator mediator = new ClientMediator(nexus, drain, userId, signedJwt);
      field_roomMediator.set(mediator, room1);

        String msgTxt = "playerLocation,dummy.DevUser,{"
                + "\"type\": \"exit\","
                + "\"content\": \"You exit through door xyz... \","
                + "\"exitId\": \"N\"}";
        RoutedMessage origMessage = new RoutedMessage(msgTxt);
        System.out.println(origMessage);

        new Expectations() {{
            room1.getType(); result = Type.EMPTY;
            nexus.transitionViaExit(mediator, "N");
        }};

        mediator.switchRooms(origMessage); // called on _a_ mediator by nexus

        new Verifications() {{
            nexus.transitionViaExit(mediator, "N"); times = 1;
            drain.send((RoutedMessage) any); times = 0;
        }};
    }

    @Test
    public void testSwitchSOS(@Mocked RoomMediator room1,
                                   @Mocked RoomMediator firstRoom) throws Exception {

        Field field_roomMediator = ClientMediator.class.getDeclaredField("roomMediator");
        field_roomMediator.setAccessible(true);

        ClientMediator mediator = new ClientMediator(nexus, drain, userId, signedJwt);
        field_roomMediator.set(mediator, room1);

        String msgTxt = "sos,{}";
        RoutedMessage origMessage = new RoutedMessage(msgTxt);
        System.out.println(origMessage);

        new Expectations() {{
            nexus.transition(mediator, Constants.FIRST_ROOM);
        }};

        mediator.switchRooms(origMessage); // called on _a_ mediator by nexus

        new Verifications() {{
            List<RoutedMessage> responses = new ArrayList<>();

            nexus.transition(mediator, Constants.FIRST_ROOM); times = 1;

            drain.send(withCapture(responses)); // joinpart, ack
            System.out.println(responses);

            Assert.assertEquals("Expected 1 response: "+ responses, 1, responses.size());

            RoutedMessage message = responses.get(0); // playerLocation is sent to client
            assertSameBody(Constants.EXIT_ELECTRIC_THUMB, message.getParsedBody());
        }};
    }


    @Test
    public void testSwitchNoExit(@Mocked RoomMediator room1,
                                  @Mocked RoomMediator firstRoom) throws Exception {

        Field field_roomMediator = ClientMediator.class.getDeclaredField("roomMediator");
        field_roomMediator.setAccessible(true);

        ClientMediator mediator = new ClientMediator(nexus, drain, userId, signedJwt);
        field_roomMediator.set(mediator, room1);

        String msgTxt = "playerLocation,dummy.DevUser,{"
                + "\"type\": \"exit\","
                + "\"content\": \"You exit through door xyz... \"}";
        RoutedMessage origMessage = new RoutedMessage(msgTxt);
        System.out.println(origMessage);

        new Expectations() {{
            nexus.transition(mediator, Constants.FIRST_ROOM);
        }};

        mediator.switchRooms(origMessage); // called on _a_ mediator by nexus

        new Verifications() {{
            nexus.transition(mediator, Constants.FIRST_ROOM); times = 1;
            drain.send((RoutedMessage) any); times = 0;
        }};
    }

    @Test
    public void testSwitchTeleport(@Mocked RoomMediator room1,
                                    @Mocked RoomMediator firstRoom) throws Exception {

        Field field_roomMediator = ClientMediator.class.getDeclaredField("roomMediator");
        field_roomMediator.setAccessible(true);

        ClientMediator mediator = new ClientMediator(nexus, drain, userId, signedJwt);
        field_roomMediator.set(mediator, firstRoom);

        String msgTxt = "playerLocation,dummy.DevUser,{"
                + "\"type\": \"exit\","
                + "\"teleport\": true,"
                + "\"exitId\": \"roomId\","
                + "\"content\": \"You exit through door xyz... \"}";

        RoutedMessage origMessage = new RoutedMessage(msgTxt);
        System.out.println(origMessage);

        new Expectations() {{
            firstRoom.getType(); result = Type.FIRST_ROOM;

            nexus.transition(mediator, roomId);
        }};

        mediator.switchRooms(origMessage); // called on _a_ mediator by nexus

        new Verifications() {{
            nexus.transition(mediator, roomId); times = 1;
            drain.send((RoutedMessage) any); times = 0;
        }};
    }


    void assertSameBody(String expectedMessage, JsonObject body) {
        JsonReader jsonReader = Json.createReader(new StringReader(expectedMessage));
        JsonObject jsonData = jsonReader.readObject();

        Assert.assertEquals(jsonData, body);
    }

}
