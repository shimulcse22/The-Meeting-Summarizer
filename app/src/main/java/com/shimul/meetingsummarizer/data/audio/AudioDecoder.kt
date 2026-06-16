package com.shimul.meetingsummarizer.data.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext

/**
 * Decodes the audio track of an audio/video file to 16 kHz mono PCM (float),
 * emitting it in fixed-length windows so memory stays bounded for long files.
 */
object AudioDecoder {

    private const val TARGET_RATE = 16_000
    private const val TIMEOUT_US = 10_000L

    /**
     * @param onProgress 0f..1f based on decode position.
     * @param onChunk called per ~[chunkSeconds] window of 16 kHz mono float samples.
     */
    suspend fun decodeToMonoChunks(
        context: Context,
        uri: Uri,
        chunkSeconds: Int = 30,
        onProgress: (Float) -> Unit,
        onChunk: suspend (FloatArray) -> Unit
    ) = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
        } catch (e: Exception) {
            extractor.release()
            throw IllegalArgumentException("Couldn't open this file — it may be corrupt or unreadable.")
        }

        val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
            extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                ?.startsWith("audio/") == true
        }
        if (trackIndex == null) {
            extractor.release()
            throw IllegalArgumentException("No audio track found in the file.")
        }
        extractor.selectTrack(trackIndex)

        val inputFormat = extractor.getTrackFormat(trackIndex)
        val mime = inputFormat.getString(MediaFormat.KEY_MIME)!!
        val durationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION))
            inputFormat.getLong(MediaFormat.KEY_DURATION) else 0L

        var channels = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
            inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1
        var sampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE))
            inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) else TARGET_RATE
        var resampler = LinearResampler(sampleRate, TARGET_RATE)

        val codec = try {
            MediaCodec.createDecoderByType(mime).apply {
                configure(inputFormat, null, null, 0)
                start()
            }
        } catch (e: Exception) {
            extractor.release()
            throw IllegalArgumentException(
                "This file's audio format isn't supported on this device. " +
                    "Try a different file (e.g. MP3, M4A, or MP4)."
            )
        }

        val windowSize = TARGET_RATE * chunkSeconds
        val window = FloatArray(windowSize)
        var filled = 0

        val info = MediaCodec.BufferInfo()
        var sawInputEOS = false
        var sawOutputEOS = false

        try {
            while (!sawOutputEOS) {
                coroutineContext.ensureActive()

                if (!sawInputEOS) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val inBuf = codec.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(inBuf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(
                                inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            sawInputEOS = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                if (outIndex >= 0) {
                    if (info.size > 0) {
                        val outBuf = codec.getOutputBuffer(outIndex)!!
                        outBuf.position(info.offset)
                        outBuf.limit(info.offset + info.size)
                        val shorts = ShortArray(info.size / 2)
                        outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)

                        val frames = if (channels > 0) shorts.size / channels else shorts.size
                        val mono = FloatArray(frames)
                        var s = 0
                        for (f in 0 until frames) {
                            var sum = 0
                            for (c in 0 until channels) sum += shorts[s++]
                            mono[f] = (sum.toFloat() / channels) / 32768f
                        }

                        for (v in resampler.process(mono)) {
                            window[filled++] = v
                            if (filled == windowSize) {
                                onChunk(window.copyOf(filled))
                                filled = 0
                            }
                        }

                        if (durationUs > 0) {
                            onProgress((info.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f))
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEOS = true
                } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val of = codec.outputFormat
                    if (of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    if (of.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        resampler = LinearResampler(sampleRate, TARGET_RATE)
                    }
                }
            }

            if (filled > 0) onChunk(window.copyOf(filled))
            onProgress(1f)
        } finally {
            runCatching { codec.stop() }
            codec.release()
            extractor.release()
        }
    }
}

/** Streaming linear resampler from [inRate] to [outRate], keeps state across buffers. */
private class LinearResampler(inRate: Int, outRate: Int) {
    private val ratio = inRate.toDouble() / outRate
    private var inIndex = 0L
    private var outIndex = 0L
    private var prev = 0f
    private var hasPrev = false

    fun process(mono: FloatArray): FloatArray {
        if (mono.isEmpty()) return FloatArray(0)
        val out = ArrayList<Float>((mono.size / ratio).toInt() + 2)
        for (x in mono) {
            if (!hasPrev) {
                prev = x
                hasPrev = true
                inIndex = 1
                while (outIndex * ratio <= 0.0) {
                    out.add(prev)
                    outIndex++
                }
                continue
            }
            val curIdx = inIndex
            while (true) {
                val p = outIndex * ratio
                if (p > curIdx) break
                val frac = (p - (curIdx - 1)).toFloat().coerceIn(0f, 1f)
                out.add(prev * (1 - frac) + x * frac)
                outIndex++
            }
            prev = x
            inIndex++
        }
        return out.toFloatArray()
    }
}
