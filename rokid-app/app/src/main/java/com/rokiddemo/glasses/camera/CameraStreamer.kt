package com.rokiddemo.glasses.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.rokiddemo.glasses.ClientBus
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

/**
 * Captures frames from the glasses camera with CameraX and hands them to `onJpeg`
 * as compressed JPEG bytes, throttled to ~minIntervalMs. No preview surface is
 * used — we only need the pixels to ship to the phone.
 *
 * If the glasses block raw Camera2 access (Rokid may gate the camera behind their
 * SDK), start() will log a clear error; that's the signal to switch this one class
 * to the Rokid CXR camera API without touching networking/UI.
 */
class CameraStreamer(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val minIntervalMs: Long = 120,   // cap ~8 FPS; real rate self-paces to the network
    private val maxDim: Int = 480,
    private val jpegQuality: Int = 60,
    private val onJpeg: (ByteArray) -> Unit
) {
    private val exec = Executors.newSingleThreadExecutor()
    private var provider: ProcessCameraProvider? = null

    @Volatile private var lastSent = 0L
    @Volatile var running = false
        private set

    fun start() {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val p = future.get()
                provider = p
                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                analysis.setAnalyzer(exec) { proxy -> handle(proxy) }

                p.unbindAll()
                // Prefer back camera; fall back to any available camera.
                val selector = try {
                    if (p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA))
                        CameraSelector.DEFAULT_BACK_CAMERA
                    else CameraSelector.DEFAULT_FRONT_CAMERA
                } catch (e: Exception) {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                p.bindToLifecycle(lifecycleOwner, selector, analysis)
                running = true
                ClientBus.log("Camera started")
            } catch (e: Exception) {
                running = false
                ClientBus.log("Camera error: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        running = false
        try { provider?.unbindAll() } catch (_: Exception) {}
    }

    private fun handle(proxy: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            if (now - lastSent < minIntervalMs) return
            val jpeg = toJpeg(proxy) ?: return
            lastSent = now
            onJpeg(jpeg)
        } catch (e: Exception) {
            ClientBus.log("Frame error: ${e.message}")
        } finally {
            proxy.close()
        }
    }

    // Reused across frames to avoid per-frame allocation churn (GC pauses = jank).
    private val jpegBuffer = ByteArrayOutputStream(64 * 1024)

    /**
     * RGBA_8888 ImageProxy -> upright, downscaled JPEG in as few allocations as
     * possible: one bitmap from the buffer, then a SINGLE createBitmap that crops
     * padding + rotates + scales in one pass. Both bitmaps are recycled.
     */
    private fun toJpeg(proxy: ImageProxy): ByteArray? {
        val plane = proxy.planes[0]
        val pixelStride = plane.pixelStride
        val paddedWidth = plane.rowStride / pixelStride
        val realW = proxy.width
        val realH = proxy.height

        val src = Bitmap.createBitmap(paddedWidth, realH, Bitmap.Config.ARGB_8888)
        src.copyPixelsFromBuffer(plane.buffer)

        val rot = proxy.imageInfo.rotationDegrees
        val scale = (maxDim.toFloat() / maxOf(realW, realH)).coerceAtMost(1f)
        val m = Matrix()
        if (rot != 0) m.postRotate(rot.toFloat())
        if (scale < 1f) m.postScale(scale, scale)

        // Crop the row padding (source rect = realW x realH) + rotate + scale at once.
        val out = Bitmap.createBitmap(src, 0, 0, realW, realH, m, true)
        if (out !== src) src.recycle()

        jpegBuffer.reset()
        out.compress(Bitmap.CompressFormat.JPEG, jpegQuality, jpegBuffer)
        out.recycle()
        return jpegBuffer.toByteArray()
    }
}
