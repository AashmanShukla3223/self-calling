package com.parallax.selfcalling.telecom;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;

import com.parallax.selfcalling.R;
import com.parallax.selfcalling.databinding.ActivityVideoCallBinding;

import java.util.Locale;

/**
 * Full-screen video call screen. Samsung's dialer never drives third-party Telecom
 * video providers, so video self-calls render here, on top of the call itself.
 *
 * <p>The remote pane is the provider's simulated feed and the small pane is the live
 * camera preview; both surfaces are fed by the same {@link SelfCallVideoProvider}
 * that a standards-compliant dialer would drive.
 */
public class VideoCallActivity extends Activity {

    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_NUMBER = "number";

    private static final int REQ_CAMERA = 1;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private ActivityVideoCallBinding b;
    private SelfCallConnection connection;
    private SelfCallVideoProvider provider;

    private Surface displaySurface;
    private Surface previewSurface;
    private String cameraId;
    private boolean frontCamera = true;
    private boolean cameraOn = true;
    private boolean cameraRequested = false;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!callStillUp()) return;
            long elapsed = SystemClock.elapsedRealtime() - connection.getActiveAt();
            b.callTimer.setText(formatDuration(elapsed));
            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityVideoCallBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }

        String name = getIntent().getStringExtra(EXTRA_NAME);
        b.callTitle.setText(name != null ? name : getString(R.string.app_name));

        b.remoteView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                displaySurface = holder.getSurface();
                attachDisplay();
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                displaySurface = null;
                if (provider != null) provider.onSetDisplaySurface(null);
            }
        });

        b.localView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
                previewSurface = new Surface(texture);
                b.localView.setScaleX(frontCamera ? -1f : 1f);
                attachPreview();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
                detachPreview();
                if (previewSurface != null) {
                    previewSurface.release();
                    previewSurface = null;
                }
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture texture) {
            }
        });

        b.btnEnd.setOnClickListener(v -> {
            hangUp();
            finish();
        });
        b.btnFlip.setOnClickListener(v -> flipCamera());
        b.btnVideo.setOnClickListener(v -> {
            cameraOn = !cameraOn;
            if (provider != null) provider.onSetCamera(cameraOn ? cameraId : null);
            showCameraOff();
        });
        b.btnMute.setOnClickListener(v -> {
            SelfCallRegistry.setMuted(!SelfCallRegistry.isMuted());
            updateMuteUi();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        connection = SelfCallRegistry.activeVideoCall();
        if (connection == null) {
            finish();
            return;
        }
        provider = (SelfCallVideoProvider) connection.getVideoProvider();
        String number = connection.getPreset().number;
        if (number != null && number.length() > 0) {
            b.callSubtitle.setText(number);
        }
        updateMuteUi();
        attachDisplay();
        attachPreview();
        handler.removeCallbacks(tick);
        tick.run();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
        // Leaving the screen (home, recents, another call) stops the camera; the
        // telecom call itself keeps running.
        if (provider != null) {
            provider.detachPreview();
            provider.onSetDisplaySurface(null);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (provider != null) {
            provider.detachPreview();
            provider.onSetDisplaySurface(null);
        }
        if (previewSurface != null) {
            previewSurface.release();
            previewSurface = null;
        }
    }

    private void hangUp() {
        if (connection != null && !connection.isEnded()) {
            connection.end(new DisconnectCause(DisconnectCause.LOCAL));
        }
    }

    private boolean callStillUp() {
        if (connection != null && !connection.isEnded()
                && connection.getState() == Connection.STATE_ACTIVE) {
            return true;
        }
        finish();
        return false;
    }

    private void attachDisplay() {
        if (provider != null && displaySurface != null && displaySurface.isValid()) {
            provider.onSetDisplaySurface(displaySurface);
        }
    }

    private void attachPreview() {
        if (provider == null || previewSurface == null) return;
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if (!cameraRequested) {
                cameraRequested = true;
                requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
            } else {
                showCameraUnavailable();
            }
            return;
        }
        if (cameraId == null) {
            cameraId = pickCamera(frontCamera);
        }
        if (cameraId == null) {
            showCameraUnavailable();
            return;
        }
        b.localPlaceholder.setVisibility(View.GONE);
        provider.attachPreview(previewSurface, cameraOn ? cameraId : null);
    }

    private void detachPreview() {
        if (provider == null) return;
        provider.detachPreview();
    }

    private void flipCamera() {
        frontCamera = !frontCamera;
        String next = pickCamera(frontCamera);
        if (next == null) return;
        cameraId = next;
        b.localView.setScaleX(frontCamera ? -1f : 1f);
        if (provider != null && cameraOn) {
            provider.onSetCamera(cameraId);
        }
    }

    private void showCameraOff() {
        if (cameraOn) {
            b.localPlaceholder.setVisibility(View.GONE);
        } else {
            b.localPlaceholder.setText(R.string.video_off);
            b.localPlaceholder.setVisibility(View.VISIBLE);
        }
        b.videoLabel.setText(cameraOn ? R.string.video_camera : R.string.video_off);
        b.videoIcon.setAlpha(cameraOn ? 1f : 0.5f);
    }

    private void showCameraUnavailable() {
        b.localPlaceholder.setText(R.string.video_unavailable);
        b.localPlaceholder.setVisibility(View.VISIBLE);
    }

    private void updateMuteUi() {
        boolean muted = SelfCallRegistry.isMuted();
        b.muteLabel.setText(muted ? R.string.video_muted : R.string.video_mute);
        b.muteIcon.setAlpha(muted ? 0.45f : 1f);
    }

    private String pickCamera(boolean front) {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) return null;
        try {
            String first = null;
            for (String id : manager.getCameraIdList()) {
                if (first == null) first = id;
                Integer facing = manager.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == (front
                        ? CameraCharacteristics.LENS_FACING_FRONT
                        : CameraCharacteristics.LENS_FACING_BACK)) {
                    return id;
                }
            }
            return first;
        } catch (Exception e) {
            return null;
        }
    }

    private static String formatDuration(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (requestCode == REQ_CAMERA) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                attachPreview();
            } else {
                showCameraUnavailable();
            }
        }
    }
}
