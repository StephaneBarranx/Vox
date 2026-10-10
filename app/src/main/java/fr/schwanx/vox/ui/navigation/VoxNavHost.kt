package fr.schwanx.vox.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import fr.schwanx.vox.ui.home.HomeScreen
import fr.schwanx.vox.ui.home.HomeViewModel
import fr.schwanx.vox.ui.onboarding.OnboardingScreen
import fr.schwanx.vox.ui.onboarding.OnboardingViewModel
import fr.schwanx.vox.ui.record.RecordScreen
import fr.schwanx.vox.ui.record.RecordViewModel
import fr.schwanx.vox.ui.result.ResultScreen
import fr.schwanx.vox.ui.result.ResultViewModel
import fr.schwanx.vox.ui.settings.SettingsScreen
import fr.schwanx.vox.ui.settings.SettingsViewModel
import fr.schwanx.vox.ui.transcribe.TranscribeScreen
import fr.schwanx.vox.ui.transcribe.TranscribeViewModel

@Composable
fun VoxNavHost(
    navController: NavHostController,
    isOnboardingCompleted: Boolean,
    modifier: Modifier = Modifier
) {
    val startRoute = if (isOnboardingCompleted) HomeRoute else OnboardingRoute

    NavHost(
        navController = navController,
        startDestination = startRoute,
        enterTransition = { fadeIn(animationSpec = tween(300)) },
        exitTransition = { fadeOut(animationSpec = tween(300)) },
        popEnterTransition = { fadeIn(animationSpec = tween(300)) },
        popExitTransition = { fadeOut(animationSpec = tween(300)) },
        modifier = modifier
    ) {
        composable<OnboardingRoute> {
            val viewModel: OnboardingViewModel = hiltViewModel()
            OnboardingScreen(
                viewModel = viewModel,
                onNavigateToHome = {
                    navController.navigate(HomeRoute) {
                        popUpTo(OnboardingRoute) { inclusive = true }
                    }
                }
            )
        }

        composable<HomeRoute> {
            val viewModel: HomeViewModel = hiltViewModel()
            HomeScreen(
                viewModel = viewModel,
                onNavigateToRecord = {
                    navController.navigate(RecordRoute)
                },
                onNavigateToTranscribe = { audioPath ->
                    navController.navigate(TranscribeRoute(audioPath = audioPath))
                },
                onNavigateToResult = { transcriptionId ->
                    navController.navigate(ResultRoute(transcriptionId = transcriptionId))
                },
                onNavigateToSettings = {
                    navController.navigate(SettingsRoute)
                }
            )
        }

        composable<RecordRoute> {
            val viewModel: RecordViewModel = hiltViewModel()
            RecordScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToTranscribe = { audioPath ->
                    navController.navigate(TranscribeRoute(audioPath = audioPath)) {
                        popUpTo(RecordRoute) { inclusive = true }
                    }
                }
            )
        }

        composable<TranscribeRoute> { backStackEntry ->
            val route: TranscribeRoute = backStackEntry.toRoute()
            val viewModel: TranscribeViewModel = hiltViewModel()
            TranscribeScreen(
                audioPath = route.audioPath,
                title = route.title,
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToResult = { transcriptionId ->
                    navController.navigate(ResultRoute(transcriptionId = transcriptionId)) {
                        popUpTo(HomeRoute) { inclusive = false }
                    }
                }
            )
        }

        composable<ResultRoute> { backStackEntry ->
            val route: ResultRoute = backStackEntry.toRoute()
            val viewModel: ResultViewModel = hiltViewModel()
            ResultScreen(
                transcriptionId = route.transcriptionId,
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable<SettingsRoute> {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
