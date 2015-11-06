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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

/**
 *
 */
public class FirstRoom implements RoomMediator {

	PlayerConnectionMediator session= null;
	private AtomicInteger counter = new AtomicInteger(0);
	boolean newbie = false;

	public FirstRoom() {
		this(false);
	}

	public FirstRoom(boolean newbie) {
		this.newbie = newbie;
	}

	@Override
	public String getId() {
		return Constants.FIRST_ROOM;
	}

	@Override
	public String getName() {
		return Constants.FIRST_ROOM;
	}

	@Override
	public boolean connect() {
		return true;
	}

	@Override
	public boolean subscribe(PlayerConnectionMediator playerSession, long lastmessage) {
		this.session = playerSession;
		return true;
	}

	@Override
	public void unsubscribe(PlayerConnectionMediator playerSession) {
	}

	@Override
	public void disconnect() {
	}

	@Override
	public void send(RoutedMessage message) {
		Log.log(Level.FINEST, this, "TheFirstRoom received: {0}", message);

		JsonObject sourceMessage = message.getParsedBody();

		JsonObjectBuilder builder = Json.createObjectBuilder();
		builder.add(Constants.BOOKMARK, counter.incrementAndGet());
		switch(message.getFlowTarget()) {
			case Constants.ROOM_HELLO :
				buildLocationResponse(builder);
				break;
			case Constants.ROOM_GOODBYE :
				// no response for roomGoodbye
				return;
			default :
				parseCommand(sourceMessage, builder);
		}


		JsonObject response = builder.build();

		String target = Constants.PLAYER;
		if ( response.containsKey(Constants.EXIT_ID) ) {
			target = Constants.PLAYER_LOCATION;
		}
		session.sendToClient(RoutedMessage.createMessage(target, sourceMessage.getString(Constants.USER_ID), response));
	}

	protected void parseCommand(JsonObject sourceMessage, JsonObjectBuilder responseBuilder) {
		String content = sourceMessage.getString(Constants.CONTENT);
		String contentToLower = content.toLowerCase();

		// The First Room will just look for the leading / with a few verbs.
		// Other rooms may go for more complicated grammar (though leading slash will be prevalent).
		if ( contentToLower.startsWith("/look")) {
			buildLocationResponse(responseBuilder);
		} else if ( contentToLower.startsWith("/exits") ) {
			responseBuilder.add(Constants.TYPE, Constants.EXITS)
			.add(Constants.CONTENT, buildExitsResponse());
		} else if ( contentToLower.startsWith("/exit") || contentToLower.startsWith("/go ") ) {
			responseBuilder.add(Constants.TYPE, Constants.EXIT)
			.add(Constants.EXIT_ID, "N")
			.add(Constants.CONTENT, "You've found a way out, well done!");
		} else if ( contentToLower.startsWith("/inventory") ) {
			responseBuilder.add(Constants.TYPE, Constants.EVENT)
			.add(Constants.CONTENT, buildContentResponse("You do not appear to be carrying anything."));
		} else if ( contentToLower.startsWith("/examine") ) {
			responseBuilder.add(Constants.TYPE, Constants.EVENT)
			.add(Constants.CONTENT, buildContentResponse("You don't see anything of interest."));
		} else {
			responseBuilder.add(Constants.USERNAME, sourceMessage.getString(Constants.USERNAME))
			.add(Constants.CONTENT, "echo " + content)
			.add(Constants.TYPE, Constants.CHAT);
		}
	}

	private JsonObject buildContentResponse(String message) {
		JsonObjectBuilder content = Json.createObjectBuilder();
		content.add("*", message);
		return content.build();
	}

	private void buildLocationResponse(JsonObjectBuilder responseBuilder) {
		responseBuilder.add(Constants.TYPE, Constants.LOCATION);
		responseBuilder.add(Constants.NAME, Constants.FIRST_ROOM);
		responseBuilder.add(Constants.EXITS, buildExitsResponse());

		if ( newbie ) {
			responseBuilder.add(Constants.DESCRIPTION, Constants.FIRST_ROOM_DESC + Constants.FIRST_ROOM_EXTENDED);
			newbie = false;
		} else {
			responseBuilder.add(Constants.DESCRIPTION, Constants.FIRST_ROOM_DESC);
		}
	}

	private JsonObject buildExitsResponse() {
		JsonObjectBuilder content = Json.createObjectBuilder();
		content.add("N", "Simple door to the North (<b>/go N</b>)");
		content.add("S", "Simple door to the South (<b>/go S</b>)");
		content.add("E", "Simple door to the East (<b>/go E</b>)");
		content.add("W", "Simple door to the West (<b>/go W</b>)");
		content.add("U", "Hatch in the ceiling (<b>/go U</b>)");
		content.add("D", "Trap-door in the floor (<b>/go D</b>)");

		return content.build();
	}

}
