/*******************************************************************************
 * Copyright (c) 2016 IBM Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *******************************************************************************/
package org.gameontext.signed;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.enterprise.context.ApplicationScoped;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

@ApplicationScoped
public class SignedJWTValidator {
    
    public static final String JWT_QUERY_PARAMETER = "jwt";
    public static final String JWT_CLIENT_HEADER = "gameon-jwt";
    public static final String JWT_CLIENT_VALID = "valid";
    public static final String JWT_CLIENT_INVALID = "invalid";
    

    // Keystore info for jwt parsing / creation.
    @Resource(lookup = "jwtKeyStore")
    String keyStore;
    
    @Resource(lookup = "jwtKeyStorePassword")
    String keyStorePW;
    
    @Resource(lookup = "jwtKeyStoreAlias")
    String keyStoreAlias;

    /** SignedJWT Signing key */
    private Key signingKey = null;

    /**
     * Obtain the key we'll use to sign the jwts we issue.
     *
     * @throws IOException
     *             if there are any issues with the keystore processing.
     */
    @PostConstruct
    protected void getKeyStoreInfo() {
        try {
            // load up the keystore..
            FileInputStream is = new FileInputStream(keyStore);
            KeyStore signingKeystore = KeyStore.getInstance(KeyStore.getDefaultType());
            signingKeystore.load(is, keyStorePW.toCharArray());

            // grab the key we'll use to sign
            signingKey = signingKeystore.getKey(keyStoreAlias, keyStorePW.toCharArray());

        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | UnrecoverableKeyException | IOException e) {
            throw new IllegalStateException("Unable to retrieve keystore required to sign JWTs: " + keyStore, e);
        }
    }
    
    public SignedJWT getJWT(String jwtParam) {
        return new SignedJWT(signingKey, jwtParam);
    }
        
    public String clientToServer(SignedJWT jwt) {
        if ( jwt.isValid() ) {
            Claims onwardsClaims = Jwts.claims();
            // add all the client claims
            onwardsClaims.putAll(jwt.getClaims());
            // upgrade the type to server
            onwardsClaims.setAudience("server");

            // build the new jwt
            String newJwt = Jwts.builder().setHeaderParam("kid", "playerssl")
                    .setClaims(onwardsClaims)
                    .signWith(SignatureAlgorithm.RS256, signingKey).compact();

            return newJwt;
        } 
        
        return null;
    }
}
