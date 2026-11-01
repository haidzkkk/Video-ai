package com.example.video_app_jc.data.auth

import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * Hiện FAKE: giả lập gọi API đăng nhập mất 3s rồi trả về thành công.
 * Sau này thay bằng gọi API thật.
 */
class AuthRepositoryImpl @Inject constructor() : AuthRepository {
    override suspend fun login(): Boolean {
        delay(3_000)
        return true
    }
}
