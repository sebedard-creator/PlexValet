package com.seb.sdpr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.seb.sdpr.ui.filter.FilterScreen
import com.seb.sdpr.ui.filter.FilterState
import com.seb.sdpr.ui.filter.FilterViewModel
import com.seb.sdpr.ui.playlist.PlaylistScreen
import com.seb.sdpr.ui.result.ResultScreen
import com.seb.sdpr.ui.settings.SettingsScreen
import com.seb.sdpr.ui.theme.PlexValetTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlexValetTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "filter") {
                    composable("settings") { settingsEntry ->
                        val filterEntry = remember(settingsEntry) { navController.getBackStackEntry("filter") }
                        val filterViewModel: FilterViewModel = hiltViewModel(filterEntry)
                        val filterUi by filterViewModel.uiState.collectAsStateWithLifecycle()
                        SettingsScreen(
                            canRefresh = filterUi.isConfigured,
                            isSyncing = filterUi.isSyncing,
                            onFullRefresh = {
                                filterViewModel.sync(forceFull = true)
                                navController.popBackStack()
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("filter") {
                        FilterScreen(
                            onNavigateToSettings = { navController.navigate("settings") },
                            onNavigateToResult = { ratingKey, filterState ->
                                navController.currentBackStackEntry?.savedStateHandle?.set("filterState", filterState)
                                navController.navigate("result/$ratingKey")
                            },
                            onNavigateToPlaylist = { filterState ->
                                navController.currentBackStackEntry?.savedStateHandle?.set("filterState", filterState)
                                navController.navigate("playlist")
                            },
                            onOpenMovie = { ratingKey ->
                                navController.navigate("playlistResult/$ratingKey")
                            }
                        )
                    }
                    composable("playlist") {
                        val filterState = navController.previousBackStackEntry?.savedStateHandle?.get<FilterState>("filterState")
                        PlaylistScreen(
                            filterState = filterState,
                            onBack = { navController.popBackStack() },
                            onOpenMovie = { ratingKey -> navController.navigate("playlistResult/$ratingKey") }
                        )
                    }
                    composable(
                        "result/{ratingKey}",
                        arguments = listOf(navArgument("ratingKey") { type = NavType.StringType })
                    ) {
                        val filterState = navController.previousBackStackEntry?.savedStateHandle?.get<FilterState>("filterState")
                        ResultScreen(
                            filterState = filterState,
                            onBack = { navController.popBackStack() },
                            onReroll = {},
                            showRerollButton = true
                        )
                    }
                    composable(
                        "playlistResult/{ratingKey}",
                        arguments = listOf(navArgument("ratingKey") { type = NavType.StringType })
                    ) {
                        ResultScreen(
                            filterState = null,
                            onBack = { navController.popBackStack() },
                            onReroll = {},
                            showRerollButton = false
                        )
                    }
                }
            }
        }
    }
}
