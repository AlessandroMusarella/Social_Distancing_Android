package my.application.sda.model;

/*
href="https://github.com/tensorflow/examples/blob/master/lite/examples/object_detection/android/lib_interpreter/src/main/java/org/tensorflow/lite/examples/detection/tflite/TFLiteObjectDetectionAPIModel.java">lib_interpreter</a>
 */

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Trace;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;
import org.tensorflow.lite.task.vision.detector.ObjectDetector.ObjectDetectorOptions;

import my.application.sda.detector.Detector;

/**
 * Wrapper for frozen detection models trained using the Tensorflow Object Detection API: -
 * https://github.com/tensorflow/models/tree/master/research/object_detection where you can find the
 * training code.
 *
 * <p>To use pretrained models in the API or convert to TF Lite models, please see docs for details:
 * -
 * https://github.com/tensorflow/models/blob/master/research/object_detection/g3doc/tf1_detection_zoo.md
 * -
 * https://github.com/tensorflow/models/blob/master/research/object_detection/g3doc/tf2_detection_zoo.md
 * -
 * https://github.com/tensorflow/models/blob/master/research/object_detection/g3doc/running_on_mobile_tensorflowlite.md#running-our-model-on-android
 *
 * <p>For more information about Metadata and associated fields (eg: `labels.txt`), see <a
 * href="https://www.tensorflow.org/lite/convert/metadata#read_the_metadata_from_models">Read the
 * metadata from models</a>
 */

public class TFLiteObjectDetectionModel{

    /** Only return this many results. */
    private static final int NUM_DETECTIONS = 50;

    private final MappedByteBuffer modelBuffer;

    /** An instance of the driver class to run model inference with Tensorflow Lite. */
    private ObjectDetector objectDetector;

    /** Builder of the options used to config the ObjectDetector. */
    private final ObjectDetectorOptions.Builder optionsBuilder;

    /**
     * Initializes a native TensorFlow session for classifying images.
     *
     * <p>{@code labelFilename}, {@code inputSize}, and {@code isQuantized}, are NOT required, but to
     * keep consistency with the implementation using the TFLite Interpreter Java API. See <a
     * href="https://github.com/tensorflow/examples/blob/master/lite/examples/object_detection/android/lib_interpreter/src/main/java/org/tensorflow/lite/examples/detection/tflite/TFLiteObjectDetectionAPIModel.java">lib_interpreter</a>.
     *
     * @param modelFilename The model file path relative to the assets folder
     * @param labelFilename The label file path relative to the assets folder
     * @param inputSize The size of image input
     * @param isQuantized Boolean representing model is quantized or not
     */

    public TFLiteObjectDetectionModel(Context context, String modelFilename) throws IOException {
        modelBuffer = FileUtil.loadMappedFile(context, modelFilename);
        optionsBuilder = ObjectDetectorOptions.builder().setMaxResults(NUM_DETECTIONS);
        objectDetector = ObjectDetector.createFromBufferAndOptions(modelBuffer, optionsBuilder.build());
    }

    public List<Detector.Recognition> recognizeImage(final Bitmap bitmap) {
        // Log this method so that it can be analyzed with systrace.
        Trace.beginSection("recognizeImage");
        List<Detection> results = objectDetector.detect(TensorImage.fromBitmap(bitmap));

        // Converts a list of {@link Detection} objects into a list of {@link Recognition} objects
        // to match the interface of other inference method, such as using the <a
        // href="https://github.com/tensorflow/examples/tree/master/lite/examples/object_detection/android/lib_interpreter">TFLite
        // Java API.</a>.
        final ArrayList<Detector.Recognition> recognitions = new ArrayList<>();
        int cnt = 0;
        for (Detection detection : results) {
            recognitions.add(
                    new Detector.Recognition(
                            "" + cnt++,
                            detection.getCategories().get(0).getLabel(),
                            detection.getCategories().get(0).getScore(),
                            detection.getBoundingBox()));
        }
        Trace.endSection(); // "recognizeImage"
        return recognitions;
    }

    public void enableStatLogging(final boolean logStats) {}

    public String getStatString() {
        return "";
    }

    public void close() {
        if (objectDetector != null) {
            objectDetector.close();
        }
    }

    public void setNumThreads(int numThreads) {
        if (objectDetector != null) {
            optionsBuilder.setNumThreads(numThreads);
            recreateDetector();
        }
    }

    public void setUseNNAPI(boolean isChecked) {
        throw new UnsupportedOperationException(
                "Manipulating the hardware accelerators is not allowed in the Task"
                        + " library currently. Only CPU is allowed.");
    }

    private void recreateDetector() {
        objectDetector.close();
        objectDetector = ObjectDetector.createFromBufferAndOptions(modelBuffer, optionsBuilder.build());
    }
}
