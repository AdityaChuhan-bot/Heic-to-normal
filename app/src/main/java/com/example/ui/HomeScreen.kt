package com.example.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.example.ui.theme.MintTeal
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateCardBg
import com.example.ui.theme.SlateCardVariant
import com.example.ui.theme.SlateDarkBg
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
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
    var showHelpDialog by remember { mutableStateOf(false) }

    // SAF Multi-Document Pickers
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
        containerColor = SlateDarkBg,
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
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ElectricSky.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = ElectricSky,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Private Media Converter",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showHelpDialog = true },
                        modifier = Modifier.testTag("help_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "WhatsApp Guide",
                            tint = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = SlateDarkBg
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Privacy Shield Banner
            item {
                PrivacyBanner()
            }

            // Hero Action Cards
            item {
                ConversionActionCards(
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
                    onSelectVideosClick = {
                        videoPickerLauncher.launch(
                            arrayOf(
                                "video/quicktime",
                                "video/mp4",
                                "video/*",
                                "*/*"
                            )
                        )
                    }
                )
            }

            // Settings Card
            item {
                SettingsCard(
                    settings = uiState.settings,
                    onSelectFolderClick = { folderPickerLauncher.launch(null) },
                    onResetFolderClick = { viewModel.clearOutputFolder() },
                    onQualityChange = { viewModel.updateJpegQuality(it) },
                    onVideoPresetChange = { viewModel.updateVideoPreset(it) },
                    onToggleStripGps = { viewModel.toggleStripGps(it) }
                )
            }

            // Batch Queue Section
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

            // Completed History Section
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

            // Empty state guide if queue and history are both empty
            if (uiState.queue.isEmpty() && uiState.history.isEmpty()) {
                item {
                    EmptyStateCard()
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showHelpDialog) {
        WhatsAppGuideDialog(onDismiss = { showHelpDialog = false })
    }
}

@Composable
private fun EmptyStateCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp)),
        colors = CardDefaults.cardColors(containerColor = SlateCardBg),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(SlateCardVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.UploadFile,
                    contentDescription = null,
                    tint = ElectricSky,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Ready to Convert",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Tap 'Select Photos' or 'Select Videos' above to pick WhatsApp documents or iPhone media from your device.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 18.sp
                )
            )
        }
    }
}

@Composable
private fun WhatsAppGuideDialog(onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "iPhone & WhatsApp Guide",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "Why do iPhone media files need conversion?",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = ElectricSky
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "• Photos: iPhones capture in Apple HEIC/HEIF format. When sent as 'Documents' on WhatsApp to preserve quality, Android apps cannot open or upload them without converting to JPEG.\n\n" +
                            "• Videos: iPhones record in Apple QuickTime (.MOV) containers often with HEVC or display matrix rotation. Converting to universal MP4 ensures compatibility on all platforms.\n\n" +
                            "• Zero-Trust Privacy: Your files never leave your Moto G85. Everything is decoded and encoded locally via offline platform APIs.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(onClick = onDismiss) {
                Text("Got It")
            }
        },
        containerColor = SlateCardBg
    )
}
