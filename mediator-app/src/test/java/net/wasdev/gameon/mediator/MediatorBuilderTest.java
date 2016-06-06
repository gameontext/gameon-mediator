package net.wasdev.gameon.mediator;

import static org.junit.Assert.fail;

import java.text.MessageFormat;
import java.util.logging.Level;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import mockit.integration.junit4.JMockit;

/**
 * @author elh
 *
 */
@RunWith(JMockit.class)
public class MediatorBuilderTest {

    @Mocked MapClient mapClient;
    @Mocked PlayerClient playerClient;
    @Mocked Drain drain;

    static final String signedJwt = "testJwt";
    static final String userId = "dummy.DevUser";
    static final String userName = "DevUser";
    
    static final String roomId = "roomId";
    static final String roomName = "roomName";
    static final String roomFullName = "roomFullName";
    
    @Rule
    public TestName testName = new TestName();
    
    @Before
    public void before() {
        System.out.println("-- " + testName.getMethodName() + " --------------------------------------");
        
        new MockUp<Log>()
        {
            @Mock
            public void log(Level level, Object source, String msg, Object[] params) {
                System.out.println("Log: " + MessageFormat.format(msg, params));
            }
            
            @Mock
            public void log(Level level, Object source, String msg, Throwable thrown) {
                System.out.println("Log: " + msg + ": " + thrown.getMessage());
                thrown.printStackTrace(System.out);
            }
        };
    }

    @Test
    public void testBuildClientMediator() {
        fail("Not yet implemented");
    }
    @Test
    public void testFindMediatorForRoom() {
        fail("Not yet implemented");
    }

    @Test
    public void testFindMediatorForExit() {
        fail("Not yet implemented");
    }

    @Test
    public void testGetFirstRoomMediator() {
        fail("Not yet implemented");
    }

}
