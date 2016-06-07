package net.wasdev.gameon.mediator.room;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import javax.json.JsonValue;

import net.wasdev.gameon.mediator.Log;
import net.wasdev.gameon.mediator.MediatorBuilder;
import net.wasdev.gameon.mediator.MediatorNexus;
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
    final String userId;
    volatile RoomMediator delegate;

    AtomicBoolean updating = new AtomicBoolean(false);

    /**
     * Creates a new proxy. Calls {@link MediatorBuilder#createDelegate(RemoteRoomProxy, String, Site)}
     * to build the initial delegate.
     *
     * @param mediatorBuilder
     * @param userId
     * @param site
     */
    public RemoteRoomProxy(MediatorBuilder mediatorBuilder, String userId, Site site) {
        this.mediatorBuilder = mediatorBuilder;
        this.userId = userId;
        delegate = mediatorBuilder.createDelegate(this, userId, site);
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
            mediatorBuilder.updateDelegate(this, delegate, userId, site);
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
     * Callback when the remote connection is disrupted. Go back to the
     * mediatorBuilder to start again.
     * @param roomHello
     */
    public void connectRemote(boolean roomHello) {
        if ( updating.compareAndSet(false, true)) {
            Log.log(Level.FINEST, this, "RemoteRoomProxy -- connect");
            mediatorBuilder.updateDelegate(this, delegate, userId, roomHello);
        } else {
            Log.log(Level.FINEST, this, "RemoteRoomProxy -- connect in progress");
        }
    }

    /**
     * Called after remote connection has dropped, or on a schedule (sick room)
     *
     */
    public void reconnect() {
        if ( updating.compareAndSet(false, true)) {
            Log.log(Level.FINEST, this, "RemoteRoomProxy -- reconnect {0}", delegate.getFullName());
            mediatorBuilder.updateDelegate(this, delegate, userId, null); // refresh site
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
