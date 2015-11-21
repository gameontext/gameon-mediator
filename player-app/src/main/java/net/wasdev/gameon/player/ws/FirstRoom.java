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

	String FIRST_ROOM_DESC = "You've entered a vaguely squarish room, with walls of an indeterminate color.";
	String FIRST_ROOM_EXTENDED = "<br /><br />You are alone at the moment, and have a strong suspicion that "
			+ " you're in a place that requires '/' before commands and is picky about syntax. You notice "
			+ " buttons at the top right of the screen, and a button at the bottom left to help with that"
			+ " leading slash.<br /><br />You feel a strong temptation to try the buttons.";

	String FIRST_ROOM_INV = "Sadly, there is nothing here.";

	String FIRST_ROOM_POCKETS = "You do not appear to be carrying anything.";
	String FIRST_ROOM_POCKETS_EXTENDED = "<br /><br />But you will be eventually! As you explore, use /TAKE"
			+ " to pick things up. Some things will remain with the room when you leave, others might stay"
			+ " in your pocket for a little longer. Nothing is guaranteed to stay with you indefinitely. "
			+ " Sneaky characters and self-healing rooms will foil most hoarder's plans.";

	PlayerConnectionMediator session= null;
	private AtomicInteger counter = new AtomicInteger(0);
	boolean newbie = false;
	boolean inventory = false;

	public FirstRoom() {
		this(false);
	}

	public FirstRoom(boolean newbie) {
		Log.log(Level.FINEST, this, "New First Room, new player {0}", newbie);
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
		if( newbie ) {
			JsonObjectBuilder builder = Json.createObjectBuilder();
			builder.add(Constants.BOOKMARK, counter.incrementAndGet());
			buildLocationResponse(builder);

			session.sendToClient(RoutedMessage.createMessage(Constants.PLAYER,
					playerSession.getUserId(), builder.build()));
		}
		return true;
	}

	@Override
	public void unsubscribe(PlayerConnectionMediator playerSession) {
	}

	@Override
	public void disconnect() {
	}

	/**
	 * "Send to the room"
	 *
	 * The First room is different because this is the room. So here, we take
	 * the pseudo-sent messages and provide coverage for the most basic commands to ensure they get handled'
	 * and provide some help and context.
	 *
	 * @see net.wasdev.gameon.player.ws.RoomMediator#send(net.wasdev.gameon.player.ws.RoutedMessage)
	 */
	@Override
	public void send(RoutedMessage message) {
		Log.log(Level.FINEST, this, "TheFirstRoom received: {0}", message);

		JsonObject sourceMessage = message.getParsedBody();

		JsonObjectBuilder builder = Json.createObjectBuilder();
		builder.add(Constants.BOOKMARK, counter.incrementAndGet());
		switch(message.getFlowTarget()) {
			case Constants.ROOM_HELLO :
				// First room doesn't usually see roomHello,
				// but may in the case of a botched transition
				buildLocationResponse(builder);
				break;
			case Constants.ROOM_GOODBYE :
				// no response for roomGoodbye
				return;
			default :
				parseCommand(sourceMessage, builder);
				break;
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
			responseBuilder.add(Constants.TYPE, Constants.EVENT).add(Constants.CONTENT, buildInventoryResponse());
		} else if ( contentToLower.startsWith("/examine") ) {
			responseBuilder.add(Constants.TYPE, Constants.EVENT)
			.add(Constants.CONTENT, buildContentResponse("You don't see anything of interest."));
		} else if ( contentToLower.startsWith("/help") ) {
			responseBuilder.add(Constants.TYPE, Constants.EVENT)
			.add(Constants.CONTENT, buildContentResponse("Commands do and should start with '/' This room doesn't understand many commands, but others will."));
		} else if ( contentToLower.startsWith("/") ) {
			responseBuilder.add(Constants.TYPE, Constants.EVENT)
			.add(Constants.CONTENT, buildContentResponse("This room is a basic model. It doesn't understand that command."));
		} else {
			responseBuilder.add(Constants.USERNAME, sourceMessage.getString(Constants.USERNAME))
			.add(Constants.CONTENT, content)
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
			responseBuilder.add(Constants.DESCRIPTION, FIRST_ROOM_DESC + FIRST_ROOM_EXTENDED);
			newbie = false;
		} else {
			responseBuilder.add(Constants.DESCRIPTION, FIRST_ROOM_DESC);
		}
	}

	private JsonObject buildExitsResponse() {
		JsonObjectBuilder content = Json.createObjectBuilder();
		content.add("N", "Simple door to the North");
		content.add("S", "Simple door to the South");
		content.add("E", "Simple door to the East");
		content.add("W", "Simple door to the West");
		content.add("U", "Hatch in the ceiling (Up)");
		content.add("D", "Trap-door in the floor (Down)");

		return content.build();
	}

	protected JsonObject buildInventoryResponse() {
		if ( inventory )
			return buildContentResponse(FIRST_ROOM_POCKETS);

		inventory = true;
		return buildContentResponse(FIRST_ROOM_POCKETS + FIRST_ROOM_POCKETS_EXTENDED);
	}

}
