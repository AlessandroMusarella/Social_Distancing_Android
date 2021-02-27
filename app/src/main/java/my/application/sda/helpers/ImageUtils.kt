//https://stackoverflow.com/questions/52053864/how-to-get-bitmap-from-session-update-in-arcore-android-studio
//https://stackoverflow.com/questions/40090681/android-camera2-api-yuv-420-888-to-jpeg

package my.application.sda.helpers

import android.graphics.*
import android.media.Image
import java.io.ByteArrayOutputStream

fun yuvToBitmap(image: Image): Bitmap{

    /*
    //The camera image received is in YUV YCbCr Format. Get buffers for each of the planes and use them to create a new bytearray defined by the size of all three buffers combined
    val cameraPlaneY = image.planes[0].buffer
    val cameraPlaneU = image.planes[1].buffer
    val cameraPlaneV = image.planes[2].buffer

    //Use the buffers to create a new byteArray that
    val compositeByteArray = ByteArray(cameraPlaneY.capacity() + cameraPlaneU.capacity() + cameraPlaneV.capacity())

    cameraPlaneY.get(compositeByteArray, 0, cameraPlaneY.capacity())
    cameraPlaneU.get(compositeByteArray, cameraPlaneY.capacity(), cameraPlaneU.capacity())
    cameraPlaneV.get(compositeByteArray, cameraPlaneY.capacity() + cameraPlaneU.capacity(), cameraPlaneV.capacity())
    */



    val yBuffer = image.planes[0].buffer
    val vuBuffer = image.planes[2].buffer

    val ySize: Int = yBuffer.capacity()
    val vuSize: Int = vuBuffer.capacity()

    val nv21 = ByteArray(ySize + vuSize)

    yBuffer.get(nv21, 0, ySize)
    vuBuffer.get(nv21, ySize, vuSize)






    val baOutputStream = ByteArrayOutputStream()
    val yuvImage: YuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
    yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 75, baOutputStream)
    val byteForBitmap = baOutputStream.toByteArray()
    val bitmap = BitmapFactory.decodeByteArray(byteForBitmap, 0, byteForBitmap.size)

    return bitmap
}