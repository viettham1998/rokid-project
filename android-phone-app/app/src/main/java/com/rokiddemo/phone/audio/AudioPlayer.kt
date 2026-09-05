package com.rokiddemo.phone.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.rokiddemo.phone.ServerBus

/**
 * Plays back a 16 kHz mono PCM16 buffer (the audio the glasses recorded) through the
 * phone speaker. Used to verify what the glasses mic captured — no AI involved.
 */
object AudioPlayer {

    private const val SAMPLE_RATE = 16000

    @Volatile private var track: AudioTrack? = null

    fun play(pcm: ByteArray) {
        if (pcm.isEmpty()) return
        stop()
        Thread {
            try {
                val minBuf = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
                )
                val t = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(minBuf, 8192))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                track = t
                t.play()
                t.write(pcm, 0, pcm.size)   // blocks as the buffer drains
                t.stop()                    // play out the tail, then stop
                t.release()
            } catch (e: Exception) {
                ServerBus.log("Playback error: ${e.message}")
            } finally {
                track = null
            }
        }.apply { isDaemon = true; start() }
    }

    fun stop() {
        try { track?.pause(); track?.flush(); track?.release() } catch (_: Exception) {}
        track = null
    }
}
