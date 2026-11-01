package com.parallax.selfcalling.telecom;

import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.PhoneAccountHandle;

public class SelfCallService extends ConnectionService {

    @Override
    public Connection onCreateOutgoingConnection(PhoneAccountHandle cam, ConnectionRequest request) {
        SelfCallConnection c = new SelfCallConnection(this, SelfCallConnection.Kind.OUTGOING, request);
        SelfCallRegistry.addCall(c);
        c.initOutgoing();
        return c;
    }

    @Override
    public Connection onCreateIncomingConnection(PhoneAccountHandle cam, ConnectionRequest request) {
        SelfCallConnection c = new SelfCallConnection(this, SelfCallConnection.Kind.INCOMING, request);
        SelfCallRegistry.addCall(c);
        c.initIncoming();
        return c;
    }

    /**
     * Called by Telecom when the dialer merges two standalone self-calls. Builds the
     * conference which carries the Swap/Merge controls and the manage-conference list.
     */
    @Override
    public void onConference(Connection connection1, Connection connection2) {
        if (!(connection1 instanceof SelfCallConnection)
                || !(connection2 instanceof SelfCallConnection)
                || connection1 == connection2) {
            failMerge(connection1, connection2);
            return;
        }

        SelfCallConnection first = (SelfCallConnection) connection1;
        SelfCallConnection second = (SelfCallConnection) connection2;
        if (first.isEnded() || second.isEnded()) {
            failMerge(first, second);
            return;
        }

        // Merging a new call into an existing conference: Telecom addresses the conference
        // through one of its participants, so route the request to the parent conference.
        SelfCallConference firstConference = first.getSelfCallConference();
        SelfCallConference secondConference = second.getSelfCallConference();
        if (firstConference != null || secondConference != null) {
            if (firstConference != null && secondConference == null) {
                firstConference.onMerge(second);
            } else if (secondConference != null && firstConference == null) {
                secondConference.onMerge(first);
            } else {
                failMerge(first, second);
            }
            return;
        }

        SelfCallConference conference = new SelfCallConference(
                this, TelecomHelper.handle(this));
        // Register the conference with Telecom before attaching children so the system
        // recognises the participants as part of a live conference (avoids spurious
        // disconnects of the absorbed calls).
        addConference(conference);
        boolean firstAdded = conference.addChild(first);
        boolean secondAdded = firstAdded && conference.addChild(second);
        if (!firstAdded || !secondAdded) {
            conference.rollbackFailedMerge();
            failMerge(first, second);
            return;
        }

        SelfCallRegistry.addConference(conference);
        android.util.Log.i("SelfCall", "conference created with 2 participants");
    }

    private static void failMerge(Connection a, Connection b) {
        if (a != null) a.notifyConferenceMergeFailed();
        if (b != null) b.notifyConferenceMergeFailed();
    }
}
