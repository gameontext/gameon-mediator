package net.wasdev.gameon.auth.dummy;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.naming.InitialContext;
import javax.naming.NamingException;

@WebServlet("/DummyAuth")
public class DummyAuth extends HttpServlet {
	private static final long serialVersionUID = 1L;

	private String webappBase;

    public DummyAuth() {
    	try {
			this.webappBase = (String) new InitialContext().lookup("webappBase");
		} catch (NamingException e) {
			System.err.println("Error finding webapp base URL; please set this in your environment variables!");
		}
    }
    
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		String s = request.getParameter("dummyUserName");
		
		if(s==null){
			s="AnonymousUser";
		}

        //redirect the user to facebook to be authenticated.
        response.sendRedirect(webappBase + "/#/login/callback/DUMMY::"+s);
	}


}
