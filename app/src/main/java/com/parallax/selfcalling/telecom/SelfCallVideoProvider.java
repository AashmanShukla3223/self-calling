package com.parallax.selfcalling.telecom;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.telecom.Connection;
import android.telecom.VideoProfile;
import android.util.Size;
import android.view.Surface;

import java.util.Arrays;

/**
 * Backs the native dialer's video call screen: the dialer requests preview and display
 * surfaces through Telecom, and this provider renders the local camera into the former
 * and a simulated remote feed into the latter.
 */
public class SelfCallVideoProvider extends Connection.VideoProvider {

    /** Tells the connection to follow video states requested from the dialer (mute/upgrade). */
    public interface Listener {
        void onVideoStateChanged(int videoState);
    }

    private final Context ctx;
    private final Listener listener;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;

    private HandlerThread renderThread;
    private Handler renderHandler;

    private String cameraId;
    private Surface previewSurface;
    private Surface displaySurface;
    private boolean txEnabled = true;
    private int frame;
    private int drawWidth;
    private int drawHeight;
    private float blobX, blobY, blobVx = 2.1f, blobVy = 1.6f;

    public SelfCallVideoProvider(Context c, Listener l) {
        ctx = c.getApplicationContext();
        listener = l;
        // The provider runs on the main looper; camera and rendering live on their own threads.
        paint.setStyle(Paint.Style.FILL);
    }

    // ---- calls from the native dialer ----

    @Override
    public void onSetCamera(String id) {
        cameraId = id;
        if (id == null) {
            txEnabled = false;
            stopCamera();
        } else {
            txEnabled = true;
            openCamera();
        }
    }

    @Override
    public void onSetPreviewSurface(Surface surface) {
        previewSurface = surface;
        if (surface == null) {
            stopCamera();
        } else {
            openCamera();
        }
    }

    /**
     * Attaches the in-app video screen's preview directly, bypassing the Telecom
     * bridge. Firmware whose dialer ignores third-party providers still needs a
     * surface for the camera; this sets surface and camera in one open.
     */
    public void attachPreview(Surface surface, String id) {
        previewSurface = surface;
        cameraId = id;
        txEnabled = true;
        if (surface == null) {
            stopCamera();
        } else {
            openCamera();
        }
    }

    /** Detaches the in-app preview without releasing the provider. */
    public void detachPreview() {
        previewSurface = null;
        stopCamera();
    }

    @Override
    public void onSetDisplaySurface(Surface surface) {
        displaySurface = surface;
        if (surface == null) {
            stopRendering();
        } else {
            startRendering();
        }
    }

    @Override
    public void onSetDeviceOrientation(int rotation) {
    }

    @Override
    public void onSetZoom(float value) {
    }

    @Override
    public void onSendSessionModifyRequest(VideoProfile fromProfile, VideoProfile toProfile) {
        if (toProfile == null) return;
        int state = toProfile.getVideoState();
        txEnabled = VideoProfile.isTransmissionEnabled(state);
        if (listener != null) listener.onVideoStateChanged(state);
        if (txEnabled) openCamera(); else stopCamera();
        receiveSessionModifyResponse(
                SESSION_MODIFY_REQUEST_SUCCESS, fromProfile, toProfile);
    }

    @Override
    public void onSendSessionModifyResponse(VideoProfile responseProfile) {
    }

    @Override
    public void onRequestCameraCapabilities() {
        int[] size = previewSize(cameraId);
        changeCameraCapabilities(new VideoProfile.CameraCapabilities(size[0], size[1]));
    }

    @Override
    public void onRequestConnectionDataUsage() {
        setCallDataUsage(0L);
    }

    @Override
    public void onSetPauseImage(Uri uri) {
    }

    /** Stops camera and rendering; called when the call ends. */
    public void release() {
        txEnabled = false;
        previewSurface = null;
        displaySurface = null;
        stopCamera();
        stopRendering();
        if (cameraThread != null) {
            cameraThread.quitSafely();
            cameraThread = null;
            cameraHandler = null;
        }
        if (renderThread != null) {
            renderThread.quitSafely();
            renderThread = null;
            renderHandler = null;
        }
    }

    // ---- local camera preview ----

    private boolean hasCameraPermission() {
        return ctx.checkSelfPermission(Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void openCamera() {
        final String id = cameraId;
        final Surface surface = previewSurface;
        if (!txEnabled || id == null || surface == null || !hasCameraPermission()) {
            stopCamera();
            return;
        }
        ensureCameraThread();
        if (cameraHandler == null) return;
        cameraHandler.post(() -> {
            releaseCamera();
            CameraManager manager =
                    (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
            if (manager == null) return;
            try {
                String target = id;
                boolean known = false;
                for (String candidate : manager.getCameraIdList()) {
                    if (candidate.equals(id)) {
                        known = true;
                        break;
                    }
                }
                if (!known) {
                    String[] ids = manager.getCameraIdList();
                    if (ids.length == 0) return;
                    target = ids[0];
                }
                manager.openCamera(target, cameraCallback, cameraHandler);
            } catch (Exception e) {
                handleCallSessionEvent(SESSION_EVENT_CAMERA_FAILURE);
            }
        });
    }

    private final CameraDevice.StateCallback cameraCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            cameraDevice = camera;
            startPreview(camera);
            handleCallSessionEvent(SESSION_EVENT_CAMERA_READY);
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            camera.close();
            if (cameraDevice == camera) cameraDevice = null;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            camera.close();
            if (cameraDevice == camera) cameraDevice = null;
            handleCallSessionEvent(SESSION_EVENT_CAMERA_FAILURE);
        }
    };

    private void startPreview(CameraDevice camera) {
        Surface surface = previewSurface;
        if (surface == null || cameraHandler == null) return;
        try {
            CaptureRequest.Builder builder =
                    camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(surface);
            builder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON);
            camera.createCaptureSession(Arrays.asList(surface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            captureSession = session;
                            try {
                                session.setRepeatingRequest(builder.build(), null, cameraHandler);
                            } catch (Exception ignored) {
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            handleCallSessionEvent(SESSION_EVENT_CAMERA_FAILURE);
                        }
                    }, cameraHandler);
        } catch (Exception e) {
            handleCallSessionEvent(SESSION_EVENT_CAMERA_FAILURE);
        }
    }

    private void stopCamera() {
        Handler handler = cameraHandler;
        if (handler == null) return;
        handler.post(this::releaseCamera);
    }

    private void releaseCamera() {
        try {
            if (captureSession != null) {
                captureSession.close();
                captureSession = null;
            }
        } catch (Exception ignored) {
        }
        try {
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
        } catch (Exception ignored) {
        }
    }

    private void ensureCameraThread() {
        if (cameraThread != null) return;
        cameraThread = new HandlerThread("selfcall-video-camera");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
    }

    /** Aspect-friendly preview size used for dialer camera capability reporting. */
    private int[] previewSize(String id) {
        try {
            CameraManager manager =
                    (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
            if (manager != null) {
                String target = id;
                if (target == null) {
                    String[] ids = manager.getCameraIdList();
                    target = ids.length > 0 ? ids[0] : null;
                }
                if (target != null) {
                    CameraCharacteristics ch = manager.getCameraCharacteristics(target);
                    StreamConfigurationMap map = ch.get(
                            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    if (map != null) {
                        Size[] sizes = map.getOutputSizes(SurfaceTexture.class);
                        if (sizes == null || sizes.length == 0) {
                            sizes = map.getOutputSizes(Surface.class);
                        }
                        if (sizes != null) {
                            for (Size size : sizes) {
                                if (size.getWidth() == 1280 && size.getHeight() == 720) {
                                    return new int[]{1280, 720};
                                }
                            }
                        }
                        if (sizes != null && sizes.length > 0) {
                            return new int[]{sizes[0].getWidth(), sizes[0].getHeight()};
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return new int[]{640, 480};
    }

    // ---- simulated remote feed on the display surface ----

    private void startRendering() {
        ensureRenderThread();
        if (renderHandler == null) return;
        renderHandler.removeCallbacks(drawFrame);
        renderHandler.post(drawFrame);
    }

    private void stopRendering() {
        if (renderHandler != null) {
            renderHandler.removeCallbacksAndMessages(null);
        }
    }

    private void ensureRenderThread() {
        if (renderThread != null) return;
        renderThread = new HandlerThread("selfcall-video-render");
        renderThread.start();
        renderHandler = new Handler(renderThread.getLooper());
    }

    private final Runnable drawFrame = new Runnable() {
        @Override
        public void run() {
            Surface surface = displaySurface;
            if (surface == null || renderHandler == null) return;
            Canvas canvas = null;
            try {
                canvas = surface.lockCanvas(null);
                if (canvas != null) {
                    drawRemote(canvas, canvas.getWidth(), canvas.getHeight());
                }
            } catch (Exception ignored) {
            } finally {
                if (canvas != null) {
                    try {
                        surface.unlockCanvasAndPost(canvas);
                    } catch (Exception ignored) {
                    }
                }
            }
            if (displaySurface != null && renderHandler != null) {
                renderHandler.postDelayed(this, 33);
            }
        }
    };

    private void drawRemote(Canvas canvas, int w, int h) {
        if (w <= 0 || h <= 0) return;
        if (w != drawWidth || h != drawHeight) {
            drawWidth = w;
            drawHeight = h;
            blobX = w / 2f;
            blobY = h / 2f;
        }
        frame++;
        int t = frame % 360;
        int c1 = Color.HSVToColor(new float[]{(t / 2) % 360, 0.35f, 0.26f});
        int c2 = Color.HSVToColor(new float[]{(t / 2 + 60) % 360, 0.35f, 0.17f});
        paint.setShader(new LinearGradient(0, 0, w, h, c1, c2, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        float pulse = (float) (Math.sin(frame * 0.08f) * 0.5f + 0.5f);
        blobX += blobVx;
        blobY += blobVy;
        if (blobX < 0f || blobX > w) blobVx = -blobVx;
        if (blobY < 0f || blobY > h) blobVy = -blobVy;
        paint.setColor(Color.argb((int) (45 + 25 * pulse), 255, 255, 255));
        canvas.drawCircle(blobX, blobY, 44 + 18 * pulse, paint);

        paint.setColor(Color.WHITE);
        paint.setTextSize(26f);
        canvas.drawText("REMOTE", 24f, 46f, paint);
    }
}
