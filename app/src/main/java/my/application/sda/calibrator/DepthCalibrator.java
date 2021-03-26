package my.application.sda.calibrator;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.Image;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

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
    final private int numberOfIterations = 200;
    final private float normalizedThreshold = 0.08f;
    final private float percentagePossibleInlier = 0.3f;

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

    public void doInference(FrameContainer frameContainer){

        imageBitmap = frameContainer.getImage();

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

        depthBitmap = Bitmap.createScaledBitmap(argbOutputBitmap, imageBitmap.getWidth(), imageBitmap.getHeight(), true);

        // Calibrate depth map
        calibrator.setCalibrator(frameContainer.getCameraPose(), frameContainer.getProjectionMatrix() , frameContainer.getViewMatrix());
        calibrator.calibrate(normalizedOutput, frameContainer.getPointCloud());

        scaleFactor = calibrator.getScaleFactor();
        shiftFactor = calibrator.getShiftFactor();
        depthMap = bufferFromBitmap(depthBitmap);
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

    // return a FloatBuffer, with values in [0,1], from a depth bitmap, with values from [0,255]
    private FloatBuffer bufferFromBitmap(Bitmap bitmap){
        int numValues= bitmap.getWidth()*bitmap.getHeight();
        FloatBuffer result = FloatBuffer.allocate(DataType.FLOAT32.byteSize()*numValues);

        for (int i=0; i<numValues; i++){
            int color = bitmap.getPixel(i % bitmap.getWidth(), i / bitmap.getWidth());
            int R = (color >> 16) & 0xff;

            float value = (float) R / 255f;
            result.put(value);
        }

        result.rewind();
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
