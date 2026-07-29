package com.denggl2.mason.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.denggl2.mason.data.InterfaceStyle
import com.denggl2.mason.data.ThemeMode
import com.denggl2.mason.data.UiPreferences
import com.denggl2.mason.ui.chat.ChatScreen
import com.denggl2.mason.ui.collection.CollectionKind
import com.denggl2.mason.ui.collection.CollectionListScreen
import com.denggl2.mason.ui.integration.IntegrationsScreen
import com.denggl2.mason.ui.settings.PermissionScreen
import com.denggl2.mason.ui.settings.SettingsScreen
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object Routes {
    const val CHAT_NEW = "chat_new/{fresh}/{sessionId}"
    const val CHAT = "chat/{conversationId}"
    const val SETTINGS = "settings"
    const val SETTINGS_AI = "settings/ai"
    const val PERMISSION = "permission"
    const val INTEGRATIONS = "integrations"
    const val COLLECTION = "collection/{kind}"

    fun chat(conversationId: Long) = "chat/$conversationId"
    fun newChat(fresh: Boolean = false, sessionId: String) = "chat_new/$fresh/$sessionId"
    fun collection(kind: CollectionKind) = "collection/${kind.routeName}"
}

@Composable
fun MasonNavGraph(
    uiPreferences: UiPreferences,
    openConversationId: Long? = null,
    notificationTaskCommand: String? = null,
    notificationArtifactPath: String? = null,
    onThemeModeChange: (ThemeMode) -> Unit,
    onInterfaceStyleChange: (InterfaceStyle) -> Unit,
    onLiquidGlassTransparencyChange: (Float) -> Unit,
    onAccentColorChange: (Long) -> Unit,
    onRegularNotificationsChange: (Boolean) -> Unit,
    onIslandNotificationsChange: (Boolean) -> Unit,
) {
    val navController = rememberNavController()
    val startSessionId = remember { UUID.randomUUID().toString() }
    val startRoute = remember(startSessionId) {
        Routes.newChat(fresh = true, sessionId = startSessionId)
    }
    val conversationRoutes = remember { mutableStateMapOf<Long, String>() }
    val transitionScope = rememberCoroutineScope()
    var conversationSwitchInProgress by remember { mutableStateOf(false) }
    var conversationSwitchResetJob by remember { mutableStateOf<Job?>(null) }
    var drawerResetGeneration by remember { mutableIntStateOf(0) }

    fun beginConversationSwitch() {
        conversationSwitchInProgress = true
        conversationSwitchResetJob?.cancel()
        conversationSwitchResetJob = transitionScope.launch {
            delay(320)
            conversationSwitchInProgress = false
        }
    }

    fun navigateToConversation(conversationId: Long, isRunning: Boolean) {
        if (navController.currentBackStackEntry?.savedStateHandle?.get<Long>("boundConversationId") == conversationId) {
            return
        }
        drawerResetGeneration += 1
        beginConversationSwitch()
        val route = conversationRoutes[conversationId]
            ?.takeIf { candidate ->
                isRunning && runCatching {
                    navController.getBackStackEntry(candidate)
                        .savedStateHandle
                        .get<Long>("boundConversationId") == conversationId
                }.getOrDefault(false)
            }
            ?: Routes.chat(conversationId)
        if (!navController.popBackStack(route, inclusive = false)) {
            // Every conversation needs its own SavedStateHandle-backed ChatViewModel.
            // launchSingleTop would reuse the current chat destination and its first ID.
            navController.navigate(Routes.chat(conversationId))
        }
    }

    fun navigateToNewChat() {
        drawerResetGeneration += 1
        navController.navigate(
            Routes.newChat(
                fresh = true,
                sessionId = UUID.randomUUID().toString(),
            ),
        )
    }

    LaunchedEffect(openConversationId, notificationArtifactPath) {
        if (notificationArtifactPath != null) {
            navController.navigate(Routes.collection(CollectionKind.ARTIFACTS)) {
                popUpTo(startRoute) { inclusive = false }
                launchSingleTop = true
            }
        } else {
            openConversationId?.let { conversationId ->
            navController.navigate(Routes.chat(conversationId)) {
                popUpTo(startRoute) { inclusive = false }
                launchSingleTop = true
            }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startRoute,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        enterTransition = {
            if (conversationSwitchInProgress) {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                )
            } else {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(360, easing = FastOutSlowInEasing),
                ) + fadeIn(tween(220)) + scaleIn(
                    initialScale = 0.985f,
                    animationSpec = tween(360, easing = FastOutSlowInEasing),
                )
            }
        },
        exitTransition = {
            if (conversationSwitchInProgress) {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                )
            } else {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(320, easing = FastOutSlowInEasing),
                ) + fadeOut(tween(180)) + scaleOut(
                    targetScale = 0.985f,
                    animationSpec = tween(320, easing = FastOutSlowInEasing),
                )
            }
        },
        popEnterTransition = {
            if (conversationSwitchInProgress) {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                )
            } else {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(340, easing = FastOutSlowInEasing),
                ) + fadeIn(tween(200)) + scaleIn(
                    initialScale = 0.99f,
                    animationSpec = tween(340, easing = FastOutSlowInEasing),
                )
            }
        },
        popExitTransition = {
            if (conversationSwitchInProgress) {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                )
            } else {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                ) + fadeOut(tween(160)) + scaleOut(
                    targetScale = 0.99f,
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                )
            }
        },
    ) {
        composable(
            route = Routes.CHAT_NEW,
            arguments = listOf(
                navArgument("fresh") { type = NavType.BoolType },
                navArgument("sessionId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val fresh = backStackEntry.arguments?.getBoolean("fresh") == true
            val sessionId = backStackEntry.arguments?.getString("sessionId").orEmpty()
            val route = Routes.newChat(fresh = fresh, sessionId = sessionId)
            ChatScreen(
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToIntegrations = { navController.navigate(Routes.INTEGRATIONS) },
                onNavigateToPermission = { navController.navigate(Routes.PERMISSION) },
                onConversationSelected = ::navigateToConversation,
                onNewChat = ::navigateToNewChat,
                drawerResetGeneration = drawerResetGeneration,
                onConversationBound = { id ->
                    val previousId = backStackEntry.savedStateHandle.get<Long>("boundConversationId")
                    if (previousId != null && conversationRoutes[previousId] == route) {
                        conversationRoutes.remove(previousId)
                    }
                    if (id == null) {
                        backStackEntry.savedStateHandle.remove<Long>("boundConversationId")
                    } else {
                        backStackEntry.savedStateHandle["boundConversationId"] = id
                        conversationRoutes[id] = route
                    }
                },
                startFresh = fresh,
                onOpenWorkbench = { navController.navigate(Routes.collection(CollectionKind.ARTIFACTS)) },
                notificationTaskCommand = notificationTaskCommand,
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
        ) { backStackEntry ->
            ChatScreen(
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToIntegrations = { navController.navigate(Routes.INTEGRATIONS) },
                onNavigateToPermission = { navController.navigate(Routes.PERMISSION) },
                onConversationSelected = ::navigateToConversation,
                onNewChat = ::navigateToNewChat,
                drawerResetGeneration = drawerResetGeneration,
                onConversationBound = { id ->
                    val currentRoute = id?.let(Routes::chat)
                    val previousId = backStackEntry.savedStateHandle.get<Long>("boundConversationId")
                    if (previousId != null && conversationRoutes[previousId] == Routes.chat(previousId)) {
                        conversationRoutes.remove(previousId)
                    }
                    if (id == null || currentRoute == null) {
                        backStackEntry.savedStateHandle.remove<Long>("boundConversationId")
                    } else {
                        backStackEntry.savedStateHandle["boundConversationId"] = id
                        conversationRoutes[id] = currentRoute
                    }
                },
                startFresh = false,
                onOpenWorkbench = { navController.navigate(Routes.collection(CollectionKind.ARTIFACTS)) },
                notificationTaskCommand = notificationTaskCommand,
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToPermission = {
                    navController.navigate(Routes.PERMISSION)
                },
                onNavigateToIntegrations = {
                    navController.navigate(Routes.INTEGRATIONS)
                },
                uiPreferences = uiPreferences,
                onThemeModeChange = onThemeModeChange,
                onInterfaceStyleChange = onInterfaceStyleChange,
                onLiquidGlassTransparencyChange = onLiquidGlassTransparencyChange,
                onAccentColorChange = onAccentColorChange,
                onRegularNotificationsChange = onRegularNotificationsChange,
                onIslandNotificationsChange = onIslandNotificationsChange,
            )
        }

        composable(Routes.SETTINGS_AI) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToPermission = {
                    navController.navigate(Routes.PERMISSION)
                },
                onNavigateToIntegrations = {
                    navController.navigate(Routes.INTEGRATIONS)
                },
                uiPreferences = uiPreferences,
                onThemeModeChange = onThemeModeChange,
                onInterfaceStyleChange = onInterfaceStyleChange,
                onLiquidGlassTransparencyChange = onLiquidGlassTransparencyChange,
                onAccentColorChange = onAccentColorChange,
                onRegularNotificationsChange = onRegularNotificationsChange,
                onIslandNotificationsChange = onIslandNotificationsChange,
            )
        }

        composable(Routes.PERMISSION) {
            PermissionScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.INTEGRATIONS) {
            IntegrationsScreen(onBack = { navController.popBackStack() })
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
                initialPreviewPath = notificationArtifactPath,
            )
        }
    }
}
