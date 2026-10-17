package com.example.model

import android.net.Uri

enum class MediaType {
    PHOTO,
    VIDEO
}

data class MediaSourceItem(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String,
    val mediaType: MediaType
)

enum class JobStatus {
    QUEUED,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class ConversionJob(
    val id: String,
    val source: MediaSourceItem,
    val status: JobStatus = JobStatus.QUEUED,
    val progress: Float = 0f,
    val outputUri: Uri? = null,
    val outputFileName: String? = null,
    val outputSizeBytes: Long? = null,
    val durationMs: Long = 0L,
    val details: String? = null,
    val errorMessage: String? = null
) {
    val sizeSavingsPercent: Int?
        get() {
            if (outputSizeBytes == null || outputSizeBytes <= 0 || source.sizeBytes <= 0) return null
            val diff = source.sizeBytes - outputSizeBytes
            return ((diff.toDouble() / source.sizeBytes.toDouble()) * 100).toInt()
        }
}

enum class VideoQualityPreset(
    val label: String,
    val subtitle: String,
    val targetBitrateKbps: Int?,
    val maxDimension: Int?
) {
    ORIGINAL("Original Resolution", "Max fidelity, universal H.264/AAC MP4", null, null),
    BALANCED_1080P("Balanced 1080p", "Optimized bitrate for social & WhatsApp", 4500, 1920),
    COMPACT_720P("Compact 720p", "Lightweight size for quick sharing", 2200, 1280)
}

data class ConversionSettings(
    val jpegQuality: Int = 95,
    val stripLocationMetadata: Boolean = true,
    val videoPreset: VideoQualityPreset = VideoQualityPreset.BALANCED_1080P,
    val outputFolderUriString: String? = null,
    val outputFolderName: String? = null
)
