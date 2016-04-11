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

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import javax.annotation.Resource;
import javax.enterprise.concurrent.ManagedScheduledExecutorService;
import javax.enterprise.concurrent.ManagedThreadFactory;
import javax.enterprise.context.ApplicationScoped;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.EncodeException;
import javax.websocket.Endpoint;
import javax.websocket.RemoteEndpoint.Basic;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

/**
 * A common set of utilities to manage websocket connections.
 * <p>
 * CDI will create this (the {@code ConnectionUtils} as an application scoped
 * bean. This bean will be created when the application starts, and can be
 * injected into other CDI-managed beans for as long as the application is
 * valid.
 * </p>
 *
 * @see ApplicationScoped
 */
@ApplicationScoped
public class ConnectionUtils {

    /** CDI injection of Java EE7 Managed thread factory */
    @Resource
    protected ManagedThreadFactory threadFactory;

    /** CDI injection of Java EE7 Managed scheduled executor service */
    @Resource
    protected ManagedScheduledExecutorService executor;

    public ScheduledExecutorService getScheduledExecutorService() {
        return executor;
    }

    /**
     * Simple text based broadcast.
     *
     * @param session
     *            Target session (used to find all related sessions)
     * @param message
     *            {@link RoutedMessage} to send
     * @see #sendMessage(Session, RoutedMessage)
     */
    public void broadcast(Session session, RoutedMessage message) {
        for (Session s : session.getOpenSessions()) {
            sendMessage(s, message);
        }
    }

    /**
     * Try sending the {@link RoutedMessage} using
     * {@link Session#getBasicRemote()}, {@link Basic#sendObject(Object)}.
     *
     * @param session
     *            Session to send the message on
     * @param message
     *            {@link RoutedMessage} to send
     * @return true if send was successful, or false if it failed
     */
    public boolean sendMessage(Session session, RoutedMessage message) {
        if (session.isOpen()) {
            try {
                session.getBasicRemote().sendObject(message);
                return true;
            } catch (EncodeException e) {
                Log.log(Level.FINEST, session, "Unexpected condition writing message", e);
                // Something was wrong encoding this message, but the connection
                // is likely just fine.
            } catch (IOException ioe) {
                // An IOException, on the other hand, suggests the connection is
                // in a bad state.
                Log.log(Level.FINEST, session, "Unexpected condition writing message", ioe);
                tryToClose(session, new CloseReason(CloseCodes.UNEXPECTED_CONDITION, trimReason(ioe.toString())));
            }
        }
        return false;
    }

    /**
     * {@code CloseReason} can include a value, but the length of the text is
     * limited.
     *
     * @param message
     *            String to trim
     * @return a string no longer than 123 characters.
     */
    private static String trimReason(String message) {
        return message.length() > 123 ? message.substring(0, 123) : message;
    }

    /**
     * Create an outbound/client websocket connection
     *
     * @param endpoint
     *            Programmatic websocket endpoint instance
     * @param cec
     *            Client websocket endpoint configuration
     * @param uriServerEP
     *            target websocket uri
     *
     * @return Established websocket session
     * @throws DeploymentException
     *             if the configuration is invalid
     * @throws IOException
     *             if there was a network or protocol issue
     */
    public Session connectToServer(Endpoint endpoint, ClientEndpointConfig cec, URI uriServerEP)
            throws DeploymentException, IOException {
        WebSocketContainer c = ContainerProvider.getWebSocketContainer();
        return c.connectToServer(endpoint, cec, uriServerEP);
    }

    /**
     * Try to close the WebSocket session and give a reason for doing so.
     *
     * @param s
     *            Session to close
     * @param reason
     *            {@link CloseReason} the WebSocket is closing.
     */
    public void tryToClose(Session s, CloseReason reason) {
        try {
            s.close(reason);
        } catch (IOException e) {
            tryToClose(s);
        }
    }

    /**
     * Try to close a {@code Closeable} (usually once an error has already
     * occurred).
     *
     * @param c
     *            Closable to close
     */
    public void tryToClose(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException e1) {
            }
        }
    }

    /**
     * Utility method to drain a queue: will write all pending messages (as they
     * arrive) to the websocket. Assumes a connection break will result in the
     * onClose method being driven elsewhere.
     *
     * @param id
     * @param pending
     * @param targetSession
     * @return
     */
    public Drain drain(String id, LinkedBlockingDeque<RoutedMessage> pending, Session targetSession) {
        Drain d = new Drain(id, pending, targetSession, this);
        threadFactory.newThread(d).start();
        return d;
    }

    /**
     * Encapsulation of a drain. Uses the {@code ManagedThreadFactory} to create
     * a dedicated thread that will drain the queue as messages arrive.
     *
     */
    static class Drain implements Runnable {
        private final CountDownLatch ended = new CountDownLatch(1);
        private final Session targetSession;
        private final LinkedBlockingDeque<RoutedMessage> pendingMessages;
        private final String id;
        private Thread t;
        private ConnectionUtils connectionUtils;

        private volatile boolean keepGoing = true;

        /**
         * Construct a drain around the given message queue.
         *
         * @param id
         *            An identifier for the drain (used in logs)
         * @param pending
         *            The queue for pending messages
         * @param targetSession
         *            The target session to publish queued messages
         * @param connectionUtils
         *            Instance of connection utils for managing the connections
         *            (provided by the CDI bean that creates/manages this drain)
         */
        public Drain(String id, LinkedBlockingDeque<RoutedMessage> pending, Session targetSession,
                ConnectionUtils connectionUtils) {
            this.id = id;
            this.targetSession = targetSession;
            this.pendingMessages = pending;
            this.connectionUtils = connectionUtils;
        }

        @Override
        public void run() {
            t = Thread.currentThread();

            boolean interrupted = false;

            // Dedicated thread sending messages to the room as fast
            // as it can take them: maybe we batch these someday.
            while (keepGoing) {
                try {
                    RoutedMessage message = pendingMessages.take();
                    Log.log(Level.FINEST, this, "Sending to {0} ({1}): {2}", id, targetSession.isOpen(), message);

                    try {
                        if (!connectionUtils.sendMessage(targetSession, message)) {
                            // If the send failed, tuck the message back in the
                            // head of the queue.
                            pendingMessages.offerFirst(message);
                        }
                    } catch (IllegalStateException e) {
                        // write not allowed because another in progress. Try
                        // again.
                        pendingMessages.offerFirst(message);
                    }
                } catch (InterruptedException ex) {
                    interrupted = true;
                }
            }
            Log.log(Level.FINER, this, "END {0}", id);

            // reset interrupted flag
            if (interrupted)
                Thread.currentThread().interrupt();

            ended.countDown();
        }


        public void stop() {
            keepGoing = false;

            // Interrupt the other thread
            if (t != null) {
                t.interrupt();

                // Wait for the interrupted thread to finish
                try {
                    ended.await(400, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                t = null;
            }

            connectionUtils.tryToClose(targetSession);
        }
    }

}
