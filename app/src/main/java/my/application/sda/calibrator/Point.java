package my.application.sda.calibrator;

import com.google.ar.core.PointCloud;

import java.nio.FloatBuffer;

// Single point of AR PointCloud
public class Point {

    // Static method
    public static Point[] parsePointCloud(PointCloud pointCloud){

        FloatBuffer points = pointCloud.getPoints();
        points.rewind();

        int numPoints = points.remaining() / 4;
        Point[] result = new Point[numPoints];

        float[] point = new float[4];

        for (int i = 0; i < numPoints; i++) {
            points.get(point, 0, 4);
            result[i] = new Point(point);
        }

        return result;
    }


    // Class
    private float x,y,z,confidence;

    public Point(float x, float y, float z, float confidence) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.confidence = confidence;
    }

    private Point(float[] point){
        setPoint(point);
    }

    private void setPoint(float[] point){
        this.x = point[0];
        this.y = point[1];
        this.z = point[2];
        this.confidence = point[3];
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getZ() {
        return z;
    }

    public float getConfidence() {
        return confidence;
    }
}
