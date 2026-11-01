package com.parallax.selfcalling.telecom;

import android.content.Context;
import android.telecom.Connection;

import com.parallax.selfcalling.audio.CallFilePlayer;
import com.parallax.selfcalling.audio.LoopbackEngine;
import com.parallax.selfcalling.util.Preset;

import java.util.ArrayList;
import java.util.List;

/**
 * Process-wide bookkeeping for self-call connections and conferences.
 *
 * <p>Two jobs:
 * <ul>
 *     <li>Keep the dialer's merge button reachable: standalone calls which are active or
 *     held are mutually "conferenceable"; a conference is conferenceable with every
 *     standalone call that could still join it.</li>
 *     <li>Make the in-call voice follow the call that currently owns the audio path.
 *     The loopback/file players are singletons, so without this a conference child ending
 *     (or a swap) would stop the audio of the call that is still active.</li>
 * </ul>
 */
public final class SelfCallRegistry {

    /** Keeps the fake conference at a phone-like size. */
    static final int MAX_CONFERENCE_SIZE = 5;

    private static final List<SelfCallConnection> sCalls = new ArrayList<>();
    private static final List<SelfCallConference> sConferences = new ArrayList<>();

    private static SelfCallConnection sAudioOwner;
    private static boolean sAudioStarted;
    private static boolean sMuted;

    private SelfCallRegistry() {
    }

    static synchronized void addCall(SelfCallConnection c) {
        if (!sCalls.contains(c)) {
            sCalls.add(c);
        }
        refreshConferenceables();
    }

    static synchronized void addConference(SelfCallConference c) {
        if (!sConferences.contains(c)) {
            sConferences.add(c);
        }
        refreshConferenceables();
    }

    static synchronized void onConferenceEnded(SelfCallConference c) {
        sConferences.remove(c);
        refreshConferenceables();
    }

    /**
     * Called when a connection leaves the call (hang-up, missed ring, conference split).
     * Detaches it from its conference and gives the voice path to another active call.
     */
    static synchronized void onCallEnded(SelfCallConnection c) {
        sCalls.remove(c);

        SelfCallConference conf = c.getSelfCallConference();
        if (conf != null) {
            conf.onChildEnded(c);
        }

        if (sAudioOwner == c) {
            sAudioOwner = firstActive(c);
            if (sAudioOwner == null) {
                stopAudio();
            } else if (!sMuted) {
                startAudio(sAudioOwner);
            } else {
                pauseAudio();
            }
        }

        if (sCalls.isEmpty()) {
            sMuted = false;
            sAudioOwner = null;
            stopAudio();
        }

        refreshConferenceables();
    }

    /** Called for every connection state transition. */
    static synchronized void onConnectionStateChanged(SelfCallConnection c) {
        if (c.isEnded()) return;

        if (c.getState() == Connection.STATE_ACTIVE) {
            boolean changed = sAudioOwner != c;
            if (sAudioOwner == null || sAudioOwner.getState() != Connection.STATE_ACTIVE) {
                sAudioOwner = c;
                changed = true;
            }
            if (!sMuted && (changed || !sAudioStarted)) {
                startAudio(sAudioOwner);
            }
        } else if (sAudioOwner == c) {
            sAudioOwner = firstActive(c);
            if (sAudioOwner != null && !sMuted) {
                startAudio(sAudioOwner);
            } else if (sAudioOwner == null) {
                pauseAudio();
            }
        }

        SelfCallConference conf = c.getSelfCallConference();
        if (conf != null) {
            conf.onChildStateChanged();
        }
        refreshConferenceables();
    }

    static synchronized void setMuted(boolean muted) {
        sMuted = muted;
        if (muted) {
            // Mute silences the local microphone. A looping audio file is the simulated
            // remote party, so it keeps playing, exactly like a real call.
            if (sAudioOwner != null
                    && Preset.VOICE_MIC.equals(sAudioOwner.getPreset().voice)) {
                LoopbackEngine.stop();
                sAudioStarted = false;
            }
        } else if (sAudioOwner != null
                && sAudioOwner.getState() == Connection.STATE_ACTIVE
                && !sAudioStarted) {
            startAudio(sAudioOwner);
        }
    }

    static synchronized boolean isMuted() {
        return sMuted;
    }

    /** Whether any self-call is still live; gates the account's sip-scheme window. */
    static synchronized boolean hasCalls() {
        return !sCalls.isEmpty();
    }

    /** The connected video call the in-app video screen is showing. */
    static synchronized SelfCallConnection activeVideoCall() {
        for (SelfCallConnection c : sCalls) {
            if (!c.isEnded() && c.isVideo() && c.getState() == Connection.STATE_ACTIVE) {
                return c;
            }
        }
        return null;
    }

    private static SelfCallConnection firstActive(SelfCallConnection exclude) {
        for (SelfCallConnection c : sCalls) {
            if (c != exclude && !c.isEnded()
                    && c.getState() == Connection.STATE_ACTIVE) {
                return c;
            }
        }
        return null;
    }

    private static void startAudio(SelfCallConnection c) {
        if (c == null || c.isEnded()) {
            stopAudio();
            return;
        }
        Preset p = c.getPreset();
        if (Preset.VOICE_FILE.equals(p.voice)) {
            LoopbackEngine.stop();
            CallFilePlayer.startFiles(c.getContext(), p.chainedFiles());
        } else if (Preset.VOICE_MIC.equals(p.voice)) {
            CallFilePlayer.stop();
            LoopbackEngine.start(c.getContext());
        } else {
            CallFilePlayer.stop();
            LoopbackEngine.stop();
        }
        sAudioStarted = true;
    }

    private static void pauseAudio() {
        if (sAudioOwner != null && Preset.VOICE_FILE.equals(sAudioOwner.getPreset().voice)) {
            CallFilePlayer.pause();
        } else {
            CallFilePlayer.stop();
        }
        LoopbackEngine.stop();
        sAudioStarted = false;
    }

    private static void stopAudio() {
        CallFilePlayer.stop();
        LoopbackEngine.stop();
        sAudioStarted = false;
    }

    /**
     * Refreshes the "merge with" list the dialer uses to draw its Merge button:
     * active/held standalone calls are mutually mergeable, and a conference may pull in
     * any remaining standalone call.
     */
    static void refreshConferenceables() {
        List<Connection> topLevel = new ArrayList<>();
        for (SelfCallConnection c : sCalls) {
            if (c.isEnded() || c.getConference() != null) continue;
            int state = c.getState();
            if (state == Connection.STATE_ACTIVE || state == Connection.STATE_HOLDING) {
                topLevel.add(c);
            }
        }

        for (SelfCallConnection c : sCalls) {
            if (c.isEnded() || c.getConference() != null) continue;
            List<Connection> others = new ArrayList<>(topLevel.size());
            for (Connection other : topLevel) {
                if (other != c) others.add(other);
            }
            // A standalone call can also merge into an existing conference. Telecom only
            // exposes connection ids here, so offer one participant as the conference's
            // merge handle; ConnectionService.onConference routes it to the parent.
            for (SelfCallConference conf : sConferences) {
                if (conf.isEnded() || conf.getConnections().size() >= MAX_CONFERENCE_SIZE) {
                    continue;
                }
                Connection participant = firstLiveConnection(conf);
                if (participant != null) others.add(participant);
            }
            c.setConferenceableConnections(others);
        }

        for (SelfCallConference conf : sConferences) {
            if (conf.isEnded()) continue;
            if (conf.getConnections().size() >= MAX_CONFERENCE_SIZE) {
                conf.setConferenceableConnections(new ArrayList<Connection>());
            } else {
                conf.setConferenceableConnections(new ArrayList<>(topLevel));
            }
        }
    }

    private static Connection firstLiveConnection(SelfCallConference conf) {
        for (Connection c : conf.getConnections()) {
            if (c.getState() != Connection.STATE_DISCONNECTED) return c;
        }
        return null;
    }
}
