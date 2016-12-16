package org.gameontext.mediator.room;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.ClientEndpointConfig.Configurator;
import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.HandshakeResponse;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import org.gameontext.mediator.Drain;
import org.gameontext.mediator.Log;
import org.gameontext.mediator.MediatorNexus;
import org.gameontext.mediator.MediatorNexus.View;
import org.gameontext.mediator.RoutedMessage;
import org.gameontext.mediator.RoutedMessage.FlowTarget;
import org.gameontext.mediator.RoutedMessageDecoder;
import org.gameontext.mediator.RoutedMessageEncoder;
import org.gameontext.mediator.WSUtils;
import org.gameontext.mediator.models.ConnectionDetails;
import org.gameontext.mediator.models.RoomInfo;
import org.gameontext.mediator.models.Site;
import org.gameontext.signed.SignedRequestHmac;
import org.gameontext.signed.SignedRequestMap;

class WebSocketClientConnection extends Endpoint implements RemoteRoom.Connection {
    final static long MAX_PROTOCOL_VERSION = 2;
    
    /**
     * The WebSocket protocol version.
     */
    private long protocolVersion = 1;

    final RemoteRoomProxy proxy;
    final String id;
    final RoomInfo info;
    final Drain drain;
    final MediatorNexus.View nexus;

    GameOnHeaderAuthConfigurator authConfigurator;
    Session session;

    WebSocketClientConnection(RemoteRoomProxy proxy, View nexus, Drain drain, Site site) {
        this.proxy = proxy;
        this.nexus = nexus;
        this.drain = drain;
        this.id = site.getId();
        this.info = site.getInfo();
    }

    @Override
    public void connect() throws DeploymentException, IOException {
        ConnectionDetails details = info.getConnectionDetails();
        Log.log(Level.FINE, drain, "Creating websocket to {0}", details.getTarget());

        URI uriServerEP = URI.create(details.getTarget());

        authConfigurator = new GameOnHeaderAuthConfigurator(details.getToken(), uriServerEP.getRawPath());
        final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create()
                .decoders(Arrays.asList(RoutedMessageDecoder.class)).encoders(Arrays.asList(RoutedMessageEncoder.class))
                .configurator(authConfigurator)
                .build();

        WebSocketContainer c = ContainerProvider.getWebSocketContainer();
        this.session = c.connectToServer(this, cec, uriServerEP);
    }

    @Override
    public void sendToRoom(RoutedMessage message) {
        drain.send(message);
    }

    @Override
    public void disconnect() {
        Log.log(Level.FINE, drain, "Disconnecting... ");
        drain.stop();
    }

    @Override
    public long version() {
        return protocolVersion;
    }

    @Override
    public void onOpen(Session session, EndpointConfig config) {
        //check all validations passed before proceeding with the session
        if(!authConfigurator.isResponseValid()) {
            WSUtils.tryToClose(session,
                    new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "Handshake validation failed"));
            return;
        }

        // let the room mediator know the connection was opened
        Log.log(Level.FINER, drain, "ROOM CONNECTION OPEN {0}: {1}", id, this);
        drain.start(session);

        // Add message handler
        session.addMessageHandler(new MessageHandler.Whole<RoutedMessage>() {
            @Override
            public void onMessage(RoutedMessage message) {
                Log.log(Level.FINEST, drain, "C    M <- R : {0}", message);

                if(message.getFlowTarget() == FlowTarget.ack){
                    //ack from room is meant for us..
                    handleAck(message);
                } else {
                    try {
                        nexus.sendToClients(message);
                    } catch(Exception e) {
                        Log.log(Level.WARNING, session, "Uncaught exception handling client-bound message", e);
                    }
                }
            }
        });
    }

    @Override
    public void onClose(Session session, CloseReason closeReason) {
        // let the room mediator know the connection was closed
        Log.log(Level.FINER, drain, "ROOM CONNECTION CLOSED {0}: {1}", id, closeReason);
        drain.stop();

        if (nexus.stillConnected() && !closeReason.getCloseCode().equals(CloseCodes.NORMAL_CLOSURE)) {
            proxy.reconnect();
        }
    }

    @Override
    public void onError(Session session, Throwable thr) {
        Log.log(Level.FINEST, drain, "BADNESS " + session.getUserProperties(), thr);

        WSUtils.tryToClose(session,
                new CloseReason(CloseReason.CloseCodes.UNEXPECTED_CONDITION, thr.toString()));
    }

    /**
     * ack from room is sent to allow us to select a compatible websocket json protocol
     * currently we only support the one, maybe in future we'll add more.
     */
    private void handleAck(RoutedMessage ackack){
        JsonObject ackackobject = ackack.getParsedBody();
        JsonArray versions = ackackobject.getJsonArray("version");

        for(JsonValue version : versions){
            if(JsonValue.ValueType.NUMBER.equals(version.getValueType())){
                long value = ((JsonNumber) version).longValue();
                if ( value > protocolVersion && value <= MAX_PROTOCOL_VERSION ) {
                    protocolVersion = value;
                }
            }
        }
    }

    public class GameOnHeaderAuthConfigurator extends Configurator {
        private final SignedRequestHmac wsHmac;
        private boolean responseValid = false;

        /**
         * Constructor to be used by the client.
         *
         * @param userId
         *            the userId making this request.
         * @param secret
         *            the shared secret to use.
         */
        public GameOnHeaderAuthConfigurator(String secret, String uriPath) {
            this.wsHmac = secret == null ? null : new SignedRequestHmac("", secret, "", uriPath);
        }

        @Override
        public void beforeRequest(Map<String, List<String>> headers) {
            super.beforeRequest(headers);

            //create the HMAC for the room to validate, use a time stamp to allow the room to time-out requests
            if( wsHmac != null ) {
                wsHmac.signRequest(new SignedRequestMap.MLS_StringMap(headers));
            }
        }

        @Override
        public void afterResponse(HandshakeResponse hr) {
            super.afterResponse(hr);
            if( wsHmac != null ) {
                try {
                    Log.log(Level.FINEST, drain, "Validating HMAC supplied for WS");
                    wsHmac.wsVerifySignature(new SignedRequestMap.MLS_StringMap(hr.getHeaders()));
                    Log.log(Level.FINEST, drain, "Validating HMAC result is {0}", responseValid);
                } catch (Exception e) {
                    Log.log(Level.FINEST, drain, "Failed to validate HMAC, unable to establish connection", e);
                }

            } else {
                Log.log(Level.INFO, drain, "No token supplied for room, skipping WS handshake validation");
                responseValid = true;
            }
        }

        public boolean isResponseValid() {
            return responseValid;
        }
    }
}
