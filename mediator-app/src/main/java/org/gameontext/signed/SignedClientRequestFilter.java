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

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.WriterInterceptor;
import javax.ws.rs.ext.WriterInterceptorContext;

public class SignedClientRequestFilter implements ClientRequestFilter, WriterInterceptor {

    final String userId;
    final String secret;
    List<String> header_names;
    List<String> parameter_names;

    public SignedClientRequestFilter(String userId, String secret) {
        this(userId, secret, null, null);
    }

    public SignedClientRequestFilter(String userId, String secret,
            List<String> header_names,
            List<String> parameter_names) {
        if (secret == null)
            throw new NullPointerException("NULL secret");
        this.userId = userId;
        this.secret = secret;
        setRequiredAttributes(header_names, parameter_names);
    }

    public void setRequiredAttributes(List<String> header_names, List<String> parameter_names) {
        this.header_names = header_names;
        this.parameter_names = parameter_names;
    }

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        WebApplicationException invalidHmacEx = null;
        SignedRequestHmac clientHmac = null;
        SignedRequestMap headers = new SignedRequestMap.MVSO_StringMap(requestContext.getHeaders());
        SignedRequestMap parameters = new SignedRequestMap.QueryParameterMap(requestContext.getUri().getRawQuery());

        SignedRequestFeature.writeLog(Level.FINEST, this, "REQUEST FILTER: USER={0}, PATH={1}, QUERY={2}, HEADERS={3}, HAS_ENTITY={4}",
                userId,
                requestContext.getMethod() + " "  + requestContext.getUri().getRawPath(),
                requestContext.getUri().getRawQuery(),
                requestContext.getHeaders(),
                requestContext.getEntity());

        try {
            clientHmac = new SignedRequestHmac(userId, secret,
                    requestContext.getMethod(),
                    requestContext.getUri().getRawPath());

            clientHmac.setNow()
                  .generateRequestHeaderHashes(headers, header_names, parameters, parameter_names);

            if ( requestContext.hasEntity() ) {

                // set this as a property on the request context, and wait for the
                // signed request interceptor to catch the request
                // @see SignedReaderInterceptor as assigned by SignedRequestFeature
                requestContext.setProperty("SignedRequestHmac", clientHmac);
            } else {
                clientHmac.signRequest(headers);
            }
        } catch(WebApplicationException ex) {
            invalidHmacEx = ex;
        } catch(Exception e) {
            invalidHmacEx = new WebApplicationException("Unexpected exception signing request",
                    e, Response.Status.INTERNAL_SERVER_ERROR);
        }

        SignedRequestFeature.writeLog(Level.FINEST, this, "CLIENT FILTER: {0} {1} {2}", invalidHmacEx, clientHmac, headers);

        if ( invalidHmacEx != null ) {
            // STOP!! turn this right around with the bad response
            requestContext.abortWith(invalidHmacEx.getResponse());
        }
    }

    @Override
    public void aroundWriteTo(WriterInterceptorContext context) throws IOException, WebApplicationException {
        SignedWriterInterceptor interceptor = new SignedWriterInterceptor();
        interceptor.aroundWriteTo(context);
    }
}
