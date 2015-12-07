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

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.enterprise.context.ApplicationScoped;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.ResponseProcessingException;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * A wrapped/encapsulation of outbound REST requests to the concierge.
 * <p>
 * The URL for the concierge service and the API key are injected via CDI:
 * {@code <jndiEntry />} elements defined in server.xml maps the
 * environment variables to JNDI values.
 * </p><p>
 * CDI will create this (the {@code ConciergeClient} as an application
 * scoped bean. This bean will be created when the application starts,
 * and can  be injected into other CDI-managed beans for as long as
 * the application is valid.
 * </p>
 *
 * @see ApplicationScoped
 */
@ApplicationScoped
public class ConciergeClient {

    /** The concierge URL injected from JNDI via CDI.
     * @see {@code conciergeUrl} in {@code /mediator-wlpcfg/servers/gameon-mediator/server.xml}
     */
    @Resource(lookup = "conciergeUrl")
    String conciergeLocation;

    /** The concierge API key injected from JNDI via CDI.
     * @see {@code conciergeQueryApiKey} in {@code /mediator-wlpcfg/servers/gameon-mediator/server.xml}
     */
    @Resource(lookup = "conciergeQueryApiKey")
    String querySecret;

    /**
     * The root target used to define the root path and common query parameters
     * for all outbound requests to the concierge service.
     *
     * @see WebTarget
     */
    private WebTarget root;

    /**
     * The {@code @PostConstruct} annotation indicates that this method
     * should be called immediately after the {@code ConciergeClient} is
     * instantiated with the default no-argument constructor.
     *
     * @see PostConstruct
     * @see ApplicationScoped
     */
    @PostConstruct
    public void initClient() {
        Client client = ClientBuilder.newClient();

        // create the jax-rs 2.0 client
        this.root = client.target(conciergeLocation);

        // create the apikey filter for the lookup requests.
        ApiKeyFilter apikey = new ApiKeyFilter("roomQuery", "MyQuerySecret");

        // register the API key filter that will ensure the api key is invoked
        // for each outbound request.
        this.root.register(apikey);

        Log.log(Level.FINER, this, "Concierge initialized with {0}", conciergeLocation);
    }

    /**
     * Query the concierge: given the current room and the selected exit, where
     * do we go?
     *
     * @param currentRoom
     *            Current room mediator
     * @param exit
     *            A string indicating the selected exit, e.g. {@code N}. This
     *            parameter may be null (for the initial starting room)
     * @return new Room Id
     */
    public RoomEndpointList findNextRoom(RoomMediator currentRoom, String exit) {
        RoomEndpointList roomEndpoints = null;

        if (exit == null) {
            // SOS or First Room: randomly grab a new room (start over with
            // starting rooms)
            roomEndpoints = getRoomEndpoints();
        } else {
            roomEndpoints = getRoomEndpoints(currentRoom.getId(), exit);
        }

        return roomEndpoints;
    }

    /**
     * Construct an outbound {@code WebTarget} that builds on the
     * root {@code WebTarget#path(String)} to add the path segment
     * required to request a starting room ({@code startingRoom}).
     *
     * @return The list of available endpoints returned from the concierge.
     *   This may be null if the list could not be retrieved.
     * @see #getRoomList(WebTarget)
     */
    public RoomEndpointList getRoomEndpoints() {
        WebTarget target = this.root.path("startingRoom");

        return getRoomList(target);
    }

    /**
     * Construct an outbound {@code WebTarget} that builds on the
     * root {@code WebTarget#path(String)} to add the path segment
     * required to request the exits available for a given room (<code>rooms/{roomId}</code>).
     *
     * @param roomId
     *          The specific room to find exits for
     *
     * @return The list of available endpoints returned from the concierge.
     *   This may be null if the list could not be retrieved.
     *
     * @see #getRoomList(WebTarget)
     * @see WebTarget#resolveTemplate(String, Object)
     */
    public RoomEndpointList getRoomEndpoints(String roomId) {
        WebTarget target = this.root.path("rooms/{roomId}").resolveTemplate("roomId", roomId);

        return getRoomList(target);
    }

    /**
     * Construct an outbound {@code WebTarget} that builds on the
     * root {@code WebTarget#path(String)} to add the path segment
     * required to request the endpoints available for a
     * specific exit from the given room <code>rooms/{roomId}/{exit}</code>).
     *
     * @param roomId
     *          The specific room to find exits for
     * @param exit
     *          The specific exit to find
     *
     * @return The list of available endpoints (URLs) returned from the concierge.
     *   This may be null if the list could not be retrieved.
     *
     * @see #getRoomList(WebTarget)
     * @see WebTarget#path(String)
     * @see WebTarget#resolveTemplate(String, Object)
     */
    public RoomEndpointList getRoomEndpoints(String roomId, String exit) {
        WebTarget target = this.root.path("rooms/{roomId}/{exit}").resolveTemplate("roomId", roomId)
                .resolveTemplate("exit", exit);
        return getRoomList(target);
    }

    /**
     * Invoke the provided {@code WebTarget}, and resolve/parse the result
     * into a {@code RoomEndpointList} that the caller can use to create a new
     * connection to the target room.
     *
     * @param target
     *          {@code WebTarget} that includes the requred parameters
     *          to retrieve information about available or specified exits.
     *          All of the REST requests that find or work with exits return
     *          the same result structure
     * @return A populated {@code RoomEndpointList}, or null if the request failed.
     */
    protected RoomEndpointList getRoomList(WebTarget target) {
        Log.log(Level.FINER, this, "making request to {0} for room", target.getUri().toString());
        try {
            // pojo magic binding wasn't working to parse the result.
            // For some requests the reader for the object could not be found.
            // We'll do it the explicit/direct-JSON way until we figure out why.

            // TODO: debug why we can't use pojo bindings for room change
            // requests..

            // Make GET request using the specified target (see methods above)
            String resultStr = target.request(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                    .header("Content-type", "application/json").get(String.class);

            JsonReader reader = Json.createReader(new StringReader(resultStr));
            JsonObject result = reader.readObject();

            // RoomEndpointList
            JsonObject rel = (JsonObject) result.get("rel");

            // create the list, and populate with the result
            if (rel != null) {
                RoomEndpointList res = new RoomEndpointList();

                JsonString id = rel.getJsonString("roomId");
                res.setRoomId(id.getString());

                JsonArray exits = rel.getJsonArray("endpoints");
                ArrayList<String> strexits = new ArrayList<String>();
                if (exits != null) {
                    for (JsonValue e : exits) {
                        JsonString s = (JsonString) e;
                        strexits.add(s.getString());
                    }
                }
                res.setEndpoints(strexits);

                return res;
            } else {
                Log.log(Level.FINER, this, "Room list was lacking any rel element uri: {0} result: {1}",
                        target.getUri().toString(), resultStr);
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
        } catch (ProcessingException | WebApplicationException ex) {
            Log.log(Level.FINEST, this, "Exception fetching room list (" + target.getUri().toString() + ")", ex);
        }

        // Sadly, badness happened while trying to get the endpoints
        return null;
    }

    /**
     * DTO for the room endpoint list
     *
     */
    public static class RoomEndpointList {
        String roomId;
        List<String> endpoints;

        public RoomEndpointList() {
        }

        /**
         * @return the roomId
         */
        public String getRoomId() {
            return roomId;
        }

        /**
         * @param roomId
         *            the roomId to set
         */
        public void setRoomId(String roomId) {
            this.roomId = roomId;
        }

        /**
         * @return the endpoints
         */
        public List<String> getEndpoints() {
            return endpoints;
        }

        /**
         * @param endpoints
         *            the endpoints to set
         */
        public void setEndpoints(List<String> endpoints) {
            this.endpoints = endpoints;
        }

        /**
         * @return
         */
        public String getRoomName() {
            return roomId;
        }
    }
}
