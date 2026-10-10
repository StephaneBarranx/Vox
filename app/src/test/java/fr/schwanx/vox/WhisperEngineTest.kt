package fr.schwanx.vox

import fr.schwanx.vox.core.whisper.WhisperEngine
import org.junit.Assert.assertFalse
import org.junit.Test

class WhisperEngineTest {

    @Test
    fun testUninitializedEngineReturnsFalse() {
        val engine = WhisperEngine()
        assertFalse(engine.isInitialized())
    }
}
