package my.application.sda;

import android.opengl.Matrix;
import android.renderscript.Float3;

import java.nio.FloatBuffer;
import java.util.List;

import my.application.sda.detector.Detector;

public class DistanceTracker {

    // Camera Matrix
    private float[] viewMatrix = new float[16];
    private float[] projectionMatrix = new float[16];
    private int height;
    private int width;

    // Depth
    private FloatBuffer depthMap;
    private float scaleFactor;
    private float shiftFactor;



    public void setCameraMatrix(float[] viewMatrix, float[] projectionMatrix){
        this.viewMatrix = viewMatrix;
        this.projectionMatrix = projectionMatrix;
    }

    private Float3[] get3dCoordinates(List<Detector.Recognition> mappedRecognitions){
        float A = -1.000001f;
        float B = -0.001000001f;
        Float3[] result = new Float3[mappedRecognitions.size()];
        float[] viewProj = new float[16];
        float[] inverse = new float[16];
        float[] inPoint = new float[4];
        float[] position = new float[4];

        Matrix.multiplyMM(viewProj, 0, projectionMatrix, 0 , viewMatrix, 0);
        Matrix.invertM(inverse, 0, viewProj, 0);

        for(int i=0; i<mappedRecognitions.size(); i++){
            int x_screen = (int) mappedRecognitions.get(i).getLocation().centerX(); //???????????????????????????????????
            int y_screen = (int) mappedRecognitions.get(i).getLocation().centerY(); //???????????????????????????????????
            float x_norm = (2*x_screen)/width - 1f;
            float y_norm = 1f - (2*y_screen)/height;

            float depth = depthMap.get(y_screen*width + x_screen);  //disparity value, between [0,1]
            depth = depth * scaleFactor + shiftFactor;
            depth = 1 / depth;  //distance from the camera measured in meters

            depth = (-A*depth + B) / depth;

            inPoint[0] = x_norm;
            inPoint[1] = y_norm;
            inPoint[2] = depth;
            inPoint[3] = 1f;

            Matrix.multiplyMV(position, 0, inverse, 0, inPoint, 0);

            position[0] = position[0] / position[3];
            position[1] = position[1] / position[3];
            position[2] = position[2] / position[3];

            result[i] = new Float3(position[0], position[1], position[2]);
        }

        return result;
    }

    private Float getDistanceBetweenPeople(Float3 p1, Float3 p2){
        return (float) Math.sqrt(Math.pow(p1.x -p2.x, 2) + Math.pow(p1.x -p2.x, 2) + Math.pow(p1.x -p2.x, 2));
    }
}
