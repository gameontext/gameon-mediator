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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;

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
     * Query the map: given the current room and the selected exit, where do we
     * go?
     *
     * @param currentRoom
     *            Current room mediator
     * @param exit
     *            A string indicating the selected exit, e.g. {@code N}. This
     *            parameter may be null (for the initial starting room)
     * @return new Room Id
     */
    public Exit findIdentifedExitForRoom(RoomMediator currentRoom, String exit) {
        Exit exitResult;
        System.out.println("Asked to get exit " + exit + " for room " + currentRoom.getName());
        if (exit == null) {
            // SOS or first room.. return exit with id for first room..
            exitResult = new Exit();
            exitResult.setId(Constants.FIRST_ROOM);
        } else {
            // so.. not great.. until we have room exit push, then we need to
            // a) re-retrieve our current room, to find out what it's wired to.
            // this part would normally be handled by the room itself, via push.
            //
            // b) obtain the exit we plan to use from that room, if there is one
            //
            System.out.println(
                    "Asking map service for Site for id:" + currentRoom.getId() + " name:" + currentRoom.getName());
            Site current = getSite(currentRoom.getId());
            Exits exits = current.getExits();
            switch (exit.toLowerCase()) {
                case "n": {
                    exitResult = exits.getN();
                    break;
                }
                case "s": {
                    exitResult = exits.getS();
                    break;
                }
                case "e": {
                    exitResult = exits.getE();
                    break;
                }
                case "w": {
                    exitResult = exits.getW();
                    break;
                }
                case "u": {
                    exitResult = exits.getU();
                    break;
                }
                case "d": {
                    exitResult = exits.getD();
                    break;
                }
                default: {
                    // unknown exit.. return null;
                    return null;
                }
            }
        }
        return exitResult;
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RoomInfo {
        String name;
        ConnectionDetails connectionDetails = null;
        String fullName;
        String description;
        Doors doors;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public ConnectionDetails getConnectionDetails() {
            return connectionDetails;
        }

        public void setConnectionDetails(ConnectionDetails connectionDetails) {
            this.connectionDetails = connectionDetails;
        }

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Doors getDoors() {
            return doors;
        }

        public void setDoors(Doors doors) {
            this.doors = doors;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Doors {
        String n;
        String s;
        String e;
        String w;
        String u;
        String d;

        public String getN() {
            return n;
        }

        public void setN(String n) {
            this.n = n;
        }

        public String getS() {
            return s;
        }

        public void setS(String s) {
            this.s = s;
        }

        public String getE() {
            return e;
        }

        public void setE(String e) {
            this.e = e;
        }

        public String getW() {
            return w;
        }

        public void setW(String w) {
            this.w = w;
        }

        public String getU() {
            return u;
        }

        public void setU(String u) {
            this.u = u;
        }

        public String getD() {
            return d;
        }

        public void setD(String d) {
            this.d = d;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(Include.NON_EMPTY)
    public static class ConnectionDetails {
        String type;
        String target;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getTarget() {
            return target;
        }

        public void setTarget(String target) {
            this.target = target;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Exit {
        @JsonProperty("_id")
        String id;
        String name;
        String fullName;
        String door = null;
        ConnectionDetails connectionDetails = null;

        @JsonProperty("_id")
        public String getId() {
            return id;
        }

        @JsonProperty("_id")
        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public String getDoor() {
            return door;
        }

        public void setDoor(String door) {
            this.door = door;
        }

        public ConnectionDetails getConnectionDetails() {
            return connectionDetails;
        }

        public void setConnectionDetails(ConnectionDetails connectionDetails) {
            this.connectionDetails = connectionDetails;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(Include.NON_EMPTY)
    public static class Exits {
        Exit n;
        Exit s;
        Exit e;
        Exit w;
        Exit u;
        Exit d;

        public Exit getN() {
            return n;
        }

        public void setN(Exit n) {
            this.n = n;
        }

        public Exit getS() {
            return s;
        }

        public void setS(Exit s) {
            this.s = s;
        }

        public Exit getE() {
            return e;
        }

        public void setE(Exit e) {
            this.e = e;
        }

        public Exit getW() {
            return w;
        }

        public void setW(Exit w) {
            this.w = w;
        }

        public Exit getU() {
            return u;
        }

        public void setU(Exit u) {
            this.u = u;
        }

        public Exit getD() {
            return d;
        }

        public void setD(Exit d) {
            this.d = d;
        }

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(Include.NON_EMPTY)
    public static class Site {
        RoomInfo info;
        Exits exits;
        String owner;
        @JsonProperty("_id")
        String id;
        String type;

        public RoomInfo getInfo() {
            return info;
        }

        public void setInfo(RoomInfo roomInfo) {
            this.info = roomInfo;
        }

        public Exits getExits() {
            return exits;
        }

        public void setExits(Exits exits) {
            this.exits = exits;
        }

        public String getOwner() {
            return owner;
        }

        public void setOwner(String owner) {
            this.owner = owner;
        }

        @JsonProperty("_id")
        public String getId() {
            return id;
        }

        @JsonProperty("_id")
        public void setId(String id) {
            this.id = id;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

    }
}
