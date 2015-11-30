package net.wasdev.gameon.player;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

public class RequestNotAllowedForThisIDException extends RuntimeException implements ExceptionMapper<RequestNotAllowedForThisIDException> {
	private static final long serialVersionUID = 1L;

	public RequestNotAllowedForThisIDException(){
	}
	
	public RequestNotAllowedForThisIDException(String message) {
		super(message);
	}
	
	@Override
	public Response toResponse(RequestNotAllowedForThisIDException exception) {		
		return Response.status(403).entity(exception.getMessage()).type("text/plain").build();
	}
}
