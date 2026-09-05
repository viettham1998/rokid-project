package com.rokiddemo.glasses.net

import android.util.Base64
import org.json.JSONObject

/**
 * Mirror of the phone-side MessageProtocol. The two apps live in separate Gradle
 * projects, so we duplicate this tiny file rather than share a module (fine for a
 * prototype). Keep the type strings identical on both sides!
 */
object MessageProtocol {

    const val HELLO = "HELLO"
    const val HELLO_ACK = "HELLO_ACK"

    // Reserved for later phases:
    const val IMAGE_FRAME = "IMAGE_FRAME"
    const val DETECTION_RESULT = "DETECTION_RESULT"
    const val SPEECH_RESULT = "SPEECH_RESULT"
    const val ASSISTANT_RESPONSE = "ASSISTANT_RESPONSE"
    const val AUDIO_START = "AUDIO_START"
    const val AUDIO_CHUNK = "AUDIO_CHUNK"
    const val AUDIO_END = "AUDIO_END"
    const val STATUS = "STATUS"

    fun typeOf(text: String): String? = try {
        JSONObject(text).optString("type").ifEmpty { null }
    } catch (e: Exception) {
        null
    }

    fun parse(text: String): JSONObject? = try {
        JSONObject(text)
    } catch (e: Exception) {
        null
    }

    fun hello(): String = JSONObject().apply {
        put("type", HELLO)
        put("device", "ROKID")
        put("timestamp", System.currentTimeMillis())
    }.toString()

    fun speechResult(text: String): String = JSONObject().apply {
        put("type", SPEECH_RESULT)
        put("text", text)
        put("timestamp", System.currentTimeMillis())
    }.toString()

    // Audio is sent in small chunks (each well under the WebSocket frame-size limit)
    // between an AUDIO_START and AUDIO_END so the phone can reassemble the utterance.
    fun audioStart(sampleRate: Int = 16000): String = JSONObject().apply {
        put("type", AUDIO_START)
        put("sampleRate", sampleRate)
    }.toString()

    fun audioChunk(pcmChunk: ByteArray): String = JSONObject().apply {
        put("type", AUDIO_CHUNK)
        put("pcm", Base64.encodeToString(pcmChunk, Base64.NO_WRAP))
    }.toString()

    fun audioEnd(): String = JSONObject().apply {
        put("type", AUDIO_END)
    }.toString()

    const val CHUNK_BYTES = 16000  // ~0.5s of 16kHz PCM16 -> ~21KB base64, safe

    /** Extract the "text" field from an ASSISTANT_RESPONSE (or any message). */
    fun textOf(json: String): String = parse(json)?.optString("text").orEmpty()
}
