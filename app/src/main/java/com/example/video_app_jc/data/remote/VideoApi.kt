package com.example.video_app_jc.data.remote

import com.example.video_app_jc.data.remote.dto.CreateJobResponse
import com.example.video_app_jc.data.remote.dto.JobStatusResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

/**
 * Interface Retrofit. Mỗi hàm = 1 endpoint. ĐỔI path cho khớp server thật.
 * Dùng `suspend` nên Retrofit tự chạy trên coroutine, không cần callback.
 */
interface VideoApi {

    @Multipart
    @POST("api/v1/image-to-video")
    suspend fun createJob(
        @Part image: MultipartBody.Part,
        @Part("prompt") prompt: RequestBody
    ): CreateJobResponse

    @GET("api/v1/jobs/{id}")
    suspend fun getJobStatus(@Path("id") jobId: String): JobStatusResponse
}
