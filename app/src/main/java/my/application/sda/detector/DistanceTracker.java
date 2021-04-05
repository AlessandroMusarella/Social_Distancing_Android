package my.application.sda.detector;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.opengl.Matrix;
import android.renderscript.Float3;
import android.renderscript.Float4;

import java.math.RoundingMode;
import java.nio.FloatBuffer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DistanceTracker {

    // Camera Parameters
    private int height;
    private int width;
    float fx_d, fy_d, cx_d, cy_d;

    // Depth
    private FloatBuffer depthMap;
    private float scaleFactor;
    private float shiftFactor;

    // Tracker
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

    private List<String> colored_shapes;

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

        colored_shapes = new ArrayList<>();

        Bitmap resultBitmap = currentFrame.copy(Bitmap.Config.ARGB_8888, true);
        final Canvas canvas = new Canvas(resultBitmap);

        Float4[] coordinates = get3dCoordinates(mappedRecognitions);

        // Formatting distance between persons
        DecimalFormat df = new DecimalFormat("##.##");
        df.setRoundingMode(RoundingMode.DOWN);

        List<DistanceBetween> distancesList = new ArrayList<DistanceBetween>();

        // Draw shape
        for (int i = 0; i < mappedRecognitions.size(); i++) {
            float minDistance = Float.MAX_VALUE;
            int minJ = -1;
            for (int j = 0; j < mappedRecognitions.size(); j++) {
                if (i != j) {
                    float tempDistance = getDistanceBetweenPeople(coordinates[i], coordinates[j]);
                    if (tempDistance < minDistance) {
                        minDistance = tempDistance;
                        minJ = j;
                    }
                }
            }
            if (minDistance > 2) {
                canvas.drawRect(mappedRecognitions.get(i).getLocation(), paints[GREEN]);
                canvas.drawText(df.format(coordinates[i].w), mappedRecognitions.get(i).getLocation().centerX(), mappedRecognitions.get(i).getLocation().centerY(), paints[GREEN]);
                colored_shapes.add("GREEN");
            } else if (minDistance > 1 && minDistance < 2) {
                canvas.drawRect(mappedRecognitions.get(i).getLocation(), paints[YELLOW]);
                canvas.drawText(df.format(coordinates[i].w), mappedRecognitions.get(i).getLocation().centerX(), mappedRecognitions.get(i).getLocation().centerY(), paints[YELLOW]);
                colored_shapes.add("YELLOW");
            } else if (minDistance < 1) {
                canvas.drawRect(mappedRecognitions.get(i).getLocation(), paints[RED]);
                canvas.drawText(df.format(coordinates[i].w), mappedRecognitions.get(i).getLocation().centerX(), mappedRecognitions.get(i).getLocation().centerY(), paints[RED]);
                colored_shapes.add("RED");
            }

            if (minJ >= 0){
                DistanceBetween distance = new DistanceBetween(i, minJ, mappedRecognitions.get(i).getLocation(), mappedRecognitions.get(minJ).getLocation(), minDistance);
                boolean contains = false;
                for (DistanceBetween d : distancesList) {
                    if (distance.equals(d))
                        contains = true;
                }
                if (!contains)
                    distancesList.add(distance);
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
        this.depthMap = depthMap;
        this.scaleFactor = scaleFactor;
        this.shiftFactor = shiftFactor;
    }

    public void setCameraParameters(float fx_d, float fy_d, float cx_d, float cy_d){
        this.fx_d = fx_d;
        this.fy_d = fy_d;
        this.cx_d = cx_d;
        this.cy_d = cy_d;
    }

    // https://medium.com/yodayoda/from-depth-map-to-point-cloud-7473721d3f

    private Float4[] get3dCoordinates(List<Detector.Recognition> mappedRecognitions){
        Float4[] result = new Float4[mappedRecognitions.size()];

        width = 640;
        height = 480;

        for(int i=0; i<mappedRecognitions.size(); i++){
            int u = (int) mappedRecognitions.get(i).getLocation().centerX();
            int v = (int) mappedRecognitions.get(i).getLocation().centerY();

            float depth = depthMap.get(v*width + u);  //disparity value, between [0,1]
            depth = depth * scaleFactor + shiftFactor;
            depth = 1 / depth;  //distance from the camera measured in meters

            float x_over_z = (cx_d - u) / fx_d;
            float y_over_z = (cy_d - v) / fy_d;
            float z = (float) (depth / Math.sqrt(1. + Math.pow(x_over_z,2) + Math.pow(y_over_z,2)));
            float x = x_over_z * z;
            float y = y_over_z * z;

            result[i] = new Float4(x, y, z, depth);
        }

        return result;
    }

    private Float getDistanceBetweenPeople(Float4 p1, Float4 p2){
        return (float) Math.sqrt(Math.pow(p1.x - p2.x, 2) + Math.pow(p1.y - p2.y, 2) + Math.pow(p1.z -p2.z, 2));
    }

    public List<String> getColored_shapes() {
        return colored_shapes;
    }
}

class DistanceBetween {
    protected RectF location1, location2;
    protected float distance;
    protected int person1, person2;

    public DistanceBetween(int person1, int person2, RectF location1, RectF location2, float distance){
        this.distance = distance;
        this.location1 = location1;
        this.location2 = location2;
        this.person1 = person1;
        this.person2 = person2;
    }

    public boolean equals(DistanceBetween d){
        if((this.person1 == d.person1 && this.person2 == d.person2) || (this.person1 == d.person2 && this.person2 == d.person1)){
            return true;
        }
        else return false;
    }
}