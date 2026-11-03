package com.parallax.selfcalling.telecom;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.telecom.Conference;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.DisconnectCause;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;

import com.parallax.selfcalling.audio.CallTonePlayer;
import com.parallax.selfcalling.util.CallEntry;
import com.parallax.selfcalling.util.CallLogStore;
import com.parallax.selfcalling.util.Prefs;
import com.parallax.selfcalling.util.Preset;

public class SelfCallConnection extends Connection {

    public enum Kind { OUTGOING, INCOMING }

    /** Historical value for a bidirectional video call (constant removed in recent APIs). */
    private static final int VIDEO_STATE_BIDIRECTIONAL = 3;

    private static final int BASE_CAPABILITIES = Connection.CAPABILITY_SUPPORT_HOLD
            | Connection.CAPABILITY_HOLD
            | Connection.CAPABILITY_MUTE
            | Connection.CAPABILITY_SWAP_CONFERENCE
            | Connection.CAPABILITY_MERGE_CONFERENCE;

    /** Advertises carrier-style VT so the native dialer opens its own video call screen. */
    private static final int VIDEO_CAPABILITIES =
            Connection.CAPABILITY_SUPPORTS_VT_LOCAL_BIDIRECTIONAL
                    | Connection.CAPABILITY_SUPPORTS_VT_REMOTE_BIDIRECTIONAL
                    | Connection.CAPABILITY_CAN_PAUSE_VIDEO
                    | Connection.CAPABILITY_CAN_UPGRADE_TO_VIDEO;

    private final Context ctx;
    private final Kind kind;
    private final Preset preset;
    private final int ringTimeoutOverride;
    private final boolean dialed;
    private final boolean video;
    private final SelfCallVideoProvider videoProvider;
    private final Handler h = new Handler(Looper.getMainLooper());
    private long activeAt = 0;
    private boolean ended = false;
    private boolean autoAnswer = false;
    private boolean autoEndScheduled = false;
    private boolean videoUiShown = false;

    private final Runnable autoEnd = () -> {
        autoEndScheduled = false;
        if (!ended && getState() == STATE_ACTIVE && getConference() == null) {
            end(new DisconnectCause(DisconnectCause.LOCAL));
        }
    };

    public SelfCallConnection(Context c, Kind k, ConnectionRequest req) {
        ctx = c;
        kind = k;
        // For simulated incoming calls, honour the preset that was active when the call was
        // scheduled, so switching presets later does not change the ringing identity.
        Preset resolved = null;
        if (kind == Kind.INCOMING && req != null && req.getExtras() != null) {
            android.os.Bundle ex = req.getExtras();
            try {
                String snap = ex.getString("preset_snapshot");
                if (snap != null) {
                    resolved = Preset.fromJson(new org.json.JSONObject(snap));
                }
            } catch (Exception ignored) {
            }
            if (resolved == null) {
                try {
                    int presetIdx = ex.getInt("preset_index", -1);
                    if (presetIdx >= 0) {
                        java.util.List<Preset> all = Prefs.presets(c);
                        if (presetIdx >= 0 && presetIdx < all.size()) {
                            resolved = all.get(presetIdx);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
        if (resolved == null) {
            resolved = Prefs.activePreset(c);
        }
        preset = resolved;

        int rto = -1;
        if (kind == Kind.INCOMING && req != null && req.getExtras() != null
                && req.getExtras().containsKey("ring_timeout_sec")) {
            rto = req.getExtras().getInt("ring_timeout_sec", -1);
        }
        ringTimeoutOverride = rto;

        // A number dialed in the phone app is re-placed as sip:<number>@dial.selfcall.local;
        // "Call Myself" uses sip:<number>@selfcall.local and keeps the preset identity.
        String outgoingNumber = preset.number;
        boolean isDialed = false;
        Uri request = req != null ? req.getAddress() : null;
        if (kind == Kind.OUTGOING && request != null) {
            String scheme = request.getScheme();
            String ssp = request.getSchemeSpecificPart();
            if ("sip".equals(scheme) && ssp != null && ssp.contains("@")) {
                int at = ssp.indexOf('@');
                outgoingNumber = ssp.substring(0, at);
                isDialed = TelecomHelper.DIALED_HOST.equals(ssp.substring(at + 1));
            } else if ("tel".equals(scheme) && ssp != null) {
                // Routed to this account straight from the dialer's tel: intent.
                outgoingNumber = ssp;
                isDialed = true;
            }
        }
        dialed = isDialed;

        Uri address = kind == Kind.OUTGOING
                ? TelecomHelper.telUri(outgoingNumber)
                : request;
        if (address != null) {
            setAddress(address, TelecomManager.PRESENTATION_ALLOWED);
        }
        if (!dialed) {
            // Dialed numbers keep the number only, so the dialer can resolve real contacts.
            setCallerDisplayName(preset.name, TelecomManager.PRESENTATION_ALLOWED);
        }
        setAudioModeIsVoip(true);
        video = !dialed && preset.video;
        int capabilities = BASE_CAPABILITIES;
        if (video) {
            // Bidirectional VT turns the dialer's in-call screen into its video variant;
            // the provider feeds the camera preview and remote feed into that UI.
            setVideoState(VIDEO_STATE_BIDIRECTIONAL);
            videoProvider = new SelfCallVideoProvider(ctx, this::onDialerVideoState);
            setVideoProvider(videoProvider);
            capabilities |= VIDEO_CAPABILITIES;
        } else {
            videoProvider = null;
        }
        // Hold, un-hold and mute straight from the native dialer's in-call screen.
        // Merge/swap are added by the conference object once calls are combined.
        setConnectionCapabilities(capabilities);
    }

    /** Follows the video state the native dialer asks for (video mute, upgrade to audio). */
    private void onDialerVideoState(int state) {
        if (!ended && video) {
            setVideoState(state);
        }
    }

    private int standardCapabilities() {
        return video ? BASE_CAPABILITIES | VIDEO_CAPABILITIES : BASE_CAPABILITIES;
    }

    void initOutgoing() {
        setDialing();
        CallTonePlayer.startRingback(ctx);
        long delay = Math.max(500, Prefs.connectDelayMs(ctx));
        h.postDelayed(this::connectSelf, delay);
    }

    void initIncoming() {
        setRinging();
        maybeAutoAnswer();
        int rt = ringTimeoutOverride >= 0 ? ringTimeoutOverride : Prefs.ringTimeoutSec(ctx);
        if (rt > 0) {
            h.postDelayed(() -> {
                if (getState() == STATE_RINGING) {
                    end(new DisconnectCause(DisconnectCause.MISSED));
                }
            }, rt * 1000L);
        }
    }

    private void connectSelf() {
        if (getState() == STATE_DIALING) {
            setActive();
        }
    }

    private void maybeAutoAnswer() {
        int delay = Prefs.autoAnswerDelay(ctx);
        if (delay < 0) return;
        autoAnswer = true;
        Runnable answer = () -> {
            if (getState() != STATE_RINGING) return;
            boolean canAccept = Build.VERSION.SDK_INT >= 26
                    && ctx.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED;
            if (canAccept) {
                TelecomManager tm = (TelecomManager) ctx.getSystemService(Context.TELECOM_SERVICE);
                if (tm != null) {
                    try {
                        tm.acceptRingingCall();
                    } catch (Exception ignored) {
                    }
                }
            } else {
                try {
                    setActive();
                } catch (Exception ignored) {
                }
            }
        };
        h.postDelayed(answer, delay * 1000L);
    }

    @Override
    public void onAnswer(int videoState) {
        if (getState() == STATE_RINGING) {
            setActive();
        }
    }

    @Override
    public void onReject() {
        end(new DisconnectCause(DisconnectCause.REJECTED));
    }

    @Override
    public void onAbort() {
        end(new DisconnectCause(DisconnectCause.CANCELED));
    }

    @Override
    public void onDisconnect() {
        end(new DisconnectCause(DisconnectCause.LOCAL));
    }

    @Override
    public void onSeparate() {
        SelfCallConference conf = getSelfCallConference();
        if (conf != null) {
            conf.onSeparate(this);
        }
    }

    @Override
    public void onHold() {
        if (getState() == STATE_ACTIVE) {
            setOnHold();
        }
    }

    @Override
    public void onUnhold() {
        if (getState() == STATE_HOLDING) {
            setActive();
        }
    }

    @Override
    public void onMuteStateChanged(boolean muted) {
        SelfCallRegistry.setMuted(muted);
    }

    /** Whether this connection is a video call. */
    public boolean isVideo() {
        return video;
    }

    @Override
    public void onStateChanged(int state) {
        if (ended) return;
        if (state == STATE_ACTIVE && activeAt == 0) {
            activeAt = SystemClock.elapsedRealtime();
            CallTonePlayer.stopRingback();
            CallTonePlayer.playConnect(ctx);
        }
        SelfCallRegistry.onConnectionStateChanged(this);
        if (state == STATE_ACTIVE) {
            scheduleAutoEndIfNeeded();
            showVideoUi();
        }
    }

    /**
     * Opens the in-app video screen. Samsung's dialer never drives third-party video
     * providers, so the video itself is rendered by the app on top of the call.
     */
    private void showVideoUi() {
        if (ended || !video || videoProvider == null || videoUiShown
                || getConference() != null) {
            return;
        }
        videoUiShown = true;
        Intent intent = new Intent(ctx, VideoCallActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(VideoCallActivity.EXTRA_NAME, preset.name);
        intent.putExtra(VideoCallActivity.EXTRA_NUMBER, preset.number);
        try {
            ctx.startActivity(intent);
        } catch (Exception ignored) {
        }
    }

    /** Called by the conference when this call joins it. */
    void markConferenced() {
        cancelAutoEnd();
        setConnectionCapabilities(standardCapabilities()
                | Connection.CAPABILITY_SEPARATE_FROM_CONFERENCE
                | Connection.CAPABILITY_DISCONNECT_FROM_CONFERENCE);
    }

    /** Restores the normal standalone capabilities after leaving a conference. */
    void restoreStandaloneCapabilities() {
        setConnectionCapabilities(standardCapabilities());
    }

    void cancelAutoEnd() {
        autoEndScheduled = false;
        h.removeCallbacks(autoEnd);
    }

    /** Schedules auto-end for a standalone call; conferences manage their own timer. */
    void scheduleAutoEndIfNeeded() {
        if (ended || autoEndScheduled || getConference() != null) return;
        int autoEndSec = Prefs.autoEndSec(ctx);
        if (autoEndSec <= 0 || getState() != STATE_ACTIVE) return;
        autoEndScheduled = true;
        h.postDelayed(autoEnd, autoEndSec * 1000L);
    }

    boolean isEnded() {
        return ended;
    }

    Context getContext() {
        return ctx;
    }

    long getActiveAt() {
        return activeAt;
    }

    Preset getPreset() {
        return preset;
    }

    SelfCallConference getSelfCallConference() {
        Conference c = getConference();
        return c instanceof SelfCallConference ? (SelfCallConference) c : null;
    }

    void end(DisconnectCause cause) {
        if (ended) return;
        ended = true;
        CallTonePlayer.stopRingback();
        CallTonePlayer.playEnd(ctx);
        h.removeCallbacksAndMessages(null);
        if (videoProvider != null) {
            videoProvider.release();
        }
        int dur = activeAt > 0 ? (int) ((SystemClock.elapsedRealtime() - activeAt) / 1000) : 0;
        boolean answered = activeAt > 0;

        // Detach from a conference first so the remaining participants keep their audio.
        // Doing this before setDisconnected avoids the system tearing down the whole
        // conference when a single participant hangs up.
        SelfCallRegistry.onCallEnded(this);
        setDisconnected(cause);
        TelecomHelper.restoreTelOnly(ctx);
        destroy();

        CallLogStore.add(ctx, new CallEntry(
                System.currentTimeMillis(),
                kind == Kind.OUTGOING ? "out" : "in",
                answered,
                dur,
                answered && kind == Kind.INCOMING && autoAnswer,
                video));
    }
}
