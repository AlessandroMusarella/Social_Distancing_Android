package my.application.sda;

import android.renderscript.Float3;

import java.nio.FloatBuffer;
import java.util.List;

import my.application.sda.detector.Detector;

public class DistanceTracker {

    // Depth
    private FloatBuffer depthMap;
    private double scaleFactor;
    private double shiftFactor;




    private Float3[] get3dCoordinate(List<Detector.Recognition> mappedRecognitions){
    }

    private Float getDistance(Float3 person1, Float3 person2){

    }
}
