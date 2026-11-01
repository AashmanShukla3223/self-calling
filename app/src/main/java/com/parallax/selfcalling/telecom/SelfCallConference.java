package com.parallax.selfcalling.telecom;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Conference;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;

import com.parallax.selfcalling.util.Prefs;

import java.util.ArrayList;
import java.util.List;

/**
 * A self-call conference.
 *
 * <p>Shown by the native dialer with the Merge and Swap controls:
 * <ul>
 *     <li><b>Merge</b> becomes available when a participant is held (after a swap or when a
 *     call joins from the outside); pressing it brings every participant back into the call.</li>
 *     <li><b>Swap</b> rotates the foreground participant: the active one is held and the next
 *     one becomes active. A participant can also be separated or disconnected from the
 *     dialer's manage-conference screen.</li>
 * </ul>
 *
 * The conference also owns hold, mute and auto-end for the whole multi-party call.
 */
public class SelfCallConference extends Conference {

    private static final int BASE_CAPABILITIES =
            Connection.CAPABILITY_SUPPORT_HOLD
                    | Connection.CAPABILITY_HOLD
                    | Connection.CAPABILITY_MUTE
                    | Connection.CAPABILITY_MANAGE_CONFERENCE;

    private final Context ctx;
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean ended;

    public SelfCallConference(Context c, PhoneAccountHandle account) {
        super(account);
        ctx = c;
        // Mark as an ad-hoc conference (merged calls) so the dialer exposes Swap/Merge.
        // setConnectionProperties was added in API 26; guard for Android 6-7.
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            setConnectionProperties(Connection.PROPERTY_IS_ADHOC_CONFERENCE);
        }
        setActive();
        updateCapabilities();

        int autoEnd = Prefs.autoEndSec(c);
        if (autoEnd > 0) {
            h.postDelayed(() -> {
                if (!ended) {
                    terminate(new DisconnectCause(DisconnectCause.LOCAL));
                }
            }, autoEnd * 1000L);
        }
    }

    boolean isEnded() {
        return ended;
    }

    /** Adds a standalone self-call to this conference (third+ participant). */
    boolean addChild(SelfCallConnection c) {
        if (ended || c == null || getConnections().contains(c)) return false;
        if (getConnections().size() >= SelfCallRegistry.MAX_CONFERENCE_SIZE) return false;
        if (c.getConference() != null) return false;

        c.setConferenceableConnections(new ArrayList<Connection>());
        if (!addConnection(c)) return false;

        c.markConferenced();
        c.cancelAutoEnd();
        setActive();
        // Adding on behalf of the dialer's Merge control brings every participant back
        // into the call, including calls left on hold by an earlier Swap.
        for (Connection child : liveChildren()) {
            activate(child);
        }
        updateCapabilities();
        SelfCallRegistry.refreshConferenceables();
        return true;
    }

    @Override
    public void onMerge() {
        // The dialer's Merge control. Re-join every participant, including one which was
        // left on hold by a previous swap.
        if (ended) return;
        setActive();
        for (Connection child : liveChildren()) {
            activate(child);
        }
        updateCapabilities();
        SelfCallRegistry.refreshConferenceables();
    }

    @Override
    public void onMerge(Connection connection) {
        // A standalone call was merged into this conference. The framework calls this when
        // the dialer's Merge button is pressed while the conference is the focused call.
        if (connection == null) return;
        if (!(connection instanceof SelfCallConnection) || !addChild((SelfCallConnection) connection)) {
            connection.notifyConferenceMergeFailed();
        }
    }

    @Override
    public void onMerge(Conference conference) {
        // Merging two conferences is outside the self-call scope.
        notifyConferenceMergeFailed();
    }

    @Override
    public void onSwap() {
        // Rotate the foreground participant. This is the dialer's Swap control.
        if (ended) return;
        List<Connection> children = liveChildren();
        if (children.size() < 2) return;

        int active = -1;
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i).getState() == Connection.STATE_ACTIVE) {
                active = i;
                break;
            }
        }
        int next = active < 0 ? 0 : (active + 1) % children.size();

        // Activate the conference first so the system allows child state transitions.
        setActive();
        // Bring the next participant up before holding the current one so the audio path
        // always has an owner during the hand-over.
        activate(children.get(next));
        for (int i = 0; i < children.size(); i++) {
            if (i != next) hold(children.get(i));
        }

        updateCapabilities();
        SelfCallRegistry.refreshConferenceables();
    }

    @Override
    public void onSeparate(Connection connection) {
        if (ended || connection == null || !getConnections().contains(connection)) return;

        removeConnection(connection);
        if (connection instanceof SelfCallConnection) {
            SelfCallConnection c = (SelfCallConnection) connection;
            c.restoreStandaloneCapabilities();
            // A separated participant stays available in the background so the user can
            // swap back to it, matching how the dialer handles a split call.
            c.setOnHold();
            c.scheduleAutoEndIfNeeded();
        }

        if (getConnections().isEmpty()) {
            finish();
        } else if (getConnections().size() == 1) {
            dissolve();
        } else {
            updateCapabilities();
            SelfCallRegistry.refreshConferenceables();
        }
    }

    @Override
    public void onHold() {
        if (ended) return;
        setOnHold();
        for (Connection child : liveChildren()) {
            hold(child);
        }
        updateCapabilities();
        SelfCallRegistry.refreshConferenceables();
    }

    @Override
    public void onUnhold() {
        if (ended) return;
        setActive();
        for (Connection child : liveChildren()) {
            activate(child);
        }
        updateCapabilities();
        SelfCallRegistry.refreshConferenceables();
    }

    @Override
    public void onMuteStateChanged(boolean muted) {
        SelfCallRegistry.setMuted(muted);
    }

    @Override
    public void onPlayDtmfTone(char c) {
        Connection child = activeChild();
        if (child != null) child.onPlayDtmfTone(c);
    }

    @Override
    public void onStopDtmfTone() {
        Connection child = activeChild();
        if (child != null) child.onStopDtmfTone();
    }

    @Override
    public void onDisconnect() {
        terminate(new DisconnectCause(DisconnectCause.LOCAL));
    }

    /** Called by a child connection when it disconnects on its own. */
    void onChildEnded(Connection connection) {
        if (ended || connection == null || !getConnections().contains(connection)) return;

        removeConnection(connection);
        if (getConnections().isEmpty()) {
            finish();
        } else if (getConnections().size() == 1) {
            dissolve();
        } else {
            updateCapabilities();
            SelfCallRegistry.refreshConferenceables();
        }
    }

    /** Refreshes Merge/Swap after a participant is held, un-held or otherwise changed. */
    void onChildStateChanged() {
        if (!ended) updateCapabilities();
    }

    /** Undoes a conference which failed while being created by the dialer. */
    void rollbackFailedMerge() {
        if (ended) return;
        ended = true;
        h.removeCallbacksAndMessages(null);
        for (Connection c : new ArrayList<>(getConnections())) {
            removeConnection(c);
            if (c instanceof SelfCallConnection) {
                SelfCallConnection sc = (SelfCallConnection) c;
                sc.restoreStandaloneCapabilities();
                sc.scheduleAutoEndIfNeeded();
            }
        }
        destroy();
    }

    /** Ends the whole conference and every participant. */
    void terminate(DisconnectCause cause) {
        if (ended) return;
        ended = true;
        h.removeCallbacksAndMessages(null);

        for (Connection c : new ArrayList<>(getConnections())) {
            removeConnection(c);
            if (c instanceof SelfCallConnection) {
                ((SelfCallConnection) c).end(cause);
            }
        }
        setDisconnected(cause);
        destroy();
        SelfCallRegistry.onConferenceEnded(this);
    }

    /**
     * Drops a conference which is down to a single participant: that call becomes a normal
     * standalone call again (and is no longer shown as a one-person conference).
     */
    private void dissolve() {
        if (ended) return;
        ended = true;
        h.removeCallbacksAndMessages(null);

        boolean wasActive = getState() == Connection.STATE_ACTIVE;
        for (Connection c : new ArrayList<>(getConnections())) {
            removeConnection(c);
            if (c instanceof SelfCallConnection) {
                SelfCallConnection sc = (SelfCallConnection) c;
                sc.restoreStandaloneCapabilities();
                if (wasActive) {
                    sc.setActive();
                } else {
                    sc.setOnHold();
                }
                sc.scheduleAutoEndIfNeeded();
            }
        }
        setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
        destroy();
        SelfCallRegistry.onConferenceEnded(this);
    }

    /** No participants left. */
    private void finish() {
        if (ended) return;
        ended = true;
        h.removeCallbacksAndMessages(null);
        setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
        destroy();
        SelfCallRegistry.onConferenceEnded(this);
    }

    private List<Connection> liveChildren() {
        List<Connection> children = new ArrayList<>(getConnections().size());
        for (Connection c : getConnections()) {
            if (c.getState() != Connection.STATE_DISCONNECTED) {
                children.add(c);
            }
        }
        return children;
    }

    private Connection activeChild() {
        for (Connection c : getConnections()) {
            if (c.getState() == Connection.STATE_ACTIVE) return c;
        }
        return getConnections().isEmpty() ? null : getConnections().get(0);
    }

    private static void activate(Connection c) {
        if (c.getState() == Connection.STATE_ACTIVE
                || c.getState() == Connection.STATE_DISCONNECTED) return;
        if (c instanceof SelfCallConnection
                && c.getState() == Connection.STATE_HOLDING) {
            ((SelfCallConnection) c).onUnhold();
        } else {
            c.setActive();
        }
    }

    private static void hold(Connection c) {
        if (c.getState() != Connection.STATE_ACTIVE) return;
        if (c instanceof SelfCallConnection) {
            ((SelfCallConnection) c).onHold();
        } else {
            c.setOnHold();
        }
    }

    /**
     * Swap is offered for every multi-party conference so the dialer can rotate the
     * foreground call; Merge appears when a participant is on hold.
     */
    private void updateCapabilities() {
        if (ended) return;
        List<Connection> children = liveChildren();
        boolean anyHeld = false;
        for (Connection c : children) {
            if (c.getState() == Connection.STATE_HOLDING) {
                anyHeld = true;
                break;
            }
        }
        int capabilities = BASE_CAPABILITIES;
        if (children.size() > 1) {
            capabilities |= Connection.CAPABILITY_SWAP_CONFERENCE;
            if (anyHeld) {
                capabilities |= Connection.CAPABILITY_MERGE_CONFERENCE;
            }
        }
        setConnectionCapabilities(capabilities);
    }
}
