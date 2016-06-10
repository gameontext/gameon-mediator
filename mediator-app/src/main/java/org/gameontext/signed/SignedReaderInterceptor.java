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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.stream.Collectors;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.ext.ReaderInterceptor;
import javax.ws.rs.ext.ReaderInterceptorContext;

public class SignedReaderInterceptor implements ReaderInterceptor {

    public SignedReaderInterceptor() {
    }

    /* (non-Javadoc)
     * @see javax.ws.rs.ext.ReaderInterceptor#aroundReadFrom(javax.ws.rs.ext.ReaderInterceptorContext)
     */
    @Override
    public Object aroundReadFrom(ReaderInterceptorContext context) throws IOException, WebApplicationException {

        SignedRequestHmac hmac = (SignedRequestHmac) context.getProperty("SignedRequestHmac");

        if ( hmac != null ) {
            // Fully read request body

            BufferedReader buffer = new BufferedReader(new InputStreamReader(context.getInputStream(), SignedRequestHmac.UTF8));
            String body = buffer.lines().collect(Collectors.joining("\n"));
            byte[] bodyBytes = body.getBytes(SignedRequestHmac.UTF8);

            try {
                // Validate HMAC signature (including body hash)
                hmac.verifyBodyHash(bodyBytes)
                    .verifyFullSignature();
            } finally {
                // we've read the body in, set the stream for the context to read from
                // what we read...
                context.setInputStream(new ByteArrayInputStream(bodyBytes));
            }
            SignedRequestFeature.writeLog(Level.FINEST, this, "READER INTERCEPTOR: {0} {1}", hmac);
        }
        return context.proceed();
    }
}
