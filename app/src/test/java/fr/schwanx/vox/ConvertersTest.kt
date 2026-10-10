package fr.schwanx.vox

import fr.schwanx.vox.data.local.db.Converters
import fr.schwanx.vox.domain.model.Segment
import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun testSegmentsJsonConversion() {
        val original = listOf(
            Segment(0L, 1500L, "Bonjour et bienvenue"),
            Segment(1500L, 3000L, "Ceci est Vox")
        )

        val json = converters.fromSegmentsList(original)
        val decoded = converters.toSegmentsList(json)

        assertEquals(original.size, decoded.size)
        assertEquals(original[0].text, decoded[0].text)
        assertEquals(original[1].startMs, decoded[1].startMs)
    }

    @Test
    fun testEmptySegmentsJson() {
        val decoded = converters.toSegmentsList("")
        assertEquals(0, decoded.size)
    }
}
