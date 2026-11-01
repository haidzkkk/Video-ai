package com.example.video_app_jc.data.repository

import com.example.video_app_jc.data.remote.VideoApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

class VideoRepositoryImpl @Inject constructor(
    private val api: VideoApi
) : VideoRepository {

    override fun generateVideo(
        imageBytes: ByteArray,
        fileName: String,
        prompt: String
    ): Flow<GenProgress> = flow {

        emit(GenProgress.Uploading)

        // 1) Upload ảnh dạng multipart
        val imagePart = MultipartBody.Part.createFormData(
            name = "image",
            filename = fileName,
            body = imageBytes.toRequestBody("image/*".toMediaTypeOrNull())
        )
        val promptBody = prompt.toRequestBody("text/plain".toMediaTypeOrNull())
        val created = api.createJob(imagePart, promptBody)

        // 2) Polling trạng thái job
        var attempt = 0
        while (attempt < MAX_ATTEMPTS) {
            val status = api.getJobStatus(created.jobId)
            when (status.status.uppercase()) {
                "SUCCEEDED" -> {
                    val url = status.videoUrl
                    if (url != null) emit(GenProgress.Done(url))
                    else emit(GenProgress.Failed("Server báo xong nhưng thiếu videoUrl"))
                    return@flow
                }

                "FAILED" -> {
                    emit(GenProgress.Failed(status.error ?: "Server xử lý thất bại"))
                    return@flow
                }

                else -> emit(GenProgress.Processing(status.progress)) // PENDING / PROCESSING
            }
            delay(POLL_INTERVAL_MS)
            attempt++
        }
        emit(GenProgress.Failed("Quá thời gian chờ (timeout)"))
    }

    companion object {
        private const val POLL_INTERVAL_MS = 2_000L
        private const val MAX_ATTEMPTS = 150 // 150 x 2s = 5 phút
    }
}
