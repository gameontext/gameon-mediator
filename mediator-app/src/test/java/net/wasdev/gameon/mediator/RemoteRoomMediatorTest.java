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

import static mockit.Deencapsulation.setField;
import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;

import mockit.Expectations;
import mockit.Mocked;
import mockit.integration.junit4.JMockit;
import net.wasdev.gameon.mediator.models.Exit;
import net.wasdev.gameon.mediator.models.Exits;
import net.wasdev.gameon.mediator.models.Site;

@RunWith(JMockit.class)
public class RemoteRoomMediatorTest {

    @Test
    public void testGetExits(@Mocked Exit exit,
            @Mocked MapClient mapClient,
            @Mocked ConnectionUtils connectionUtils,
            @Mocked Site site,
            @Mocked Exits exits){

        new Expectations() {{
            exit.getId(); returns("FISH");
            exit.getConnectionDetails();
            exit.getName();
            exit.getFullName();
            mapClient.getSite("FISH"); returns(site);
            site.getExits(); returns(exits);

        }};

        RemoteRoomMediator r = new RemoteRoomMediator(exit, mapClient, connectionUtils);
        setField(r,"lastCheck",0);
        Exits e = r.getExits();

        assertEquals("Exits object as expected",e,exits);
    }
}
