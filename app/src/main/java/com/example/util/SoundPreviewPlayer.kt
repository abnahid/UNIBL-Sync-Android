package com.example.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

object SoundPreviewPlayer {
    private var currentJob: Job? = null
    private var currentTrack: AudioTrack? = null

    val AVAILABLE_TONES = listOf(
        "Gentle Chime",
        "Digital Beep",
        "Soft Bell",
        "Classic Alarm"
    )

    fun playTone(toneName: String, scope: CoroutineScope) {
        stopTone()

        currentJob = scope.launch(Dispatchers.IO) {
            try {
                val sampleRate = 44100
                val audioData = generateToneSamples(toneName, sampleRate)
                val bufferSize = audioData.size * 2

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                currentTrack = track
                track.write(audioData, 0, audioData.size)
                track.play()
            } catch (_: Exception) { }
        }
    }

    fun stopTone() {
        currentJob?.cancel()
        currentJob = null
        try {
            currentTrack?.stop()
            currentTrack?.release()
        } catch (_: Exception) { }
        currentTrack = null
    }

    private fun generateToneSamples(toneName: String, sampleRate: Int): ShortArray {
        return when (toneName) {
            "Digital Beep" -> generateDigitalBeep(sampleRate)
            "Soft Bell" -> generateSoftBell(sampleRate)
            "Classic Alarm" -> generateClassicAlarm(sampleRate)
            else -> generateGentleChime(sampleRate) // default: Gentle Chime
        }
    }

    // Two warm harmonic chimes (E5 ~ 659Hz, B5 ~ 987Hz)
    private fun generateGentleChime(sampleRate: Int): ShortArray {
        val durationSec = 1.2
        val totalSamples = (durationSec * sampleRate).toInt()
        val samples = ShortArray(totalSamples)

        val note1Samples = (0.5 * sampleRate).toInt()
        val note2Samples = totalSamples - note1Samples

        for (i in 0 until note1Samples) {
            val t = i.toDouble() / sampleRate
            val envelope = exp(-3.5 * t)
            val freq1 = 659.25
            val freq2 = 1318.5
            val value = (sin(2 * PI * freq1 * t) * 0.7 + sin(2 * PI * freq2 * t) * 0.3) * envelope
            samples[i] = (value * 28000).toInt().coerceIn(-32767, 32767).toShort()
        }

        for (i in 0 until note2Samples) {
            val t = i.toDouble() / sampleRate
            val envelope = exp(-3.0 * t)
            val freq1 = 987.77
            val freq2 = 1975.53
            val value = (sin(2 * PI * freq1 * t) * 0.7 + sin(2 * PI * freq2 * t) * 0.3) * envelope
            samples[note1Samples + i] = (value * 28000).toInt().coerceIn(-32767, 32767).toShort()
        }

        return samples
    }

    // Crisp high tech double beep
    private fun generateDigitalBeep(sampleRate: Int): ShortArray {
        val durationSec = 0.8
        val totalSamples = (durationSec * sampleRate).toInt()
        val samples = ShortArray(totalSamples)
        val beepLen = (0.15 * sampleRate).toInt()
        val pauseLen = (0.1 * sampleRate).toInt()

        for (i in 0 until beepLen) {
            val t = i.toDouble() / sampleRate
            val value = sin(2 * PI * 1800.0 * t)
            samples[i] = (value * 24000).toInt().toShort()
        }

        val startBeep2 = beepLen + pauseLen
        for (i in 0 until beepLen) {
            val t = i.toDouble() / sampleRate
            val value = sin(2 * PI * 2200.0 * t)
            if (startBeep2 + i < totalSamples) {
                samples[startBeep2 + i] = (value * 24000).toInt().toShort()
            }
        }

        return samples
    }

    // Resonant soft bell chime with rich overtones
    private fun generateSoftBell(sampleRate: Int): ShortArray {
        val durationSec = 1.5
        val totalSamples = (durationSec * sampleRate).toInt()
        val samples = ShortArray(totalSamples)

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = exp(-2.2 * t)
            val v1 = sin(2 * PI * 523.25 * t) // C5
            val v2 = sin(2 * PI * 1046.50 * t) * 0.5
            val v3 = sin(2 * PI * 1567.98 * t) * 0.25
            val value = (v1 + v2 + v3) * envelope * 0.6
            samples[i] = (value * 28000).toInt().coerceIn(-32767, 32767).toShort()
        }

        return samples
    }

    // Classic pulsating alarm (wake-up alert pattern)
    private fun generateClassicAlarm(sampleRate: Int): ShortArray {
        val durationSec = 1.4
        val totalSamples = (durationSec * sampleRate).toInt()
        val samples = ShortArray(totalSamples)
        val pulseLen = (0.2 * sampleRate).toInt()
        val gap = (0.1 * sampleRate).toInt()

        for (pulse in 0 until 4) {
            val startIdx = pulse * (pulseLen + gap)
            for (i in 0 until pulseLen) {
                val idx = startIdx + i
                if (idx < totalSamples) {
                    val t = i.toDouble() / sampleRate
                    val freq = if (pulse % 2 == 0) 800.0 else 1000.0
                    val value = sin(2 * PI * freq * t)
                    samples[idx] = (value * 26000).toInt().toShort()
                }
            }
        }

        return samples
    }
}
