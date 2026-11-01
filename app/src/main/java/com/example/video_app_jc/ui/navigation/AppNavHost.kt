package com.example.video_app_jc.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.video_app_jc.ui.shell.ShellScreen
import com.example.video_app_jc.ui.splash.SplashScreen

@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = SplashRoute,
        modifier = modifier
    ) {
        composable<SplashRoute> {
            SplashScreen(
                onLoginDone = {
                    // Vào Shell và xoá Splash khỏi back stack (back không quay lại Splash)
                    navController.navigate(ShellRoute) {
                        popUpTo(SplashRoute) { inclusive = true }
                    }
                }
            )
        }

        composable<ShellRoute> {
            ShellScreen()
        }
    }
}
