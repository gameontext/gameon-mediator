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
package net.wasdev.gameon.player.ws;

import java.util.logging.Level;

import javax.annotation.PostConstruct;
import javax.json.Json;
import javax.json.JsonObject;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.ResponseProcessingException;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Client for the Player CRUD service to update the Player's location
 */
public class PlayerClient {

	Client client;
	WebTarget root;
	String playerLocation;

	@PostConstruct
	public void initClient() {
		try {
			this.playerLocation = (String) new InitialContext().lookup("playerUrl");
		} catch (NamingException e) {
		}
		this.client = ClientBuilder.newClient();
		this.root = this.client.target(playerLocation);

		Log.log(Level.FINER, this, "Player client initialized with {0}", playerLocation);
	}

	/**
	 * Update the player's location. The new location will always be returned from
	 * the service, in the face of a conflict between updates for the player across devices,
	 * we'll get back the one that won.
	 *
	 * @param playerId The player id
	 * @param oldRoomId The old room's id
	 * @param newRoomId The new room's id
	 * @return The id of the selected new room, taking contention into account.
	 */
	public String updatePlayerLocation(String playerId, String oldRoomId, String newRoomId) {
		WebTarget target = this.root.path("{playerId}/location")
				.resolveTemplate("playerId", playerId);

		JsonObject parameter = Json.createObjectBuilder()
				.add("old", oldRoomId)
				.add("new", newRoomId)
				.build();

		Log.log(Level.FINER, this, "updating location using {0}", target.getUri().toString());

		try {
			// Make PUT request using the specified target, get result as a string containing JSON
			JsonObject result = target.request(MediaType.APPLICATION_JSON)
					.accept(MediaType.APPLICATION_JSON)
					.header("Content-type", "application/json")
					.put(Entity.json(parameter), JsonObject.class);

			return result.getString("location");
		} catch (ResponseProcessingException rpe) {
			Response response = rpe.getResponse();
			Log.log(Level.FINER, this, "Exception changing player location,  uri: {0} resp code: {1} data: {2}",
					target.getUri().toString(),
					response.getStatusInfo().getStatusCode()+" "+response.getStatusInfo().getReasonPhrase(),
					response.readEntity(String.class));

			Log.log(Level.FINEST, this, "Exception changing player location", rpe);
		} catch ( ProcessingException|WebApplicationException ex ) {
			Log.log(Level.FINEST, this, "Exception changing player location ("+target.getUri().toString()+")", ex);
		}

		// Sadly, badness happened while trying to set the new room location
		// return to old room
		return oldRoomId;
	}

}
