package com.rokiddemo.phone.core

import android.content.Context
import com.rokiddemo.phone.assistant.CommandProcessor
import com.rokiddemo.phone.speech.SpeechToText
import com.rokiddemo.phone.vision.DetectedObject
import java.util.concurrent.Executors

/**
 * Transcribes a spoken utterance (PCM from the glasses) on a worker thread, turns it
 * into a reply using the latest detections, and hands back (question, answer).
 */
class AudioProcessor(
    context: Context,
    private val latestDetections: () -> List<DetectedObject>,
    private val onResponse: (question: String, answer: String) -> Unit
) {
    private val stt = SpeechToText(context)
    private val exec = Executors.newSingleThreadExecutor()

    fun submit(pcm: ByteArray) {
        exec.execute {
            val question = stt.transcribe(pcm)
            val answer =
                if (question.isBlank()) "Sorry, I didn't catch that."
                else CommandProcessor.respond(question, latestDetections())
            onResponse(question, answer)
        }
    }

    fun close() {
        exec.shutdown()
        stt.close()
    }
}
