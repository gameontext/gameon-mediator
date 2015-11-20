package net.wasdev.gameon.player;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

public class PlayerNotFoundException extends RuntimeException implements ExceptionMapper<PlayerNotFoundException> {
		
	private static final long serialVersionUID = 1L;

	public PlayerNotFoundException(){		
	}
	
	public PlayerNotFoundException(String message) {
		super(message);
	}
	
	@Override
	public Response toResponse(PlayerNotFoundException exception) {		
		return Response.status(404).entity("Player not found").type("text/plain").build();
	}
}
