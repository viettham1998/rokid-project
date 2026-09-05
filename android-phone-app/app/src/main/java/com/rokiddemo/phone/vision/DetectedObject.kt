package com.rokiddemo.phone.vision

/**
 * One detected object. Bounding box is normalized (0..1) relative to the frame,
 * so the glasses can position an overlay regardless of resolution.
 */
data class DetectedObject(
    val label: String,
    val score: Float,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)
