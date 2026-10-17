package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.converter.PhotoConverter
import com.example.converter.VideoConverter
import com.example.model.ConversionJob
import com.example.model.ConversionSettings
import com.example.model.JobStatus
import com.example.model.MediaType
import com.example.model.VideoQualityPreset
import com.example.storage.SafFileManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ConverterUiState(
    val queue: List<ConversionJob> = emptyList(),
    val history: List<ConversionJob> = emptyList(),
    val activeJobId: String? = null,
    val isProcessing: Boolean = false,
    val isPaused: Boolean = false,
    val settings: ConversionSettings = ConversionSettings(),
    val currentBatchOverallProgress: Float = 0f,
    val toastMessage: String? = null
)

class ConverterViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ConverterUiState())
    val uiState: StateFlow<ConverterUiState> = _uiState.asStateFlow()

    private var batchJob: Job? = null
    private var activeTranscodeSession: VideoConverter.ActiveTranscodeSession? = null

    init {
        loadPersistedFolder()
    }

    private fun loadPersistedFolder() {
        val prefs = getApplication<Application>().getSharedPreferences("private_media_prefs", Context.MODE_PRIVATE)
        val folderUri = prefs.getString("output_folder_uri", null)
        val folderName = prefs.getString("output_folder_name", null)
        if (folderUri != null) {
            _uiState.update { state ->
                state.copy(
                    settings = state.settings.copy(
                        outputFolderUriString = folderUri,
                        outputFolderName = folderName
                    )
                )
            }
        }
    }

    fun setOutputFolder(uri: Uri, folderName: String?) {
        val uriString = uri.toString()
        val prefs = getApplication<Application>().getSharedPreferences("private_media_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("output_folder_uri", uriString)
            .putString("output_folder_name", folderName)
            .apply()

        _uiState.update { state ->
            state.copy(
                settings = state.settings.copy(
                    outputFolderUriString = uriString,
                    outputFolderName = folderName
                ),
                toastMessage = "Output folder set: ${folderName ?: "Selected folder"}"
            )
        }
    }

    fun clearOutputFolder() {
        val prefs = getApplication<Application>().getSharedPreferences("private_media_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("output_folder_uri").remove("output_folder_name").apply()
        _uiState.update { state ->
            state.copy(
                settings = state.settings.copy(
                    outputFolderUriString = null,
                    outputFolderName = null
                ),
                toastMessage = "Output reset to default storage"
            )
        }
    }

    fun updateJpegQuality(quality: Int) {
        _uiState.update { state ->
            state.copy(settings = state.settings.copy(jpegQuality = quality))
        }
    }

    fun updateVideoPreset(preset: VideoQualityPreset) {
        _uiState.update { state ->
            state.copy(settings = state.settings.copy(videoPreset = preset))
        }
    }

    fun toggleStripGps(enabled: Boolean) {
        _uiState.update { state ->
            state.copy(settings = state.settings.copy(stripLocationMetadata = enabled))
        }
    }

    fun addMediaUris(uris: List<Uri>, forcedType: MediaType? = null) {
        val context = getApplication<Application>()
        val newJobs = uris.map { uri ->
            val sourceItem = SafFileManager.queryMediaItem(context, uri, forcedType)
            ConversionJob(
                id = UUID.randomUUID().toString(),
                source = sourceItem,
                status = JobStatus.QUEUED
            )
        }

        _uiState.update { state ->
            state.copy(
                queue = state.queue + newJobs,
                toastMessage = "Added ${newJobs.size} item(s) to queue"
            )
        }

        // Auto-start queue if not already processing
        if (!_uiState.value.isProcessing && !_uiState.value.isPaused) {
            startBatch()
        }
    }

    fun startBatch() {
        if (_uiState.value.isProcessing) return

        batchJob = viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, isPaused = false) }
            val context = getApplication<Application>()

            while (true) {
                if (_uiState.value.isPaused) break

                val nextJob = _uiState.value.queue.firstOrNull { it.status == JobStatus.QUEUED }
                if (nextJob == null) {
                    // All queue items processed
                    break
                }

                processSingleJob(context, nextJob.id)
                updateOverallProgress()
            }

            _uiState.update { it.copy(isProcessing = false, activeJobId = null) }
        }
    }

    private suspend fun processSingleJob(context: Context, jobId: String) {
        _uiState.update { state ->
            state.copy(
                activeJobId = jobId,
                queue = state.queue.map { if (it.id == jobId) it.copy(status = JobStatus.PROCESSING, progress = 0.05f) else it }
            )
        }

        val job = _uiState.value.queue.find { it.id == jobId } ?: return
        val settings = _uiState.value.settings
        val targetFolderUri = settings.outputFolderUriString?.let { Uri.parse(it) }

        if (job.source.mediaType == MediaType.PHOTO) {
            val result = PhotoConverter.convert(
                context = context,
                source = job.source,
                settings = settings,
                targetFolderUri = targetFolderUri,
                onProgress = { p -> updateJobProgress(jobId, p) }
            )

            result.fold(
                onSuccess = { res ->
                    markJobCompleted(jobId, res.outputUri, res.outputFileName, res.outputSizeBytes, res.durationMs, res.details)
                },
                onFailure = { err ->
                    markJobFailed(jobId, err.message ?: "Photo conversion failed")
                }
            )
        } else {
            val result = VideoConverter.convert(
                context = context,
                source = job.source,
                settings = settings,
                targetFolderUri = targetFolderUri,
                onProgress = { p -> updateJobProgress(jobId, p) },
                onSessionCreated = { session -> activeTranscodeSession = session }
            )

            activeTranscodeSession = null

            result.fold(
                onSuccess = { res ->
                    markJobCompleted(jobId, res.outputUri, res.outputFileName, res.outputSizeBytes, res.durationMs, res.details)
                },
                onFailure = { err ->
                    markJobFailed(jobId, err.message ?: "Video conversion failed")
                }
            )
        }
    }

    private fun updateJobProgress(jobId: String, progress: Float) {
        _uiState.update { state ->
            state.copy(
                queue = state.queue.map {
                    if (it.id == jobId) it.copy(progress = progress) else it
                }
            )
        }
        updateOverallProgress()
    }

    private fun markJobCompleted(
        jobId: String,
        outputUri: Uri,
        fileName: String,
        outputBytes: Long,
        duration: Long,
        details: String
    ) {
        _uiState.update { state ->
            var completedJob: ConversionJob? = null
            val updatedQueue = state.queue.map {
                if (it.id == jobId) {
                    val updated = it.copy(
                        status = JobStatus.COMPLETED,
                        progress = 1.0f,
                        outputUri = outputUri,
                        outputFileName = fileName,
                        outputSizeBytes = outputBytes,
                        durationMs = duration,
                        details = details
                    )
                    completedJob = updated
                    updated
                } else it
            }

            state.copy(
                queue = updatedQueue,
                history = if (completedJob != null) listOf(completedJob!!) + state.history else state.history
            )
        }
    }

    private fun markJobFailed(jobId: String, errorMsg: String) {
        _uiState.update { state ->
            state.copy(
                queue = state.queue.map {
                    if (it.id == jobId) it.copy(status = JobStatus.FAILED, errorMessage = errorMsg) else it
                }
            )
        }
    }

    fun pauseBatch() {
        _uiState.update { it.copy(isPaused = true) }
        activeTranscodeSession?.cancel()
        activeTranscodeSession = null
        batchJob?.cancel()
        _uiState.update { it.copy(isProcessing = false) }
    }

    fun cancelActiveJob() {
        val activeId = _uiState.value.activeJobId
        activeTranscodeSession?.cancel()
        activeTranscodeSession = null
        if (activeId != null) {
            _uiState.update { state ->
                state.copy(
                    queue = state.queue.map {
                        if (it.id == activeId) it.copy(status = JobStatus.CANCELLED) else it
                    }
                )
            }
        }
    }

    fun cancelAll() {
        activeTranscodeSession?.cancel()
        activeTranscodeSession = null
        batchJob?.cancel()
        _uiState.update { state ->
            state.copy(
                isProcessing = false,
                isPaused = false,
                activeJobId = null,
                queue = state.queue.map {
                    if (it.status == JobStatus.QUEUED || it.status == JobStatus.PROCESSING) {
                        it.copy(status = JobStatus.CANCELLED)
                    } else it
                }
            )
        }
    }

    fun retryJob(jobId: String) {
        _uiState.update { state ->
            state.copy(
                queue = state.queue.map {
                    if (it.id == jobId) it.copy(status = JobStatus.QUEUED, progress = 0f, errorMessage = null) else it
                }
            )
        }
        if (!_uiState.value.isProcessing) {
            startBatch()
        }
    }

    fun removeJob(jobId: String) {
        _uiState.update { state ->
            state.copy(queue = state.queue.filterNot { it.id == jobId })
        }
    }

    fun clearCompleted() {
        _uiState.update { state ->
            state.copy(
                queue = state.queue.filter { it.status == JobStatus.QUEUED || it.status == JobStatus.PROCESSING }
            )
        }
    }

    fun clearHistory() {
        _uiState.update { it.copy(history = emptyList(), toastMessage = "History cleared") }
    }

    private fun updateOverallProgress() {
        val total = _uiState.value.queue.size
        if (total == 0) {
            _uiState.update { it.copy(currentBatchOverallProgress = 0f) }
            return
        }
        val sumProgress = _uiState.value.queue.sumOf {
            when (it.status) {
                JobStatus.COMPLETED -> 1.0
                JobStatus.PROCESSING -> it.progress.toDouble()
                else -> 0.0
            }
        }
        _uiState.update { it.copy(currentBatchOverallProgress = (sumProgress / total).toFloat()) }
    }

    fun dismissToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }
}
