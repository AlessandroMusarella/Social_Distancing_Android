/*
 * Copyright 2017 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package my.application.sda;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.Image;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.examples.java.common.helpers.CameraPermissionHelper;
import com.google.ar.core.examples.java.common.helpers.DisplayRotationHelper;
import com.google.ar.core.examples.java.common.helpers.FullScreenHelper;
import com.google.ar.core.examples.java.common.helpers.SnackbarHelper;
import com.google.ar.core.examples.java.common.helpers.TrackingStateHelper;
import com.google.ar.core.examples.java.common.samplerender.Framebuffer;
import com.google.ar.core.examples.java.common.samplerender.Mesh;
import com.google.ar.core.examples.java.common.samplerender.SampleRender;
import com.google.ar.core.examples.java.common.samplerender.Shader;
import com.google.ar.core.examples.java.common.samplerender.VertexBuffer;
import com.google.ar.core.examples.java.common.samplerender.arcore.BackgroundRenderer;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


import my.application.sda.helpers.CoordsUtils;
import my.application.sda.helpers.ImageUtilsKt;
import my.application.sda.helpers.Logger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * This is a simple example that shows how to create an augmented reality (AR) application using the
 * ARCore API. The application will display any detected planes and will allow the user to tap on a
 * plane to place a 3D model.
 */
public class MainActivity extends Activity implements SampleRender.Renderer {

  private static final String TAG = MainActivity.class.getSimpleName();

  private static final float Z_NEAR = 0.1f;
  private static final float Z_FAR = 100f;


  // Rendering. The Renderers are created here, and initialized when the GL surface is created.
  private GLSurfaceView surfaceView;

  private boolean installRequested;

  private Session session;
  private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
  private DisplayRotationHelper displayRotationHelper;
  private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);
  private SampleRender render;

  private BackgroundRenderer backgroundRenderer;
  private Framebuffer virtualSceneFramebuffer;
  private boolean hasSetTextureNames = false;



  // Assumed distance from the device camera to the surface on which user will try to place objects.
  // This value affects the apparent scale of objects while the tracking method of the
  // Instant Placement point is SCREENSPACE_WITH_APPROXIMATE_DISTANCE.
  // Values in the [0.2, 2.0] meter range are a good choice for most AR experiences. Use lower
  // values for AR experiences where users are expected to place objects on surfaces close to the
  // camera. Use larger values for experiences where the user will likely be standing and trying to
  // place an object on the ground or floor in front of them.
  private static final float APPROXIMATE_DISTANCE_METERS = 2.0f;

  // Point Cloud
  private VertexBuffer pointCloudVertexBuffer;
  private Mesh pointCloudMesh;
  private Shader pointCloudShader;
  // Keep track of the last point cloud rendered to avoid updating the VBO if point cloud
  // was not changed.  Do this using the timestamp since we can't compare PointCloud objects.
  private long lastPointCloudTimestamp = 0;


  // Temporary matrix allocated here to reduce number of allocations for each frame.
  private final float[] modelMatrix = new float[16];
  private final float[] viewMatrix = new float[16];
  private final float[] projectionMatrix = new float[16];
  private final float[] modelViewMatrix = new float[16]; // view x model
  private final float[] modelViewProjectionMatrix = new float[16]; // projection x view x model
  private final float[] viewInverseMatrix = new float[16];
  private int cont = 0;

  // Button to start and stop recording ARCore feature points
  private ImageButton startRecording;

  // TextView for notifications
  private TextView textNotification;

  // Container variables
  Point[] currentPointCloud;
  FrameContainer frameContainer = new FrameContainer();

  // Logger
  private Logger logger;

  boolean isRecording;
  int frameCounter;
  File dirRecordings;
  String recordingPath;


  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    surfaceView = findViewById(R.id.surfaceview);

    logger = new Logger(this.getApplicationContext());
    logger.addRecordToLog("onCreate: init logger");

    // TakePicture
    isRecording = false;
    startRecording = (ImageButton)findViewById(R.id.startRecording);
    startRecording.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        isRecording = !isRecording;
        if(isRecording) {
          String timeStamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
          File currentVideo = new File(dirRecordings.getAbsolutePath(), timeStamp);
          currentVideo.mkdir();
          recordingPath = currentVideo.getAbsolutePath();
        }
      }
    });

    // Text label
    textNotification = (TextView)findViewById(R.id.textNotification);

    displayRotationHelper = new DisplayRotationHelper(/*context=*/ this);

    // Create directory in which we will save videos
    dirRecordings = getApplicationContext().getDir("recordings", 0);

    // Set up renderer.
    render = new SampleRender(surfaceView, this, getAssets());

    installRequested = false;
  }


  @Override
  protected void onDestroy() {
    if (session != null) {
      // Explicitly close ARCore Session to release native resources.
      // Review the API reference for important considerations before calling close() in apps with
      // more complicated lifecycle requirements:
      // https://developers.google.com/ar/reference/java/arcore/reference/com/google/ar/core/Session#close()
      session.close();
      session = null;
    }


    super.onDestroy();
  }

  @Override
  protected void onResume() {
    super.onResume();

    isRecording = false;

    if (session == null) {
      Exception exception = null;
      String message = null;
      try {
        switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
          case INSTALL_REQUESTED:
            installRequested = true;
            return;
          case INSTALLED:
            break;
        }

        // ARCore requires camera permissions to operate. If we did not yet obtain runtime
        // permission on Android M and above, now is a good time to ask the user for it.
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
          CameraPermissionHelper.requestCameraPermission(this);
          return;
        }

        // Create the session.
        session = new Session(/* context= */ this);
      } catch (UnavailableArcoreNotInstalledException
              | UnavailableUserDeclinedInstallationException e) {
        message = "Please install ARCore";
        exception = e;
      } catch (UnavailableApkTooOldException e) {
        message = "Please update ARCore";
        exception = e;
      } catch (UnavailableSdkTooOldException e) {
        message = "Please update this app";
        exception = e;
      } catch (UnavailableDeviceNotCompatibleException e) {
        message = "This device does not support AR";
        exception = e;
      } catch (Exception e) {
        message = "Failed to create AR session";
        exception = e;
      }

      if (message != null) {
        messageSnackbarHelper.showError(this, message);
        logger.addRecordToLog("onResume: " + message.toString());
        return;
      }
    }

    // Note that order matters - see the note in onPause(), the reverse applies here.
    try {
      configureSession();
      // To record a live camera session for later playback, call
      // `session.startRecording(recorderConfig)` at anytime. To playback a previously recorded AR
      // session instead of using the live camera feed, call
      // `session.setPlaybackDataset(playbackDatasetPath)` before calling `session.resume()`. To
      // learn more about recording and playback, see:
      // https://developers.google.com/ar/develop/java/recording-and-playback
      session.resume();
    } catch (CameraNotAvailableException e) {
      messageSnackbarHelper.showError(this, "Camera not available. Try restarting the app.");
      logger.addRecordToLog("onResume: Camera not available. Try restarting the app" );
      session = null;
      return;
    }

    surfaceView.onResume();
    displayRotationHelper.onResume();
  }

  @Override
  public void onPause() {
    super.onPause();
    if (session != null) {
      // Note that the order matters - GLSurfaceView is paused first so that it does not try
      // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
      // still call session.update() and get a SessionPausedException.
      displayRotationHelper.onPause();
      surfaceView.onPause();
      session.pause();
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(requestCode, permissions, results);
    if (!CameraPermissionHelper.hasCameraPermission(this)) {
      // Use toast instead of snackbar here since the activity will exit.
      Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
              .show();
      if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
        // Permission denied with checking "Do not ask again".
        CameraPermissionHelper.launchPermissionSettings(this);
      }
      finish();
    }
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
  }

  @Override
  public void onSurfaceCreated(SampleRender render) {
    // Prepare the rendering objects. This involves reading shaders and 3D model files, so may throw
    // an IOException.
    try {
      backgroundRenderer = new BackgroundRenderer(render);
      virtualSceneFramebuffer = new Framebuffer(render, /*width=*/ 1, /*height=*/ 1);

      // Set BackgroundRenderer state to match the depth settings.
      try {
        backgroundRenderer.setUseDepthVisualization(
                render, false);
        backgroundRenderer.setUseOcclusion(render, false);
      } catch (IOException e) {
        logger.addRecordToLog("onSurfaceCreated: Failed to read a required asset file\n" + e.toString());
        messageSnackbarHelper.showError(this, "Failed to read a required asset file: " + e);
        return;
      }

      // Point cloud
      pointCloudShader =
              Shader.createFromAssets(
                      render, "shaders/point_cloud.vert", "shaders/point_cloud.frag", /*defines=*/ null)
                      .setVec4(
                              "u_Color", new float[] {31.0f / 255.0f, 188.0f / 255.0f, 210.0f / 255.0f, 1.0f})
                      .setFloat("u_PointSize", 5.0f);
      // four entries per vertex: X, Y, Z, confidence
      pointCloudVertexBuffer =
              new VertexBuffer(render, /*numberOfEntriesPerVertex=*/ 4, /*entries=*/ null);
      final VertexBuffer[] pointCloudVertexBuffers = {pointCloudVertexBuffer};
      pointCloudMesh =
              new Mesh(
                      render, Mesh.PrimitiveMode.POINTS, /*indexBuffer=*/ null, pointCloudVertexBuffers);


    } catch (IOException e) {
      logger.addRecordToLog("onSurfaceCreated: Failed to read a required asset file\n" + e.toString());
      messageSnackbarHelper.showError(this, "Failed to read a required asset file: " + e);
    }
  }

  @Override
  public void onSurfaceChanged(SampleRender render, int width, int height) {
    displayRotationHelper.onSurfaceChanged(width, height);
    virtualSceneFramebuffer.resize(width, height);
  }

  @Override
  public void onDrawFrame(SampleRender render) {

    if (session == null) {
      logger.addRecordToLog("onDrawFrame: session is null");
      return;
    }

    // -- Manage ARCore Session

    // Texture names should only be set once on a GL thread unless they change. This is done during
    // onDrawFrame rather than onSurfaceCreated since the session is not guaranteed to have been
    // initialized during the execution of onSurfaceCreated.
    if (!hasSetTextureNames) {
      session.setCameraTextureNames(
              new int[] {backgroundRenderer.getCameraColorTexture().getTextureId()});
      hasSetTextureNames = true;
    }

    // Notify ARCore session that the view size changed so that the perspective matrix and
    // the video background can be properly adjusted.
    displayRotationHelper.updateSessionIfNeeded(session);

    // Obtain the current frame from ARSession. When the configuration is set to
    // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
    // camera framerate.
    Frame frame;
    try {
      frame = session.update();
    } catch (CameraNotAvailableException e) {
      logger.addRecordToLog("onDrawFrame: Camera not available during onDrawFrame\n" + e.toString());
      messageSnackbarHelper.showError(this, "Camera not available. Try restarting the app.");
      return;
    }
    Camera camera = frame.getCamera();

    // Save information about current frame on FrameContainer
    if(isRecording) {
      try {
        Image imageFrame = frame.acquireCameraImage();

        Bitmap imageBitmap = ImageUtilsKt.yuvToBitmap(imageFrame);

        float fx_d, fy_d, cx_d, cy_d;
        float[] projectionMatrix = new float[16];
        float[] viewMatrix = new float[16];
        frame.getCamera().getProjectionMatrix(projectionMatrix, 0, Z_NEAR, Z_FAR);
        frame.getCamera().getViewMatrix(viewMatrix, 0);

        fx_d = frame.getCamera().getImageIntrinsics().getFocalLength()[0];
        fy_d = frame.getCamera().getImageIntrinsics().getFocalLength()[1];
        cx_d = frame.getCamera().getImageIntrinsics().getPrincipalPoint()[0];
        cy_d = frame.getCamera().getImageIntrinsics().getPrincipalPoint()[1];

        frameContainer.fill(imageBitmap, frame.getCamera().getPose(), projectionMatrix, viewMatrix, fx_d, fy_d, cx_d, cy_d);

        //save frame image
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
        FileOutputStream fo = new FileOutputStream(recordingPath + "/img" + frameCounter + ".jpg");
        fo.write(bytes.toByteArray());
        fo.close();

        //save camera parameters
        writeCameraParametersJSON(recordingPath + "/camera" + frameCounter + ".json", frameContainer);
        imageFrame.close();
      } catch (NotYetAvailableException | IOException e) {
        e.printStackTrace();
        //logger.addRecordToLog("onDrawFrame: " + e.toString());
      }
    }

    // BackgroundRenderer.updateDisplayGeometry must be called every frame to update the coordinates
    // used to draw the background camera image.
    backgroundRenderer.updateDisplayGeometry(frame);

    // Keep the screen unlocked while tracking, but allow it to lock when tracking stops.
    trackingStateHelper.updateKeepScreenOnFlag(camera.getTrackingState());

    // -- Draw background

    if (frame.getTimestamp() != 0) {
      // Suppress rendering if the camera did not produce the first frame yet. This is to avoid
      // drawing possible leftover data from previous sessions if the texture is reused.
      backgroundRenderer.drawBackground(render);
    }

    // If not tracking, don't draw 3D objects.
    if (camera.getTrackingState() == TrackingState.PAUSED) {
      return;
    }

    // -- Draw non-occluded virtual objects (planes, point cloud)

    // Get projection matrix.
    camera.getProjectionMatrix(projectionMatrix, 0, Z_NEAR, Z_FAR);

    // Get camera matrix and draw.
    camera.getViewMatrix(viewMatrix, 0);

    // Visualize tracked points.
    // Use try-with-resources to automatically release the point cloud.
    try (PointCloud pointCloud = frame.acquirePointCloud()) {
      if (pointCloud.getTimestamp() > lastPointCloudTimestamp) {
        pointCloudVertexBuffer.set(pointCloud.getPoints());
        lastPointCloudTimestamp = pointCloud.getTimestamp();
        currentPointCloud = Point.parsePointCloud(pointCloud);

        // save pointCloud
        if(isRecording){
          writePointCloudJSON(recordingPath+"/pointCloud"+frameCounter+".json", currentPointCloud, frameContainer);
          frameCounter++;
        }
      }
      Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
      pointCloudShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);
      render.draw(pointCloudMesh, pointCloudShader);
    }

    runOnUiThread(
            new Runnable() {
              @Override
              public void run() {
                if(currentPointCloud != null)
                  textNotification.setText("Point Cloud: " + currentPointCloud.length);
              }
            });
  }

  /** Configures the session with feature settings. */
  private void configureSession() {
    Config config = session.getConfig();

    session.configure(config);
  }

  private void writeCameraParametersJSON(String filename, FrameContainer frameContainer){
    JSONObject jObj = new JSONObject();
    try{
      long timestamp = System.nanoTime();
      jObj.put("timestamp", timestamp);

      float[] cameraTranslationMatrix = new float[16];
      frameContainer.getCameraPose().toMatrix(cameraTranslationMatrix, 0);
      JSONArray cameraPose = new JSONArray(cameraTranslationMatrix);
      jObj.put("cameraPose", cameraPose);

      JSONArray projectionMatrix = new JSONArray(frameContainer.getProjectionMatrix());
      jObj.put("projectionMatrix", projectionMatrix);

      JSONArray viewMatrix = new JSONArray(frameContainer.getViewMatrix());
      jObj.put("viewMatrix", viewMatrix);

      jObj.put("fx_d", frameContainer.getFx_d());
      jObj.put("fy_d", frameContainer.getFy_d());
      jObj.put("cx_d", frameContainer.getCx_d());
      jObj.put("cy_d", frameContainer.getCy_d());

      FileOutputStream fos = new FileOutputStream(filename);
      String finalMessage = jObj.toString(5);
      fos.write(finalMessage.getBytes());
      fos.flush();
      fos.close();
    } catch (JSONException | FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void writePointCloudJSON(String filename, Point[] points, FrameContainer frameContainer){
    JSONObject jObj = new JSONObject();
    try{
      JSONArray j3D = new JSONArray();
      JSONArray j2D = new JSONArray();
      if(points != null) {
        for (Point point : points) {

          android.graphics.Point screenPoint = CoordsUtils.worldToScreen(new float[]{point.getX(), point.getY(), point.getZ()},
                  frameContainer.getImage().getWidth(),
                  frameContainer.getImage().getHeight(),
                  frameContainer.getProjectionMatrix(),
                  frameContainer.getViewMatrix());

          // Clipping point outside screen
          if(screenPoint.x >= frameContainer.getImage().getWidth() || screenPoint.y >= frameContainer.getImage().getHeight() || screenPoint.x < 0 || screenPoint.y < 0) {
            continue;
          }

          JSONArray coords3D = new JSONArray(new float[]{point.getX(), point.getY(), point.getZ()});
          j3D.put(coords3D);
          JSONArray coords2D = new JSONArray(new float[]{screenPoint.x, screenPoint.y});
          j2D.put(coords2D);
        }
      }

      jObj.put("3d_coords", j3D);
      jObj.put("2d_coords", j2D);

      FileOutputStream fos = new FileOutputStream(filename);
      String finalMessage = jObj.toString(5);
      fos.write(finalMessage.getBytes());
      fos.flush();
      fos.close();
    } catch (JSONException | FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
