package org.gameontext.mediator.room;

import static org.junit.Assert.fail;

import java.text.MessageFormat;
import java.util.logging.Level;

import org.gameontext.mediator.Log;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import mockit.Mock;
import mockit.MockUp;

@Ignore
public class RoomMediatorProxyTest {

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
    public void testRoomMediatorProxy() {
        fail("Not yet implemented");
    }

    @Test
    public void testGetType() {
        fail("Not yet implemented");
    }

    @Test
    public void testGetId() {
        fail("Not yet implemented");
    }

    @Test
    public void testGetName() {
        fail("Not yet implemented");
    }

    @Test
    public void testGetFullName() {
        fail("Not yet implemented");
    }

    @Test
    public void testGetDescription() {
        fail("Not yet implemented");
    }

    @Test
    public void testListExits() {
        fail("Not yet implemented");
    }

    @Test
    public void testGetExits() {
        fail("Not yet implemented");
    }

    @Test
    public void testUpdateInformation() {
        fail("Not yet implemented");
    }

    @Test
    public void testGetEmergencyReturnExit() {
        fail("Not yet implemented");
    }

    @Test
    public void testHello() {
        fail("Not yet implemented");
    }

    @Test
    public void testGoodbye() {
        fail("Not yet implemented");
    }

    @Test
    public void testJoin() {
        fail("Not yet implemented");
    }

    @Test
    public void testPart() {
        fail("Not yet implemented");
    }

    @Test
    public void testSendToRoom() {
        fail("Not yet implemented");
    }

    @Test
    public void testSendToClients() {
        fail("Not yet implemented");
    }

    @Test
    public void testDisconnect() {
        fail("Not yet implemented");
    }

    @Test
    public void testSameInfo() {
        fail("Not yet implemented");
    }

}
