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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tagsmith.core.nfc.TagSnapshot
import com.tagsmith.ui.LocalAppContainer
import com.tagsmith.ui.batch.BatchRunScreen
import com.tagsmith.ui.batch.BatchSetupScreen
import com.tagsmith.ui.batch.BatchSummaryScreen
import com.tagsmith.ui.clients.ClientDetailNav
import com.tagsmith.ui.clients.ClientDetailScreen
import com.tagsmith.ui.clients.ClientEditorScreen
import com.tagsmith.ui.clients.ClientsScreen
import com.tagsmith.ui.components.TagsmithIcons
import com.tagsmith.ui.details.TagDetailsScreen
import com.tagsmith.ui.erase.EraseScreen
import com.tagsmith.ui.history.HistoryDetailScreen
import com.tagsmith.ui.history.HistoryScreen
import com.tagsmith.ui.home.HomeNav
import com.tagsmith.ui.home.HomeScreen
import com.tagsmith.ui.lock.LockConfirmScreen
import com.tagsmith.ui.onboarding.OnboardingScreen
import com.tagsmith.ui.placeholder.ComingSoonScreen
import com.tagsmith.ui.scan.ScanScreen
import com.tagsmith.ui.settings.SettingsScreen
import com.tagsmith.ui.templates.ReviewLinkBuilderScreen
import com.tagsmith.ui.templates.TemplateEditorScreen
import com.tagsmith.ui.templates.TemplatesNav
import com.tagsmith.ui.templates.TemplatesScreen
import com.tagsmith.ui.theme.Tagsmith
import com.tagsmith.ui.util.openNfcSettings
import com.tagsmith.ui.write.WriteNav
import com.tagsmith.ui.write.WriteScreen
import kotlinx.coroutines.launch

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

    // The snapshot the details, lock and erase screens read. Held here rather
    // than serialised through nav arguments — it is a live object, not a key.
    var inspected by remember { mutableStateOf<TagSnapshot?>(null) }
    val scope = rememberCoroutineScope()

    // A URL shared in from another app lands straight in the write screen.
    LaunchedEffect(sharedUrl) {
        if (!sharedUrl.isNullOrBlank()) {
            navController.navigate(Routes.write(sharedUrl))
            onSharedUrlConsumed()
        }
    }

    fun back() = navController.popBackStack()

    /** Hands a value to the screen underneath, then closes this one. */
    fun returnResult(key: String, value: Any) {
        navController.previousBackStackEntry?.savedStateHandle?.set(key, value)
        navController.popBackStack()
    }

    fun openBatch(id: Long) {
        container.nfc.cancel()
        scope.launch {
            val batch = container.batches.find(id)
            navController.navigate(
                if (batch?.status?.isOpen == true) Routes.batchRun(id) else Routes.batchSummary(id)
            )
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
            if (showShell) ScanButton(onClick = { navController.navigate(Routes.SCAN) })
        },
        floatingActionButtonPosition = FabPosition.End,
        containerColor = Tagsmith.colors.ground,
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
                        nav = HomeNav(
                            onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                            onOpenNfcSettings = { openNfcSettings(context) },
                            onOpenHistory = { navController.navigate(Routes.HISTORY) },
                            onWrite = { navController.navigate(Routes.write()) },
                            onWriteTemplate = { navController.navigate(Routes.write(templateId = it)) },
                            onTemplates = { navController.navigate(Routes.TEMPLATES) },
                            onNewBatch = { navController.navigate(Routes.batchSetup()) },
                            onResumeBatch = { navController.navigate(Routes.batchRun(it)) },
                            onOpenEntry = { navController.navigate(Routes.historyDetail(it)) },
                        ),
                    )
                }

                composable(Routes.TAGS) {
                    ComingSoonScreen(
                        title = "Tags",
                        kicker = "Inventory",
                        body = "Your stock and deployed tags, filterable by status and " +
                            "searchable by UID — so you can answer \"what is this card?\" " +
                            "without tapping it. Every tag you scan or write is already being " +
                            "recorded for it.",
                        icon = TagsmithIcons.Tag,
                        actionLabel = "Scan a tag instead",
                        onAction = { navController.navigate(Routes.SCAN) },
                    )
                }

                composable(Routes.CLIENTS) {
                    ClientsScreen(
                        onOpen = { navController.navigate(Routes.client(it)) },
                        onNew = { navController.navigate(Routes.clientEdit()) },
                    )
                }

                composable(Routes.HISTORY) {
                    HistoryScreen(onOpenEntry = { navController.navigate(Routes.historyDetail(it)) })
                }

                composable(Routes.SCAN) {
                    ScanScreen(
                        onClose = { back() },
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
                        LaunchedEffect(Unit) { back() }
                    } else {
                        TagDetailsScreen(
                            snapshot = snapshot,
                            onBack = { back() },
                            onOverwrite = { navController.navigate(Routes.write()) },
                            onErase = { navController.navigate(Routes.ERASE) },
                            onLock = { navController.navigate(Routes.LOCK) },
                            onOpenBatch = ::openBatch,
                            onNewClient = { navController.navigate(Routes.clientEdit(returnsResult = true)) },
                        )
                    }
                }

                composable(
                    route = Routes.WRITE,
                    arguments = listOf(optionalString(Routes.ARG_URL), optionalLong(Routes.ARG_TEMPLATE)),
                ) { entry ->
                    val handle = entry.savedStateHandle
                    val reviewUrl by handle.getStateFlow<String?>(Routes.RESULT_REVIEW_URL, null)
                        .collectAsStateWithLifecycle()
                    val newClientId by handle.getStateFlow<Long?>(Routes.RESULT_NEW_CLIENT, null)
                        .collectAsStateWithLifecycle()
                    WriteScreen(
                        prefill = entry.arguments?.getString(Routes.ARG_URL),
                        templateId = entry.longArg(Routes.ARG_TEMPLATE),
                        reviewUrl = reviewUrl,
                        newClientId = newClientId,
                        onResultConsumed = { entry.clearResults() },
                        availability = availability,
                        nav = WriteNav(
                            onClose = { back() },
                            onLockTag = { navController.navigate(Routes.LOCK) },
                            onOpenNfcSettings = { openNfcSettings(context) },
                            onOpenReviewBuilder = { navController.navigate(Routes.reviewBuilder(returnsResult = true)) },
                            onManageTemplates = { navController.navigate(Routes.TEMPLATES) },
                            onNewClient = { navController.navigate(Routes.clientEdit(returnsResult = true)) },
                            onStartBatch = { payload, clientId, templateId ->
                                navController.navigate(Routes.batchSetup(templateId, clientId, payload))
                            },
                        ),
                    )
                }

                composable(Routes.LOCK) {
                    LockConfirmScreen(
                        snapshot = inspected ?: lastSnapshot,
                        onCancel = { back() },
                        onLocked = { navController.popBackStack(Routes.HOME, inclusive = false) },
                    )
                }

                composable(Routes.ERASE) {
                    EraseScreen(
                        snapshot = inspected ?: lastSnapshot,
                        onCancel = { back() },
                        onErased = { navController.popBackStack(Routes.HOME, inclusive = false) },
                    )
                }

                // — templates —

                composable(Routes.TEMPLATES) {
                    TemplatesScreen(
                        TemplatesNav(
                            onBack = { back() },
                            onNew = { navController.navigate(Routes.templateEdit()) },
                            onEdit = { navController.navigate(Routes.templateEdit(it)) },
                            onWrite = { navController.navigate(Routes.write(templateId = it)) },
                            onBatch = { navController.navigate(Routes.batchSetup(templateId = it)) },
                            onReviewBuilder = { navController.navigate(Routes.reviewBuilder(returnsResult = false)) },
                        )
                    )
                }

                composable(
                    route = Routes.TEMPLATE_EDIT,
                    arguments = listOf(
                        optionalLong(Routes.ARG_ID),
                        optionalString(Routes.ARG_PAYLOAD),
                        optionalString(Routes.ARG_NAME),
                    ),
                ) { entry ->
                    val handle = entry.savedStateHandle
                    val reviewUrl by handle.getStateFlow<String?>(Routes.RESULT_REVIEW_URL, null)
                        .collectAsStateWithLifecycle()
                    val newClientId by handle.getStateFlow<Long?>(Routes.RESULT_NEW_CLIENT, null)
                        .collectAsStateWithLifecycle()
                    TemplateEditorScreen(
                        templateId = entry.longArg(Routes.ARG_ID),
                        prefillPayload = entry.arguments?.getString(Routes.ARG_PAYLOAD),
                        prefillName = entry.arguments?.getString(Routes.ARG_NAME),
                        reviewUrl = reviewUrl,
                        newClientId = newClientId,
                        onResultConsumed = { entry.clearResults() },
                        onClose = { back() },
                        onOpenReviewBuilder = { navController.navigate(Routes.reviewBuilder(returnsResult = true)) },
                        onNewClient = { navController.navigate(Routes.clientEdit(returnsResult = true)) },
                    )
                }

                composable(
                    route = Routes.REVIEW_BUILDER,
                    arguments = listOf(navArgument(Routes.ARG_RETURNS) { type = NavType.BoolType; defaultValue = false }),
                ) { entry ->
                    val returns = entry.arguments?.getBoolean(Routes.ARG_RETURNS) ?: false
                    ReviewLinkBuilderScreen(
                        onClose = { back() },
                        onUse = { url ->
                            if (returns) {
                                returnResult(Routes.RESULT_REVIEW_URL, url)
                            } else {
                                navController.navigate(Routes.write(url)) {
                                    popUpTo(Routes.REVIEW_BUILDER) { inclusive = true }
                                }
                            }
                        },
                    )
                }

                // — clients —

                composable(
                    route = Routes.CLIENT,
                    arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.LongType }),
                ) { entry ->
                    val id = entry.arguments?.getLong(Routes.ARG_ID) ?: return@composable
                    ClientDetailScreen(
                        clientId = id,
                        nav = ClientDetailNav(
                            onBack = { back() },
                            onEdit = { navController.navigate(Routes.clientEdit(id)) },
                            onNewBatch = { navController.navigate(Routes.batchSetup(clientId = id)) },
                            onOpenBatch = { batch ->
                                navController.navigate(
                                    if (batch.status.isOpen) Routes.batchRun(batch.id) else Routes.batchSummary(batch.id)
                                )
                            },
                            onWriteTemplate = { navController.navigate(Routes.write(templateId = it)) },
                        ),
                    )
                }

                composable(
                    route = Routes.CLIENT_EDIT,
                    arguments = listOf(
                        optionalLong(Routes.ARG_ID),
                        navArgument(Routes.ARG_RETURNS) { type = NavType.BoolType; defaultValue = false },
                    ),
                ) { entry ->
                    val editing = entry.longArg(Routes.ARG_ID)
                    val returns = entry.arguments?.getBoolean(Routes.ARG_RETURNS) ?: false
                    ClientEditorScreen(
                        clientId = editing,
                        onClose = { back() },
                        onSaved = { id ->
                            when {
                                returns -> returnResult(Routes.RESULT_NEW_CLIENT, id)
                                editing != null -> back()
                                // A new client from the Clients tab opens on their page.
                                else -> navController.navigate(Routes.client(id)) {
                                    popUpTo(Routes.CLIENT_EDIT) { inclusive = true }
                                }
                            }
                        },
                        onDeleted = { navController.popBackStack(Routes.CLIENTS, inclusive = false) || back() },
                    )
                }

                // — batches —

                composable(
                    route = Routes.BATCH_SETUP,
                    arguments = listOf(
                        optionalLong(Routes.ARG_TEMPLATE),
                        optionalLong(Routes.ARG_CLIENT),
                        optionalString(Routes.ARG_PAYLOAD),
                    ),
                ) { entry ->
                    val newClientId by entry.savedStateHandle.getStateFlow<Long?>(Routes.RESULT_NEW_CLIENT, null)
                        .collectAsStateWithLifecycle()
                    BatchSetupScreen(
                        templateId = entry.longArg(Routes.ARG_TEMPLATE),
                        clientId = entry.longArg(Routes.ARG_CLIENT),
                        payloadJson = entry.arguments?.getString(Routes.ARG_PAYLOAD),
                        availability = availability,
                        newClientId = newClientId,
                        onResultConsumed = { entry.clearResults() },
                        onClose = { back() },
                        onStarted = { id ->
                            navController.navigate(Routes.batchRun(id)) {
                                popUpTo(Routes.BATCH_SETUP) { inclusive = true }
                            }
                        },
                        onManageTemplates = { navController.navigate(Routes.TEMPLATES) },
                        onNewClient = { navController.navigate(Routes.clientEdit(returnsResult = true)) },
                    )
                }

                composable(
                    route = Routes.BATCH_RUN,
                    arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.LongType }),
                ) { entry ->
                    val id = entry.arguments?.getLong(Routes.ARG_ID) ?: return@composable
                    BatchRunScreen(
                        batchId = id,
                        onComplete = {
                            navController.navigate(Routes.batchSummary(id)) {
                                popUpTo(Routes.BATCH_RUN) { inclusive = true }
                            }
                        },
                        onLeave = { back() },
                    )
                }

                composable(
                    route = Routes.BATCH_SUMMARY,
                    arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.LongType }),
                ) { entry ->
                    val id = entry.arguments?.getLong(Routes.ARG_ID) ?: return@composable
                    BatchSummaryScreen(
                        batchId = id,
                        onBack = { back() },
                        onResume = {
                            navController.navigate(Routes.batchRun(id)) {
                                popUpTo(Routes.BATCH_SUMMARY) { inclusive = true }
                            }
                        },
                    )
                }

                // — the rest —

                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onBack = { back() },
                        versionName = versionName,
                        onNewClient = { navController.navigate(Routes.clientEdit(returnsResult = true)) },
                    )
                }

                composable(
                    route = Routes.HISTORY_DETAIL,
                    arguments = listOf(navArgument(Routes.HISTORY_DETAIL_ARG) { type = NavType.LongType }),
                ) { entry ->
                    HistoryDetailScreen(
                        entryId = entry.arguments?.getLong(Routes.HISTORY_DETAIL_ARG) ?: 0L,
                        onBack = { back() },
                        onOpenBatch = ::openBatch,
                    )
                }
            }
        }
    }
}

private fun optionalString(name: String) = navArgument(name) {
    type = NavType.StringType
    nullable = true
    defaultValue = null
}

/** Long arguments cannot be null in Navigation, so -1 stands for "absent". */
private fun optionalLong(name: String) = navArgument(name) {
    type = NavType.LongType
    defaultValue = -1L
}

private fun NavBackStackEntry.longArg(name: String): Long? =
    arguments?.getLong(name, -1L)?.takeIf { it >= 0 }

private fun NavBackStackEntry.clearResults() {
    savedStateHandle[Routes.RESULT_REVIEW_URL] = null
    savedStateHandle[Routes.RESULT_NEW_CLIENT] = null
}

