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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.StatusType;

import org.gameontext.mediator.Log;
import org.gameontext.mediator.MapClient;
import org.gameontext.mediator.models.Site;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import mockit.Expectations;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import mockit.integration.junit4.JMockit;

@RunWith(JMockit.class)
public class MapClientTest {

    @Mocked WebTarget target;
    @Mocked Builder builder;
    @Mocked Response response;
    @Mocked StatusType statusInfo;
    MapClient mapClient = new MapClient();

    @Before
    public void setup() {

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

        new Expectations() {{
            target.request(new String[] {MediaType.APPLICATION_JSON}); result = builder;
            builder.accept(MediaType.APPLICATION_JSON); result = builder;
            builder.get(); result = response;
            response.getStatusInfo(); result = statusInfo;
        }};
    }

    @Test
    public void test204() {

        new Expectations() {{
            statusInfo.getStatusCode(); result = 204;
        }};

        List<Site> sites = mapClient.getSites(target);
        assertNotNull("A 204 should not return a null object", sites);
        assertTrue("No sites should be returned", sites.isEmpty());
    }

    @Test
    public void test200WithNoSites() {
        List<Site> returnedSiteList = new ArrayList<Site>();

        new Expectations() {{
            statusInfo.getStatusCode(); result = 200;
            response.readEntity(new GenericType<List<Site>>() {}); result = returnedSiteList;
        }};

        List<Site> sites = mapClient.getSites(target);
        assertNotNull("A 200 should not return a null object", sites);
        assertTrue("No sites should be returned", sites.isEmpty());
    }

    @Test
    public void test200WithOneSite() {
        List<Site> returnedSiteList = new ArrayList<Site>();
        Site site1 = new Site();
        returnedSiteList.add(site1);

        new Expectations() {{
            statusInfo.getStatusCode(); result = 200;
            response.readEntity(new GenericType<List<Site>>() {}); result = returnedSiteList;
        }};

        List<Site> sites = mapClient.getSites(target);
        assertNotNull("A 200 should not return a null object", sites);
        assertEquals("No sites should be returned", 1, sites.size());
    }

    @Test
    public void test200WithNullSitesObject() {

        MapClient mapClient = new MapClient();
        new Expectations() {{
            statusInfo.getStatusCode(); result = 200;
            response.readEntity(new GenericType<List<Site>>() {}); result = null;
        }};

        List<Site> sites = mapClient.getSites(target);
        assertNotNull("A 200 should not return a null object", sites);
        assertTrue("No sites should be returned", sites.isEmpty());
    }
}
