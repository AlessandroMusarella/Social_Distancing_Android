package my.application.sda;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.Image;

import com.google.ar.core.Frame;
import com.google.ar.core.PointCloud;
import com.google.ar.core.exceptions.NotYetAvailableException;

import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import my.application.sda.calibrator.Calibrator;
import my.application.sda.calibrator.Point;
import my.application.sda.helpers.ImageUtilsKt;
import my.application.sda.model.TFLiteDepthModel;

public class DepthCalibrator {
    // ML model for depth inference
    private TFLiteDepthModel model;
    private ImageProcessor imageProcessor;

    // Current image
    private Image image;
    private Bitmap imageBitmap;
    private Bitmap depthBitmap;

    // Calibrator
    private Calibrator calibrator;
    final private int numberOfIterations = 100;
    final private float normalizedThreshold = 0.1f;
    final private float percentagePossibleInlier = 0.33f;

    private float[] viewMatrix = new float[16];
    private float[] projectionMatrix = new float[16];

    // Outputs
    private FloatBuffer depthMap;
    private double scaleFactor;
    private double shiftFactor;


    public DepthCalibrator(Context context, int viewWidth, int viewHeight){
        model = new TFLiteDepthModel("tflite_pydnet.tflite");
        try {
            model.init(context);
        } catch (IOException e) {
            e.printStackTrace();
        }

        int temp = model.depthWidth*viewHeight/viewWidth;

        imageProcessor = new ImageProcessor.Builder().add(new ResizeOp(model.depthHeight, model.depthWidth, ResizeOp.ResizeMethod.BILINEAR))
                                                     .add(new NormalizeOp(0,255))
                                                     .build();

        calibrator = new Calibrator(viewWidth, viewHeight, model.depthWidth, model.depthHeight, numberOfIterations, normalizedThreshold, percentagePossibleInlier);
    }

    public void doInference(Frame frame, Point[] pointCloud){

        // Acquire current image from camera
        try {
            image = frame.acquireCameraImage();
        } catch (NotYetAvailableException e) {
            e.printStackTrace();
            return;
        }
        imageBitmap = ImageUtilsKt.yuvToBitmap(image);

        // Resize, crop and normalize via Tensorflow
        TensorImage tensorImage = new TensorImage();
        tensorImage.load(imageBitmap);
        TensorImage normalizedTensorImage = imageProcessor.process(tensorImage);
        ByteBuffer modelInput = normalizedTensorImage.getBuffer();

        // Get prediction
        ByteBuffer inference = model.doInference(modelInput);

        FloatBuffer floatOutput = inference.asFloatBuffer();
        FloatBuffer normalizedOutput = normalizeOutput(floatOutput);

        // To visualize if the output is correct
        Bitmap argbOutputBitmap = Bitmap.createBitmap(model.depthWidth, model.depthHeight, Bitmap.Config.ARGB_8888);
        for(int x=0; x<model.depthWidth; x++){
            for(int y=0; y<model.depthHeight; y++){
                float depth = normalizedOutput.get(model.depthWidth*y + x);
                int pixel = (int) (depth*255);
                floatOutput.rewind();
                int color = Color.rgb(pixel, pixel, pixel);
                argbOutputBitmap.setPixel(x,y,color);
            }
        }
        normalizedOutput.rewind();

        depthBitmap = Bitmap.createScaledBitmap(argbOutputBitmap, image.getWidth(), image.getHeight(), false);


        // Calibrate depth map
        frame.getCamera().getProjectionMatrix(projectionMatrix, 0, 0.05f, 100f);
        frame.getCamera().getViewMatrix(viewMatrix, 0);
        calibrator.setCalibrator(frame.getCamera().getPose(), projectionMatrix , viewMatrix);
        calibrator.calibrate(normalizedOutput, pointCloud);

        scaleFactor = calibrator.getScaleFactor();
        shiftFactor = calibrator.getShiftFactor();
        depthMap = normalizedOutput;

        image.close();
    }


    private FloatBuffer normalizeOutput(FloatBuffer modelOutput) {
        FloatBuffer result = FloatBuffer.allocate(modelOutput.remaining());

        float max = Float.MIN_VALUE;
        float min = Float.MAX_VALUE;

        while(modelOutput.hasRemaining()){
            float depth = modelOutput.get();
            if(depth > max)
                max = depth;
            else if(depth < min)
                min = depth;
        }

        modelOutput.rewind();

        while(modelOutput.hasRemaining()){
            float depth = modelOutput.get();
            float normalizedDepth = (depth - min) / (max - min);
            result.put(normalizedDepth);
        }

        return result;
    }

    public Bitmap getImageBitmap() {
        return imageBitmap;
    }

    public Bitmap getDepthBitmap() { return depthBitmap; }

    public FloatBuffer getDepthMap() {
        return depthMap;
    }

    public double getScaleFactor() {
        return scaleFactor;
    }

    public double getShiftFactor() {
        return shiftFactor;
    }
}
