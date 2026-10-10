package fr.schwanx.vox

import fr.schwanx.vox.core.audio.AudioUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

class AudioUtilsTest {

    @Test
    fun testWavHeaderSize() {
        val tempFile = File.createTempFile("test_header", ".wav")
        tempFile.deleteOnExit()

        val raf = RandomAccessFile(tempFile, "rw")
        AudioUtils.writeWavHeader(raf, 32000, 32036)
        raf.close()

        assertEquals(44L, tempFile.length())
    }

    @Test
    fun testResamplingSampleCountRatio() {
        val inputSamples = FloatArray(44100) { 0.5f }
        val resampled = AudioUtils.resampleLinear(inputSamples, 44100, 16000)

        // Expected ratio: 44100 / 16000 = 2.75625
        // Expected resampled size: ~16000
        assertEquals(16000, resampled.size)
    }

    @Test
    fun testRmsAmplitudeCalculation() {
        val silenceBuffer = ByteArray(1024) { 0 }
        val rmsSilence = AudioUtils.calculateRmsAmplitude(silenceBuffer, 1024)
        assertEquals(0f, rmsSilence, 0.001f)

        // Buffer with max short amplitude
        val loudBuffer = ByteArray(1024)
        for (i in 0 until 512) {
            loudBuffer[i * 2] = 0xFF.toByte()
            loudBuffer[i * 2 + 1] = 0x7F.toByte() // 32767
        }
        val rmsLoud = AudioUtils.calculateRmsAmplitude(loudBuffer, 1024)
        assertTrue(rmsLoud > 0.99f)
    }
}
