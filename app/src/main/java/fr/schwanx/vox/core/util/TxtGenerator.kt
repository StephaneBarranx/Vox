package fr.schwanx.vox.core.util

import fr.schwanx.vox.domain.model.Transcription

object TxtGenerator {

    fun generateTxt(transcription: Transcription): String {
        return """
=== ${transcription.title} ===
Modèle: ${transcription.modelName} | Langue: ${transcription.language}
--------------------------------------------------
${transcription.text}
        """.trimIndent()
    }
}
