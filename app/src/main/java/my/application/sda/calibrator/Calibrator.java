package my.application.sda.calibrator;

import android.graphics.Point;
import android.opengl.Matrix;

import com.google.ar.core.PointCloud;
import com.google.ar.core.Pose;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Random;
import java.util.SortedSet;
import java.util.TreeSet;

class RansacIntelObject{
    public double groundTruth;
    public double prediction;

    public RansacIntelObject(double groundTruth, double prediction){
        this.groundTruth = groundTruth;
        this.prediction = prediction;
    }
}


public class Calibrator {

    private double scaleFactor = 1.;
    private double shiftFactor = 0.;

    private Pose camera;
    private float[] projectionMatrix;
    private float[] viewMatrix;
    private int width;
    private int height;
    private double scaleX;
    private double scaleY;

    //Ransac Intel variables
    private int numberOfIterations;
    private double normalizedThreshold;
    private double percentagePossibleInlier;
    private Random rnd = new Random();

    public Calibrator(int width, int height, double scaleX, double scaleY, int numberOfIterations, double normalizedThreshold, double percentagePossibleInlier) {
        this.width = width;
        this.height = height;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.numberOfIterations = numberOfIterations;
        this.normalizedThreshold = normalizedThreshold;
        this.percentagePossibleInlier = percentagePossibleInlier;
    }

    public double getScaleFactor() {
        return scaleFactor;
    }

    public double getShiftFactor() {
        return shiftFactor;
    }

    public void setCalibrator(Pose camera, float[] projectionMatrix, float[] viewMatrix){
        this.camera = camera;
        this.projectionMatrix = projectionMatrix;
        this.viewMatrix = viewMatrix;
    }


    public void calibrate(FloatBuffer prediction, PointCloud pointCloud){       // i punti sono da rilasciare alla fine con release???

        FloatBuffer points = pointCloud.getPoints();
        int numPoints = points.remaining() / 4;
        float[] point = new float[4];
        RansacIntelObject[] ransacIntelObjects = new RansacIntelObject[numPoints];

        int numValidPoint = 0;
        for(int i=0; i<numPoints; i++){
            point[0] = points.get();    //x
            point[1] = points.get();    //y
            point[2] = points.get();    //z
            point[3] = points.get();    //confidence

            // Unproject point to screen
            Point screenPoint = worldToScreen(point, width, height, projectionMatrix, viewMatrix);

            // Clipping point outside screen
            if(screenPoint.x >= width || screenPoint.y > height || screenPoint.x < 0 || screenPoint.y < 0) {
                continue;
            }

            // Distance of the point from the camera
            double distance = distanceTo(point, camera.getTranslation());

            // Transform screen coordinates relative to the image depth
            Point coordML = getCoordsML(screenPoint, scaleX, scaleY);

            // Calculate depth with neural network
            float predictedDistance = getPredictionFromPoint(prediction, coordML);

            ransacIntelObjects[numValidPoint++] = new RansacIntelObject(1/distance, predictedDistance);     // 1/distance se è disparità
        }

        // Need at least one element
        if(numValidPoint < 1){
            return;
        }

        // RANSAC algorithm
        // Based on the pseudo-code of https://it.wikipedia.org/wiki/RANSAC
        double bestScaleFactor = Double.NaN;
        double bestShiftFactor = Double.NaN;
        Integer[] bestConsensusSet = new Integer[0];
        int numBestConsensusSet = 0;

        // Init

        // Number of point in maybeInlier
        int numMaybeInlier = (int) (numValidPoint * percentagePossibleInlier);

        // Number of point in consensusSet: range [numMaybeInlier, numVisiblePoint]
        int numConsensusSet;

        for(int i=0; i<numberOfIterations; i++){
            Integer[] possibleInlier = new Integer[numMaybeInlier];

            // Sort possibleInlier: it will help to find the point not included faster
            // Use set to avoid repetion
            SortedSet<Integer> possibiliInlierSet = new TreeSet<>();
            while(possibiliInlierSet.size() < numMaybeInlier){
                possibiliInlierSet.add(rnd.nextInt(numValidPoint));
            }
            // possibleInlier: Points choosen randomly from the dataset
            possibleInlier = possibiliInlierSet.toArray(possibleInlier);

            // Possible scaleFactor and shiftFactor estimated from possibleInlier
            double possibleScaleFactor;
            double possibleShiftFactor;

            // Transform the array back to RansacIntelObject[]
            RansacIntelObject[] tmp = new RansacIntelObject[possibleInlier.length];

            for (int j = 0; j < tmp.length; j++) {
                tmp[j] = ransacIntelObjects[possibleInlier[j]];
            }

            // Calculate possibileScaleFactor and possibileShiftFactor
            double[] factors = calibrateIntel(tmp);

            // Calibration failed, continue with a new dataset
            if(factors == null){
                continue;
            }

            // Get possible values
            possibleScaleFactor = factors[0];
            possibleShiftFactor = factors[1];

            // Skip if value is not valid
            if(possibleScaleFactor <= 0) {
                continue;
            }

            // Get min and max error to normalize later
            double minError = Double.MAX_VALUE;
            double maxError = Double.MIN_VALUE;

            for(int k = 0, j = 0; k < numValidPoint; k++) {
                if(j < numMaybeInlier && k == possibleInlier[j]) {
                    // If already in array, skip
                    j++;
                    continue;
                }

                // New point, calculate quadratic error
                RansacIntelObject foreignObj = ransacIntelObjects[k];

                double thisError = Math.pow((foreignObj.prediction * possibleScaleFactor + possibleShiftFactor) - foreignObj.groundTruth, 2);

                if(thisError > maxError) {
                    maxError = thisError;
                }
                if(thisError < minError) {
                    minError = thisError;
                }
            }

            // ConsensusSet: possible consensus set
            Integer[] consensusSet = Arrays.copyOf(possibleInlier, numValidPoint);
            numConsensusSet = numMaybeInlier;

            // Add points not included in possibleInlier with error minor than threshold
            for(int k = 0, j = 0; k < numValidPoint; k++) {
                if(j < numMaybeInlier && k == possibleInlier[j]) {
                    // If already in possibleInlier, skip
                    j++;
                    continue;
                }

                // New point
                RansacIntelObject foreignObj = ransacIntelObjects[k];

                double thisError = Math.pow((foreignObj.prediction * possibleScaleFactor + possibleShiftFactor) - foreignObj.groundTruth, 2);
                double normalizedError = (thisError - minError) / (maxError - minError);

                //
                if(normalizedError < normalizedThreshold) {
                    consensusSet[numConsensusSet++] = k;
                }
            }

            // The new model is better if the consensus set has more points
            if(numConsensusSet >= numBestConsensusSet) {
                bestScaleFactor = possibleScaleFactor;
                bestShiftFactor = possibleShiftFactor;
                bestConsensusSet = Arrays.copyOfRange(consensusSet, 0, numConsensusSet);
                numBestConsensusSet = numConsensusSet;
            }

            // Save the new value
            if(Double.isFinite(bestScaleFactor)) {
                scaleFactor = bestScaleFactor;
                shiftFactor = bestShiftFactor;
            }
        }

    }




    public double[] calibrateIntel(RansacIntelObject[] objects){
        // Least-square algorithm
        // Matrix A = [[a_00, a_01], [a_10, a_11]]
        // a_01 = a_10
        // Array B = [b_0, b_1]

        double a00 = 0.0;
        double a01 = 0.0;
        double a11 = objects.length;

        // The factor a10 is equal to a01

        double b0 = 0.0;
        double b1 = 0.0;

        for(int i = 0; i<objects.length;i++){
            a00 += objects[i].prediction * objects[i].prediction;
            a01 += objects[i].prediction;

            b0 += objects[i].prediction * objects[i].groundTruth;
            b1 += objects[i].groundTruth;
        }

        // Calculate determinant of A
        double detA = a00 * a11 - a01 * a01;

        // The determinant must be strictly positive
        if(detA > 0.0){

            double scaleFactor = (a11*b0-a01*b1)/detA;
            double shiftFactor = (-a01*b0+a00*b1)/detA;

            // Save the new value
            if (Double.isFinite(scaleFactor) && Double.isFinite(shiftFactor)) {
                return new double[]{scaleFactor, shiftFactor};
            } else {
                return null;
            }
        }

        // Determinant not valid
        return null;
    }


    // ---------------- Utils method ------------------

    // Distance from camera to point
    private double distanceTo(float[] point, float[] camera) {
        return Math.sqrt(Math.pow(point[0] - camera[0], 2) + Math.pow(point[1] - camera[1], 2) + Math.pow(point[2] - camera[2], 2));
    }

    // Transform the screen coordinates into the depth image coordinate
    private Point getCoordsML(Point screenPoint, double scaleX, double scaleY) {
        //Hp: the app is always used in landscape
        return new Point((int) (screenPoint.x*scaleX), (int) (screenPoint.y*scaleY));
    }

    // Get xy screen coordinates from world 3D coordinate
    private Point worldToScreen(float[] point, int width, int height, float[] projectionMatrix, float[] viewMatrix) {
        float[] modelViewProjection = new float[16];
        Matrix.multiplyMM(modelViewProjection, 0, projectionMatrix, 0, viewMatrix, 0);

        float[] projectionCoord = new float[4];
        float[] worldCoord = {point[0], point[1], point[2], 1};
        Matrix.multiplyMV(projectionCoord, 0, modelViewProjection, 0, worldCoord, 0);

        projectionCoord[0] = projectionCoord[0] / projectionCoord[3];
        projectionCoord[1] = projectionCoord[1] / projectionCoord[3];

        int screenX = (int) (projectionCoord[0] + 1 / 2. * width);
        int screenY = (int) (1 - projectionCoord[1] / 2. * height);

        return new Point(screenX, screenY);
    }

    // Get predicted distance from prediction of a specific point
    private float getPredictionFromPoint(FloatBuffer prediction, Point point){
        int position = (width * point.y) + point.x;
        prediction.rewind();

        return prediction.get(position);    // qualche controllo in caso di errore???
    }
}

