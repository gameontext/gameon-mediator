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
package org.gameontext.signed;

import java.security.Key;
import java.security.cert.Certificate;
import java.util.logging.Level;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureException;

/**
 * Common class for handling JSON Web Tokens
 *
 * @author marknsweep
 *
 */

public class SignedJWT {
    private final AuthenticationState state;
    private FailureCode code = FailureCode.NONE;

    private String token = null;
    private Jws<Claims> jwt = null;

    public SignedJWT(Certificate cert, String... sources) {
        state = processSources(cert.getPublicKey(), sources);
    }

    public SignedJWT(Key key, String... sources) {
        state = processSources(key, sources);
    }

    // the authentication steps that are performed on an incoming request
    public enum AuthenticationState {
        PASSED,
        ACCESS_DENIED // end state
    }

    public enum FailureCode {
        NONE("ok"),
        MISSING_JWT("JWT not found in header or query string"),
        BAD_SIGNATURE("Bad signature."),
        EXPIRED("Expired token");

        final String reason;

        FailureCode(String reason) {
            this.reason = reason;
        }

        public String getReason() {
            return reason;
        }
    }

    private AuthenticationState processSources(Key key, String[] sources) {
        AuthenticationState state = AuthenticationState.ACCESS_DENIED; // default

        //find the first non-empty source, assign to token
        for(int i = 0; i < sources.length && ((token == null) || token.isEmpty()); token = sources[i++]);

        if ((token == null) || token.isEmpty()) {
            // we couldn't find a non-empty token. No dice.
            code = FailureCode.MISSING_JWT;
        } else {
            try {
                jwt = Jwts.parser().setSigningKey(key).parseClaimsJws(token);
                state = AuthenticationState.PASSED;
                code = FailureCode.NONE;
            } catch (MalformedJwtException | SignatureException e) {
                code = FailureCode.BAD_SIGNATURE;
                SignedRequestFeature.writeLog(Level.WARNING, this, "JWT has a bad signature {0}. {1}", e.getMessage(), token);
            } catch (ExpiredJwtException e) {
                code = FailureCode.EXPIRED;
                SignedRequestFeature.writeLog(Level.WARNING, this, "JWT has expired {0}. {1}", e.getMessage(), token);
            }
        }

        return state;
    }

    public boolean isValid() {
        return state == AuthenticationState.PASSED;
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
