package com.example.videocapturesample;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;

import com.opentok.android.BaseVideoCapturer;
import com.opentok.android.OpentokError;
import com.opentok.android.Publisher;
import com.opentok.android.Session;
import com.opentok.android.Stream;

import org.w3c.dom.Text;

import java.io.File;
import java.io.IOException;
import java.security.Permission;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener, Session.SessionListener, SurfaceTexture.OnFrameAvailableListener {

    private static final String TAG = MainActivity.class.getSimpleName();


    public static final String API_KEY = "";
    public static final String TOKEN = "";
    public static final String SESSION_ID = "";

    String cameraId;
    Surface previewSurface;
    TextureView cameraPreview;
    MediaRecorder recorder;
    CameraCaptureSession captureSession;
    Handler backgroundHandler;
    CaptureRequest.Builder requestBuilder;
    SurfaceTexture cameraPreviewSurface;
    boolean appInit = false;
    int[] frameData;

    // opentok properties
    Session otSession;
    Publisher otPublisher;
    OTCapturer otCapturer;

    // ----------------------------------------
    // APP INITIALIZATION
    // ----------------------------------------
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        cameraPreview = findViewById(R.id.camera_preview);
        cameraPreview.setSurfaceTextureListener(this);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_DENIED
            )
        {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.RECORD_AUDIO },
                    1234);
        }
        setUpApp();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode,
                permissions,
                grantResults);
        setUpApp();
    }

    // As the app needs to have the surface of the texture view ready and the permissions
    // granted, this method acts as the starting point for the sample
    private void setUpApp() {
        if (cameraPreviewSurface == null || appInit) {
            return;
        }

        appInit = true;
        HandlerThread backgroundThread = new HandlerThread("Background");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        initCameraCapture();
        initRecorder();
        initOpentok();
    }

    @SuppressLint("MissingPermission")
    private void initCameraCapture() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraId = manager.getCameraIdList()[1];
            manager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void initRecorder() {
        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        String filePath = String.format("%s/%s", getExternalCacheDir().getAbsolutePath(), "video.mp4");
        Log.d(TAG, "Output file: " + filePath);
        recorder.setOutputFile(filePath);
        try {
            recorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initOpentok() {
        otSession = new Session.Builder(this, API_KEY, SESSION_ID)
                .build();
        otSession.setSessionListener(this);
        otSession.connect(TOKEN);
    }


    // ----------------------------------------
    // Camera2 Callback
    // ----------------------------------------
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.d(TAG, "Camera opened");
            try {
                List<Surface> surfaces = new ArrayList<>();
                requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

                cameraPreviewSurface.setDefaultBufferSize(cameraPreview.getWidth(), cameraPreview.getHeight());
                previewSurface = new Surface(cameraPreviewSurface);
                requestBuilder.addTarget(previewSurface);
                requestBuilder.addTarget(recorder.getSurface());
                surfaces.add(previewSurface);
                surfaces.add(recorder.getSurface());

                camera.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        captureSession = session;
                        HandlerThread backgroundCapture = new HandlerThread("CameraPreview");
                        backgroundCapture.start();
                        try {
                            captureSession.setRepeatingRequest(requestBuilder.build(), null, new Handler(backgroundCapture.getLooper()));
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }
                        runOnUiThread(() -> recorder.start());
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                    }
                }, backgroundHandler);

            } catch (CameraAccessException e) {
                e.printStackTrace();
            }

        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {}
        @Override
        public void onError(@NonNull CameraDevice camera, int error) {}
    };

    // ----------------------------------------
    // SurfaceTexture Listener
    // ----------------------------------------
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        cameraPreviewSurface = surface;
        setUpApp();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) { }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) { return false; }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        Bitmap bmp = cameraPreview.getBitmap();
        if (bmp == null || otCapturer == null) {
            return;
        }

        int w = bmp.getWidth();
        int h = bmp.getHeight();

        if (frameData == null) {
            frameData = new int[w * h];
        }

        bmp.getPixels(frameData, 0, w, 0, 0, w, h);
        otCapturer.provideFrame(frameData, w, h);
    }

    // ----------------------------------------
    // Opentok Session callbacks
    // ----------------------------------------
    @Override
    public void onConnected(Session session) {
        otCapturer = new OTCapturer();
        otPublisher = new Publisher.Builder(this)
                .capturer(otCapturer)
                .build();
        otSession.publish(otPublisher);
    }

    @Override
    public void onDisconnected(Session session) {}

    @Override
    public void onStreamReceived(Session session, Stream stream) {}

    @Override
    public void onStreamDropped(Session session, Stream stream) {}

    @Override
    public void onError(Session session, OpentokError opentokError) {}

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {}
}

// ----------------------------------------
// Opentok Custom capturer
// ----------------------------------------
class OTCapturer extends BaseVideoCapturer {

    private boolean captureStarted;
    @Override
    public void init() {
        captureStarted = false;
    }

    @Override
    public int startCapture() {
        captureStarted = true;
        return 0;
    }

    @Override
    public int stopCapture() {
        captureStarted = false;
        return 0;
    }

    @Override
    public void destroy() {}

    @Override
    public boolean isCaptureStarted() {
        return captureStarted;
    }

    @Override
    public CaptureSettings getCaptureSettings() {
        return new CaptureSettings();
    }

    @Override
    public void onPause() {}

    @Override
    public void onResume() {}

    public void provideFrame(int[] frameData, int width, int height) {
        provideIntArrayFrame(frameData, ARGB, width, height, 0, false);
    }
}
