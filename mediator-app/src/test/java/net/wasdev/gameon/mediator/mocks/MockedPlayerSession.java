package net.wasdev.gameon.mediator.mocks;

import net.wasdev.gameon.mediator.ConnectionUtils;
import net.wasdev.gameon.mediator.MapClient;
import net.wasdev.gameon.mediator.PlayerClient;
import net.wasdev.gameon.mediator.PlayerConnectionMediator;
import net.wasdev.gameon.mediator.RoutedMessage;

public class MockedPlayerSession extends PlayerConnectionMediator {

    private RoutedMessage lastClientMessage;

    public MockedPlayerSession(String userId, String username, String jwt, MapClient mapClient,
            PlayerClient playerClient, ConnectionUtils connectionUtils) {
        super(userId, username, jwt, mapClient, playerClient, connectionUtils, true);
    }
    
    public MockedPlayerSession() {
        super(null, null, null, null, null, null, true);
    }

    @Override
    public void sendToClient(RoutedMessage message) {
        System.out.println("New message: " + message);
        lastClientMessage = message;
    }
    
    public RoutedMessage getLastClientMessage() {
        return lastClientMessage;
    }

}
