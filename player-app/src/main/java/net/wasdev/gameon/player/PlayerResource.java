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
package net.wasdev.gameon.player;

import java.io.IOException;

import javax.annotation.Resource;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Providers;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;

/**
 * The Player service, where players remember where they are, and what they have
 * in their pockets.
 *
 */
@Path("/{id}")
public class PlayerResource {
	@Context Providers ps;

	@Resource(name = "mongo/playerDB")
	protected DB playerDB;

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Player getPlayerInformation(@PathParam("id") String id) throws IOException {
		DBObject player = findPlayer(null, id);
		Player p = Player.fromDBObject(ps, player);
		return p;
	}

	@PUT
	public Response updatePlayer(@PathParam("id") String id, Player newPlayer) throws IOException {
		DBCollection players = playerDB.getCollection("players");
		DBObject player = findPlayer(players, id);
		DBObject nPlayer = newPlayer.toDBObject(ps);

		players.update(player, nPlayer);
		return Response.status(204).build();
	}

	@DELETE
	public Response removePlayer(@PathParam("id") String id) {
		DBCollection players = playerDB.getCollection("players");
		DBObject player = findPlayer(players, id);

		players.remove(player);
		return Response.status(200).build();
	}

	@PUT
	@Path("/location")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response updatePlayerLocation(@PathParam("id") String id, JsonObject update) throws IOException {
		DBCollection players = playerDB.getCollection("players");
		DBObject player = findPlayer(players, id);
		Player p = Player.fromDBObject(ps, player);

		String oldLocation = update.getString("old");
		String newLocation = update.getString("new");
		String currentLocation = p.getLocation();

		// try setting to the new location
		int rc;
		JsonObjectBuilder result = Json.createObjectBuilder();

		if ( currentLocation.equals(oldLocation)) {
			p.setLocation(newLocation);
			try {
				players.update(player, p.toDBObject(ps));
				rc = 200;
				result.add("location", newLocation);
			} catch (IOException e) {
				rc = 500;
				result.add("location", currentLocation);
			}
		} else {
			rc = 409;
			result.add("location", currentLocation);
		}

		return Response.status(rc).entity(result.build()).build();
	}

	private DBObject findPlayer(DBCollection players, String id) {
		if ( players == null ) {
			players = playerDB.getCollection("players");
		}
		DBObject query = new BasicDBObject("id",id);
		DBCursor cursor = players.find(query);
		if(!cursor.hasNext()){
			//will be mapped to 404 by the PlayerExceptionMapper
			throw new PlayerNotFoundException("user id not found : "+id);
		}
		return cursor.one();
	}
}
