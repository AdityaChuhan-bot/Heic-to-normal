package com.example.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.MediaType
import com.example.storage.SafFileManager
import com.example.ui.theme.ElectricSky
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.MintTeal
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianCardElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidDarkBg
import com.example.ui.theme.WhatsAppGreen
import com.example.ui.theme.WhatsAppGreenDim
import com.example.viewmodel.ConverterViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: ConverterViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showWhatsAppGuide by remember { mutableStateOf(false) }

    // SAF Document Pickers
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addMediaUris(uris, forcedType = MediaType.PHOTO)
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addMediaUris(uris, forcedType = MediaType.VIDEO)
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (_: Exception) {}

            val folderDoc = DocumentFile.fromTreeUri(context, uri)
            val name = folderDoc?.name ?: "Selected Folder"
            viewModel.setOutputFolder(uri, name)
        }
    }

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissToast()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        containerColor = VoidDarkBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(ElectricSky.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = ElectricSky,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Private Media Converter",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 17.sp
                                )
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(WhatsAppGreen)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Direct WhatsApp Forwarding Ready",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = WhatsAppGreen,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showWhatsAppGuide = true },
                        modifier = Modifier.testTag("whatsapp_guide_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "WhatsApp Forward Guide",
                            tint = WhatsAppGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = VoidDarkBg
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Incoming WhatsApp Forward Banner (if media shared from WhatsApp)
            item {
                WhatsAppForwardBanner(
                    count = uiState.incomingShareNoticeCount,
                    onStartConversion = { viewModel.startBatch() },
                    onDismiss = { viewModel.dismissIncomingShareNotice() }
                )
            }

            // Privacy Security Status Pill
            item {
                PrivacyBanner()
            }

            // Studio Mode Switcher
            item {
                StudioSegmentedBar(
                    selectedTab = uiState.selectedTab,
                    onTabSelected = { viewModel.setSelectedTab(it) },
                    queueCount = uiState.queue.size,
                    historyCount = uiState.history.size
                )
            }

            // Studio Hero & Selection based on active tab
            item {
                when (uiState.selectedTab) {
                    0 -> {
                        PhotoStudioHero(
                            settings = uiState.settings,
                            onSelectPhotosClick = {
                                photoPickerLauncher.launch(
                                    arrayOf(
                                        "image/heic",
                                        "image/heif",
                                        "image/jpeg",
                                        "image/png",
                                        "image/webp",
                                        "image/*",
                                        "*/*"
                                    )
                                )
                            },
                            onQualityChange = { viewModel.updateJpegQuality(it) },
                            onToggleStripGps = { viewModel.toggleStripGps(it) }
                        )
                    }
                    1 -> {
                        VideoStudioHero(
                            settings = uiState.settings,
                            onSelectVideosClick = {
                                videoPickerLauncher.launch(
                                    arrayOf(
                                        "video/quicktime",
                                        "video/mp4",
                                        "video/*",
                                        "*/*"
                                    )
                                )
                            },
                            onPresetChange = { viewModel.updateVideoPreset(it) }
                        )
                    }
                    2 -> {
                        // Vault Tab: Show completed history directly
                        if (uiState.history.isEmpty()) {
                            EmptyVaultCard(onSelectTab = { viewModel.setSelectedTab(it) })
                        }
                    }
                }
            }

            // Output Folder Destination Bar
            item {
                FolderDestinationBar(
                    settings = uiState.settings,
                    onSelectFolderClick = { folderPickerLauncher.launch(null) },
                    onResetFolderClick = { viewModel.clearOutputFolder() }
                )
            }

            // Active Batch Queue Section (always visible when queue has items)
            if (uiState.queue.isNotEmpty()) {
                item {
                    BatchQueueSection(
                        queue = uiState.queue,
                        isProcessing = uiState.isProcessing,
                        isPaused = uiState.isPaused,
                        overallProgress = uiState.currentBatchOverallProgress,
                        onStartBatch = { viewModel.startBatch() },
                        onPauseBatch = { viewModel.pauseBatch() },
                        onCancelAll = { viewModel.cancelAll() },
                        onRetryJob = { viewModel.retryJob(it) },
                        onRemoveJob = { viewModel.removeJob(it) },
                        onClearCompleted = { viewModel.clearCompleted() }
                    )
                }
            }

            // Converted History Section (visible in Vault tab or when history exists)
            if (uiState.history.isNotEmpty() && uiState.selectedTab == 2) {
                item {
                    HistorySection(
                        history = uiState.history,
                        onOpenFile = { job ->
                            job.outputUri?.let { uri ->
                                val mime = if (job.source.mediaType == MediaType.VIDEO) "video/mp4" else "image/jpeg"
                                SafFileManager.openFile(context, uri, mime)
                            }
                        },
                        onShareFile = { job ->
                            job.outputUri?.let { uri ->
                                val mime = if (job.source.mediaType == MediaType.VIDEO) "video/mp4" else "image/jpeg"
                                SafFileManager.shareFile(context, uri, mime, job.outputFileName)
                            }
                        },
                        onClearHistory = { viewModel.clearHistory() }
                    )
                }
            } else if (uiState.history.isNotEmpty() && uiState.selectedTab != 2) {
                // Mini shortcut to history when in other tabs
                item {
                    HistorySection(
                        history = uiState.history.take(3),
                        onOpenFile = { job ->
                            job.outputUri?.let { uri ->
                                val mime = if (job.source.mediaType == MediaType.VIDEO) "video/mp4" else "image/jpeg"
                                SafFileManager.openFile(context, uri, mime)
                            }
                        },
                        onShareFile = { job ->
                            job.outputUri?.let { uri ->
                                val mime = if (job.source.mediaType == MediaType.VIDEO) "video/mp4" else "image/jpeg"
                                SafFileManager.shareFile(context, uri, mime, job.outputFileName)
                            }
                        },
                        onClearHistory = { viewModel.clearHistory() }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showWhatsAppGuide) {
        WhatsAppForwardGuideDialog(onDismiss = { showWhatsAppGuide = false })
    }
}

@Composable
private fun EmptyVaultCard(onSelectTab: (Int) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, HairlineBorder, RoundedCornerShape(20.dp)),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = ObsidianCard),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(ObsidianCardElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = EmeraldSuccess,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Vault is Empty",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Converted JPEG photos and MP4 videos will appear here with 1-tap forwarding to WhatsApp.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 18.sp
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { onSelectTab(0) },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Start Converting Photos")
            }
        }
    }
}

@Composable
private fun WhatsAppForwardGuideDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(WhatsAppGreenDim),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        tint = WhatsAppGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Direct WhatsApp Forwarding",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "You can forward images & videos directly from WhatsApp to this app in 3 quick steps:",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                )

                GuideStep(
                    number = "1",
                    title = "In WhatsApp chat",
                    desc = "Tap on the received iPhone photo (HEIC) or video (MOV) document."
                )

                GuideStep(
                    number = "2",
                    title = "Tap Share / Forward",
                    desc = "Tap the Share icon (or 3-dots ➔ Share) in WhatsApp."
                )

                GuideStep(
                    number = "3",
                    title = "Select 'Private Media Converter'",
                    desc = "The app opens immediately, queues your media, and converts it to universal JPEG/MP4!"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = WhatsAppGreen,
                    contentColor = Color(0xFF032612)
                )
            ) {
                Text("Got It", fontWeight = FontWeight.Bold)
            }
        },
        containerColor = ObsidianCard,
        tonalElevation = 6.dp
    )
}

@Composable
private fun GuideStep(
    number: String,
    title: String,
    desc: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(WhatsAppGreen.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = WhatsAppGreen
                )
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            )
        }
    }
}
