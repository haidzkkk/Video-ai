package com.example.video_app_jc.ui.generate

import android.net.Uri

/**
 * Single source of truth cho màn Generate. Bất biến — ViewModel tạo bản copy mới mỗi lần đổi.
 */
data class GenerateUiState(
    val selectedImage: Uri? = null,
    val prompt: String = "",
    val stage: Stage = Stage.Idle,
    val progress: Int = 0,
    val videoUrl: String? = null,
    val errorMessage: String? = null
) {
    enum class Stage { Idle, Uploading, Processing, Done, Failed }

    val isBusy: Boolean get() = stage == Stage.Uploading || stage == Stage.Processing
    val canSubmit: Boolean get() = selectedImage != null && !isBusy
}
