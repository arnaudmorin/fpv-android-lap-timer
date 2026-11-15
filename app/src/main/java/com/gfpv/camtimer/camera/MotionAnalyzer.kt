package com.gfpv.camtimer.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

/** Receives the luma plane of an upright analysis frame. */
fun interface LumaFrameListener {
    fun onLumaFrame(plane: ByteArray, rowStride: Int, pixelStride: Int, width: Int, height: Int, timestampNanos: Long)
}

/**
 * Copies the Y plane of each frame and hands it over with the sensor timestamp, which is more
 * accurate for lap timing than the time the callback happens to run.
 */
class MotionAnalyzer(private val listener: LumaFrameListener) : ImageAnalysis.Analyzer {
    private var buffer = ByteArray(0)

    override fun analyze(image: ImageProxy) {
        image.use {
            val plane = it.planes[0]
            val source = plane.buffer
            source.rewind()
            val size = source.remaining()
            if (buffer.size != size) buffer = ByteArray(size)
            source.get(buffer, 0, size)
            listener.onLumaFrame(buffer, plane.rowStride, plane.pixelStride, it.width, it.height, it.imageInfo.timestamp)
        }
    }
}
