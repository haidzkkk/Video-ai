package com.example.video_app_jc.data.repository

import kotlinx.coroutines.flow.Flow

/**
 * Trạng thái tiến trình gen video — emit dần ra UI trong suốt quá trình polling.
 */
sealed interface GenProgress {
    data object Uploading : GenProgress
    data class Processing(val percent: Int) : GenProgress
    data class Done(val videoUrl: String) : GenProgress
    data class Failed(val reason: String) : GenProgress
}

interface VideoRepository {
    /**
     * Upload ảnh rồi poll trạng thái tới khi xong/lỗi/timeout.
     * @param imageBytes nội dung ảnh người dùng chọn
     * @param fileName tên file gửi lên (vd "input.jpg")
     * @param prompt mô tả cho server (có thể để rỗng)
     */
    fun generateVideo(imageBytes: ByteArray, fileName: String, prompt: String): Flow<GenProgress>
}
