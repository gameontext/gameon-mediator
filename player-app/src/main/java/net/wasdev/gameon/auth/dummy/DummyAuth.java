package net.wasdev.gameon.auth.dummy;

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

/**
 * A backend-less auth impl for testing. 
 * 
 * Accepts the username as a parameter, and returns a signed jwt for that username. 
 */
@WebServlet("/DummyAuth")
public class DummyAuth extends JwtAuth {
	private static final long serialVersionUID = 1L;

	@Resource(lookup="authCallbcakURLSuccess")
	String callbackSuccess;
	
    public DummyAuth() {
    	super();
    }
    
	@PostConstruct
	private void verifyInit(){
		if(webappBase==null){
			System.err.println("Error finding webapp base URL; please set this in your environment variables!");
		}
	}
    
    
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {	
		String s = request.getParameter("dummyUserName");
		
		if(s==null){
			s="AnonymousUser";
		}
		
		Map<String,String> claims = new HashMap<String,String>();
		
		claims.put("id", "dummy."+s);
		claims.put("name", s);
		
		String newJwt = createJwt(claims);
		
		//debug.
		System.out.println("New User Authed: "+claims.get("id"));

		response.sendRedirect(callbackSuccess + newJwt);
		
	}


}
