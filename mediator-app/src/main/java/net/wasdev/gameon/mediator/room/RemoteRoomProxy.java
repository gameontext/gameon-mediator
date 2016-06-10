package net.wasdev.gameon.mediator.room;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import javax.json.JsonValue;

import net.wasdev.gameon.mediator.Log;
import net.wasdev.gameon.mediator.MediatorBuilder;
import net.wasdev.gameon.mediator.MediatorBuilder.UpdateType;
import net.wasdev.gameon.mediator.MediatorNexus;
import net.wasdev.gameon.mediator.MediatorNexus.UserView;
import net.wasdev.gameon.mediator.RoutedMessage;
import net.wasdev.gameon.mediator.models.Exit;
import net.wasdev.gameon.mediator.models.Exits;
import net.wasdev.gameon.mediator.models.RoomInfo;
import net.wasdev.gameon.mediator.models.Site;

/**
 * The RemoteRoomProxy is a pass-through. It gives the ClientMediator a consistent RoomMediator,
 * insulating it from remote room lifecycle changes (especially in the face of remote pushes for
 * room updates).
 *
 *  Mediator creation and rebuilding is done in the MediatorBuilder, and room transitions are
 *  handled by the MediatorNexus, making this class fairly uninteresting. The bit that is important
 *  to follow is how the room delegates are built:
 *
 *  {@link #RemoteRoomProxy(MediatorBuilder, String, Site)} calls
 *  {@link MediatorBuilder#buildClientMediator(String, javax.websocket.Session, String)} to obtain
 *  the first delegate, either a {@link ConnectingRoom} or an {@link EmptyRoom}.
 *
 *  The {@link ConnectingRoom} and/or external events will trigger the connection to the
 *  remote room using {@link #connectRemote(boolean)} or {@link #updateInformation(Site)}.
 *  Once the update is complete, the {@link MediatorBuilder} will call
 *  {@link #updateComplete(RoomMediator)} to set the new delegate.
 *
 */
public class RemoteRoomProxy implements RoomMediator {

    final MediatorBuilder mediatorBuilder;
    final UserView user;
    final String roomId;
    volatile RoomMediator delegate;

    AtomicBoolean updating = new AtomicBoolean(false);

    /**
     * Creates a new proxy. Calls {@link MediatorBuilder#createDelegate(RemoteRoomProxy, UserView, Site)}
     * to build the initial delegate.
     *
     * @param mediatorBuilder
     * @param user
     * @param site
     */
    public RemoteRoomProxy(MediatorBuilder mediatorBuilder, UserView user, String roomId) {
        this.mediatorBuilder = mediatorBuilder;
        this.user = user;
        this.roomId = roomId;
        this.delegate = mediatorBuilder.createDelegate(this, user, roomId);
    }

    @Override
    public Type getType() {
        return delegate.getType();
    }

    @Override
    public String getId() {
        return delegate.getId();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public String getFullName() {
        return delegate.getFullName();
    }

    @Override
    public String getDescription() {
        return delegate.getDescription();
    }

    @Override
    public JsonValue listExits() {
        return delegate.listExits();
    }

    @Override
    public Exits getExits() {
        return delegate.getExits();
    }

    /**
     * Update the delegate based on new/refreshed site information.
     * EmptyRoom -> RemoteRoom or SickRoom is the most likely.
     *
     * @see net.wasdev.gameon.mediator.room.RoomMediator#updateInformation(net.wasdev.gameon.mediator.models.Site)
     */
    @Override
    public void updateInformation(Site site) {
        if ( updating.compareAndSet(false, true)) {
            Log.log(Level.FINEST, this, "RemoteRoomProxy -- update begin {0}", site);
            mediatorBuilder.updateDelegate(UpdateType.HELLO, this, delegate, site, user);
        } else {
            Log.log(Level.FINEST, this, "RemoteRoomProxy -- update in progress {0}", site);
        }
    }

    /**
     * Update complete. If the delegate has changed, disconnect the
     * old delegate (ensure cleanup)
     *
     * @see net.wasdev.gameon.mediator.room.RoomMediator#updateInformation(net.wasdev.gameon.mediator.models.Site)
     */
    public void updateComplete(RoomMediator newDelegate) {
        RoomMediator oldDelegate = delegate;

        try {
            if ( newDelegate != null ) {
                delegate = newDelegate;

                // Not a site transition, but perhaps a change in connection
                // information, or empty -> full, or sick -> healthy, or
                // healthy -> sick. Make sure previous delegate is cleaned up.
                if ( oldDelegate != delegate ) {
                    oldDelegate.disconnect();
                }
            }
        } finally {
            updating.set(false);
            Log.log(Level.FINEST, this, "RemoteRoomProxy -- update complete: {0}", delegate);
        }
    }

    /**
     * This is called by hello/join in ConnectingRoom to trigger initial
     * attempt at creating a remote delegate. This creates a new thread
     * to allow the hello method on ConnectingRoom to return promptly,
     * as it is w/in a synchronized block on the pod.
     *
     * @param roomHello true if this is triggered by a hello, false if
     * triggered by a join.
     */
    public void connectRemote(boolean roomHello) {
        if ( updating.compareAndSet(false, true)) {
            // Queue the initial connect to a different thread
            mediatorBuilder.execute(() -> {
                Log.log(Level.FINEST, this, "RemoteRoomProxy -- connect for {0}", user);
                UpdateType type = roomHello ? UpdateType.HELLO : UpdateType.JOIN;
                mediatorBuilder.updateDelegate(type, this, delegate, null, user);
            });
        } else {
            Log.log(Level.FINEST, this, "RemoteRoomProxy -- connect in progress");
        }
    }

    /**
     * Called after remote connection has dropped, or on a schedule (sick room)
     * This is an independently fired event/
     */
    public void reconnect() {
        if ( updating.compareAndSet(false, true)) {
            Log.log(Level.FINEST, this, "RemoteRoomProxy -- reconnect to {0} for {1}", delegate.getName(), user);
            mediatorBuilder.updateDelegate(UpdateType.RECONNECT, this, delegate, null, user); // refresh site
        } else {
            Log.log(Level.FINEST, this, "RemoteRoomProxy -- connect in progress");
        }
    }


    @Override
    public Exit getEmergencyReturnExit() {
        return delegate.getEmergencyReturnExit();
    }

    @Override
    public void hello(MediatorNexus.UserView user, boolean recovery) {
        delegate.hello(user, false);
    }

    @Override
    public void goodbye(MediatorNexus.UserView user) {
        delegate.goodbye(user);
    }

    @Override
    public void join(MediatorNexus.UserView user) {
        delegate.join(user);
    }

    @Override
    public void part(MediatorNexus.UserView user) {
        delegate.part(user);
    }

    @Override
    public void sendToRoom(RoutedMessage message) {
        delegate.sendToRoom(message);
    }

    @Override
    public void sendToClients(RoutedMessage message) {
        delegate.sendToClients(message);
    }

    @Override
    public void disconnect() {
        delegate.disconnect();
    }

    @Override
    public boolean sameConnectionDetails(RoomInfo localInfo) {
        return delegate.sameConnectionDetails(localInfo);
    }

    @Override
    public MediatorNexus.View getNexusView() {
        return delegate.getNexusView();
    }

    @Override
    public String toString() {
        return "Proxy[" + delegate.toString() + "]";
    }
}
