package fr.schwanx.vox.core.util

import fr.schwanx.vox.domain.model.Segment

object SrtGenerator {

    fun generateSrt(segments: List<Segment>): String {
        val sb = StringBuilder()
        segments.forEachIndexed { index, segment ->
            sb.append("${index + 1}\n")
            sb.append("${formatSrtTime(segment.startMs)} --> ${formatSrtTime(segment.endMs)}\n")
            sb.append("${segment.text}\n\n")
        }
        return sb.toString()
    }

    private fun formatSrtTime(ms: Long): String {
        val hours = ms / 3600000
        val minutes = (ms % 3600000) / 60000
        val seconds = (ms % 60000) / 1000
        val millis = ms % 1000
        return String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }
}
