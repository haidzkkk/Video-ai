package com.example.video_app_jc.data.auth

interface AuthRepository {
    /** Đăng nhập. Hiện đang FAKE: delay rồi trả về true. */
    suspend fun login(): Boolean
}
