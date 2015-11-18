package net.wasdev.gameon.auth.dummy;

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

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

@WebServlet("/DummyAuth")
public class DummyAuth extends HttpServlet {
	private static final long serialVersionUID = 1L;

	private String webappBase;

    public DummyAuth() {
    	try {
			this.webappBase = (String) new InitialContext().lookup("webappBase");
			System.out.println("Using webAppBase of "+webappBase);
		} catch (NamingException e) {
			System.err.println("Error finding webapp base URL; please set this in your environment variables!");
		}
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
    
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		if(signingKey==null)
			getKeyStoreInfo();
		
		String s = request.getParameter("dummyUserName");
		
		if(s==null){
			s="AnonymousUser";
		}
		
		Map<String,String> claims = new HashMap<String,String>();
		
		claims.put("id", "dummy."+s);
		claims.put("name", s);
		
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
