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

import java.util.concurrent.ScheduledExecutorService;

import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import mockit.Expectations;
import mockit.Mocked;
import mockit.Verifications;
import mockit.integration.junit4.JMockit;
import net.wasdev.gameon.mediator.Constants;
import net.wasdev.gameon.mediator.MapClient;
import net.wasdev.gameon.mediator.MediatorNexus;
import net.wasdev.gameon.mediator.models.RoomInfo;
import net.wasdev.gameon.mediator.models.Site;
import net.wasdev.gameon.mediator.room.RoomMediator.Type;

@RunWith(JMockit.class)
public class SickRoomTest {

    static final String name = "test";
    static final String fullName = "Full Test";
    static final String description = "Room description";

    @Test
    public void testBasics(@Mocked MediatorNexus.View nexus,
                           @Mocked MapClient mapClient,
                           @Mocked Site site,
                           @Mocked RoomInfo info,
                           @Mocked RemoteRoomProxy proxy,
                           @Mocked ScheduledExecutorService exec,
                           @Mocked JsonObjectBuilder builder) {


        new Expectations() {{
            site.getInfo(); returns(info);
            info.getName(); returns(name);
            info.getFullName(); returns(fullName);
         }};

         SickRoom sickRoom = new SickRoom(proxy, mapClient, exec, site, null, nexus);

        Assert.assertEquals(Type.SICK, sickRoom.getType());
        Assert.assertEquals(name, sickRoom.getName());
        Assert.assertEquals(fullName, sickRoom.getFullName());
        Assert.assertFalse("room info should not be the same when null", sickRoom.sameConnectionDetails(null));

        Assert.assertNotEquals("Sick room should provide its own description",
                description, sickRoom.getDescription());

        sickRoom.buildLocationResponse(builder);

        new Verifications() {{
            info.getDescription(); times = 0;
            builder.add(RoomUtils.TYPE, RoomUtils.LOCATION);  times = 1;
            builder.add(Constants.KEY_ROOM_NAME, name); times = 1;
            builder.add(Constants.KEY_ROOM_FULLNAME, fullName); times = 1;
            builder.add(Constants.KEY_ROOM_EXITS, (JsonObject) any); times = 1;
            builder.add(RoomUtils.DESCRIPTION, anyString); times = 1;
        }};
    }

}
