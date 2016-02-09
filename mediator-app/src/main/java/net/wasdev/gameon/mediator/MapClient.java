/*******************************************************************************
 * Copyright (c) 2015 IBM Corp.
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

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.ResponseProcessingException;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;

import net.wasdev.gameon.mediator.models.Exit;
import net.wasdev.gameon.mediator.models.Exits;
import net.wasdev.gameon.mediator.models.Site;

/**
 * A wrapped/encapsulation of outbound REST requests to the map service.
 * <p>
 * The URL for the map service and the API key are injected via CDI: {@code
 * <jndiEntry />} elements defined in server.xml maps the environment variables
 * to JNDI values.
 * </p>
 * <p>
 * CDI will create this (the {@code MapClient} as an application scoped bean.
 * This bean will be created when the application starts, and can be injected
 * into other CDI-managed beans for as long as the application is valid.
 * </p>
 *
 * @see ApplicationScoped
 */
@ApplicationScoped
public class MapClient {

    /**
     * The map URL injected from JNDI via CDI.
     *
     * @see {@code mapUrl} in
     *      {@code /mediator-wlpcfg/servers/gameon-mediator/server.xml}
     */
    @Resource(lookup = "mapUrl")
    String mapLocation;

    /**
     * The concierge API key injected from JNDI via CDI.
     *
     * @see {@code conciergeQueryApiKey} in
     *      {@code /mediator-wlpcfg/servers/gameon-mediator/server.xml}
     */
    @Resource(lookup = "mapApiKey")
    String querySecret;

    /**
     * The root target used to define the root path and common query parameters
     * for all outbound requests to the concierge service.
     *
     * @see WebTarget
     */
    private WebTarget root;

    /** Last check for the mediator-owned/created First Room exits */
    long lastCheck;

    /** Cached exits for first room */
    Exits firstRoomExits;

    /**
     * The {@code @PostConstruct} annotation indicates that this method should
     * be called immediately after the {@code ConciergeClient} is instantiated
     * with the default no-argument constructor.
     *
     * @see PostConstruct
     * @see ApplicationScoped
     */
    @PostConstruct
    public void initClient() {
        Client client = ClientBuilder.newClient().register(JacksonJsonProvider.class);

        // create the jax-rs 2.0 client
        this.root = client.target(mapLocation);

        // create the apikey filter for the lookup requests.
        ApiKeyFilter apikey = new ApiKeyFilter("mapQuery", querySecret);

        // register the API key filter that will ensure the api key is invoked
        // for each outbound request.
        this.root.register(apikey);

        Log.log(Level.FINER, this, "Map client initialized with url {0}", mapLocation);
    }

    public List<Site> getRoomsByOwner(String ownerId) {
        WebTarget target = this.root.queryParam("owner", ownerId);
        return getSites(target);
    }

    /**
     * Construct an outbound {@code WebTarget} that builds on the root
     * {@code WebTarget#path(String)} to add the path segment required to
     * request a starting room ({@code startingRoom}).
     *
     * @return The list of available endpoints returned from the concierge. This
     *         may be null if the list could not be retrieved.
     * @see #getRoomList(WebTarget)
     */
    public Site getSite() {
        WebTarget target = this.root.path(Constants.FIRST_ROOM);
        return getSite(target);
    }

    /**
     * Construct an outbound {@code WebTarget} that builds on the root
     * {@code WebTarget#path(String)} to add the path segment required to
     * request the exits available for a given room (<code>rooms/{roomId}</code>
     * ).
     *
     * @param roomId
     *            The specific room to find exits for
     *
     * @return The list of available endpoints returned from the concierge. This
     *         may be null if the list could not be retrieved.
     *
     * @see #getRoomList(WebTarget)
     * @see WebTarget#resolveTemplate(String, Object)
     */
    public Site getSite(String roomId) {
        WebTarget target = this.root.path("{roomId}").resolveTemplate("roomId", roomId);
        return getSite(target);
    }

    /**
     * Invoke the provided {@code WebTarget}, and resolve/parse the result into
     * a {@code Site} that the caller can use to create a new connection to the
     * target room.
     *
     * @param target
     *            {@code WebTarget} that includes the required parameters to
     *            retrieve information about available or specified exits. All
     *            of the REST requests that find or work with exits return the
     *            same result structure
     * @return A populated {@code RoomEndpointList}, or null if the request
     *         failed.
     */
    protected List<Site> getSites(WebTarget target) {
        Log.log(Level.FINER, this, "making request to {0} for room", target.getUri().toString());
        Response r = null;
        try {
            r = target.request(MediaType.APPLICATION_JSON).get(); // .accept(MediaType.APPLICATION_JSON).get();
            if (r.getStatusInfo().getFamily().equals(Response.Status.Family.SUCCESSFUL)) {
                List<Site> list = r.readEntity(new GenericType<List<Site>>() {
                });
                return list;
            }

            // Sadly, no endpoints found!
            return null;
        } catch (ResponseProcessingException rpe) {
            Response response = rpe.getResponse();
            Log.log(Level.FINER, this, "Exception fetching room list uri: {0} resp code: {1} data: {2}",
                    target.getUri().toString(),
                    response.getStatusInfo().getStatusCode() + " " + response.getStatusInfo().getReasonPhrase(),
                    response.readEntity(String.class));
            Log.log(Level.FINEST, this, "Exception fetching room list", rpe);

            System.out.println("ResponseProcessingException " + rpe.getMessage());
            rpe.printStackTrace();
            System.out.println("Response toString " + rpe.getResponse().toString());
            System.out.println("Response as String " + rpe.getResponse().readEntity(String.class));
        } catch (ProcessingException e) {
            System.out.println("ResponseProcessingException " + e.getMessage());
            e.printStackTrace();
            System.out.println("Response toString " + r.toString());
            System.out.println("Response as String " + r.readEntity(String.class));
        } catch (WebApplicationException ex) {
            Log.log(Level.FINEST, this, "Exception fetching room list (" + target.getUri().toString() + ")", ex);
            System.out.println("WebApplicationException " + ex.getMessage());
        }
        // Sadly, badness happened while trying to get the endpoints
        return null;
    }

    /**
     * Invoke the provided {@code WebTarget}, and resolve/parse the result into
     * a {@code RoomEndpointList} that the caller can use to create a new
     * connection to the target room.
     *
     * @param target
     *            {@code WebTarget} that includes the requred parameters to
     *            retrieve information about available or specified exits. All
     *            of the REST requests that find or work with exits return the
     *            same result structure
     * @return A populated {@code RoomEndpointList}, or null if the request
     *         failed.
     */
    protected Site getSite(WebTarget target) {
        Log.log(Level.FINER, this, "making request to {0} for room", target.getUri().toString());
        Response r = null;
        try {
            r = target.request(MediaType.APPLICATION_JSON).get(); // .accept(MediaType.APPLICATION_JSON).get();
            if (r.getStatusInfo().getFamily().equals(Response.Status.Family.SUCCESSFUL)) {
                Site site = r.readEntity(Site.class);
                return site;
            }
            // Sadly, no endpoints found!
            return null;
        } catch (ResponseProcessingException rpe) {
            Response response = rpe.getResponse();
            Log.log(Level.FINER, this, "Exception fetching room list uri: {0} resp code: {1} data: {2}",
                    target.getUri().toString(),
                    response.getStatusInfo().getStatusCode() + " " + response.getStatusInfo().getReasonPhrase(),
                    response.readEntity(String.class));
            Log.log(Level.FINEST, this, "Exception fetching room list", rpe);

            System.out.println("ResponseProcessingException " + rpe.getMessage());
            rpe.printStackTrace();
            System.out.println("Response toString " + rpe.getResponse().toString());
            System.out.println("Response as String " + rpe.getResponse().readEntity(String.class));
        } catch (ProcessingException e) {
            System.out.println("ResponseProcessingException " + e.getMessage());
            e.printStackTrace();
            System.out.println("Response toString " + r.toString());
            System.out.println("Response as String " + r.readEntity(String.class));
        } catch (WebApplicationException ex) {
            Log.log(Level.FINEST, this, "Exception fetching room list (" + target.getUri().toString() + ")", ex);
            System.out.println("WebApplicationException " + ex.getMessage());
        }
        // Sadly, badness happened while trying to get the endpoints
        return null;
    }

    /**
     * First Room lives in the mediator. We need to either regularly fetch, or have
     * this list of exits updated. First room changes per player (to determine
     * what messages to show), but exit checking does not need to be per player.
     *
     * @return Most current exits for the first room
     */
    public Exits getFirstRoomExits() {
        long now = System.nanoTime();
        if ( lastCheck == 0 || now - lastCheck > TimeUnit.SECONDS.toNanos(30) ) {
            try {
                Site firstRoom = getSite();
                firstRoomExits = firstRoom.getExits();
                lastCheck = now;
            } catch(Exception e) {
                Log.log(Level.WARNING, this, "Unable to retrieve exits for first room, will continue with old values", e);
            }
        }

        return firstRoomExits;
    }
}
