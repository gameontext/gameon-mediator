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

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.logging.Level;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.MultivaluedMap;

import net.wasdev.gameon.mediator.Log;
import net.wasdev.gameon.mediator.MapClient;

/**
 * Outbound Client request filter. Used by {@link MapClient} to authenticate with the map service.
 */
public class GameOnHeaderAuthFilter extends GameOnHeaderAuth implements ClientRequestFilter {

    public GameOnHeaderAuthFilter(String userId, String secret) {
        super(secret,userId);
        if (secret == null)
            throw new RuntimeException("NULL secret");
    }

    @Override
    public void filter(ClientRequestContext context) throws IOException {
        try {
            // create the timestamp
            Instant now = Instant.now();
            String dateValue = now.toString();

            // create the signature
            String hmac = buildHmac(Arrays.asList(new String[] { userId, dateValue }), secret);

            MultivaluedMap<String, Object> headers = context.getHeaders();
            headers.add("gameon-id", userId);
            headers.add("gameon-date", dateValue);
            headers.add("gameon-signature", hmac);

            Log.log(Level.FINER, this, "Added headers to request to {0} for id {1}", context.getUri(), userId);

        } catch (Exception e) {
            Log.log(Level.SEVERE, this, "Error calculating hmac headers", e);
            throw new IOException(e);
        }
    }
}
