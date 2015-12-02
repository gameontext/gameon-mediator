package net.wasdev.gameon.player.ws;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;

public class ApiKey implements ClientRequestFilter {
	public static final String SYSPROP_LOGGING = "apikey.log";	//system property to let you see what is going on
	private static final String CHAR_SET = "UTF-8";
	private static final String HMAC_ALGORITHM = "HmacSHA256";

	private final String serviceID;	//this is the ID of the service making the API call
	private final String secret;	//the system property or environment variable which contains the shared secret
	
	//ensure consistent parameter names
	public enum Params {
		apikey,
		serviceID,
		stamp;
		
		public String toString() {
			return "&" + this.name() + "=";
		}
		
	}
	
	/**
	 * Constructor to be used by the client.
	 * 
	 * @param serviceID the ID representing this service.
	 * @param syspropName the system property or env var which contains the shared secret to use when invoking the remote API.
	 */
	public ApiKey(String serviceID, String secret) {
		this.serviceID = serviceID;
		this.secret = secret;
	}

	/* 
	 * Entry point for the client that wants to make a request to a second 
	 * service. It takes the original URI supplied and adds additional query string
	 * parameters. These are 
	 * 
	 * 1. The service ID supplied by the client
	 * 2. A timestamp of when the request was made
	 * 3. A generated API key for this invocation.
	 * 
	 * @see javax.ws.rs.client.ClientRequestFilter#filter(javax.ws.rs.client.ClientRequestContext)
	 */
	@Override
	public void filter(ClientRequestContext ctx) throws IOException {
		String idparams = Params.serviceID.toString() + serviceID + Params.stamp.toString() + Long.toString(System.currentTimeMillis());
		//handle when the original request had no query params.
		String rawQuery = ctx.getUri().getRawQuery();
		if(rawQuery==null){
			rawQuery = "";
		}	
		String apikey = rawQuery + idparams;
		String hmac = URLEncoder.encode(digest(apikey), CHAR_SET);
		String uriStr = ctx.getUri().toString();
		if(!uriStr.contains("?")){
			uriStr+="?";
		}
		URI uri = URI.create(uriStr + idparams + Params.apikey.toString() + hmac);
		System.setProperty(SYSPROP_LOGGING, "Outgoing request url : " + uri.toString());
		ctx.setUri(uri);
	}

	/*
	 * Construct a HMAC for this request.
	 * It is then base 64 and URL encoded ready for transmission as a query parameter.
	 */
	private String digest(String message) throws IOException {
		try {
			byte[] data = message.getBytes(CHAR_SET);
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			SecretKeySpec key = getKey();
			mac.init(key);
			return javax.xml.bind.DatatypeConverter.printBase64Binary(mac.doFinal(data));
		} catch (Exception e) {
			throw new IOException(e);
		}
	}
	
	/*
	 * Gets the secret key from either a system property or environment variable.
	 * The system property takes precedence over the environment variable.
	 */
	private SecretKeySpec getKey() throws IOException {
		return new SecretKeySpec(secret.getBytes(CHAR_SET), HMAC_ALGORITHM);
	}
	
}
