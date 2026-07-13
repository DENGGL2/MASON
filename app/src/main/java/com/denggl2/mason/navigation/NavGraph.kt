package com.denggl2.mason.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.denggl2.mason.data.IslandVendorMode
import com.denggl2.mason.data.NotificationDeliveryMode
import com.denggl2.mason.data.ThemeMode
import com.denggl2.mason.data.UiPreferences
import com.denggl2.mason.ui.chat.ChatScreen
import com.denggl2.mason.ui.collection.CollectionKind
import com.denggl2.mason.ui.collection.CollectionListScreen
import com.denggl2.mason.ui.conversation.ConversationListScreen
import com.denggl2.mason.ui.settings.PermissionScreen
import com.denggl2.mason.ui.settings.SettingsScreen

object Routes {
    const val CHAT_NEW = "chat_new"
    const val CONVERSATION_LIST = "conversation_list"
    const val CHAT = "chat/{conversationId}"
    const val SETTINGS = "settings"
    const val PERMISSION = "permission"
    const val COLLECTION = "collection/{kind}"

    fun chat(conversationId: Long) = "chat/$conversationId"
    fun collection(kind: CollectionKind) = "collection/${kind.routeName}"
}

@Composable
fun MasonNavGraph(
    uiPreferences: UiPreferences,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAccentColorChange: (Long) -> Unit,
    onNotificationIslandEnabledChange: (Boolean) -> Unit,
    onNotificationDeliveryModeChange: (NotificationDeliveryMode) -> Unit,
    onNotifyOnTaskCompleteChange: (Boolean) -> Unit,
    onNotifyOnPaymentSuccessChange: (Boolean) -> Unit,
    onIslandVendorModeChange: (IslandVendorMode) -> Unit,
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.CHAT_NEW,
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(360, easing = FastOutSlowInEasing),
            ) + fadeIn(tween(220)) + scaleIn(
                initialScale = 0.985f,
                animationSpec = tween(360, easing = FastOutSlowInEasing),
            )
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(320, easing = FastOutSlowInEasing),
            ) + fadeOut(tween(180)) + scaleOut(
                targetScale = 0.985f,
                animationSpec = tween(320, easing = FastOutSlowInEasing),
            )
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(340, easing = FastOutSlowInEasing),
            ) + fadeIn(tween(200)) + scaleIn(
                initialScale = 0.99f,
                animationSpec = tween(340, easing = FastOutSlowInEasing),
            )
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(300, easing = FastOutSlowInEasing),
            ) + fadeOut(tween(160)) + scaleOut(
                targetScale = 0.99f,
                animationSpec = tween(300, easing = FastOutSlowInEasing),
            )
        },
    ) {
        composable(Routes.CHAT_NEW) {
            ChatScreen(
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onConversationSelected = { id ->
                    navController.navigate(Routes.chat(id)) {
                        launchSingleTop = true
                    }
                },
                onNewChat = {
                    navController.navigate(Routes.CHAT_NEW) {
                        launchSingleTop = true
                    }
                },
                onOpenArtifacts = { navController.navigate(Routes.collection(CollectionKind.ARTIFACTS)) },
                onOpenSkills = { navController.navigate(Routes.collection(CollectionKind.SKILLS)) },
                onOpenAutomations = { navController.navigate(Routes.collection(CollectionKind.AUTOMATIONS)) },
            )
        }

        composable(Routes.CONVERSATION_LIST) {
            ConversationListScreen(
                onConversationClick = { id ->
                    navController.navigate(Routes.chat(id))
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
                onBack = { navController.popBackStack() },
                onNewChat = {
                    navController.navigate(Routes.CHAT_NEW) {
                        launchSingleTop = true
                    }
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
                onConversationSelected = { id ->
                    navController.navigate(Routes.chat(id)) {
                        launchSingleTop = true
                    }
                },
                onNewChat = {
                    navController.navigate(Routes.CHAT_NEW) {
                        launchSingleTop = true
                    }
                },
                onBack = { navController.popBackStack() },
                onOpenArtifacts = { navController.navigate(Routes.collection(CollectionKind.ARTIFACTS)) },
                onOpenSkills = { navController.navigate(Routes.collection(CollectionKind.SKILLS)) },
                onOpenAutomations = { navController.navigate(Routes.collection(CollectionKind.AUTOMATIONS)) },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToPermission = {
                    navController.navigate(Routes.PERMISSION)
                },
                uiPreferences = uiPreferences,
                onThemeModeChange = onThemeModeChange,
                onAccentColorChange = onAccentColorChange,
                onNotificationIslandEnabledChange = onNotificationIslandEnabledChange,
                onNotificationDeliveryModeChange = onNotificationDeliveryModeChange,
                onNotifyOnTaskCompleteChange = onNotifyOnTaskCompleteChange,
                onNotifyOnPaymentSuccessChange = onNotifyOnPaymentSuccessChange,
                onIslandVendorModeChange = onIslandVendorModeChange,
            )
        }

        composable(Routes.PERMISSION) {
            PermissionScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.COLLECTION,
            arguments = listOf(
                navArgument("kind") {
                    type = NavType.StringType
                },
            ),
        ) { backStackEntry ->
            val kind = CollectionKind.fromRouteName(
                backStackEntry.arguments?.getString("kind"),
            )
            CollectionListScreen(
                kind = kind,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
