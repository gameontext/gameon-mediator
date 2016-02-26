/*******************************************************************************
 * Copyright (c) 2016 IBM Corp.
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
package net.wasdev.gameon.mediator.auth;

import java.security.Key;
import java.security.cert.Certificate;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;

/**
 * Common class for handling JSON Web Tokens
 * 
 * @author marknsweep
 *
 */

public class JWT {
    private final AuthenticationState state;
    private FailureCode code;
    private String token = null;
    private Jws<Claims> jwt = null;

    public JWT(Certificate cert, String... sources) {
        state = processSources(cert.getPublicKey(), sources);
    }
    
    public JWT(Key key, String... sources) {
        state = processSources(key, sources);
    }
    
    // the authentication steps that are performed on an incoming request
    public enum AuthenticationState {
        UNKNOWN,                        // starting state
        hasJWTParam, hasJWTHeader, isJWTValid, 
        PASSED, ACCESS_DENIED           // end state
    }
    
    public enum FailureCode {
        NONE,
        BAD_SIGNATURE,
        EXPIRED
    }
    
    private enum ProcessState {
        FIND_SOURCE,
        VALIDATE,
        COMPLETE
    }
    
    private AuthenticationState processSources(Key key, String[] sources) {
        AuthenticationState state = AuthenticationState.ACCESS_DENIED; // default
        ProcessState process = ProcessState.FIND_SOURCE;
        while (!process.equals(ProcessState.COMPLETE)) {
            switch (process) {
            case FIND_SOURCE :
                //find the first non-empty source
                for(int i = 0; i < sources.length && ((token == null) || token.isEmpty()); token = sources[i++]);
                process = ((token == null) || token.isEmpty()) ? ProcessState.COMPLETE : ProcessState.VALIDATE;  
                break;
            case VALIDATE: // validate the jwt
                boolean jwtValid = false;
                try {
                    jwt = Jwts.parser().setSigningKey(key).parseClaimsJws(token);
                    jwtValid = true;
                    code = FailureCode.NONE;
                } catch (io.jsonwebtoken.SignatureException e) {
                    // thrown if the signature on id_token cannot be verified.
                    System.out.println("JWT did NOT validate ok, bad signature.");
                    code = FailureCode.BAD_SIGNATURE;
                } catch (ExpiredJwtException e) {
                    // thrown if the jwt had expired.
                    System.out.println("JWT did NOT validate ok, jwt had expired");
                    code = FailureCode.EXPIRED;
                }
                state = !jwtValid ? AuthenticationState.ACCESS_DENIED : AuthenticationState.PASSED;
                process = ProcessState.COMPLETE;
                break;
            default:
                process = ProcessState.COMPLETE;
                break;
            }
        }
        return state;
    }

    public AuthenticationState getState() {
        return state;
    }

    public FailureCode getCode() {
        return code;
    }

    public String getToken() {
        return token;
    }

    public Claims getClaims() {
        return jwt.getBody();
    }
    
    
}
