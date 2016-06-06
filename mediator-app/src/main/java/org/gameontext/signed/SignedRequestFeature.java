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

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.spi.CDI;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.container.DynamicFeature;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.FeatureContext;
import javax.ws.rs.ext.Provider;

@Provider
@ApplicationScoped
public class SignedRequestFeature implements DynamicFeature {

    final static Logger logger = Logger.getLogger("org.gameontext.signed");

    final static void writeLog(Level level, Object source, String message, Object... args) {
        if (logger.isLoggable(level)) {
            logger.logp(level, source.getClass().getName(), "", message, args);
        }
    }

    final static void writeLog(Level level, Object source, String message, Throwable thrown) {
        if (logger.isLoggable(level)) {
            logger.logp(level, source.getClass().getName(), "", message, thrown);
        }
    }

    SignedRequestSecretProvider playerClient;
    SignedRequestTimedCache timedCache;

    public SignedRequestFeature() {
        // TODO: Bug in Liberty: @Inject does not work (jax-rs creates its own instance)
        // work-around is to lookup the CDI beans directly
        playerClient = CDI.current().select(SignedRequestSecretProvider.class).get();
        timedCache = CDI.current().select(SignedRequestTimedCache.class).get();
    }

    @Override
    public void configure(ResourceInfo resourceInfo, FeatureContext context) {
        SignedRequest sr = resourceInfo.getResourceMethod().getAnnotation(SignedRequest.class);
        if ( sr == null ) {
            sr = resourceInfo.getResourceClass().getAnnotation(SignedRequest.class);
        }
        if ( sr == null )
            return;

        context.register(new SignedContainerRequestFilter(playerClient, timedCache));

        GET get = resourceInfo.getResourceMethod().getAnnotation(GET.class);
        DELETE delete = resourceInfo.getResourceMethod().getAnnotation(DELETE.class);

        if ( get == null && delete == null ) {
            // Signed requests only for messages with bodies!
            context.register(new SignedReaderInterceptor());
        }
    }
}
