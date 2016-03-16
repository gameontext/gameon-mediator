package net.wasdev.gameon.mediator;

import static mockit.Deencapsulation.setField;
import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;

import mockit.Expectations;
import mockit.Mocked;
import mockit.integration.junit4.JMockit;
import net.wasdev.gameon.mediator.models.Exit;
import net.wasdev.gameon.mediator.models.Exits;
import net.wasdev.gameon.mediator.models.Site;

@RunWith(JMockit.class)
public class RemoteRoomMediatorTest {
    
    @Test
    public void testGetExits(@Mocked Exit exit, 
            @Mocked MapClient mapClient, 
            @Mocked ConnectionUtils connectionUtils,
            @Mocked Site site,
            @Mocked Exits exits){
                
        new Expectations() {{  
            exit.getId(); returns("FISH");
            exit.getConnectionDetails(); 
            exit.getName();
            exit.getFullName();
            mapClient.getSite("FISH"); returns(site);
            site.getExits(); returns(exits);
            
        }};
        
        RemoteRoomMediator r = new RemoteRoomMediator(exit, mapClient, connectionUtils);
        setField(r,"lastCheck",0);
        Exits e = r.getExits();
        
        assertEquals("Exits object as expected",e,exits);
    }
}
