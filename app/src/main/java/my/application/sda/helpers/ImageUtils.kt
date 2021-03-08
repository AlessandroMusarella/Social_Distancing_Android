//https://stackoverflow.com/questions/52053864/how-to-get-bitmap-from-session-update-in-arcore-android-studio
//https://stackoverflow.com/questions/40090681/android-camera2-api-yuv-420-888-to-jpeg

package my.application.sda.helpers

import android.graphics.*
import android.media.Image
import java.io.ByteArrayOutputStream

fun yuvToBitmap(image: Image): Bitmap{

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