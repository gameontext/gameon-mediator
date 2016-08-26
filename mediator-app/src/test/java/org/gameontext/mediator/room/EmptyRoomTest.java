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

import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import org.gameontext.mediator.Constants;
import org.gameontext.mediator.MapClient;
import org.gameontext.mediator.MediatorNexus;
import org.gameontext.mediator.models.Site;
import org.gameontext.mediator.room.EmptyRoom;
import org.gameontext.mediator.room.RoomUtils;
import org.gameontext.mediator.room.RoomMediator.Type;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import mockit.Expectations;
import mockit.Mocked;
import mockit.Verifications;
import mockit.integration.junit4.JMockit;

@RunWith(JMockit.class)
public class EmptyRoomTest {

    @Test
    public void testBasics(@Mocked MediatorNexus.View nexus,
                           @Mocked MapClient mapClient,
                           @Mocked Site site,
                           @Mocked JsonObjectBuilder builder) {

        new Expectations() {{
            site.getInfo(); returns(null);
         }};

        EmptyRoom emptyRoom = new EmptyRoom(mapClient, site, null, nexus);

        Assert.assertEquals(Type.EMPTY, emptyRoom.getType());
        Assert.assertEquals(EmptyRoom.EMPTY_ROOMNAME, emptyRoom.getName());
        Assert.assertEquals(EmptyRoom.EMPTY_FULLNAME, emptyRoom.getFullName());
        Assert.assertTrue("room info should be the same when null: " + emptyRoom, emptyRoom.sameConnectionDetails(null));

        emptyRoom.buildLocationResponse(builder);

        new Verifications() {{
            builder.add(RoomUtils.TYPE, RoomUtils.LOCATION);  times = 1;
            builder.add(Constants.KEY_ROOM_NAME, EmptyRoom.EMPTY_ROOMNAME); times = 1;
            builder.add(Constants.KEY_ROOM_FULLNAME, EmptyRoom.EMPTY_FULLNAME); times = 1;
            builder.add(Constants.KEY_ROOM_EXITS, (JsonObject) any); times = 1;
            builder.add(RoomUtils.DESCRIPTION, anyString); times = 1;
        }};
    }
}
