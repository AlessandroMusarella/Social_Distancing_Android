package my.application.sda.detector;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Matrix;
import android.os.Environment;
import android.util.Size;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import my.application.sda.detector.BorderedText;
import my.application.sda.detector.Detector;
import my.application.sda.detector.MultiBoxTracker;
import my.application.sda.model.TFLiteDepthModel;
import my.application.sda.model.TFLiteObjectDetectionModel;

public class PersonDetection {

    private TFLiteObjectDetectionModel ODModel;
    private MultiBoxTracker tracker;
    private BorderedText borderedText;
    private Bitmap cropCopyBitmap;
    private Bitmap croppedBitmap;

    private static final DetectorMode MODE = DetectorMode.TF_OD_API;
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.65f;
    private Size INPUT_SIZE = new Size(640, 480);

    public PersonDetection(){
        super();
    }

    public void init (Context context, String modelFileName, float textSizePx) throws IOException {
        ODModel = new TFLiteObjectDetectionModel(context, modelFileName);
        borderedText = new BorderedText(textSizePx);
        tracker = new MultiBoxTracker(context);
    }


    public Bitmap getRecognitionBitmapfrom(Bitmap currentFrameBitmap, Matrix cropToFrameTransform){

        currentFrameBitmap = currentFrameBitmap.copy(Bitmap.Config.ARGB_8888, true);
        croppedBitmap = Bitmap.createScaledBitmap(currentFrameBitmap, 300, 300, true);

        final List<Detector.Recognition> results = getODModel().recognizeImage(croppedBitmap);
        cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);

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

        return Bitmap.createScaledBitmap(cropCopyBitmap, getINPUT_SIZE().getWidth(), getINPUT_SIZE().getHeight(), true);

    }

    public List<Detector.Recognition> getRecognitionsTrackedfrom(Bitmap currentFrameBitmap, Matrix cropToFrameTransform){

        currentFrameBitmap = currentFrameBitmap.copy(Bitmap.Config.ARGB_8888, true);
        croppedBitmap = Bitmap.createScaledBitmap(currentFrameBitmap, 300, 300, true);

        final List<Detector.Recognition> results = getODModel().recognizeImage(croppedBitmap);

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
                cropToFrameTransform.mapRect(location);
                result.setLocation(location);
                mappedRecognitions.add(result);
            }
        }

        return mappedRecognitions;
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
