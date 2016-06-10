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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Level;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.ext.WriterInterceptor;
import javax.ws.rs.ext.WriterInterceptorContext;

public class SignedWriterInterceptor implements WriterInterceptor {

    public SignedWriterInterceptor() {
    }

    @Override
    public void aroundWriteTo(WriterInterceptorContext context) throws IOException, WebApplicationException {
        SignedRequestHmac hmac = (SignedRequestHmac) context.getProperty("SignedRequestHmac");

        if ( hmac == null ) {
            context.proceed();
        } else {
            SignedRequestMap headers = new SignedRequestMap.MVSO_StringMap(context.getHeaders());

            OutputStream old = context.getOutputStream();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            context.setOutputStream(baos);

            // Fully write response body
            context.proceed();

            // Capture response body
            final byte[] body = baos.toByteArray();

            try {
                // Finalize signature
                hmac.generateBodyHash(headers, body)
                    .signRequest(headers);

                SignedRequestFeature.writeLog(Level.FINEST, this, "WRITER INTERCEPTOR: {0}", headers);
            } finally {
                // Write the response
                old.write(body);
                context.setOutputStream(old);
            }
        }
    }
}
