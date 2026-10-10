package fr.schwanx.vox.domain.model

data class UserSettings(
    val selectedModelId: String = "ggml-base-q5_1.bin",
    val defaultLanguage: String = "auto",
    val numThreads: Int = 0, // 0 = Auto
    val keepAudioFiles: Boolean = true,
    val darkThemeMode: String = "system", // "system", "light", "dark"
    val dynamicColorEnabled: Boolean = true,
    val isOnboardingCompleted: Boolean = false
)
