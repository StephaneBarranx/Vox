package fr.schwanx.vox.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object OnboardingRoute

@Serializable
object HomeRoute

@Serializable
object RecordRoute

@Serializable
data class TranscribeRoute(
    val audioPath: String,
    val title: String? = null
)

@Serializable
data class ResultRoute(
    val transcriptionId: Long
)

@Serializable
object SettingsRoute
