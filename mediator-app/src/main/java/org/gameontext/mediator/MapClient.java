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
package org.gameontext.mediator;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
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

import org.gameontext.mediator.models.Site;
import org.gameontext.signed.SignedClientRequestFilter;

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
     * The map API key injected from JNDI via CDI.
     *
     * @see {@code mapApiKey} in
     *      {@code /mediator-wlpcfg/servers/gameon-mediator/server.xml}
     */
    @Resource(lookup = "mapApiKey")
    String querySecret;

    /**
     * The system id, that we use when making our map queries.
     *
     * @see {@code systemId} in
     *      {@code /mediator-wlpcfg/servers/gameon-mediator/server.xml}
     */
    @Resource(lookup = "systemId")
    String SYSTEM_ID;

    /**
     * The root target used to define the root path and common query parameters
     * for all outbound requests to the concierge service.
     *
     * @see WebTarget
     */
    private WebTarget queryRoot;

    /** Cache of retrieved room exits */
    private ConcurrentHashMap<String, SiteCache> roomCache = new ConcurrentHashMap<>();

    /**
     * The {@code @PostConstruct} annotation indicates that this method should
     * be called immediately after the {@code MapClient} is instantiated
     * with the default no-argument constructor.
     *
     * @see PostConstruct
     * @see ApplicationScoped
     */
    @PostConstruct
    public void initClient() {
        if ( mapLocation == null ) {
            Log.log(Level.SEVERE, this, "Map client can not be initialized, 'mapUrl' is not defined");
            throw new IllegalStateException("Unable to initialize MapClient");
        }

        if ( querySecret == null ) {
            Log.log(Level.SEVERE, this, "Map client can not be initialized, 'mapApiKey' is not defined");
            throw new IllegalStateException("Unable to initialize MapClient");
        }

        Client queryClient = ClientBuilder.newBuilder()
                                          .property("com.ibm.ws.jaxrs.client.ssl.config", "DefaultSSLSettings")
                                          .property("com.ibm.ws.jaxrs.client.disableCNCheck", true)
                                          .build();

        queryClient.register(JsonProvider.class);

        //add our shared secret so all our queries come from the system id
        queryClient.register(new SignedClientRequestFilter(SYSTEM_ID, querySecret));

        // create the jax-rs 2.0 client
        this.queryRoot = queryClient.target(mapLocation);

        Log.log(Level.FINER, this, "Map client initialized with url {0}, system-id {1}", mapLocation, SYSTEM_ID);
    }

    public List<Site> getSystemRooms() {
        WebTarget target = this.queryRoot.queryParam("owner", SYSTEM_ID);
        return getSites(target);
    }

    public List<Site> getRoomsByOwner(String ownerId) {
        WebTarget target = this.queryRoot.queryParam("owner", ownerId);
        return getSites(target);
    }

    public List<Site> getRoomsByRoomName(String name) {
        WebTarget target = this.queryRoot.queryParam("name", name);
        return getSites(target);
    }

    public List<Site> getRoomsByOwnerAndRoomName(String ownerId,String roomName) {
        WebTarget target = this.queryRoot.queryParam("owner", ownerId).queryParam("name",roomName);
        return getSites(target);
    }

    /**
     * Construct an outbound {@code WebTarget} that builds on the root
     * {@code WebTarget#path(String)} to add the path segment required to
     * request the first room.
     *
     * @return The Site representing first room from the Map Service, or null if it could not be retrieved.
     * @see #getRoomList(WebTarget)
     */
    public Site getSite() {
        return getSite(Constants.FIRST_ROOM);
    }

    /**
     * Construct an outbound {@code WebTarget} that builds on the root
     * {@code WebTarget#path(String)} to add the path segment required to
     * request the Site for a given room (<code>{roomId}</code>).
     *
     * @param roomId
     *            The specific room to find the site for
     *
     * @return The Site returned from the map service. This
     *         may be null if the site could not be retrieved.
     *
     * @see #getRoomList(WebTarget)
     * @see WebTarget#resolveTemplate(String, Object)
     */
    public Site getSite(String roomId) {
        SiteCache sc = roomCache.get(roomId);
        if ( sc == null ) {
            sc = new SiteCache();
        }

        long now = System.nanoTime();
        if ( sc.site == null || sc.refresh(now) ) {
            WebTarget target = this.queryRoot.path(roomId);
            Site ns = getSite(roomId, target);
            if ( ns != null ) {
                sc.update(ns);
                roomCache.put(roomId, sc);
                return ns;
            }
        }

        return sc.site;
    }

    /**
     * Construct an outbound {@code WebTarget} that builds on the root
     * {@code WebTarget#path(String)} to add the path segment required to
     * request the deletion of a given room (<code>{roomId}</code>
     * ).
     *
     * @param roomId
     *            The specific room to delete
     * @param secret
     * @param userid
     *
     * @return The list of available endpoints returned from the concierge. This
     *         may be null if the list could not be retrieved.
     *
     * @see #getRoomList(WebTarget)
     * @see WebTarget#resolveTemplate(String, Object)
     */
    public boolean deleteSite(String roomId, String userid, String secret) {
        Log.log(Level.FINER, this, "Asked to delete room id {0} for user {1} with secret(first2chars) {2}",roomId,userid,secret.substring(0,2));

        Client client = ClientBuilder.newClient().register(JsonProvider.class);

        // use the player's shared secret for this operation, not ours
        SignedClientRequestFilter apikey = new SignedClientRequestFilter(userid, secret);
        client.register(apikey);

        WebTarget target = client.target(mapLocation).path(roomId);

        Log.log(Level.FINER, this, "making request to {0} for room", target.getUri().toString());
        Response r = null;
        try {
            r = target.request().delete(); //
            if (r.getStatus() == 204) {
                Log.log(Level.FINER, this, "delete reported success (204)", target.getUri().toString());
                return true;
            }
            Log.log(Level.FINER, this, "delete failed reason:{0} entity:{1}", r.getStatusInfo().getReasonPhrase(),r.readEntity(String.class));

            //delete failed.
            return false;
        } catch (ResponseProcessingException rpe) {
            Response response = rpe.getResponse();
            Log.log(Level.SEVERE, this, "Exception deleting room uri: {0} resp code: {1} ",
                    target.getUri().toString(),
                    response.getStatusInfo().getStatusCode() + " " + response.getStatusInfo().getReasonPhrase());
            Log.log(Level.SEVERE, this, "Exception deleting room ", rpe);
        } catch (ProcessingException e) {
            Log.log(Level.SEVERE, this, "Exception deleting room ", e);
        } catch (WebApplicationException ex) {
            Log.log(Level.SEVERE, this, "Exception deleting room ", ex);
        }
        // Sadly, badness happened while trying to do the delete
        return false;
    }

    /**
     *
     * @param target
     *            {@code WebTarget} that includes the required parameters to
     *            retrieve information about available or specified exits. All
     *            of the REST requests that find or work with exits return the
     *            same result structure
     * @return A populated {@code List<Site>}. Never null
     */
    protected List<Site> getSites(WebTarget target) {
        Log.log(Level.FINER, this, "making request to {0} for room", target.getUri().toString());
        Response r = null;
        try {
            r = target.request(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).get();
            int statusCode = r.getStatusInfo().getStatusCode();
            if (statusCode == Response.Status.OK.getStatusCode() ) {
                List<Site> list = r.readEntity(new GenericType<List<Site>>() {
                });
                if (list == null) {
                    Log.log(Level.FINER, this, "Could not find rooms in the repsonse from uri: {0}",
                            target.getUri().toString());
                    return Collections.emptyList();
                }
                return list;
            } else if (statusCode == Response.Status.NO_CONTENT.getStatusCode()) {
                // If there was no content returned but there is no error, then we don't want to return a null
                return Collections.emptyList();
            }

            // The return code indicates something went wrong, but it wasn't bad enough to cause an exception
            return Collections.emptyList();
        } catch (ResponseProcessingException rpe) {
            Response response = rpe.getResponse();
            Log.log(Level.FINER, this, "Exception fetching room list uri: {0} resp code: {1} ",
                    target.getUri().toString(),
                    response.getStatusInfo().getStatusCode() + " " + response.getStatusInfo().getReasonPhrase());
            Log.log(Level.FINEST, this, "Exception fetching room list", rpe);
        } catch (ProcessingException e) {
            Log.log(Level.FINEST, this, "Exception fetching room list (" + target.getUri().toString() + ")", e);
        } catch (WebApplicationException ex) {
            Log.log(Level.FINEST, this, "Exception fetching room list (" + target.getUri().toString() + ")", ex);
        }

        // Sadly, badness happened while trying to get the endpoints
        return Collections.emptyList();
    }

    /**
     * Invoke the provided {@code WebTarget}, and resolve/parse the result into
     * a {@code Site} that the caller can use to create a new
     * connection to the target room.
     *
     * @param target
     *            {@code WebTarget} that includes the required parameters to
     *            retrieve information about available or specified exits. All
     *            of the REST requests that find or work with exits return the
     *            same result structure
     * @return A populated {@code Site}, or null if the request
     *         failed.
     */
    protected Site getSite(String roomId, WebTarget target) {
        Log.log(Level.FINER, this, "making request to {0} for room", target.getUri().toString());
        Response r = null;
        try {
            r = target.request(MediaType.APPLICATION_JSON).get(); // .accept(MediaType.APPLICATION_JSON).get();
            if (r.getStatusInfo().getFamily().equals(Response.Status.Family.SUCCESSFUL)) {
                Site site = r.readEntity(Site.class);
                return site;
            }
            if ( r.getStatus() == 404 ) {
                // The room doesn't exist anymore.
                roomCache.remove(roomId);
            }

            return null;
        } catch (ResponseProcessingException rpe) {
            Response response = rpe.getResponse();
            Log.log(Level.FINER, this, "Exception fetching room list uri: {0} resp code: {1} ",
                    target.getUri().toString(),
                    response.getStatusInfo().getStatusCode() + " " + response.getStatusInfo().getReasonPhrase());
            Log.log(Level.FINEST, this, "Exception fetching room list", rpe);
        } catch (ProcessingException e) {
            Log.log(Level.FINEST, this, "Exception fetching room list (" + target.getUri().toString() + ")", e);
        } catch (WebApplicationException ex) {
            Log.log(Level.FINEST, this, "Exception fetching room list (" + target.getUri().toString() + ")", ex);
        }
        // Sadly, badness happened while trying to get the endpoints
        return null;
    }

    /**
     * TODO: Contents of this cache can be maintained via push
     * when room events start arriving: that could change our timing,
     * or how/when the entries expire such that we re-lookup.
     *
     */
    static class SiteCache {
        /** Last check of the assigned exits for the room */
        long lastCheck = 0;

        /** Cached site */
        Site site = null;

        public boolean refresh(long now) {
            return ( now - lastCheck > TimeUnit.SECONDS.toNanos(5) );
        }

        public void update(Site ns) {
            lastCheck = System.nanoTime();
            site = ns;
        }
    }
}
