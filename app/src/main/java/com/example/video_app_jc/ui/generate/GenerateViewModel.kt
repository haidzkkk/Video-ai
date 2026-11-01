package com.example.video_app_jc.ui.generate

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.video_app_jc.data.repository.GenProgress
import com.example.video_app_jc.data.repository.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class GenerateViewModel @Inject constructor(
    private val repository: VideoRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(GenerateUiState())
    val uiState: StateFlow<GenerateUiState> = _uiState.asStateFlow()

    fun onImagePicked(uri: Uri?) = _uiState.update { it.copy(selectedImage = uri) }

    fun onPromptChange(text: String) = _uiState.update { it.copy(prompt = text) }

    fun generate() {
        val uri = _uiState.value.selectedImage ?: return

        viewModelScope.launch {
            // Đọc bytes từ Uri trên IO dispatcher (tránh block main thread)
            val bytes = withContext(Dispatchers.IO) {
                appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
            if (bytes == null) {
                _uiState.update {
                    it.copy(
                        stage = GenerateUiState.Stage.Failed,
                        errorMessage = "Không đọc được ảnh đã chọn"
                    )
                }
                return@launch
            }

            repository.generateVideo(bytes, "input.jpg", _uiState.value.prompt)
                .catch { e ->
                    _uiState.update {
                        it.copy(
                            stage = GenerateUiState.Stage.Failed,
                            errorMessage = e.message ?: "Lỗi mạng"
                        )
                    }
                }
                .collect { progress -> _uiState.update { it.reduce(progress) } }
        }
    }

    /** Gộp 1 GenProgress vào state hiện tại. */
    private fun GenerateUiState.reduce(progress: GenProgress): GenerateUiState = when (progress) {
        is GenProgress.Uploading -> copy(
            stage = GenerateUiState.Stage.Uploading,
            errorMessage = null
        )

        is GenProgress.Processing -> copy(
            stage = GenerateUiState.Stage.Processing,
            progress = progress.percent
        )

        is GenProgress.Done -> copy(
            stage = GenerateUiState.Stage.Done,
            videoUrl = progress.videoUrl
        )

        is GenProgress.Failed -> copy(
            stage = GenerateUiState.Stage.Failed,
            errorMessage = progress.reason
        )
    }
}
