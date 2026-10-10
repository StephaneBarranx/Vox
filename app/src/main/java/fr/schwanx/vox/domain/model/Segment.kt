package fr.schwanx.vox.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Segment(
    val startMs: Long,
    val endMs: Long,
    val text: String
)
