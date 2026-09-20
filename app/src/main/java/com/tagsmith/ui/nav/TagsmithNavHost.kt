package com.tagsmith.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tagsmith.core.nfc.NfcAvailability
import com.tagsmith.core.nfc.TagSnapshot
import com.tagsmith.ui.LocalAppContainer
import com.tagsmith.ui.components.TagsmithIcons
import com.tagsmith.ui.details.TagDetailsScreen
import com.tagsmith.ui.history.HistoryDetailScreen
import com.tagsmith.ui.history.HistoryScreen
import com.tagsmith.ui.home.HomeScreen
import com.tagsmith.ui.lock.LockConfirmScreen
import com.tagsmith.ui.onboarding.OnboardingScreen
import com.tagsmith.ui.placeholder.ComingSoonScreen
import com.tagsmith.ui.scan.ScanScreen
import com.tagsmith.ui.settings.SettingsScreen
import com.tagsmith.ui.erase.EraseScreen
import com.tagsmith.ui.write.WriteScreen
import com.tagsmith.ui.util.openNfcSettings

/**
 * One NavHost. The bottom bar and the Scan button appear only on the four tab
 * destinations; everything else is a full-bleed screen over the top.
 */
@Composable
fun TagsmithNavHost(
    startAtOnboarding: Boolean,
    versionName: String,
    sharedUrl: String?,
    onOnboardingComplete: () -> Unit,
    onSharedUrlConsumed: () -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val availability by container.nfc.availability.collectAsStateWithLifecycle()
    val lastSnapshot by container.nfc.lastSnapshot.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // The snapshot the details and lock screens read. Held here rather than
    // serialised through nav arguments — it is a live object, not a key.
    var inspected by remember { mutableStateOf<TagSnapshot?>(null) }

    // A URL shared in from another app lands straight in the write screen.
    LaunchedEffect(sharedUrl) {
        if (!sharedUrl.isNullOrBlank()) {
            navController.navigate(Routes.write(sharedUrl))
            onSharedUrlConsumed()
        }
    }

    val showShell = currentRoute in Destination.entries.map { it.route }

    Scaffold(
        bottomBar = {
            if (showShell) {
                TagsmithBottomBar(
                    current = currentRoute,
                    onSelect = { destination ->
                        navController.navigate(destination.route) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (showShell) {
                ScanButton(onClick = { navController.navigate(Routes.SCAN) })
            }
        },
        floatingActionButtonPosition = FabPosition.End,
        containerColor = com.tagsmith.ui.theme.Tagsmith.colors.ground,
    ) { innerPadding ->
        val contentModifier = if (showShell) {
            Modifier.fillMaxSize().padding(bottom = innerPadding.calculateBottomPadding())
        } else {
            Modifier.fillMaxSize()
        }

        Box(contentModifier) {
            NavHost(
                navController = navController,
                startDestination = if (startAtOnboarding) Routes.ONBOARDING else Routes.HOME,
            ) {
                composable(Routes.ONBOARDING) {
                    OnboardingScreen(
                        availability = availability,
                        onOpenNfcSettings = { openNfcSettings(context) },
                        onFinish = {
                            onOnboardingComplete()
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.ONBOARDING) { inclusive = true }
                            }
                        },
                    )
                }

                composable(Routes.HOME) {
                    HomeScreen(
                        availability = availability,
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        onOpenNfcSettings = { openNfcSettings(context) },
                        onOpenHistory = { navController.navigate(Routes.HISTORY) },
                        onWrite = { navController.navigate(Routes.write()) },
                        onOpenEntry = { navController.navigate(Routes.historyDetail(it)) },
                    )
                }

                composable(Routes.TAGS) {
                    ComingSoonScreen(
                        title = "Tags",
                        kicker = "Inventory",
                        body = "Your stock and deployed tags, filterable by status and " +
                            "searchable by UID — so you can answer \"what is this card?\" " +
                            "without tapping it. Every tag you scan is already being recorded " +
                            "for it.",
                        icon = TagsmithIcons.Tag,
                        actionLabel = "Scan a tag instead",
                        onAction = { navController.navigate(Routes.SCAN) },
                    )
                }

                composable(Routes.CLIENTS) {
                    ComingSoonScreen(
                        title = "Clients",
                        kicker = "Businesses you supply",
                        body = "The businesses you've supplied, the tags each one holds, and " +
                            "the batches you ran for them. Writes will carry a client through " +
                            "to the ledger.",
                        icon = TagsmithIcons.Clients,
                    )
                }

                composable(Routes.HISTORY) {
                    HistoryScreen(
                        onOpenEntry = { navController.navigate(Routes.historyDetail(it)) },
                    )
                }

                composable(Routes.SCAN) {
                    ScanScreen(
                        onClose = { navController.popBackStack() },
                        onWriteInstead = {
                            navController.navigate(Routes.write()) {
                                popUpTo(Routes.SCAN) { inclusive = true }
                            }
                        },
                        onOpenDetails = { snapshot ->
                            inspected = snapshot
                            navController.navigate(Routes.DETAILS)
                        },
                        onOpenNfcSettings = { openNfcSettings(context) },
                    )
                }

                composable(Routes.DETAILS) {
                    val snapshot = inspected
                    if (snapshot == null) {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                    } else {
                        TagDetailsScreen(
                            snapshot = snapshot,
                            onBack = { navController.popBackStack() },
                            onOverwrite = { navController.navigate(Routes.write()) },
                            onErase = { navController.navigate(Routes.ERASE) },
                            onLock = { navController.navigate(Routes.LOCK) },
                        )
                    }
                }

                composable(
                    route = Routes.WRITE_WITH_URL,
                    arguments = listOf(
                        navArgument(Routes.WRITE_ARG_URL) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    ),
                ) { entry ->
                    WriteScreen(
                        prefill = entry.arguments?.getString(Routes.WRITE_ARG_URL),
                        availability = availability,
                        onClose = { navController.popBackStack() },
                        onLockTag = { navController.navigate(Routes.LOCK) },
                        onOpenNfcSettings = { openNfcSettings(context) },
                    )
                }

                composable(Routes.LOCK) {
                    LockConfirmScreen(
                        snapshot = inspected ?: lastSnapshot,
                        onCancel = { navController.popBackStack() },
                        onLocked = {
                            navController.popBackStack(Routes.HOME, inclusive = false)
                        },
                    )
                }

                composable(Routes.ERASE) {
                    EraseScreen(
                        snapshot = inspected ?: lastSnapshot,
                        onCancel = { navController.popBackStack() },
                        onErased = { navController.popBackStack(Routes.HOME, inclusive = false) },
                    )
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        versionName = versionName,
                    )
                }

                composable(
                    route = Routes.HISTORY_DETAIL,
                    arguments = listOf(
                        navArgument(Routes.HISTORY_DETAIL_ARG) { type = NavType.LongType }
                    ),
                ) { entry ->
                    HistoryDetailScreen(
                        entryId = entry.arguments?.getLong(Routes.HISTORY_DETAIL_ARG) ?: 0L,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
