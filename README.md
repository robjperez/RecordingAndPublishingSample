# Record and Publish sample using Opentok

This samples tries to show how to layout the different pieces needed to both publish and record to a file the contents captured from the device camera.

**IMPORTANT**

The aim of this sample is just showing the different elements you need to setup the Application. It **lacks** a lot of needed code to create a good Opentok/Camera2 application. Please **do not** copy and paste the sample or use the code in here to create an Application.

## How it works

The sample is using:
- Camera2 API: To create a capture session that will output to different surfaces.
- TextureView: To render the contents captured by the Camera in an Android View.
- MediaRecorder API: To dump the content of the capture to a H264/MP4 file.
- Opentok Custom Capturer: To send the video frames from the camera to the opentok session.

Basically the key concept here is that Camera2 is capturing from the camera and outputting the samples to two different surfaces,
1. TextureView surface
2. MediaRecoder surface

That will allow the Application to both show the camera preview in the TextureView and at the same time, create a `video.mp4` file with the same content.

```java
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
```

In order to publish the same frames to OpenTok Session, we need two things
1. Create a custom capturer to send the frames to the session
1. In `SurfaceTextureListener` `update` method, we will provide the custom capturer with the frame data coming from the Surface

```java
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
```