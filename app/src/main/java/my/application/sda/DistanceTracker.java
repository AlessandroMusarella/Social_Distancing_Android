package my.application.sda;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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

    //tracker
    private static final int[] COLORS = {
            Color.RED,
            Color.GREEN,
            Color.YELLOW
    };
    private static final int RED = 0;
    private static final int GREEN = 1;
    private static final int YELLOW = 2;

    public DistanceTracker(){
        super();
    }

    public Bitmap getTrackedBitmap (Bitmap currentFrame, List<Detector.Recognition> mappedRecognitions){

        Bitmap resultBitmap = currentFrame.copy(Bitmap.Config.ARGB_8888, true);
        final Canvas canvas = new Canvas(resultBitmap);

        final Paint[] paints = new Paint[3];
        for(int i=0; i<paints.length; i++){
            paints[i] = new Paint();
            paints[i].setColor(COLORS[i]);
            paints[i].setStyle(Paint.Style.STROKE);
            paints[i].setStrokeWidth(2.0f);
        }

        Float3[] cordinates = get3dCoordinates(mappedRecognitions);

        for (int i = 0; i < mappedRecognitions.size(); i++) {
            float miniDistance = Float.MAX_VALUE;
            for (int j = 0; j < mappedRecognitions.size() && j != i; j++) {
                float tempDistance = getDistanceBetweenPeople(cordinates[i], cordinates[j]);
                if (tempDistance < miniDistance)
                    miniDistance = tempDistance;
            }
            if (miniDistance > 2)
                canvas.drawRect(mappedRecognitions.get(i).getLocation(), paints[GREEN]);
            else if (miniDistance > 1 && miniDistance < 2)
                canvas.drawRect(mappedRecognitions.get(i).getLocation(), paints[YELLOW]);
            else if (miniDistance < 1) {
                canvas.drawRect(mappedRecognitions.get(i).getLocation(), paints[RED]);
            }
        }

        return resultBitmap;
    }

    public void setDepthMap(FloatBuffer depthMap, float scaleFactor, float shiftFactor){
        this.depthMap = depthMap;           //????????????????????????????????????
        this.scaleFactor = scaleFactor;
        this.shiftFactor = shiftFactor;
    }

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

        width = 640;
        height = 480;

        Matrix.multiplyMM(viewProj, 0, projectionMatrix, 0 , viewMatrix, 0);
        Matrix.invertM(inverse, 0, viewProj, 0);

        for(int i=0; i<mappedRecognitions.size(); i++){
            int x_screen = (int) mappedRecognitions.get(i).getLocation().centerX(); //???????????????????????????????????
            int y_screen = (int) mappedRecognitions.get(i).getLocation().centerY(); //???????????????????????????????????
            float x_norm = (2f*x_screen)/(float)width - 1f;
            float y_norm = 1f - (2f*y_screen)/(float)height;

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
