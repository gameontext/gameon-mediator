package net.wasdev.gameon.player;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.Map;

import javax.annotation.Resource;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;

@WebFilter(
		filterName = "playerAuthFilter",
		urlPatterns = {"/players/*","/ws1/*"}
		  )
public class PlayerFilter implements Filter {

	@Resource(lookup="jwtKeyStore")
	String keyStore;
	@Resource(lookup="jwtKeyStorePassword")
	String keyStorePW;
	@Resource(lookup="jwtKeyStoreAlias")
	String keyStoreAlias;
	
	private static Certificate signingCert = null;
	
	private synchronized void getKeyStoreInfo() throws IOException{
		try{		
			//load up the keystore..
			FileInputStream is = new FileInputStream(keyStore);
			KeyStore signingKeystore = KeyStore.getInstance(KeyStore.getDefaultType());
			signingKeystore.load(is,keyStorePW.toCharArray());

			//grab the cert we'll use to test signatures
			signingCert = signingKeystore.getCertificate(keyStoreAlias);			
		}catch(KeyStoreException e){
			throw new IOException(e);
		}catch(NoSuchAlgorithmException e){
			throw new IOException(e);
		}catch(CertificateException e){
			throw new IOException(e);
		}	
	}
	
	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		try{
			getKeyStoreInfo();
		}catch(IOException io){
			throw new ServletException(io);
		}
	}
	
	private final static String jwtParamName = "jwt";
	
	//the authentication steps that are performed on an incoming request
	private enum AuthenticationState {
		hasQueryString,			//starting state
		hasJWTParam,
		isJWTValid,
		PASSED,					//end state
		ACCESS_DENIED			//end state
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		String queryString = null; 
		String playerId = null;
		Map<String,Object> claims = null;
		int pos = 0; 
		AuthenticationState state = AuthenticationState.hasQueryString;		//default
		while(!state.equals(AuthenticationState.PASSED)) {
			switch(state) {
				case hasQueryString :	//check that there is a query string containing the jwt 
					queryString = ((HttpServletRequest) request).getQueryString();	//this is the raw version
					state = (queryString == null) ? AuthenticationState.ACCESS_DENIED : AuthenticationState.hasJWTParam;
					break;
				case hasJWTParam :	//check there is an jwt parameter
					pos = queryString.lastIndexOf(jwtParamName+"=");
					state = (pos == -1) ? AuthenticationState.ACCESS_DENIED : AuthenticationState.isJWTValid;
					break;
				case isJWTValid :	//validate the jwt
					String jwtParam = request.getParameter(jwtParamName);											
					boolean jwtValid = false;					
					try{
						Jws<Claims> jwt = Jwts.parser().setSigningKey(signingCert.getPublicKey()).parseClaimsJws(jwtParam);
						
						claims = jwt.getBody();
						playerId = jwt.getBody().getSubject();
												
						System.out.println("Valid JWT: ("+playerId+") "+request.getServletContext().getContextPath()+((HttpServletRequest) request).getPathInfo()+" "+jwtParam);
						jwtValid=true;
					}catch(io.jsonwebtoken.SignatureException e){
						//thrown if the signature on id_token cannot be verified.
						System.out.println("JWT did NOT validate ok, bad signature.");
					}catch(ExpiredJwtException e){
						//thrown if the jwt had expired.
						System.out.println("JWT did NOT validate ok, jwt had expired");
					}
					state = !jwtValid ? AuthenticationState.ACCESS_DENIED : AuthenticationState.PASSED;
					break;
				case ACCESS_DENIED :
				default :
					((HttpServletResponse)response).sendError(HttpServletResponse.SC_FORBIDDEN);
					return;
			}
		}
		//request has passed all validation checks, so allow it to proceed
		request.setAttribute("player.id", playerId);
		request.setAttribute("player.claims", claims);
		chain.doFilter(request, response);	
	}

	@Override
	public void destroy() {

	}

}
