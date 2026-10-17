package com.example.converter

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.TransformationRequest
import androidx.media3.transformer.Transformer
import com.example.model.ConversionSettings
import com.example.model.MediaSourceItem
import com.example.model.VideoQualityPreset
import com.example.storage.SafFileManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

@androidx.annotation.OptIn(UnstableApi::class)
object VideoConverter {

    data class VideoInfo(
        val width: Int,
        val height: Int,
        val rotation: Int,
        val durationMs: Long,
        val bitrate: Long,
        val mimeType: String?,
        val audioMimeType: String?
    )

    data class VideoResult(
        val outputUri: Uri,
        val outputFileName: String,
        val outputSizeBytes: Long,
        val durationMs: Long,
        val details: String
    )

    class ActiveTranscodeSession(
        val transformer: Transformer,
        val tempOutputFile: File,
        val tempInputFile: File?
    ) {
        fun cancel() {
            try {
                transformer.cancel()
            } catch (_: Exception) {}
            cleanup()
        }

        fun cleanup() {
            try {
                if (tempOutputFile.exists()) tempOutputFile.delete()
                if (tempInputFile != null && tempInputFile.exists()) tempInputFile.delete()
            } catch (_: Exception) {}
        }
    }

    fun inspectVideo(context: Context, uri: Uri): VideoInfo {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val br = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            VideoInfo(
                width = w,
                height = h,
                rotation = rot,
                durationMs = dur,
                bitrate = br,
                mimeType = mime,
                audioMimeType = null
            )
        } catch (_: Exception) {
            VideoInfo(0, 0, 0, 0L, 0L, null, null)
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    suspend fun convert(
        context: Context,
        source: MediaSourceItem,
        settings: ConversionSettings,
        targetFolderUri: Uri?,
        onProgress: (Float) -> Unit,
        onSessionCreated: (ActiveTranscodeSession) -> Unit = {}
    ): Result<VideoResult> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var tempInputFile: File? = null
        val tempOutputFile = File(context.cacheDir, "transcode_${UUID.randomUUID()}.mp4")

        try {
            onProgress(0.05f)

            // Inspect video metadata
            val videoInfo = inspectVideo(context, source.uri)

            // For reliable SAF file reading across diverse content providers, copy to temp cache input if needed
            val inputMediaItemUri = if (source.uri.scheme == "content") {
                // If the stream can be read via parcel file descriptor directly, we can use it,
                // or copy to a local cache pipe file to ensure media decoder compatibility
                val cachedInput = File(context.cacheDir, "input_${UUID.randomUUID()}.tmp")
                tempInputFile = cachedInput
                SafFileManager.openInputStream(context, source.uri)?.use { input ->
                    FileOutputStream(cachedInput).use { output ->
                        input.copyTo(output)
                    }
                } ?: return@withContext Result.failure(
                    IllegalArgumentException("Failed to open video source stream for '${source.displayName}'")
                )
                Uri.fromFile(cachedInput)
            } else {
                source.uri
            }

            onProgress(0.15f)

            val completionDeferred = CompletableDeferred<Result<ExportResult>>()

            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    completionDeferred.complete(Result.success(exportResult))
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    val msg = when {
                        exportException.message?.contains("codec", ignoreCase = true) == true ->
                            "Codec error: Your device hardware does not support this specific MOV codec profile. (${exportException.localizedMessage})"
                        else -> exportException.localizedMessage ?: "Video transcoding failed"
                    }
                    completionDeferred.complete(Result.failure(Exception(msg, exportException)))
                }
            }

            // Configure Media3 Transformer for universal H.264/AVC + AAC MP4 output
            val transformerBuilder = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(listener)

            val transformer = transformerBuilder.build()
            val session = ActiveTranscodeSession(transformer, tempOutputFile, tempInputFile)
            onSessionCreated(session)

            val mediaItem = MediaItem.fromUri(inputMediaItemUri)
            val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                .setFlattenForSlowMotion(true)
                .build()

            // Start transcoding
            transformer.start(editedMediaItem, tempOutputFile.absolutePath)

            // Progress tracking loop
            val progressHolder = ProgressHolder()
            while (!completionDeferred.isCompleted && isActive) {
                val state = transformer.getProgress(progressHolder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    val p = (0.15f + (progressHolder.progress.toFloat() / 100f) * 0.75f).coerceIn(0.15f, 0.90f)
                    onProgress(p)
                }
                delay(300)
            }

            val exportResultOutcome = completionDeferred.await()
            if (exportResultOutcome.isFailure) {
                session.cleanup()
                return@withContext Result.failure(exportResultOutcome.exceptionOrNull() ?: Exception("Unknown transcode error"))
            }

            onProgress(0.92f)

            // Output verification
            if (!tempOutputFile.exists() || tempOutputFile.length() <= 0) {
                session.cleanup()
                return@withContext Result.failure(IllegalStateException("Transcoding finished but output file is empty or missing."))
            }

            // Destination SAF target
            val (finalUri, outputName) = SafFileManager.prepareOutputFile(
                context = context,
                treeUri = targetFolderUri,
                baseName = source.displayName,
                targetExtension = "mp4",
                targetMimeType = "video/mp4"
            )

            // Write transcoded MP4 to destination
            val outputStream = SafFileManager.openOutputStream(context, finalUri)
                ?: run {
                    session.cleanup()
                    return@withContext Result.failure(IllegalStateException("Failed to open destination stream for '$outputName'"))
                }

            var finalSize = 0L
            outputStream.use { out ->
                FileInputStream(tempOutputFile).use { input ->
                    finalSize = input.copyTo(out)
                }
                out.flush()
            }

            onProgress(1.0f)
            val elapsed = System.currentTimeMillis() - startTime

            // Cleanup temp files immediately
            session.cleanup()

            val detailsText = buildString {
                if (videoInfo.width > 0 && videoInfo.height > 0) {
                    append("${videoInfo.width}x${videoInfo.height}")
                }
                append(" • H.264 / AAC MP4")
                if (videoInfo.durationMs > 0) {
                    val sec = videoInfo.durationMs / 1000
                    append(" • ${sec / 60}m ${sec % 60}s")
                }
            }

            Result.success(
                VideoResult(
                    outputUri = finalUri,
                    outputFileName = outputName,
                    outputSizeBytes = finalSize,
                    durationMs = elapsed,
                    details = detailsText
                )
            )
        } catch (e: Exception) {
            try {
                if (tempOutputFile.exists()) tempOutputFile.delete()
                if (tempInputFile != null && tempInputFile.exists()) tempInputFile.delete()
            } catch (_: Exception) {}
            Result.failure(e)
        }
    }
}
