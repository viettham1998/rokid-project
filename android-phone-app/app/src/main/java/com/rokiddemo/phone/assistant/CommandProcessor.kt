package com.rokiddemo.phone.assistant

import com.rokiddemo.phone.vision.DetectedObject

/**
 * Turns a spoken question + the current detections into a reply. Deliberately
 * simple (keyword matching, no LLM) for a low-latency offline demo. This is also
 * the Phase 5 "vision + voice" glue: vision questions are answered from the latest
 * detections.
 */
object CommandProcessor {

    private val visionKeywords = listOf(
        // English
        "see", "look", "looking", "front of me", "what is", "what's", "what am",
        "detect", "object", "there",
        // Vietnamese
        "thấy", "nhìn", "gì", "vật", "trước mặt", "cái gì"
    )

    fun respond(question: String, detections: List<DetectedObject>): String {
        val q = question.lowercase()
        val asksVision = visionKeywords.any { q.contains(it) }

        if (asksVision) {
            if (detections.isEmpty()) {
                return "I don't see anything yet — tap the glasses to capture a photo first."
            }
            val labels = detections.map { it.label }.distinct()
            return "I can see " + humanList(labels) + "."
        }

        // Fallback: acknowledge and hint.
        return "You said: \"$question\". Ask me what I can see."
    }

    private fun humanList(items: List<String>): String = when (items.size) {
        0 -> "nothing"
        1 -> "a ${items[0]}"
        else -> items.dropLast(1).joinToString(", ") { "a $it" } + " and a ${items.last()}"
    }
}
