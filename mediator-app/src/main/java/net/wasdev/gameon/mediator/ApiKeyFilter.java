/*******************************************************************************
 * Copyright (c) 2015 IBM Corp.
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
package net.wasdev.gameon.mediator;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;

/**
 * This is a client request filter that is applied to outbound client requests
 * to use/propagate an API key.
 *
 * @see ConciergeClient
 * @see PlayerClient
 */
public class ApiKeyFilter implements ClientRequestFilter {
    private static final String CHAR_SET = "UTF-8";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /** ID of the service making the API call */
    private final String serviceID;

    /**
     * system property or environment variable which contains the shared secret
     */
    private final String secret;

    /** Enum to ensure consistent parameter names. */
    public enum Params {
        apikey, serviceID, stamp;

        /** Generate appropriate query string parameter */
        @Override
        public String toString() {
            return "&" + this.name() + "=";
        }

    }

    /**
     * Constructor to be used by the client making the API call.
     *
     * @param serviceID
     *            the ID representing this service.
     * @param syspropName
     *            the system property or env var which contains the shared
     *            secret to use when invoking the remote API.
     */
    public ApiKeyFilter(String serviceID, String secret) {
        this.serviceID = serviceID;
        this.secret = secret;
    }

    /**
     * Entry point for the client that wants to make a request to a second
     * service. It takes the original URI supplied and adds additional query
     * string parameters. These are
     *
     * 1. The service ID supplied by the client 2. A timestamp of when the
     * request was made 3. A generated API key for this invocation.
     *
     * @see javax.ws.rs.client.ClientRequestFilter#filter(javax.ws.rs.client.
     *      ClientRequestContext)
     */
    @Override
    public void filter(ClientRequestContext ctx) throws IOException {
        String idparams = Params.serviceID.toString() + serviceID + Params.stamp.toString()
                + Long.toString(System.currentTimeMillis());

        // handle when the original request had no query params.
        String rawQuery = ctx.getUri().getRawQuery();
        if (rawQuery == null) {
            rawQuery = "";
        }

        String apikey = rawQuery + idparams;
        String hmac = URLEncoder.encode(digest(apikey), CHAR_SET);
        String uriStr = ctx.getUri().toString();
        if (!uriStr.contains("?")) {
            uriStr += "?";
        }

        URI uri = URI.create(uriStr + idparams + Params.apikey.toString() + hmac);
        ctx.setUri(uri);
    }

    /**
     * Construct a HMAC for this request. It is then base 64 and URL encoded
     * ready for transmission as a query parameter.
     */
    private String digest(String message) throws IOException {
        try {
            byte[] data = message.getBytes(CHAR_SET);
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec key = getKey();
            mac.init(key);
            return javax.xml.bind.DatatypeConverter.printBase64Binary(mac.doFinal(data));
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    /**
     * Gets the secret key from either a system property or environment
     * variable. The system property takes precedence over the environment
     * variable.
     */
    private SecretKeySpec getKey() throws IOException {
        return new SecretKeySpec(secret.getBytes(CHAR_SET), HMAC_ALGORITHM);
    }

}
