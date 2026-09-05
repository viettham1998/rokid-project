package com.rokiddemo.phone.net

import com.rokiddemo.phone.vision.DetectedObject
import org.json.JSONArray
import org.json.JSONObject

/**
 * Shared, hand-rolled JSON envelope for all glasses <-> phone messages.
 *
 * Every message is a JSON object with at least a "type" field and a "timestamp".
 * We keep this deliberately tiny for the prototype; new message types are just
 * new constants + tiny builder helpers.
 */
object MessageProtocol {

    // ---- message types ---------------------------------------------------
    const val HELLO = "HELLO"
    const val HELLO_ACK = "HELLO_ACK"

    // Reserved for later phases (kept here so both apps agree on the strings):
    const val IMAGE_FRAME = "IMAGE_FRAME"
    const val DETECTION_RESULT = "DETECTION_RESULT"
    const val SPEECH_RESULT = "SPEECH_RESULT"
    const val ASSISTANT_RESPONSE = "ASSISTANT_RESPONSE"
    const val STATUS = "STATUS"

    /** Returns the "type" of a raw text message, or null if it isn't valid JSON. */
    fun typeOf(text: String): String? = try {
        JSONObject(text).optString("type").ifEmpty { null }
    } catch (e: Exception) {
        null
    }

    /** Parse a raw message into a JSONObject, or null on error. */
    fun parse(text: String): JSONObject? = try {
        JSONObject(text)
    } catch (e: Exception) {
        null
    }

    fun helloAck(): String = JSONObject().apply {
        put("type", HELLO_ACK)
        put("device", "PHONE")
        put("timestamp", System.currentTimeMillis())
    }.toString()

    fun detectionResult(objects: List<DetectedObject>): String {
        val arr = JSONArray()
        for (o in objects) {
            arr.put(JSONObject().apply {
                put("label", o.label)
                put("confidence", o.score)
                put("bbox", JSONObject().apply {
                    put("x", o.x); put("y", o.y)
                    put("width", o.width); put("height", o.height)
                })
            })
        }
        return JSONObject().apply {
            put("type", DETECTION_RESULT)
            put("timestamp", System.currentTimeMillis())
            put("objects", arr)
        }.toString()
    }
}
