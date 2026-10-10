package fr.schwanx.vox.core.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioDecoder @Inject constructor() {

    companion object {
        private const val TAG = "AudioDecoder"
        private const val TIMEOUT_US = 5000L
    }

    suspend fun decodeAudioToPcmFloat(
        context: Context,
        audioSource: Any,
        onProgress: ((Float) -> Unit)? = null
    ): FloatArray = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            when (audioSource) {
                is Uri -> extractor.setDataSource(context, audioSource, null)
                is String -> extractor.setDataSource(audioSource)
                is File -> extractor.setDataSource(audioSource.absolutePath)
                else -> throw IllegalArgumentException("Unsupported audio source type: ${audioSource.javaClass.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set data source for extractor", e)
            return@withContext floatArrayOf()
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
            return@withContext floatArrayOf()
        }

        extractor.selectTrack(trackIndex)

        val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
        val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
        val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 1L

        val codec = try {
            MediaCodec.createDecoderByType(mime)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create decoder for mime $mime", e)
            extractor.release()
            return@withContext floatArrayOf()
        }

        codec.configure(format, null, null, 0)
        codec.start()

        val rawPcmSamples = ArrayList<Float>()
        val bufferInfo = MediaCodec.BufferInfo()
        var isExtractorEof = false
        var isCodecEof = false

        while (!isCodecEof) {
            if (!isExtractorEof) {
                val inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                    if (inputBuffer != null) {
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputBufferIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isExtractorEof = true
                        } else {
                            val sampleTime = extractor.sampleTime
                            codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, sampleTime, 0)
                            extractor.advance()

                            if (durationUs > 0 && onProgress != null) {
                                val progress = (sampleTime.toFloat() / durationUs.toFloat()).coerceIn(0f, 1f)
                                onProgress(progress * 0.9f)
                            }
                        }
                    }
                }
            }

            val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            if (outputBufferIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                if (outputBuffer != null && bufferInfo.size > 0) {
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                    outputBuffer.order(ByteOrder.LITTLE_ENDIAN)

                    val pcmEncoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    } else {
                        android.media.AudioFormat.ENCODING_PCM_16BIT
                    }

                    when (pcmEncoding) {
                        android.media.AudioFormat.ENCODING_PCM_FLOAT -> {
                            val floatBuffer = outputBuffer.asFloatBuffer()
                            val numSamples = floatBuffer.remaining() / channelCount
                            for (i in 0 until numSamples) {
                                var monoSample = 0f
                                for (c in 0 until channelCount) {
                                    monoSample += floatBuffer.get()
                                }
                                rawPcmSamples.add(monoSample / channelCount)
                            }
                        }
                        else -> { // Default ENCODING_PCM_16BIT
                            val shortBuffer = outputBuffer.asShortBuffer()
                            val numSamples = shortBuffer.remaining() / channelCount
                            for (i in 0 until numSamples) {
                                var monoSampleSum = 0f
                                for (c in 0 until channelCount) {
                                    monoSampleSum += shortBuffer.get().toFloat() / 32768.0f
                                }
                                rawPcmSamples.add(monoSampleSum / channelCount)
                            }
                        }
                    }
                }

                codec.releaseOutputBuffer(outputBufferIndex, false)

                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    isCodecEof = true
                }
            }
        }

        codec.stop()
        codec.release()
        extractor.release()

        val rawArray = rawPcmSamples.toFloatArray()
        onProgress?.invoke(0.95f)

        val resampledArray = AudioUtils.resampleLinear(rawArray, sampleRate, AudioUtils.SAMPLE_RATE_16K)
        onProgress?.invoke(1.0f)

        Log.i(TAG, "Decoded ${rawArray.size} samples at $sampleRate Hz -> ${resampledArray.size} samples at 16000 Hz")
        return@withContext resampledArray
    }
}
