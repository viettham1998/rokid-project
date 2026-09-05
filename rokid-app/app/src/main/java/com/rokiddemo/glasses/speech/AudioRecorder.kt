package com.rokiddemo.glasses.speech

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.rokiddemo.glasses.ClientBus
import java.io.ByteArrayOutputStream

/**
 * Records a whole utterance from the glasses mic as 16 kHz mono PCM16 (the format
 * Vosk expects on the phone). tap Talk = start(), tap again = stop() which returns
 * the recorded PCM bytes. No streaming — one blob per utterance.
 *
 * If the Rokid mic is gated behind their SDK, read() may return silence; we log the
 * captured size + a rough level so the issue is visible.
 */
class AudioRecorder {

    private val sampleRate = 16000
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var recording = false
    private val out = ByteArrayOutputStream()

    val isRecording: Boolean get() = recording

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) { ClientBus.log("Audio: bad min buffer ($minBuf)"); return false }
        return try {
            val rec = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, sampleRate) // ~0.5s buffer headroom
            )
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                ClientBus.log("Audio: AudioRecord not initialized")
                rec.release(); return false
            }
            record = rec
            out.reset()
            recording = true
            rec.startRecording()
            thread = Thread {
                val buf = ByteArray(3200)
                while (recording) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n > 0) synchronized(out) { out.write(buf, 0, n) }
                }
            }.apply { isDaemon = true; start() }
            true
        } catch (e: Exception) {
            ClientBus.log("Audio start error: ${e.message}")
            false
        }
    }

    /** Stops recording and returns the captured PCM16 bytes. */
    fun stop(): ByteArray {
        recording = false
        try { thread?.join(600) } catch (_: Exception) {}
        try { record?.stop() } catch (_: Exception) {}
        try { record?.release() } catch (_: Exception) {}
        record = null
        val bytes = synchronized(out) { out.toByteArray() }
        ClientBus.log("Audio captured: ${bytes.size} bytes (${bytes.size / 32000.0} s)")
        return bytes
    }

    companion object {
        const val SAMPLE_RATE = 16000
    }
}
