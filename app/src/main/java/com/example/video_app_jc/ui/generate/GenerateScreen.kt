package com.example.video_app_jc.ui.generate

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.video_app_jc.ui.player.VideoPlayer

@Composable
fun GenerateScreen(
    modifier: Modifier = Modifier,
    viewModel: GenerateViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> viewModel.onImagePicked(uri) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (state.selectedImage != null) {
            AsyncImage(
                model = state.selectedImage,
                contentDescription = "Ảnh đã chọn",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            )
        }

        Button(
            onClick = {
                picker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            enabled = !state.isBusy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.selectedImage == null) "Chọn ảnh" else "Chọn ảnh khác")
        }

        OutlinedTextField(
            value = state.prompt,
            onValueChange = viewModel::onPromptChange,
            label = { Text("Mô tả (prompt)") },
            enabled = !state.isBusy,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = viewModel::generate,
            enabled = state.canSubmit,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Tạo video")
        }

        when (state.stage) {
            GenerateUiState.Stage.Uploading ->
                ProgressRow(label = "Đang upload ảnh...", progress = null)

            GenerateUiState.Stage.Processing ->
                ProgressRow(
                    label = "Đang xử lý... ${state.progress}%",
                    progress = state.progress / 100f
                )

            GenerateUiState.Stage.Failed ->
                Text(
                    text = "Lỗi: ${state.errorMessage}",
                    color = MaterialTheme.colorScheme.error
                )

            GenerateUiState.Stage.Done ->
                state.videoUrl?.let { url ->
                    VideoPlayer(
                        url = url,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                    )
                }

            GenerateUiState.Stage.Idle -> Unit
        }
    }
}

@Composable
private fun ProgressRow(label: String, progress: Float?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (progress == null) {
            CircularProgressIndicator()
        } else {
            CircularProgressIndicator(progress = { progress })
        }
        Text(label)
    }
}
