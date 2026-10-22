package dev.codingcoffee.bouncer.ui

import android.Manifest
import android.graphics.Color
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.codingcoffee.bouncer.ThreadRequest
import dev.codingcoffee.bouncer.container
import dev.codingcoffee.bouncer.data.prefs.ThemeMode
import dev.codingcoffee.bouncer.ui.conversations.ConversationsScreen
import dev.codingcoffee.bouncer.ui.newchat.NewChatScreen
import dev.codingcoffee.bouncer.ui.onboarding.OnboardingScreen
import dev.codingcoffee.bouncer.ui.search.SearchScreen
import dev.codingcoffee.bouncer.ui.settings.SettingsScreen
import dev.codingcoffee.bouncer.ui.theme.BouncerTheme
import dev.codingcoffee.bouncer.ui.thread.ThreadScreen
import kotlinx.coroutines.flow.Flow

object Routes {
    const val CONVERSATIONS = "conversations"
    const val ARCHIVED = "archived"
    const val NEW_CHAT = "new"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val THREAD = "thread/{threadId}?focus={focus}&draft={draft}"

    fun thread(threadId: Long, focus: Long? = null, draft: String? = null): String =
        "thread/$threadId?focus=${focus ?: Long.MIN_VALUE}&draft=${Uri.encode(draft.orEmpty())}"
}

@Composable
fun AppRoot(threadRequests: Flow<ThreadRequest>) {
    val context = LocalContext.current
    val container = context.container
    val themeMode by container.settings.themeMode.collectAsStateWithLifecycle()
    val dark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val activity = LocalActivity.current as ComponentActivity
    DisposableEffect(dark) {
        val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose { }
    }

    BouncerTheme(darkTheme = dark) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            var isDefault by remember { mutableStateOf(container.sync.isDefaultSmsApp()) }
            LifecycleResumeEffect(Unit) {
                isDefault = container.sync.isDefaultSmsApp()
                if (isDefault) container.sync.start()
                onPauseOrDispose { }
            }
            if (isDefault) {
                AppNavHost(threadRequests)
            } else {
                OnboardingScreen(onGranted = {
                    isDefault = true
                    container.sync.start()
                })
            }
        }
    }
}

@Composable
private fun AppNavHost(threadRequests: Flow<ThreadRequest>) {
    val navController = rememberNavController()
    val container = LocalContext.current.container

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        container.contacts.onPermissionChanged()
    }
    LaunchedEffect(Unit) {
        permissions.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.POST_NOTIFICATIONS))
    }
    LaunchedEffect(Unit) {
        threadRequests.collect { request ->
            navController.navigate(Routes.thread(request.threadId, draft = request.draft)) {
                popUpTo(Routes.CONVERSATIONS)
                launchSingleTop = true
            }
        }
    }

    NavHost(navController, startDestination = Routes.CONVERSATIONS) {
        composable(Routes.CONVERSATIONS) {
            ConversationsScreen(
                archived = false,
                onOpenThread = { navController.navigate(Routes.thread(it)) },
                onNewChat = { navController.navigate(Routes.NEW_CHAT) },
                onSearch = { navController.navigate(Routes.SEARCH) },
                onArchived = { navController.navigate(Routes.ARCHIVED) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onBack = {},
            )
        }
        composable(Routes.ARCHIVED) {
            ConversationsScreen(
                archived = true,
                onOpenThread = { navController.navigate(Routes.thread(it)) },
                onNewChat = {},
                onSearch = {},
                onArchived = {},
                onSettings = {},
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            Routes.THREAD,
            arguments = listOf(
                navArgument("threadId") { type = NavType.LongType },
                navArgument("focus") { type = NavType.LongType; defaultValue = Long.MIN_VALUE },
                navArgument("draft") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val args = entry.arguments!!
            ThreadScreen(
                threadId = args.getLong("threadId"),
                focusMessageId = args.getLong("focus").takeIf { it != Long.MIN_VALUE },
                initialDraft = args.getString("draft").orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.NEW_CHAT) {
            NewChatScreen(
                onBack = { navController.popBackStack() },
                onOpenThread = { threadId ->
                    navController.navigate(Routes.thread(threadId)) { popUpTo(Routes.NEW_CHAT) { inclusive = true } }
                },
            )
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onOpenMessage = { threadId, messageId -> navController.navigate(Routes.thread(threadId, messageId)) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
