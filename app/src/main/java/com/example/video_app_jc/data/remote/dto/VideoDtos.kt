package com.example.video_app_jc.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Các DTO ánh xạ JSON của server. ĐỔI field @SerialName cho khớp API thật của bạn.
 */

/** Response khi POST ảnh lên, server tạo job và trả về id. */
@Serializable
data class CreateJobResponse(
    @SerialName("job_id") val jobId: String,
    val status: String
)

/** Response khi GET trạng thái job (dùng cho polling). */
@Serializable
data class JobStatusResponse(
    @SerialName("job_id") val jobId: String,
    val status: String,                              // PENDING | PROCESSING | SUCCEEDED | FAILED
    val progress: Int = 0,                           // 0..100
    @SerialName("video_url") val videoUrl: String? = null,
    val error: String? = null
)
