package my.application.sda;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.opengl.Matrix;
import android.renderscript.Float3;

import java.math.RoundingMode;
import java.nio.FloatBuffer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import my.application.sda.detector.Detector;

public class DistanceTracker {

    // Camera Matrix
    private float[] viewMatrix = new float[16];
    private float[] projectionMatrix = new float[16];
    private int height;
    private int width;
    float fx_d, fy_d, cx_d, cy_d;

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

    private Paint[] paints;

    private Path path;

    public DistanceTracker(){
        super();
        paints = new Paint[3];
        for(int i=0; i<paints.length; i++){
            paints[i] = new Paint();
            paints[i].setColor(COLORS[i]);
            paints[i].setStyle(Paint.Style.STROKE);
            paints[i].setStrokeWidth(2.0f);
            paints[i].setTextSize(18);
        }
    }

    public Bitmap getTrackedBitmap (Bitmap currentFrame, List<Detector.Recognition> mappedRecognitions){

        Bitmap resultBitmap = currentFrame.copy(Bitmap.Config.ARGB_8888, true);
        final Canvas canvas = new Canvas(resultBitmap);

        Float3[] cordinates = get3dCoordinates(mappedRecognitions);
        //Float3[] coordinatesOld = get3dCoordinatesOLD(mappedRecognitions);

        //formatting distance between persons
        DecimalFormat df = new DecimalFormat("##.##");
        df.setRoundingMode(RoundingMode.DOWN);

        List<DistanceBetween> distancesList = new ArrayList<DistanceBetween>();

        //disegno i quadrati
        for (int i = 0; i < mappedRecognitions.size(); i++) {
            float minDistance = Float.MAX_VALUE;
            int minJ = -1;
            for (int j = 0; j < mappedRecognitions.size(); j++) {
                if (i != j) {
                    float tempDistance = getDistanceBetweenPeople(cordinates[i], cordinates[j]);
                    if (tempDistance < minDistance) {
                        minDistance = tempDistance;
                        minJ = j;
                    }
                }
            }
            if (minDistance > 2)
                canvas.drawRect(mappedRecognitions.get(i).getLocation(), paints[GREEN]);
            else if (minDistance > 1 && minDistance < 2)
                canvas.drawRect(mappedRecognitions.get(i).getLocation(), paints[YELLOW]);
            else if (minDistance < 1) {
                canvas.drawRect(mappedRecognitions.get(i).getLocation(), paints[RED]);
            }
            if (i < minJ){
                distancesList.add(new DistanceBetween(mappedRecognitions.get(i).getLocation(), mappedRecognitions.get(minJ).getLocation(), minDistance));
            }
        }

        for (DistanceBetween d : distancesList) {
            if (d.location1.centerX() > d.location2.centerX()) {
                RectF temp = d.location1;
                d.location1 = d.location2;
                d.location2 = temp;
            }

            float x1 = d.location1.centerX();
            float x2 = d.location2.centerX();
            float y1 = d.location1.centerY();
            float y2 = d.location2.centerY();

            if (d.distance >= 1 && d.distance < 2) {
                path = new Path();
                path.moveTo(x1, y1);
                path.lineTo(x2, y2);
                float hOffset = (float) Math.sqrt(Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2))/2f;
                canvas.drawPath(path, paints[YELLOW]);
                canvas.drawTextOnPath(df.format(d.distance), path, hOffset, 10f, paints[YELLOW]);
            } else if (d.distance < 1) {
                path = new Path();
                path.moveTo(x1, y1);
                path.lineTo(x2, y2);
                float hOffset = (float) Math.sqrt(Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2)) / 2f;
                canvas.drawPath(path, paints[RED]);
                canvas.drawTextOnPath(df.format(d.distance), path, hOffset, 10f, paints[RED]);
            }
        }
        return resultBitmap;
    }



    public void setDepthMap(FloatBuffer depthMap, float scaleFactor, float shiftFactor){
        this.depthMap = depthMap;           //????????????????????????????????????
        this.scaleFactor = scaleFactor;
        this.shiftFactor = shiftFactor;
    }

    public void setCameraMatrix(float[] viewMatrix, float[] projectionMatrix, float fx_d, float fy_d, float cx_d, float cy_d){
        this.viewMatrix = viewMatrix;
        this.projectionMatrix = projectionMatrix;
        this.fx_d = fx_d;
        this.fy_d = fy_d;
        this.cx_d = cx_d;
        this.cy_d = cy_d;
    }

    private Float3[] get3dCoordinatesOLD(List<Detector.Recognition> mappedRecognitions){
        float A = projectionMatrix[10];
        float B = projectionMatrix[11];
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

    private Float3[] get3dCoordinates(List<Detector.Recognition> mappedRecognitions){
        float A = projectionMatrix[10];
        float B = projectionMatrix[11];
        Float3[] result = new Float3[mappedRecognitions.size()];
        float[] viewProj = new float[16];
        float[] inverse = new float[16];
        float[] position = new float[4];

        width = 640;
        height = 480;

        Matrix.multiplyMM(viewProj, 0, projectionMatrix, 0 , viewMatrix, 0);
        Matrix.invertM(inverse, 0, viewProj, 0);

        for(int i=0; i<mappedRecognitions.size(); i++){
            int x_screen = (int) mappedRecognitions.get(i).getLocation().centerX(); //???????????????????????????????????
            int y_screen = (int) mappedRecognitions.get(i).getLocation().centerY(); //???????????????????????????????????

            float depth = depthMap.get(y_screen*width + x_screen);  //disparity value, between [0,1]
            depth = depth * scaleFactor + shiftFactor;
            depth = 1 / depth;  //distance from the camera measured in meters

            position[0] = (x_screen - cx_d) * depth / fx_d;
            position[1] = (y_screen - cy_d) * depth / fy_d;
            position[2] = depth;

            result[i] = new Float3(position[0], position[1], position[2]);
        }

        return result;
    }

    private Float getDistanceBetweenPeople(Float3 p1, Float3 p2){
        return (float) Math.sqrt(Math.pow(p1.x -p2.x, 2) + Math.pow(p1.x -p2.x, 2) + Math.pow(p1.x -p2.x, 2));
    }
}

class DistanceBetween {
    protected RectF location1, location2;
    protected float distance;

    public DistanceBetween(RectF location1, RectF location2, float distance){
        this.distance = distance;
        this.location1 = location1;
        this.location2 = location2;
    }
}