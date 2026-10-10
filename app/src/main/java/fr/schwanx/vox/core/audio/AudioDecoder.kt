package fr.schwanx.vox.core.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioDecoder @Inject constructor() {

    companion object {
        private const val TAG = "AudioDecoder"
        private const val TIMEOUT_US = 5000L
    }

    /**
     * Decodes any audio file supported by the device to 16 kHz mono float PCM
     * and streams it to [outFile] (raw little-endian float32, no header), so
     * that long recordings never have to fit in memory.
     *
     * @param audioPath a file path or a content:// / file:// URI string
     * @return the number of 16 kHz samples written, or 0 on failure
     */
    suspend fun decodeToPcmFile(
        context: Context,
        audioPath: String,
        outFile: File,
        onProgress: ((Float) -> Unit)? = null
    ): Long = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            if (audioPath.startsWith("content://") || audioPath.startsWith("file://")) {
                extractor.setDataSource(context, Uri.parse(audioPath), null)
            } else {
                extractor.setDataSource(audioPath)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set data source for extractor", e)
            extractor.release()
            return@withContext 0L
        }

        var trackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val trackFormat = extractor.getTrackFormat(i)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME)
            if (mime != null && mime.startsWith("audio/")) {
                trackIndex = i
                format = trackFormat
                break
            }
        }

        if (trackIndex < 0 || format == null) {
            Log.e(TAG, "No audio track found in media source")
            extractor.release()
            return@withContext 0L
        }

        extractor.selectTrack(trackIndex)

        val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L

        val codec = try {
            MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create decoder for mime $mime", e)
            extractor.release()
            return@withContext 0L
        }

        // The decoder's output format is authoritative (e.g. HE-AAC doubles the
        // sample rate), it may differ from what the extractor reports.
        var sampleRate = format.getIntegerOr(MediaFormat.KEY_SAMPLE_RATE, 44100)
        var channelCount = format.getIntegerOr(MediaFormat.KEY_CHANNEL_COUNT, 1)
        var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

        val writer = PcmFloatWriter(outFile)
        var resampler = LinearResampler(sampleRate, AudioUtils.SAMPLE_RATE_16K, writer)

        val startedAt = SystemClock.elapsedRealtime()
        val bufferInfo = MediaCodec.BufferInfo()
        var isExtractorEof = false
        var isCodecEof = false
        var success = false

        try {
            while (!isCodecEof) {
                ensureActive()

                // Feed every input buffer the codec has free, without blocking.
                var fedInput = false
                while (!isExtractorEof) {
                    val inputBufferIndex = codec.dequeueInputBuffer(0)
                    if (inputBufferIndex < 0) break
                    val inputBuffer = codec.getInputBuffer(inputBufferIndex) ?: break
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputBufferIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isExtractorEof = true
                    } else {
                        val sampleTime = extractor.sampleTime
                        codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, sampleTime, 0)
                        extractor.advance()

                        if (durationUs > 0 && onProgress != null) {
                            onProgress((sampleTime.toFloat() / durationUs.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                    fedInput = true
                }

                // Drain every decoded buffer available. Only wait when nothing
                // could be fed nor drained, i.e. when the codec is busy.
                var outputTimeoutUs = if (fedInput) 0L else TIMEOUT_US
                while (true) {
                    val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, outputTimeoutUs)
                    if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) break
                    outputTimeoutUs = 0L
                    if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val outFormat = codec.outputFormat
                        channelCount = outFormat.getIntegerOr(MediaFormat.KEY_CHANNEL_COUNT, channelCount)
                        pcmEncoding = outFormat.getIntegerOr(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                        val newRate = outFormat.getIntegerOr(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                        if (newRate != sampleRate) {
                            sampleRate = newRate
                            resampler = LinearResampler(sampleRate, AudioUtils.SAMPLE_RATE_16K, writer)
                        }
                        Log.i(TAG, "Decoder output format: $sampleRate Hz, $channelCount ch, encoding $pcmEncoding")
                    } else if (outputBufferIndex >= 0) {
                        val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            outputBuffer.order(ByteOrder.LITTLE_ENDIAN)

                            if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                                val floatBuffer = outputBuffer.asFloatBuffer()
                                val numFrames = floatBuffer.remaining() / channelCount
                                for (i in 0 until numFrames) {
                                    var sum = 0f
                                    for (c in 0 until channelCount) sum += floatBuffer.get()
                                    resampler.push(sum / channelCount)
                                }
                            } else {
                                val shortBuffer = outputBuffer.asShortBuffer()
                                val numFrames = shortBuffer.remaining() / channelCount
                                for (i in 0 until numFrames) {
                                    var sum = 0f
                                    for (c in 0 until channelCount) sum += shortBuffer.get() / 32768.0f
                                    resampler.push(sum / channelCount)
                                }
                            }
                        }

                        codec.releaseOutputBuffer(outputBufferIndex, false)

                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            isCodecEof = true
                            break
                        }
                    }
                }
            }
            success = true
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(TAG, "Audio decoding failed", e)
        } finally {
            writer.close()
            try { codec.stop() } catch (ignored: Exception) {}
            codec.release()
            extractor.release()
        }

        onProgress?.invoke(1.0f)
        Log.i(TAG, "Decoded $audioPath ($sampleRate Hz, $channelCount ch) -> ${writer.samplesWritten} samples at 16000 Hz in ${SystemClock.elapsedRealtime() - startedAt} ms")
        return@withContext if (success) writer.samplesWritten else 0L
    }

    private fun MediaFormat.getIntegerOr(key: String, default: Int): Int =
        if (containsKey(key)) getInteger(key) else default

    /** Buffered writer of little-endian float32 samples. */
    private class PcmFloatWriter(file: File) {
        private val out = BufferedOutputStream(FileOutputStream(file), 1 shl 16)
        private val buffer = ByteBuffer.allocate(4 * 4096).order(ByteOrder.LITTLE_ENDIAN)
        var samplesWritten = 0L
            private set

        fun write(sample: Float) {
            buffer.putFloat(sample)
            samplesWritten++
            if (!buffer.hasRemaining()) flushBuffer()
        }

        private fun flushBuffer() {
            out.write(buffer.array(), 0, buffer.position())
            buffer.clear()
        }

        fun close() {
            flushBuffer()
            out.close()
        }
    }

    /**
     * Streaming linear-interpolation resampler: same algorithm as
     * [AudioUtils.resampleLinear], one input sample at a time.
     */
    private class LinearResampler(
        inputRate: Int,
        outputRate: Int,
        private val sink: PcmFloatWriter
    ) {
        private val passthrough = inputRate == outputRate
        private val step = inputRate.toDouble() / outputRate.toDouble()
        private var nextOutputPos = 0.0 // position of the next output sample, in input samples
        private var inputIndex = -1L
        private var previous = 0f

        fun push(sample: Float) {
            if (passthrough) {
                sink.write(sample)
                return
            }
            inputIndex++
            if (inputIndex > 0) {
                // Emit every output sample lying between the previous input sample and this one.
                while (nextOutputPos <= inputIndex) {
                    val fraction = (nextOutputPos - (inputIndex - 1)).toFloat()
                    sink.write(previous + (sample - previous) * fraction)
                    nextOutputPos += step
                }
            }
            previous = sample
        }
    }
}
