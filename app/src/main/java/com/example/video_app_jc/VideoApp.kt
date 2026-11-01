package com.example.video_app_jc

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class — điểm khởi tạo của Hilt cho toàn app.
 * Đã khai báo android:name=".VideoApp" trong AndroidManifest.xml.
 */
@HiltAndroidApp
class VideoApp : Application()
