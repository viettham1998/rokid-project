package com.rokiddemo.phone.speech

import android.content.Context
import com.rokiddemo.phone.ServerBus
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.io.IOException

/**
 * Offline speech-to-text with Vosk. The glasses' YodaOS has no SpeechRecognizer, so
 * the glasses stream PCM here and we transcribe on the phone (no internet needed).
 *
 * The ~40 MB model is unpacked from assets to internal storage on first launch
 * (async); until it's ready, transcribe() returns "".
 */
class SpeechToText(context: Context) {

    @Volatile private var model: Model? = null

    val isReady: Boolean get() = model != null

    init {
        StorageService.unpack(
            context,
            "vosk-model-small-en-us-0.15",   // asset folder
            "vosk-model",                     // target under filesDir
            { m ->
                model = m
                ServerBus.log("Vosk model ready")
            },
            { e: IOException ->
                ServerBus.log("Vosk model error: ${e.message}")
            }
        )
    }

    /** Transcribe a 16 kHz mono PCM16 buffer to text (empty if not ready/failed). */
    fun transcribe(pcm: ByteArray): String {
        val m = model ?: return ""
        val rec = Recognizer(m, 16000.0f)
        return try {
            rec.acceptWaveForm(pcm, pcm.size)
            JSONObject(rec.finalResult).optString("text")
        } catch (e: Exception) {
            ServerBus.log("Transcribe error: ${e.message}")
            ""
        } finally {
            rec.close()
        }
    }

    fun close() {
        model?.close()
        model = null
    }
}
