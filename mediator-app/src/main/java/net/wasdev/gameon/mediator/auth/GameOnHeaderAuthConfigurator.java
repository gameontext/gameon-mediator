/*******************************************************************************
 * Copyright (c) 2015 IBM Corp.
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
package net.wasdev.gameon.mediator.auth;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import javax.websocket.ClientEndpointConfig.Configurator;
import javax.websocket.HandshakeResponse;

import net.wasdev.gameon.mediator.Log;

public class GameOnHeaderAuthConfigurator extends Configurator {
    private final GameOnHeaderAuth auth;
    private final String protocol;
    private boolean responseValid = false;
    
    /**
     * Constructor to be used by the client.
     * 
     * @param userId
     *            the userId making this request.
     * @param secret
     *            the shared secret to use.
     */
    public GameOnHeaderAuthConfigurator(String secret, String protocol) {
        auth = new GameOnHeaderAuth(secret, null);
        this.protocol = protocol; 
    }
    
    //adds a header
    private void addHeader(Map<String, List<String>> headers, String key, String value) {
        List<String> entries = headers.get(key);
        if(entries == null) {
            entries = new ArrayList<String>();
            headers.put(key, entries);
        }
        entries.add(value);
    }
    
    @Override
    public void beforeRequest(Map<String, List<String>> headers) {
        super.beforeRequest(headers);
        //create the HMAC for the room to validate, use a time stamp to allow the room to time-out requests
        if((auth.secret != null) && !auth.secret.isEmpty()) {          
            //create the timestamp
            Instant now = Instant.now();
            String dateValue = now.toString();
                        
            //create the hmac
            try {
                String hmac = auth.buildHmac(Arrays.asList(new String[] {dateValue}), auth.secret);
                Log.log(Level.INFO,this,"hmac(first4chars) {0} FROM {1}",hmac.substring(0, 4), dateValue);
                
                addHeader(headers, "gameon-signature", hmac);
                addHeader(headers, "gameon-date", dateValue);
            } catch (Exception e) {
                Log.log(Level.WARNING, this, "Failed to generate HMAC, connection is likely to fail", e);
            }                
        }
        addHeader(headers, "gameon-protocol", protocol);
    }

    @Override
    public void afterResponse(HandshakeResponse hr) {
        super.afterResponse(hr);
        if((auth.secret != null) && !auth.secret.isEmpty()) { 
            //we have a token, so the response needs to be validated
            Log.log(Level.INFO,this,"Token supplied for room, performing WS handshake validation");
            String dateValue = getSingletonHeader(hr, "gameon-date");
            String hmac = getSingletonHeader(hr, "gameon-signature");
            if((dateValue == null) || (hmac == null)) {
                return;     //something wrong with one of the headers
            }
            try {
                Log.log(Level.INFO, this, "Validating HMAC supplied for WS");
                String hmac2 = auth.buildHmac(Arrays.asList(new String[] {dateValue}), auth.secret);
                responseValid = hmac.equals(hmac2);
                Log.log(Level.INFO, this, "Validating HMAC result is {0}", responseValid);
            } catch (Exception e) {
                Log.log(Level.WARNING, this, "Failed to validate HMAC, unable to establish connection", e);
            }
            
        } else {
            Log.log(Level.INFO,this,"No token supplied for room, skipping WS handshake validation");
            responseValid = true; 
        }
    }
    
    //gets a header that must only have a single value, returns null if it is missing or has multiple values
    private String getSingletonHeader(HandshakeResponse response, String name) {
        List<String> values = response.getHeaders().get(name);
        if((values == null) || values.isEmpty() || (values.size() != 1)) {
            //removing the accept header will cause the connection not to be made. Depending on the implementation
            //the initiator may get either OnError() or OnClose() invoked.
            Log.log(Level.WARNING, this, "Missing or invalid " + name + " header in request, connection will be rejected", values);
            response.getHeaders().replace(HandshakeResponse.SEC_WEBSOCKET_ACCEPT, Collections.emptyList());
            return null;
        }
        return values.get(0);
    }

    public boolean isResponseValid() {
        return responseValid;
    }

}
