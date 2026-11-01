package com.example.video_app_jc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.video_app_jc.ui.generate.GenerateScreen
import com.example.video_app_jc.ui.theme.Video_app_JCTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Video_app_JCTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    GenerateScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}
