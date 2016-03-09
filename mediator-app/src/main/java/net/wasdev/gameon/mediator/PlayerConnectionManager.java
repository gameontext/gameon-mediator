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

import java.io.FileInputStream;
import java.io.IOException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.enterprise.concurrent.ManagedScheduledExecutorService;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.JsonObject;
import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.Session;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import net.wasdev.gameon.mediator.auth.JWT;

/**
 * Lifecycle management for mediators. When a "ready" message is received, the
 * payload will be inspected to see if a mediator id is present via
 * {@link #startSession(Session, String, RoutedMessage)} . If so, that session
 * will be resumed, if not, a new session will be created. The
 * {@code PlayerConnectionMediator} is then associated with the WebSocket
 * session via {@link #setMediator(Session, PlayerConnectionMediator)}. When
 * future messages are received via the WebSocket, the
 * {@code PlayerConnectionMediator} for the session is retrieved via
 * {@link #getMediator(Session)}, and then used to handle the incoming message.
 * <p>
 * CDI will create this (the {@code PlayerSessionManager} as an application
 * scoped bean. This bean will be created when the application starts, and can
 * be injected into other CDI-managed beans for as long as the application is
 * valid.
 * </p>
 *
 * @see ApplicationScoped
 */
@ApplicationScoped
public class PlayerConnectionManager implements Runnable {
    static final String CREATE_SESSION = "{\"type\": \"joinpart\",\"content\": \"building a mediator\",\"bookmark\": 0}";
    static final String RESUME_SESSION = "{\"type\": \"joinpart\",\"content\": \"reconstituting mediator\",\"bookmark\": 0}";

    private final ConcurrentHashMap<String, PlayerConnectionMediator> suspendedMediators = new ConcurrentHashMap<String, PlayerConnectionMediator>();

    /** CDI injection of Java EE7 Managed scheduled executor service */
    @Resource
    protected ManagedScheduledExecutorService executor;

    /**
     * CDI injection of Connection Utilities (consistent send/receive/error
     * handling)
     */
    @Inject
    ConnectionUtils connectionUtils;

    /** CDI injection of client for Map */
    @Inject
    MapClient mapClient;

    /** CDI injection of client for Player CRUD operations */
    @Inject
    PlayerClient playerClient;

    // Keystore info for jwt parsing / creation.
    @Resource(lookup = "jwtKeyStore")
    String keyStore;
    @Resource(lookup = "jwtKeyStorePassword")
    String keyStorePW;
    @Resource(lookup = "jwtKeyStoreAlias")
    String keyStoreAlias;

    /** JWT Signing key */
    private Key signingKey = null;

    private AtomicReference<ScheduledFuture<?>> reaper = new AtomicReference<ScheduledFuture<?>>(null);

    @Override
    public void run() {
        Log.log(Level.FINEST, this, "start culling sessions: " + suspendedMediators.size());
        Iterator<Entry<String, PlayerConnectionMediator>> entries = suspendedMediators.entrySet().iterator();
        while (entries.hasNext()) {
            Entry<String, PlayerConnectionMediator> i = entries.next();
            Log.log(Level.FINEST, this, "evaluating session " + i.getValue());
            if (i.getValue().incrementAndGet() > 5) {
                entries.remove();
                i.getValue().destroy();
            }
        }

        updateReaper();

        Log.log(Level.FINEST, this, "End culling sessions");
    }

    private void updateReaper() {
        if (suspendedMediators.isEmpty()) {
            // no more suspended sessions, clear the reaper rather than
            // resetting
            reaper.set(null);
        } else {
            // We still have suspended sessions, reschedule for 2 minutes from
            // now.
            reaper.set(executor.schedule(this, 2, TimeUnit.MINUTES));
        }
    }

    /**
     * Set the PlayerSession into the websocket session user properties.
     *
     * @param session
     *            target websocket session
     * @param mediator
     *            {@code PlayerConnectionMediator}
     */
    public void setMediator(Session session, PlayerConnectionMediator mediator) {
        session.getUserProperties().put(PlayerConnectionMediator.class.getName(), mediator);
    }

    /**
     * Get the mediator from the websocket session user properties.
     *
     * @param session
     *            source websocket session
     * @return cached {@code PlayerConnectionMediator}
     */
    public PlayerConnectionMediator getMediator(Session session) {
        if (session == null || session.getUserProperties() == null)
            return null;

        return (PlayerConnectionMediator) session.getUserProperties().get(PlayerConnectionMediator.class.getName());
    }

    /**
     * Obtain the key we'll use to sign the jwts we issue.
     *
     * @throws IOException
     *             if there are any issues with the keystore processing.
     */
    @PostConstruct
    private void getKeyStoreInfo() throws IOException {
        try {
            // load up the keystore..
            FileInputStream is = new FileInputStream(keyStore);
            KeyStore signingKeystore = KeyStore.getInstance(KeyStore.getDefaultType());
            signingKeystore.load(is, keyStorePW.toCharArray());

            // grab the key we'll use to sign
            signingKey = signingKeystore.getKey(keyStoreAlias, keyStorePW.toCharArray());

        } catch (KeyStoreException e) {
            throw new IOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        } catch (CertificateException e) {
            throw new IOException(e);
        } catch (UnrecoverableKeyException e) {
            throw new IOException(e);
        }
    }

    /**
     * Create a new {@code PlayerConnectionMediator} to mediate between the
     * client and the room
     *
     * @param clientSession
     *            WebSocket session for the connection between the client and
     *            the player
     * @param userId
     *            User's unique id
     * @param clientCache
     *            Information from the client: updated room, last message seen
     * @return a new or resumed {@code PlayerConnectionMediator}
     * @throws IOException
     *             if the keystore for the JWT processing cannot be used.
     */
    public PlayerConnectionMediator startSession(Session clientSession, String userId, RoutedMessage message)
            throws IOException {

        JsonObject sessionData = message.getParsedBody();

        String mediatorId = sessionData.getString(Constants.MEDIATOR_ID, null);
        String username = sessionData.getString(Constants.USERNAME, null);

        String roomId = message.getOptionalValue(Constants.ROOM_ID, null);
        long lastmessage = message.getOptionalValue(Constants.BOOKMARK, 0);

        PlayerConnectionMediator mediator = null;
        if (mediatorId != null) {
            mediator = suspendedMediators.remove(mediatorId);
        }

        if (mediator == null) {
            connectionUtils.sendMessage(clientSession, RoutedMessage.createMessage(Constants.PLAYER, userId, CREATE_SESSION));
            String newJwt = (String) clientSession.getUserProperties().get("jwt");
            mediator = new PlayerConnectionMediator(userId, username, newJwt, mapClient, playerClient,
                    connectionUtils);
            Log.log(Level.FINER, this, "Created new session {0} for user {1}", mediator, userId);
        } else {
            connectionUtils.sendMessage(clientSession, RoutedMessage.createMessage(Constants.PLAYER, userId, RESUME_SESSION));
            Log.log(Level.FINER, this, "Resuming session session {0} for user {1}", mediator, userId);
        }

        try {
            mediator.connect(clientSession, roomId, lastmessage);
        } catch(Exception e) {
            mediator.sendToClient(RoutedMessage.createMessage(Constants.PLAYER, userId, PlayerConnectionMediator.BAD_RIDE));
            mediator.connect(clientSession, "firstroom", 0);
        }
        return mediator;
    }

    /**
     * Move the {@code PlayerConnectionMediator} to the list of suspended
     * mediators.
     *
     * @see PlayerServerEndpoint#onClose(String, Session,
     *      javax.websocket.CloseReason)
     */
    public void suspendMediator(PlayerConnectionMediator mediator) {
        Log.log(Level.FINER, this, "Suspending session {0}", mediator);
        if (mediator != null) {
            suspendedMediators.put(mediator.getId(), mediator);
            mediator.disconnect();

            if (reaper.get() == null) {
                updateReaper();
            }
        }
    }

    public boolean validateJwt(String userId, Session clientSession) {
        // create ourselves a token for server operations for this user.

        // get the jwt from the ws query url.
        String jwtParam = null;
        String query = clientSession.getQueryString();
        if((query != null) && !query.isEmpty()) {
            String params[] = query.split("&");
            for (String param : params) {
                if (param.startsWith("jwt=")) {
                    jwtParam = param.substring("jwt=".length());
                }
            }
        }

        JWT jwt = new JWT(signingKey, jwtParam);
        if ( JWT.AuthenticationState.PASSED == jwt.getState()) {
            Claims onwardsClaims = Jwts.claims();
            // add all the client claims
            onwardsClaims.putAll(jwt.getClaims());
            // upgrade the type to server
            onwardsClaims.setAudience("server");

            // build the new jwt
            String newJwt = Jwts.builder().setHeaderParam("kid", "playerssl")
                    .setClaims(onwardsClaims)
                    .signWith(SignatureAlgorithm.RS256, signingKey).compact();

            clientSession.getUserProperties().put("jwt", newJwt);
            return true;
        } else {
            // Invalid JWT: Close the connection & provide a reason
            connectionUtils.tryToClose(clientSession, new CloseReason(CloseCodes.VIOLATED_POLICY, jwt.getCode().getReason()));
            return false;
        }
    }
}
