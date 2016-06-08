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
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;

import javax.inject.Inject;
import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.EndpointConfig;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;

import org.gameontext.signed.SignedJWT;
import org.gameontext.signed.SignedJWTValidator;
import org.gameontext.signed.SignedRequestMap;

import net.wasdev.gameon.mediator.RoutedMessage.FlowTarget;

/**
 * Server-side endpoint for the Player Client (phone/browser). This endpoint is
 * scoped to the player's id to avoid session sharing between different users.
 * <p>
 * Relies on default deployment cardinality: there will be one instance of this
 * class per websocket client.
 */
@ServerEndpoint(value = "/ws/{userId}", decoders = RoutedMessageDecoder.class, encoders = RoutedMessageEncoder.class)
public class MediatorEndpoint {

    @Inject
    MediatorBuilder mediatorBuilder;

    @Inject
    protected SignedJWTValidator validator;

    CountDownLatch mediatorCheck = new CountDownLatch(0);
    volatile ClientMediator clientMediator;
    boolean goodToGo = false;

    /**
     * Called when a new connection has been established to this endpoint.
     *
     * @param session
     * @param ec
     */
    @OnOpen
    public void onOpen(@PathParam("userId") String userId, Session session, EndpointConfig ec) {
        Log.log(Level.FINER, session, "client open - {0} {1} {2} {3}", userId, session.getQueryString(),
                session.getUserProperties(), clientMediator);

        WSUtils.sendMessage(session, RoutedMessage.createMessage(FlowTarget.player, userId, Constants.VALID_JWT));

        SignedRequestMap.MLS_StringMap map = new SignedRequestMap.MLS_StringMap(session.getRequestParameterMap());
        String jwtParam = map.getAll(SignedJWTValidator.JWT_QUERY_PARAMETER, "");

        try {
            SignedJWT clientJWT = validator.getJWT(jwtParam);
            if (clientJWT.isValid()) {
                String serverJwt = validator.clientToServer(clientJWT);
                clientMediator = mediatorBuilder.buildClientMediator(userId, session, serverJwt);
            } else {
                WSUtils.sendMessage(session, RoutedMessage.createSimpleEventMessage(FlowTarget.player, userId, Constants.EVENTMSG_INVALID_JWT));

                // Invalid JWT: Close the connection & provide a reason
                WSUtils.tryToClose(session,
                        new CloseReason(CloseCodes.VIOLATED_POLICY, clientJWT.getCode().getReason()));
            }
        } finally {
            mediatorCheck.countDown();
        }
    }

    /**
     * Called when the connection is closed (cleanup)
     *
     * @param session
     * @param reason
     */
    @OnClose
    public void onClose(@PathParam("userId") String userId, Session session, CloseReason reason) {
        Log.log(Level.FINER, session, "client session closed - {0}: {1}", userId, reason);

        mediatorCheck.countDown(); // always unblock
        if (clientMediator != null)
            clientMediator.destroy();
    }

    /**
     * Message is received from the JS client
     *
     * @param message
     * @param session
     * @throws IOException
     */
    @OnMessage
    public void onMessage(@PathParam("userId") String userId, RoutedMessage message, Session session)
            throws IOException {
        Log.log(Level.FINEST, this, "C -> M    R : {0}", message);

        try {
            if (message.getFlowTarget() == FlowTarget.ready) {
                // wait to process the ready message until we've validated the JWT (see onOpen)
                mediatorCheck.await(); 
                
                clientMediator.ready(message);
                goodToGo = true; // eventually all threads will see that we're happy
            } else if (goodToGo || mediatorCheck.getCount() == 0) {
                // we will eventually see the goodToGo check, which will bypass having to look @ the latch
                clientMediator.handleMessage(message);
            } else {
                Log.log(Level.FINEST, session, "no session, dropping message from client {0}: {1}", userId, message);
                return;
            }
        } catch (Exception e) {
            Log.log(Level.WARNING, session, "Uncaught exception handling room-bound message", e);
        }
    }

    @OnError
    public void onError(@PathParam("userId") String userId, Session session, Throwable t) {
        Log.log(Level.FINER, session, "oops for client " + userId + " connection", t);

        WSUtils.tryToClose(session, new CloseReason(CloseReason.CloseCodes.UNEXPECTED_CONDITION,
                WSUtils.trimReason(t.getClass().getName())));
    }
}
