package net.wasdev.gameon.auth.facebook;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.restfb.DefaultFacebookClient;
import com.restfb.DefaultWebRequestor;
import com.restfb.FacebookClient;
import com.restfb.Parameter;
import com.restfb.Version;
import com.restfb.WebRequestor;
import com.restfb.exception.FacebookOAuthException;
import com.restfb.types.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;


@WebServlet("/FacebookCallback")
public class FacebookCallback extends HttpServlet {
	private static final long serialVersionUID = 1L;

	private String webappBase;

	public FacebookCallback() {
		super();
		try {
			this.webappBase = (String) new InitialContext().lookup("webappBase");
		} catch (NamingException e) {
			System.err.println("Error finding webapp base URL; please set this in your environment variables!");
		}
	}

	/**
	 * Utility method to obtain an accesstoken given a facebook code and the redirecturl used to obtain it.
	 * @param code
	 * @param redirectUrl
	 * @return the acccess token
	 * @throws IOException if anything goes wrong.
	 */
	private FacebookClient.AccessToken getFacebookUserToken(String code, String redirectUrl) throws IOException {
		String appId = FacebookCredentials.getAppID();
		String secretKey = FacebookCredentials.getAppSecret();

		//restfb doesn't seem to have an obvious method to convert a response code into an access token
		//but according to the spec, this is the easy way to do it.. we'll use WebRequestor from restfb to
		//handle the request/response.

		WebRequestor wr = new DefaultWebRequestor();
		WebRequestor.Response accessTokenResponse = wr.executeGet(
				"https://graph.facebook.com/oauth/access_token?client_id=" + appId + "&redirect_uri=" + redirectUrl
				+ "&client_secret=" + secretKey + "&code=" + code);

		//finally, restfb can now process the reply to get us our access token.
		return DefaultFacebookClient.AccessToken.fromQueryString(accessTokenResponse.getBody());
	}
	
	/**
	 * Method that performs introspection on an AUTH string, and returns data as 
	 * a String->String hashmap. 
	 * 
	 * @param auth the authstring to query, as built by an auth impl.
	 * @return the data from the introspect, in a map.
	 * @throws IOException if anything goes wrong.
	 */
	private Map<String,String> introspectAuth(String accesstoken) throws IOException{
		Map<String,String> results = new HashMap<String,String>();
			
    	//create a fb client using the supplied access token
        FacebookClient client = new DefaultFacebookClient(accesstoken, Version.VERSION_2_5);
        
        try{
        	//get back just the email, and name for the user, we'll get the id for free.
        	//fb only allows us to retrieve the things we asked for back in FacebookAuth when creating the token.
	        User userWithMetadata = client.fetchObject("me", User.class, Parameter.with("fields", "email,name"));
	        
	        results.put("valid","true");
	        results.put("email",userWithMetadata.getEmail());
	        results.put("name",userWithMetadata.getName());
	        results.put("id","facebook:"+userWithMetadata.getId());

        }catch(FacebookOAuthException e){
        	results.clear();
	        results.put("valid","false");
        }
        
        return results;
	}
	
	private static Key signingKey = null;
	
	private synchronized static void getKeyStoreInfo() throws IOException{
		String keyStore = null;
		String keyStorePW = null;
		String keyStoreAlias = null;
		try{
			keyStore = new InitialContext().lookup("jwtKeyStore").toString();
			keyStorePW = new InitialContext().lookup("jwtKeyStorePassword").toString();
			keyStoreAlias = new InitialContext().lookup("jwtKeyStoreAlias").toString();
			
			//load up the keystore..
			FileInputStream is = new FileInputStream(keyStore);
			KeyStore signingKeystore = KeyStore.getInstance(KeyStore.getDefaultType());
			signingKeystore.load(is,keyStorePW.toCharArray());

			//grab the key we'll use to sign
			signingKey = signingKeystore.getKey(keyStoreAlias,keyStorePW.toCharArray());
			
		}catch(NamingException e){
			throw new IOException(e);
		}catch(KeyStoreException e){
			throw new IOException(e);
		}catch(NoSuchAlgorithmException e){
			throw new IOException(e);
		}catch(CertificateException e){
			throw new IOException(e);
		}catch(UnrecoverableKeyException e){
			throw new IOException(e);
		}
		
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		if(signingKey==null)
			getKeyStoreInfo();
		
		//facebook redirected to us, and there should be a code awaiting us as part of the request.
		String code = request.getParameter("code");

		//need the redirect url for fb to give us a token from the code it supplied.
		StringBuffer callbackURL = request.getRequestURL();
		int index = callbackURL.lastIndexOf("/");
		callbackURL.replace(index, callbackURL.length(), "").append("/FacebookCallback");

		//convert the code into an access token.
		FacebookClient.AccessToken token = getFacebookUserToken(code, callbackURL.toString());

		String accessToken = token.getAccessToken();

		Map<String,String> claims = introspectAuth(accessToken);
		
		//if auth key was no longer valid, we won't build a jwt. redirect back to start.
		if(!"true".equals(claims.get("valid"))){
			response.sendRedirect(webappBase + "/#/game");
		}else{
			Claims onwardsClaims = Jwts.claims();
			
			//add in the subject & scopes from the token introspection	
			onwardsClaims.setSubject(claims.get("id"));

			onwardsClaims.putAll(claims);
			
			//client JWT has 24 hrs validity from issue
			Calendar calendar = Calendar.getInstance();
			calendar.add(Calendar.HOUR,24);
			onwardsClaims.setExpiration(calendar.getTime());
			
			//finally build the new jwt, using the claims we just built, signing it with our
			//signing key, and adding a key hint as kid to the encryption header, which is
			//optional, but can be used by the receivers of the jwt to know which key
			//they should verifiy it with.
			String newJwt = Jwts.builder().setHeaderParam("kid","playerssl").setClaims(onwardsClaims).signWith(SignatureAlgorithm.RS256,signingKey).compact();
			
			//debug.
			System.out.println("New User Authed: "+claims.get("id"));
	
			response.sendRedirect(webappBase + "/#/login/callback/"+newJwt);
		}
	}


}
