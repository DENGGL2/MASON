package com.denggl2.mason.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.denggl2.mason.ui.chat.ChatScreen
import com.denggl2.mason.ui.conversation.ConversationListScreen
import com.denggl2.mason.ui.settings.PermissionScreen
import com.denggl2.mason.ui.settings.SettingsScreen

object Routes {
    const val CONVERSATION_LIST = "conversation_list"
    const val CHAT = "chat/{conversationId}"
    const val SETTINGS = "settings"
    const val PERMISSION = "permission"

    fun chat(conversationId: Long) = "chat/$conversationId"
}

@Composable
fun MasonNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.CONVERSATION_LIST,
        enterTransition = { fadeIn(animationSpec = tween(300)) },
        exitTransition = { fadeOut(animationSpec = tween(300)) },
    ) {
        composable(Routes.CONVERSATION_LIST) {
            ConversationListScreen(
                onConversationClick = { id ->
                    navController.navigate(Routes.chat(id))
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
            )
        }

        composable(
            route = Routes.CHAT,
            arguments = listOf(
                navArgument("conversationId") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) {
            ChatScreen(
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToPermission = {
                    navController.navigate(Routes.PERMISSION)
                },
            )
        }

        composable(Routes.PERMISSION) {
            PermissionScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
