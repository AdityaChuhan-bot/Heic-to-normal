package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ConversionJob
import com.example.model.JobStatus
import com.example.model.MediaType
import com.example.storage.SafFileManager
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricSky
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.MintTeal
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateCardBg
import com.example.ui.theme.SlateCardVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun BatchQueueSection(
    queue: List<ConversionJob>,
    isProcessing: Boolean,
    isPaused: Boolean,
    overallProgress: Float,
    onStartBatch: () -> Unit,
    onPauseBatch: () -> Unit,
    onCancelAll: () -> Unit,
    onRetryJob: (String) -> Unit,
    onRemoveJob: (String) -> Unit,
    onClearCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (queue.isEmpty()) return

    val queuedCount = queue.count { it.status == JobStatus.QUEUED }
    val processingCount = queue.count { it.status == JobStatus.PROCESSING }
    val completedCount = queue.count { it.status == JobStatus.COMPLETED }
    val failedCount = queue.count { it.status == JobStatus.FAILED }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, SlateBorder.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = SlateCardBg),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with batch progress
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Conversion Queue (${queue.size})",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Text(
                        text = when {
                            isProcessing -> "Converting $processingCount item(s)... • $queuedCount remaining"
                            isPaused -> "Queue paused • $queuedCount queued"
                            else -> "$completedCount completed • $failedCount failed"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                }

                // Batch action controls
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isProcessing) {
                        IconButton(
                            onClick = onPauseBatch,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("pause_batch_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = "Pause",
                                tint = AmberWarning,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = onCancelAll,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("cancel_all_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Cancel All",
                                tint = CoralError,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else if (queuedCount > 0) {
                        Button(
                            onClick = onStartBatch,
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("start_batch_button"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricSky)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Start",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    if (completedCount > 0 || failedCount > 0) {
                        OutlinedButton(
                            onClick = onClearCompleted,
                            modifier = Modifier
                                .height(36.dp)
                                .padding(start = 6.dp)
                                .testTag("clear_completed_button"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Clear Done", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Overall Progress bar
            if (isProcessing || overallProgress > 0f) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { overallProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .testTag("overall_progress_bar"),
                    color = ElectricSky,
                    trackColor = SlateCardVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Item cards
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                queue.forEach { job ->
                    QueueItemRow(
                        job = job,
                        onRetry = { onRetryJob(job.id) },
                        onRemove = { onRemoveJob(job.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueItemRow(
    job: ConversionJob,
    onRetry: () -> Unit,
    onRemove: () -> Unit
) {
    val isVideo = job.source.mediaType == MediaType.VIDEO
    val isProcessing = job.status == JobStatus.PROCESSING

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SlateCardVariant)
            .border(
                1.dp,
                if (isProcessing) ElectricSky.copy(alpha = 0.5f) else SlateBorder.copy(alpha = 0.3f),
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
            .testTag("queue_item_${job.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isVideo) MintTeal.copy(alpha = 0.15f) else ElectricSky.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isVideo) Icons.Default.VideoFile else Icons.Default.Image,
                        contentDescription = null,
                        tint = if (isVideo) MintTeal else ElectricSky,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = job.source.displayName,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isVideo) "MOV ➔ MP4" else "HEIC ➔ JPEG",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isVideo) MintTeal else ElectricSky,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "• ${SafFileManager.formatFileSize(job.source.sizeBytes)}",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
                        )
                    }
                }
            }

            // Status Badge / Actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (job.status) {
                    JobStatus.QUEUED -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(SlateCardBg)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Queued",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                    JobStatus.PROCESSING -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ElectricSky.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${(job.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricSky,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                    JobStatus.COMPLETED -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Completed",
                            tint = EmeraldSuccess,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    JobStatus.FAILED, JobStatus.CANCELLED -> {
                        IconButton(
                            onClick = onRetry,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("retry_button_${job.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Retry",
                                tint = AmberWarning,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                if (!isProcessing) {
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("remove_button_${job.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove",
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Per-item progress bar
        if (isProcessing) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { job.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = ElectricSky,
                trackColor = SlateCardBg
            )
        }

        // Failure explanation
        if (job.status == JobStatus.FAILED && job.errorMessage != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = CoralError,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = job.errorMessage,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = CoralError,
                        fontSize = 11.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
