package fr.schwanx.vox

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import fr.schwanx.vox.data.local.preferences.SettingsRepository
import fr.schwanx.vox.ui.navigation.TranscribeRoute
import fr.schwanx.vox.ui.navigation.VoxNavHost
import fr.schwanx.vox.ui.theme.VoxTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        val sharedAudioUri = extractSharedAudioUri(intent)

        setContent {
            val userSettings by settingsRepository.userSettingsFlow.collectAsState(initial = null)

            if (userSettings == null) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                val settings = userSettings!!
                val navController = rememberNavController()

                VoxTheme(
                    darkThemeMode = settings.darkThemeMode,
                    dynamicColor = settings.dynamicColorEnabled
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        VoxNavHost(
                            navController = navController,
                            isOnboardingCompleted = settings.isOnboardingCompleted
                        )

                        sharedAudioUri?.let { uri ->
                            navController.navigate(TranscribeRoute(audioPath = uri.toString()))
                        }
                    }
                }
            }
        }
    }

    private fun extractSharedAudioUri(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                if (intent.type?.startsWith("audio/") == true) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                    }
                } else null
            }
            Intent.ACTION_VIEW -> {
                intent.data
            }
            else -> null
        }
    }
}
