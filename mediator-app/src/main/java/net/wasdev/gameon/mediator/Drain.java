package net.wasdev.gameon.mediator;

import javax.websocket.CloseReason;
import javax.websocket.Session;

public interface Drain {

    void send(RoutedMessage message);

    void close(CloseReason reason);
    
    void start();
    
    void start(Session session);
    
    void stop();
}
