package com.rokiddemo.phone.core

import android.content.Context
import android.graphics.BitmapFactory
import com.rokiddemo.phone.ServerBus
import com.rokiddemo.phone.net.MessageProtocol
import com.rokiddemo.phone.vision.DetectedObject
import com.rokiddemo.phone.vision.ObjectDetectorHelper
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs object detection on incoming JPEG frames on a single worker thread.
 *
 * Latest-frame-only: if a detection is already in progress, new frames are dropped
 * (not queued) so detection always works on the freshest frame and never builds a
 * backlog. Produces a DETECTION_RESULT JSON string via [onResultJson] (sent to the
 * glasses) and pushes the list to [ServerBus] for the phone's own UI.
 */
class FrameProcessor(
    context: Context,
    private val onResultJson: (String) -> Unit
) {
    private val detector = ObjectDetectorHelper(context)
    private val exec = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)

    /** Most recent detections — used by the assistant to answer "what do you see?". */
    @Volatile var lastDetections: List<DetectedObject> = emptyList()
        private set

    fun submit(jpeg: ByteArray) {
        if (!busy.compareAndSet(false, true)) return  // drop while busy
        exec.execute {
            try {
                val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
                if (bmp != null) {
                    val objects = detector.detect(bmp)
                    bmp.recycle()
                    lastDetections = objects
                    onResultJson(MessageProtocol.detectionResult(objects))
                    ServerBus.detections(objects)
                }
            } catch (e: Exception) {
                ServerBus.log("Detect error: ${e.message}")
            } finally {
                busy.set(false)
            }
        }
    }

    fun close() {
        exec.shutdown()
        detector.close()
    }
}
