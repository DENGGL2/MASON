package com.denggl2.mason.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.denggl2.mason.ui.chat.ChatScreen

object Routes {
    const val CHAT = "chat"
}

@Composable
fun MasonNavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.CHAT) {
        composable(Routes.CHAT) {
            ChatScreen()
        }
    }
}
