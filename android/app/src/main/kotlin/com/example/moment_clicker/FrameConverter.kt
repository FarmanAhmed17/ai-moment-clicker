package com.example.moment_clicker

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import java.io.ByteArrayOutputStream

/** A single camera frame handed over from the Flutter camera image stream. */
data class CameraFrame(
    val y: ByteArray,
    val u: ByteArray,
    val v: ByteArray,
    val width: Int,
    val height: Int,
    val yRowStride: Int,
    val uvRowStride: Int,
    val uvPixelStride: Int,
    val rotationDegrees: Int,
    val mirrored: Boolean,
)

/** Converts YUV420 camera frames into upright RGB bitmaps that MediaPipe can consume. */
object FrameConverter {

    private const val MAX_DIMENSION = 480

    fun toBitmap(frame: CameraFrame): Bitmap? {
        val nv21 = toNv21(frame)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, frame.width, frame.height, null)
        val jpeg = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, frame.width, frame.height), 85, jpeg)
        val bytes = jpeg.toByteArray()

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(frame.width, frame.height)
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null

        if (frame.rotationDegrees == 0 && !frame.mirrored) return decoded

        val matrix = Matrix().apply {
            postRotate(frame.rotationDegrees.toFloat())
            if (frame.mirrored) postScale(-1f, 1f)
        }
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (rotated != decoded) decoded.recycle()
        return rotated
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (maxOf(width, height) / sample > MAX_DIMENSION) sample *= 2
        return sample
    }

    private fun toNv21(frame: CameraFrame): ByteArray {
        val width = frame.width
        val height = frame.height
        val out = ByteArray(width * height * 3 / 2)

        var outIndex = 0
        for (row in 0 until height) {
            val rowStart = row * frame.yRowStride
            frame.y.copyInto(out, outIndex, rowStart, rowStart + width)
            outIndex += width
        }

        val chromaHeight = height / 2
        val chromaWidth = width / 2
        for (row in 0 until chromaHeight) {
            val rowStart = row * frame.uvRowStride
            for (col in 0 until chromaWidth) {
                val index = rowStart + col * frame.uvPixelStride
                if (index >= frame.v.size || index >= frame.u.size) continue
                out[outIndex++] = frame.v[index]
                out[outIndex++] = frame.u[index]
            }
        }
        return out
    }
}
