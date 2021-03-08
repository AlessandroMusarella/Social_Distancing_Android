package my.application.sda.helpers;

import android.graphics.Point;
import android.opengl.Matrix;

public class CoordsUtils {

    // Get x,y,z world coordinates from x,y screen coordinate and distance from camera
    public static float[] screenToWorld(int screenX, int screenY, float distance, int screenWidth, int screenHeight, float[] inverseViewProjectionMatrix){

        // Transform coordinates from [0,width][0,height] to [-1,1][-1,1]
        float normalizedX = (2f * screenX) / screenWidth - 1f;
        float normalizedY = 1f - (2f * screenY) / screenHeight;

        float[] inPoint = {normalizedX, normalizedY, distance, 1f};

        float[] position = new float[4];
        Matrix.multiplyMM(position, 0, inverseViewProjectionMatrix, 0, inPoint, 0);

        float[] xyz = {position[0] / position[3], position[1] / position[3], position[2] / position[3]};

        return xyz;
    }

    // Get xy screen coordinates from world 3D coordinate
    public static android.graphics.Point worldToScreen(float[] point, int width, int height, float[] projectionMatrix, float[] viewMatrix) {
        float[] modelViewProjection = new float[16];
        Matrix.multiplyMM(modelViewProjection, 0, projectionMatrix, 0, viewMatrix, 0);

        float[] projectionCoord = new float[4];
        float[] worldCoord = {point[0], point[1], point[2], 1};
        Matrix.multiplyMV(projectionCoord, 0, modelViewProjection, 0, worldCoord, 0);

        projectionCoord[0] = projectionCoord[0] / projectionCoord[3];
        projectionCoord[1] = projectionCoord[1] / projectionCoord[3];

        int screenX = (int) ((projectionCoord[0] + 1) / 2. * width);
        int screenY = (int) ((1 - projectionCoord[1]) / 2. * height);

        return new Point(screenX, screenY);
    }

    // Transform the screen coordinates into the depth image coordinate
    public static android.graphics.Point getCoordsML(android.graphics.Point screenPoint, double scaleX, double scaleY) {
        //Hp: the app is always used in landscape
        return new android.graphics.Point((int) (screenPoint.x*scaleX), (int) (screenPoint.y*scaleY));
    }


}
