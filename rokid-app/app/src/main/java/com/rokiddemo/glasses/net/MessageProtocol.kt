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
    const val AUDIO = "AUDIO"
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

    /** Send a whole utterance as base64 PCM16 (16 kHz mono) for STT on the phone. */
    fun audio(pcm: ByteArray, sampleRate: Int = 16000): String = JSONObject().apply {
        put("type", AUDIO)
        put("sampleRate", sampleRate)
        put("pcm", Base64.encodeToString(pcm, Base64.NO_WRAP))
        put("timestamp", System.currentTimeMillis())
    }.toString()

    /** Extract the "text" field from an ASSISTANT_RESPONSE (or any message). */
    fun textOf(json: String): String = parse(json)?.optString("text").orEmpty()
}
