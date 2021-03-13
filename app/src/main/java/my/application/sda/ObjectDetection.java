package my.application.sda;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.Size;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import my.application.sda.detector.BorderedText;
import my.application.sda.detector.Detector;
import my.application.sda.detector.MultiBoxTracker;
import my.application.sda.model.TFLiteObjectDetectionModel;

public class ObjectDetection {

    private TFLiteObjectDetectionModel ODModel;
    private MultiBoxTracker tracker;
    private BorderedText borderedText;

    private static final boolean TF_OD_API_IS_QUANTIZED = true;
    private static final String TF_OD_API_MODEL_FILE = "detect.tflite";
    private static final String TF_OD_API_LABELS_FILE = "labelmap.txt";
    private static final DetectorMode MODE = DetectorMode.TF_OD_API;
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.5f;
    private Size  INPUT_SIZE = new Size(640, 480);
    private boolean computingDetection = false;

    public void init (Context context, String modelFileName, float textSizePx) throws IOException {
        ODModel = new TFLiteObjectDetectionModel(context, modelFileName);
        borderedText = new BorderedText(textSizePx);
        tracker = new MultiBoxTracker(context);
    }

    public void doTrackRecognitions(Bitmap croppedBitmap, Bitmap cropToFrameTransform, long currTimestamp){
        final List<Detector.Recognition> results = getODModel().recognizeImage(croppedBitmap);
        Bitmap cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);

        final Canvas canvas = new Canvas(cropCopyBitmap);
        final Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.0f);

        float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
        switch (MODE) {
            case TF_OD_API:
                minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                break;
        }

        final List<Detector.Recognition> mappedRecognitions =
                new ArrayList<Detector.Recognition>();


        for (final Detector.Recognition result : results) {
            final RectF location = result.getLocation();
            if (location != null && result.getConfidence() >= minimumConfidence && result.getTitle().equals("person")) {
                canvas.drawRect(location, paint);

                cropToFrameTransform.mapRect(location);
                result.setLocation(location);
                mappedRecognitions.add(result);
            }
        }
        tracker.trackResults(mappedRecognitions, currTimestamp);

        computingDetection = false;
    }

    public TFLiteObjectDetectionModel getODModel() {
        return ODModel;
    }

    public MultiBoxTracker getTracker() {
        return tracker;
    }

    public BorderedText getBorderedText() {
        return borderedText;
    }

    public Size getINPUT_SIZE() {
        return INPUT_SIZE;
    }

    private enum DetectorMode {
        TF_OD_API;
    }
}
