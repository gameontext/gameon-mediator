package net.wasdev.gameon.auth.twitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.wasdev.gameon.auth.JwtAuth;
import twitter4j.ResponseList;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.User;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;
import twitter4j.conf.ConfigurationBuilder;

/**
 * Servlet implementation class TwitterCallback
 */
@WebServlet("/TwitterCallback")
public class TwitterCallback extends JwtAuth {
	private static final long serialVersionUID = 1L;

	@Resource(lookup="twitterOAuthConsumerKey")
	String key;
	@Resource(lookup="twitterOAuthConsumerSecret")
	String secret;
	@Resource(lookup="webappBase")
	String webappBase;

	public TwitterCallback() {
		super();
	}
	
	@PostConstruct
	private void verifyInit(){
		if(webappBase==null){
			System.err.println("Error finding webapp base URL; please set this in your environment variables!");
		}
	}
	
	/**
	 * Method that performs introspection on an AUTH string, and returns data as 
	 * a String->String hashmap. 
	 * 
	 * @param auth the authstring to query, as built by an auth impl.
	 * @return the data from the introspect, in a map.
	 * @throws IOException if anything goes wrong.
	 */
	public Map<String,String> introspectAuth(String token, String tokensecret) throws IOException{
		Map<String,String> results = new HashMap<String,String>();   	
    	    	    	       
		ConfigurationBuilder c = new ConfigurationBuilder();
		c.setOAuthConsumerKey(key)
		 .setOAuthConsumerSecret(secret)
		 .setOAuthAccessToken(token)
		 .setOAuthAccessTokenSecret(tokensecret);
		 
        Twitter twitter = new TwitterFactory(c.build()).getInstance();
        
        try {
        	//ask twitter to verify the token & tokensecret from the auth string
        	//if invalid, it'll throw a TwitterException
			twitter.verifyCredentials();	
			
			//if it's valid, lets grab a little more info about the user.
			long id = twitter.getId();
			ResponseList<User> users = twitter.lookupUsers(id);
			User u = users.get(0);
			String name = u.getName();
			String screenname = u.getScreenName();
			
			results.put("valid", "true");
			results.put("id", "twitter:"+id);
			results.put("name", name);
			results.put("screenname",screenname);
			
		} catch (TwitterException e) {
			results.put("valid", "false");
		}
        
        return results;
	}
		
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {		
		//twitter calls us back at this app when a user has finished authing with them.
		//when it calls us back here, it passes an oauth_verifier token that we can exchange
		//for a twitter access token.

		//we stashed our twitter & request token into the session, we'll need those to do the exchange
		Twitter twitter = (Twitter) request.getSession().getAttribute("twitter");
		RequestToken requestToken = (RequestToken) request.getSession().getAttribute("requestToken");

		//grab the verifier token from the request parms.
		String verifier = request.getParameter("oauth_verifier");
		try {
			//clean up the session as we go (can leave twitter there if we need it again).
			request.getSession().removeAttribute("requestToken");
			
			//swap the verifier token for an access token
			AccessToken token = twitter.getOAuthAccessToken(requestToken, verifier);

			Map<String,String> claims = introspectAuth(token.getToken(), token.getTokenSecret());
			
			//if auth key was no longer valid, we won't build a jwt. redirect back to start.
			if(!"true".equals(claims.get("valid"))){
				response.sendRedirect(webappBase + "/#/game");
			}else{				
				String newJwt = createJwt(claims);
				
				//debug.
				System.out.println("New User Authed: "+claims.get("id"));	
				response.sendRedirect(webappBase + "/login/callback/"+newJwt);
			}		
			
		} catch (TwitterException e) {
			throw new ServletException(e);
		}
	}
}
