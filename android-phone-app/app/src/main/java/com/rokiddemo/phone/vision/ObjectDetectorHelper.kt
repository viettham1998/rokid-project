package com.rokiddemo.phone.vision

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector

/**
 * Thin wrapper around MediaPipe's ObjectDetector (EfficientDet-Lite0, bundled in
 * assets). Synchronous IMAGE mode — call detect() from a worker thread.
 */
class ObjectDetectorHelper(
    context: Context,
    scoreThreshold: Float = 0.4f,
    maxResults: Int = 5
) {
    private val detector: ObjectDetector

    init {
        val base = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET)
            .build()
        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.IMAGE)
            .setScoreThreshold(scoreThreshold)
            .setMaxResults(maxResults)
            .build()
        detector = ObjectDetector.createFromOptions(context, options)
    }

    fun detect(bitmap: Bitmap): List<DetectedObject> {
        val image = BitmapImageBuilder(bitmap).build()
        val result = detector.detect(image)
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        return result.detections().mapNotNull { d ->
            val cat = d.categories().maxByOrNull { it.score() } ?: return@mapNotNull null
            val box = d.boundingBox()
            DetectedObject(
                label = cat.categoryName(),
                score = cat.score(),
                x = box.left / w,
                y = box.top / h,
                width = box.width() / w,
                height = box.height() / h
            )
        }
    }

    fun close() = detector.close()

    companion object {
        const val MODEL_ASSET = "efficientdet_lite0.tflite"
    }
}
