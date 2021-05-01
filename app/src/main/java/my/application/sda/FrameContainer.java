package my.application.sda;

// Object to contain information about ARFrame and avoid issue with concurrency

import android.graphics.Bitmap;
import android.media.Image;
import com.google.ar.core.Pose;
import my.application.sda.Point;

public class FrameContainer {

    private Bitmap image;
    private Point[] pointCloud;

    private Pose cameraPose;
    private float[] projectionMatrix;
    private float[] viewMatrix;
    private float fx_d;
    private float fy_d;
    private float cx_d;
    private float cy_d;

    public FrameContainer(){
        super();
    }

    public void fill(Bitmap image, Pose cameraPose, float[] projectionMatrix, float[] viewMatrix, float fx_d, float fy_d, float cx_d, float cy_d) {
        this.image = image;
        this.cameraPose = cameraPose;
        this.projectionMatrix = projectionMatrix;
        this.viewMatrix = viewMatrix;
        this.fx_d = fx_d;
        this.fy_d = fy_d;
        this.cx_d = cx_d;
        this.cy_d = cy_d;
    }

    public void setPointCloud(Point[] pointCloud) {
        this.pointCloud = pointCloud;
    }

    public Bitmap getImage() {
        return image;
    }

    public Point[] getPointCloud() {
        return pointCloud;
    }

    public Pose getCameraPose() {
        return cameraPose;
    }

    public float[] getProjectionMatrix() {
        return projectionMatrix;
    }

    public float[] getViewMatrix() {
        return viewMatrix;
    }

    public float getFx_d() {
        return fx_d;
    }

    public float getFy_d() {
        return fy_d;
    }

    public float getCx_d() {
        return cx_d;
    }

    public float getCy_d() {
        return cy_d;
    }
}