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
package org.gameontext.mediator;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.logging.Level;

import javax.websocket.CloseReason;
import javax.websocket.Session;

/**
 * Encapsulation of a drain. Uses the {@code ManagedThreadFactory} to create
 * a dedicated thread that will drain the queue as messages arrive.
 *
 */
class WSDrain implements Runnable, Drain {
    private final String id;
    private Thread thread;
    private Session targetSession;
    boolean wsToRoom;

    /** Queue of messages  */
    private final LinkedBlockingDeque<RoutedMessage> pendingMessages;

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
    public WSDrain(String id, Session targetSession) {
        this.id = id;
        this.targetSession = targetSession;
        this.pendingMessages = new LinkedBlockingDeque<RoutedMessage>();
        this.wsToRoom = false; // outbound client connection
    }

    public WSDrain(String id) {
        this.id = id;
        this.pendingMessages = new LinkedBlockingDeque<RoutedMessage>();
        this.wsToRoom = true; // incoming server connection
    }

    @Override
    public void send(RoutedMessage message) {
        pendingMessages.offer(message);
    }

    @Override
    public void close(CloseReason reason) {
        WSUtils.tryToClose(targetSession, reason);
    }

    @Override
    public void run() {

        Log.log(Level.FINER, this, "DRAIN OPEN {0}", id);
        boolean interrupted = false;

        // Dedicated thread sending messages to the room as fast
        // as it can take them: maybe we batch these someday.
        while (keepGoing) {
            try {
                RoutedMessage message = pendingMessages.take();

                if ( wsToRoom ) {
                    Log.log(Level.FINEST, this, "C    M -> R : {0} {1}", message, targetSession.getId());
                } else {
                    Log.log(Level.FINEST, this, "C <- M    R : {0} {1}", message, targetSession.getId());
                }

                try {
                    if (!WSUtils.sendMessage(targetSession, message)) {
                        // If the send failed, tuck the message back in the
                        // head of the queue.
                        pendingMessages.offerFirst(message);
                    }
                } catch (IllegalStateException e) {
                    // write not allowed because another in progress. Try again.
                    pendingMessages.offerFirst(message);
                }
            } catch (InterruptedException ex) {
                interrupted = true;
            }
        }

        Log.log(Level.FINER, this, "DRAIN CLOSED {0}", id);

        // this really needs to not be in the stop method.
        WSUtils.tryToClose(targetSession);

        // reset interrupted flag
        if (interrupted)
            Thread.currentThread().interrupt();
    }

    @Override
    public void start() {
        if ( targetSession == null )
            return;
        thread.start();
    }

    @Override
    public void start(Session session) {
        this.targetSession = session;
        thread.start();
    }


    @Override
    public void stop() {
        keepGoing = false;

        // Interrupt the other thread
        if (thread != null) {
            thread.interrupt();
        }
    }

    public void setThread(Thread t) {
        this.thread = t;
    }
}
