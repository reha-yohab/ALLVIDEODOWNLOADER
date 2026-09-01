package com.allvideodownloader.app.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.allvideodownloader.app.R
import com.allvideodownloader.app.ui.screens.DownloadScreen
import com.allvideodownloader.app.ui.screens.LibraryScreen
import com.allvideodownloader.app.util.Prefs

private object Routes {
    const val DOWNLOAD = "download"
    const val LIBRARY = "library"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(
    incomingLink: String?,
    onIncomingLinkConsumed: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var showDisclaimer by remember { mutableStateOf(!prefs.disclaimerAccepted) }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.DOWNLOAD

    val downloadViewModel: DownloadViewModel = viewModel()
    val libraryViewModel: LibraryViewModel = viewModel()
    val downloadState by downloadViewModel.state.collectAsStateWithLifecycle()
    val libraryState by libraryViewModel.state.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    // A link shared into the app from a browser lands straight in the text field.
    LaunchedEffect(incomingLink) {
        if (!incomingLink.isNullOrBlank()) {
            downloadViewModel.onUrlChange(incomingLink)
            navController.navigateToTab(Routes.DOWNLOAD)
            onIncomingLinkConsumed()
        }
    }

    LaunchedEffect(downloadState.message) {
        downloadState.message?.let {
            snackbarHostState.showSnackbar(it)
            downloadViewModel.consumeMessage()
        }
    }

    LaunchedEffect(libraryState.message) {
        libraryState.message?.let {
            snackbarHostState.showSnackbar(it)
            libraryViewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (currentRoute == Routes.LIBRARY) {
                            stringResource(R.string.library_title)
                        } else {
                            stringResource(R.string.app_name)
                        }
                    )
                },
                actions = {
                    if (currentRoute == Routes.LIBRARY) {
                        IconButton(onClick = { libraryViewModel.refresh() }) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.action_refresh)
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute == Routes.DOWNLOAD,
                    onClick = { navController.navigateToTab(Routes.DOWNLOAD) },
                    icon = { Icon(Icons.Filled.Download, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_download)) }
                )
                NavigationBarItem(
                    selected = currentRoute == Routes.LIBRARY,
                    onClick = { navController.navigateToTab(Routes.LIBRARY) },
                    icon = { Icon(Icons.Filled.VideoLibrary, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_library)) }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.DOWNLOAD,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.DOWNLOAD) {
                DownloadScreen(
                    state = downloadState,
                    contentPadding = PaddingValues(),
                    onUrlChange = downloadViewModel::onUrlChange,
                    onSubmit = downloadViewModel::submit,
                    onClear = downloadViewModel::clearUrl,
                    onCancel = downloadViewModel::cancel,
                    onDismissPrompt = downloadViewModel::dismissPrompt,
                    onForceDownload = downloadViewModel::forceDownload,
                    onClipboardEmpty = {
                        downloadViewModel.showMessage(context.getString(R.string.clipboard_empty))
                    }
                )
            }

            composable(Routes.LIBRARY) {
                LibraryScreen(
                    state = libraryState,
                    contentPadding = PaddingValues(),
                    onRefresh = { libraryViewModel.refresh() },
                    onDelete = libraryViewModel::delete,
                    onConsentResult = libraryViewModel::onConsentResult,
                    onMessage = libraryViewModel::showMessage
                )
            }
        }
    }

    if (showDisclaimer) {
        AlertDialog(
            onDismissRequest = { /* must be acknowledged */ },
            title = { Text(stringResource(R.string.disclaimer_title)) },
            text = { Text(stringResource(R.string.disclaimer_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        prefs.disclaimerAccepted = true
                        showDisclaimer = false
                    }
                ) { Text(stringResource(R.string.disclaimer_agree)) }
            }
        )
    }
}

private fun androidx.navigation.NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
