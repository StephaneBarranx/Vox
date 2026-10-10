package fr.schwanx.vox.core.audio

import java.io.RandomAccessFile
import kotlin.math.sqrt

object AudioUtils {

    const val SAMPLE_RATE_16K = 16000
    private const val CHANNEL_MONO = 1
    private const val BITS_PER_SAMPLE_16 = 16

    /**
     * Writes a standard 44-byte RIFF WAV header for 16-bit mono PCM.
     */
    fun writeWavHeader(
        file: RandomAccessFile,
        totalAudioLen: Long,
        totalDataLen: Long,
        longSampleRate: Long = SAMPLE_RATE_16K.toLong(),
        channels: Int = CHANNEL_MONO,
        byteRate: Long = (SAMPLE_RATE_16K * CHANNEL_MONO * BITS_PER_SAMPLE_16 / 8).toLong()
    ) {
        val header = ByteArray(44)

        // RIFF/WAVE header
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xffL).toByte()
        header[5] = (totalDataLen shr 8 and 0xffL).toByte()
        header[6] = (totalDataLen shr 16 and 0xffL).toByte()
        header[7] = (totalDataLen shr 24 and 0xffL).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        // 'fmt ' chunk
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // 4 bytes: size of 'fmt ' chunk
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // Format = 1 (PCM)
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (longSampleRate and 0xffL).toByte()
        header[25] = (longSampleRate shr 8 and 0xffL).toByte()
        header[26] = (longSampleRate shr 16 and 0xffL).toByte()
        header[27] = (longSampleRate shr 24 and 0xffL).toByte()
        header[28] = (byteRate and 0xffL).toByte()
        header[29] = (byteRate shr 8 and 0xffL).toByte()
        header[30] = (byteRate shr 16 and 0xffL).toByte()
        header[31] = (byteRate shr 24 and 0xffL).toByte()
        header[32] = (channels * BITS_PER_SAMPLE_16 / 8).toByte() // block align
        header[33] = 0
        header[34] = BITS_PER_SAMPLE_16.toByte() // bits per sample
        header[35] = 0

        // 'data' chunk
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xffL).toByte()
        header[41] = (totalAudioLen shr 8 and 0xffL).toByte()
        header[42] = (totalAudioLen shr 16 and 0xffL).toByte()
        header[43] = (totalAudioLen shr 24 and 0xffL).toByte()

        file.seek(0)
        file.write(header, 0, 44)
    }

    /**
     * Calculates the normalized Root Mean Square (RMS) amplitude (0.0f to 1.0f)
     * from a 16-bit PCM byte array buffer.
     */
    fun calculateRmsAmplitude(buffer: ByteArray, bytesRead: Int): Float {
        if (bytesRead <= 0) return 0f
        var sum = 0.0
        val numSamples = bytesRead / 2
        for (i in 0 until numSamples) {
            val sample = (buffer[i * 2].toInt() and 0xFF) or (buffer[i * 2 + 1].toInt() shl 8)
            val shortSample = sample.toShort()
            sum += shortSample * shortSample
        }
        val rms = sqrt(sum / numSamples)
        // Normalize against max short value 32767.0
        return (rms / 32767.0).toFloat().coerceIn(0f, 1f)
    }

    /**
     * Resamples float PCM array from inputSampleRate to targetSampleRate (16000Hz).
     */
    fun resampleLinear(samples: FloatArray, inputSampleRate: Int, targetSampleRate: Int = SAMPLE_RATE_16K): FloatArray {
        if (inputSampleRate == targetSampleRate || samples.isEmpty()) {
            return samples
        }
        val ratio = inputSampleRate.toDouble() / targetSampleRate.toDouble()
        val outputSize = (samples.size / ratio).toInt()
        val output = FloatArray(outputSize)

        for (i in 0 until outputSize) {
            val index = i * ratio
            val index1 = index.toInt()
            val index2 = (index1 + 1).coerceAtMost(samples.size - 1)
            val fraction = (index - index1).toFloat()
            output[i] = samples[index1] * (1f - fraction) + samples[index2] * fraction
        }
        return output
    }
}
