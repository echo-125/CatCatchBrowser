package top.he2000.catcatchbrowser.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import top.he2000.catcatchbrowser.ui.navigation.BottomNavigationBar
import top.he2000.catcatchbrowser.ui.navigation.Screen
import top.he2000.catcatchbrowser.viewmodel.MainViewModel

@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            BottomNavigationBar(navController)
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Windows.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Windows.route) {
                WindowsScreen(viewModel)
            }
            composable(Screen.Downloading.route) {
                DownloadingScreen(viewModel)
            }
            composable(Screen.Downloaded.route) {
                DownloadedScreen(viewModel)
            }
        }
    }
}
