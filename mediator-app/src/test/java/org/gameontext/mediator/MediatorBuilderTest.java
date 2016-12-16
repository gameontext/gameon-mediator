package org.gameontext.mediator;

import java.lang.reflect.Field;
import java.text.MessageFormat;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import javax.enterprise.concurrent.ManagedScheduledExecutorService;
import javax.enterprise.concurrent.ManagedThreadFactory;
import javax.websocket.Session;

import org.gameontext.mediator.ClientMediator;
import org.gameontext.mediator.Constants;
import org.gameontext.mediator.Log;
import org.gameontext.mediator.MapClient;
import org.gameontext.mediator.MediatorBuilder;
import org.gameontext.mediator.MediatorNexus;
import org.gameontext.mediator.PlayerClient;
import org.gameontext.mediator.WSDrain;
import org.gameontext.mediator.MediatorNexus.ClientMediatorPod;
import org.gameontext.mediator.MediatorNexus.UserView;
import org.gameontext.mediator.models.Exit;
import org.gameontext.mediator.models.Exits;
import org.gameontext.mediator.models.RoomInfo;
import org.gameontext.mediator.models.Site;
import org.gameontext.mediator.room.FirstRoom;
import org.gameontext.mediator.room.RemoteRoomProxy;
import org.gameontext.mediator.room.RoomMediator;
import org.gameontext.mediator.room.RoomMediator.Type;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import mockit.Expectations;
import mockit.Injectable;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import mockit.Tested;
import mockit.Verifications;
import mockit.integration.junit4.JMockit;

/**
 * @author elh
 *
 */
@RunWith(JMockit.class)
public class MediatorBuilderTest {

    @Tested MediatorBuilder builder;

    @Injectable MediatorNexus nexus;
    @Injectable MapClient mapClient;
    @Injectable PlayerClient playerClient;

    @Injectable ManagedThreadFactory threadFactory;
    @Injectable ManagedScheduledExecutorService scheduledExecutor;

    static final String signedJwt = "testJwt";
    static final String userId = "dummy.DevUser";
    static final String userName = "DevUser";
    static final UserView userView = new UserView() {
        @Override
        public String getUserId() {
            return userId;
        }

        @Override
        public String getUserName() {
            // TODO Auto-generated method stub
            return userName;
        }
    };

    static final String roomId = "roomId";
    static final String roomName = "roomName";
    static final String roomFullName = "roomFullName";

    @Rule
    public TestName testName = new TestName();

    @Before
    public void before() {
        System.out.println("-- " + testName.getMethodName() + " --------------------------------------");

        new MockUp<Log>() {
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
    public void testPostConstruct() {
        builder.postConstruct();

        new Verifications() {{
            nexus.setBuilder(builder);
        }};
    }

    @Test
    public void testBuildClientMediator(@Mocked Session session,
            @Mocked WSDrain drain) {

        new Expectations() {{
            drain.start();
            new WSDrain(userId, session); result = drain;
        }};

        ClientMediator client = builder.buildClientMediator(userId, session, signedJwt);
        Assert.assertEquals(userId, client.getUserId());

        new Verifications() {{
            drain.start(); times = 1; // drain to client should be started
        }};
    }

    @Test
    public void testGetFirstRoomMediator(@Mocked ClientMediatorPod client,
            @Mocked Site firstRoomSite) {

        new Expectations() {{
            mapClient.getSite(Constants.FIRST_ROOM); result = firstRoomSite;
        }};

        FirstRoom fr = (FirstRoom) builder.getFirstRoomMediator(client);
        Assert.assertEquals(Type.FIRST_ROOM, fr.getType());
        Assert.assertEquals(Constants.FIRST_ROOM, fr.getName());
    }

    @Test
    public void testGetFirstRoomMediatorNullSite(@Mocked ClientMediatorPod client) {
        new Expectations() {{
            mapClient.getSite(Constants.FIRST_ROOM); result = null;
        }};

        FirstRoom fr = (FirstRoom) builder.getFirstRoomMediator(client);
        Assert.assertEquals(Type.FIRST_ROOM, fr.getType());
        Assert.assertEquals(Constants.FIRST_ROOM, fr.getName());
    }

    @Test
    public void testFindMediatorForRoomNull(@Mocked ClientMediatorPod client) {
        RoomMediator room = builder.findMediatorForRoom(client, null);
        Assert.assertEquals(Type.FIRST_ROOM, room.getType());
        Assert.assertEquals(Constants.FIRST_ROOM, room.getName());
    }

    @Test
    public void testFindMediatorForRoomEmpty(@Mocked ClientMediatorPod client) {
        RoomMediator room = builder.findMediatorForRoom(client, "");
        Assert.assertEquals(Type.FIRST_ROOM, room.getType());
        Assert.assertEquals(Constants.FIRST_ROOM, room.getName());
    }

    @Test
    public void testFindMediatorForRoomFirstRoom(@Mocked ClientMediatorPod client) {
        RoomMediator room = builder.findMediatorForRoom(client, Constants.FIRST_ROOM);
        Assert.assertEquals(Type.FIRST_ROOM, room.getType());
        Assert.assertEquals(Constants.FIRST_ROOM, room.getName());
    }

    @Test
    public void testFindMediatorForRoomNoSite(@Mocked ClientMediatorPod client) {
        new Expectations() {{
            mapClient.getSite(roomId); result = null;
        }};
        RoomMediator room = builder.findMediatorForRoom(client, roomId);
        Assert.assertEquals(Type.FIRST_ROOM, room.getType());
        Assert.assertEquals(Constants.FIRST_ROOM, room.getName());
    }

    @Test
    public void testFindMediatorForRoomEmptySite(@Mocked ClientMediatorPod client,
            @Mocked Site site) {
        new Expectations() {{
            mapClient.getSite(roomId); result = site;
            site.getInfo(); result = null;
        }};
        RoomMediator room = builder.findMediatorForRoom(client, roomId);
        Assert.assertEquals(Type.EMPTY, room.getType());
    }

    @Test
    public void testFindMediatorForRoomSite(@Mocked ClientMediatorPod client,
            @Mocked Site site,
            @Mocked RoomInfo info) {
        new Expectations() {{
            mapClient.getSite(roomId); result = site;
            site.getInfo(); result = info;
            info.getName(); result = roomName;
        }};
        RoomMediator room = builder.findMediatorForRoom(client, roomId);
        Assert.assertEquals(Type.CONNECTING, room.getType());
        Assert.assertEquals(roomName, room.getName());
    }

    @Test
    public void testFindMediatorForExitNoTarget(@Mocked ClientMediatorPod client,
            @Mocked RoomMediator startingRoom,
            @Mocked Exits exits) {
        new Expectations() {{
            startingRoom.getExits(); result = exits;
            exits.getExit("N"); result = null;
        }};

        RoomMediator room = builder.findMediatorForExit(client, startingRoom, "N");
        Assert.assertSame(startingRoom, room);
    }

    @Test
    public void testFindMediatorForExitFirstRoom(@Mocked ClientMediatorPod client,
            @Mocked RoomMediator startingRoom,
            @Mocked Exits exits) {

        new Expectations() {{
            startingRoom.getExits(); result = exits;
            exits.getExit("N").getId(); result = Constants.FIRST_ROOM;
        }};

        RoomMediator room = builder.findMediatorForExit(client, startingRoom, "N");
        Assert.assertEquals(Type.FIRST_ROOM, room.getType());
        Assert.assertEquals(Constants.FIRST_ROOM, room.getName());
    }

    @Test
    public void testFindMediatorForExitNoSite(@Mocked ClientMediatorPod client,
                                              @Mocked RoomMediator startingRoom,
                                              @Mocked Exit north) {
        Exits exits = new Exits();
        exits.setN(north);

        new Expectations() {{
            startingRoom.getExits(); result = exits;
            north.getId(); result = roomId;
            mapClient.getSite(roomId); result = null;
        }};

        // Fallback site built from original exit connection details
        RoomMediator room = builder.findMediatorForExit(client, startingRoom, "N");

        // The generated fallback exit should have starting room to the opposite
        // side (S), since we're attempting to use startingRoom's north door.
        Exits genExits = room.getExits();
        Assert.assertEquals(roomId, genExits.getS().getId());

        // The fallback uses the original exit information, which
        // includes connection details to make a first pass with..
        Assert.assertEquals(Type.CONNECTING, room.getType());
    }

    @Test
    public void testFindMediatorForExitEmptySite(@Mocked ClientMediatorPod client,
            @Mocked RoomMediator startingRoom,
            @Mocked Exits exits,
            @Mocked Site site) {

        new Expectations() {{
            startingRoom.getExits(); result = exits;
            exits.getExit("N").getId(); result = roomId;
            mapClient.getSite(roomId); result = site;
            site.getInfo(); result = null;
        }};
        RoomMediator room = builder.findMediatorForExit(client, startingRoom, "N");
        Assert.assertEquals(Type.EMPTY, room.getType());
    }

    @Test
    public void testFindMediatorForExitSite(@Mocked ClientMediatorPod client,
            @Mocked RoomMediator startingRoom,
            @Mocked Exits exits,
            @Mocked Site site,
            @Mocked RoomInfo info) {

        new Expectations() {{
            startingRoom.getExits(); result = exits;
            exits.getExit("N").getId(); result = roomId;
            mapClient.getSite(roomId); result = site;
            site.getInfo(); result = info;
        }};
        RoomMediator room = builder.findMediatorForExit(client, startingRoom, "N");
        Assert.assertEquals(Type.CONNECTING, room.getType());
    }

    @Test
    public void testCreateDelegateEmpty(@Mocked Site site1) {
        new Expectations() {{
            site1.getId(); result = roomId;
            site1.getInfo(); result = null;
        }};

        RemoteRoomProxy proxy = new RemoteRoomProxy(builder, userView, site1.getId());
        Assert.assertEquals(Type.EMPTY, proxy.getType()); // proxy type should reflect the guts!
    }

    @Test
    public void testCreateConnectingDelegateHelloBadConnectionType(@Mocked Site site1,
                                   @Mocked RoomInfo info,
                                   @Mocked ClientMediatorPod pod1) throws Exception {

        // Special mock of sched exec to run immediately
        ManagedScheduledExecutorService executor = new MockUp<ManagedScheduledExecutorService>() {
            @Mock
            public void execute(Runnable command) {
                System.out.println("HEY!!!");
                command.run();
            }
        }.getMockInstance();

        builder.scheduledExecutor = executor;


        new Expectations() {{
            site1.getId(); result = roomId;
            site1.getInfo(); result = info;
            info.getName(); result = roomName;
            info.getFullName(); result = roomFullName;
            info.getConnectionDetails().getType(); result = "unknown";
        }};

        RemoteRoomProxy proxy = new RemoteRoomProxy(builder, userView, roomId);

        Field field_updating = RemoteRoomProxy.class.getDeclaredField("updating");
        field_updating.setAccessible(true);
        AtomicBoolean updating = (AtomicBoolean) field_updating.get(proxy);

        Assert.assertEquals(Type.CONNECTING, proxy.getType()); // proxy type should reflect the guts!

        // Attempt connection with bad type
        proxy.hello(pod1);

        Assert.assertFalse("Updating flag should be reset to false", updating.get());
        Assert.assertEquals(Type.SICK, proxy.getType()); // proxy type should reflect the guts!
    }

    @Test
    public void testCreateConnectingDelegateHello(@Mocked Site site1,
                                   @Mocked RoomInfo info) {
        new Expectations() {{
            site1.getId(); result = roomId;
            site1.getInfo(); result = info;
        }};

        RemoteRoomProxy proxy = new RemoteRoomProxy(builder, userView, site1.getId());
        Assert.assertEquals(Type.CONNECTING, proxy.getType()); // proxy type should reflect the guts!

        // Attept connection
    }

    @Test
    public void testCreateConnectingDelegateJoin(@Mocked Site site1,
                                   @Mocked RoomInfo info) {
        new Expectations() {{
            site1.getId(); result = roomId;
            site1.getInfo(); result = info;
        }};

        RemoteRoomProxy proxy = new RemoteRoomProxy(builder, userView, site1.getId());
        Assert.assertEquals(Type.CONNECTING, proxy.getType()); // proxy type should reflect the guts!

        // Attept connection
    }

}
