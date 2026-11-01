# Hướng dẫn xây dựng App Gen Video AI (Image → Video) với Jetpack Compose

> File này viết để **vừa code vừa học**. Mỗi Phase là một mảnh độc lập, làm xong chạy 
thử rồi mới sang Phase sau.
> Setup hiện tại của project: AGP 9.4, Kotlin 2.2.10, Compose BOM 2026.02, `minSdk 24`, 
`targetSdk 37`, dùng version catalog (`gradle/libs.versions.toml`).
> Server: **HTTP REST thuần, không SSE/WebSocket → theo dõi tiến độ bằng POLLING.**

---

## 0. Mindset: từ XML + MVVM + MVRx sang Compose

Bạn giữ lại gần như toàn bộ kiến thức cũ. Chỉ đổi tầng View.

| Cũ (của bạn) | Mới (Compose) |
|---|---|
| ViewModel + MVVM | **Giữ nguyên** |
| MVRx: immutable state + `Async<T>` | → `StateFlow<UiState>` + `sealed interface` tự viết |
| `invalidate()` / subscribe state | → `collectAsStateWithLifecycle()` |
| XML + findViewById + ViewBinding | → hàm `@Composable` (Kotlin thuần) |
| RecyclerView + Adapter + DiffUtil | → `LazyColumn` / `LazyVerticalGrid` |
| Fragment + FragmentManager | → 1 Activity + `NavHost` (single-activity) |
| `notifyDataSetChanged` | → **recomposition tự động** khi state đổi |

**Câu thần chú:** `UI = f(state)`. Bạn không "update view" nữa, bạn chỉ "update state", UI tự vẽ lại.

Luồng app của chúng ta:
```
Chọn ảnh → Upload (multipart) → Server trả jobId
   → POLL GET /jobs/{id} mỗi 2-3s → PENDING/PROCESSING(progress%)
   → SUCCEEDED + videoUrl → phát bằng ExoPlayer → lưu ra gallery
```

---

## 1. PHASE 1 — Thêm dependencies

### 1a. Dán vào `gradle/libs.versions.toml`

> Version có thể cần bump lên bản mới nhất khi bạn code (search "maven <tên lib>"). Đây là mốc hợp lý.

Thêm vào `[versions]`:
```toml
# --- App stack ---
coroutines = "1.9.0"
lifecycleViewmodelCompose = "2.9.0"
navigationCompose = "2.8.5"
hilt = "2.52"
hiltNavigationCompose = "1.2.0"
ksp = "2.2.10-1.0.29"          # phải khớp Kotlin 2.2.10
retrofit = "2.11.0"
okhttp = "4.12.0"
kotlinxSerialization = "1.7.3"
retrofitSerializationConverter = "1.0.0"
coil = "2.7.0"
media3 = "1.5.1"
datastore = "1.1.1"
workManager = "2.10.0"
accompanistPermissions = "0.36.0"
```

Thêm vào `[libraries]`:
```toml
# Coroutines
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }

# ViewModel + Compose
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleViewmodelCompose" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycleViewmodelCompose" }

# Navigation
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }

# Hilt (DI)
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
androidx-hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNavigationCompose" }

# Network
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-logging = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
retrofit-serialization-converter = { group = "com.jakewharton.retrofit", name = "retrofit2-kotlinx-serialization-converter", version.ref = "retrofitSerializationConverter" }

# Image loading
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }

# Video playback
androidx-media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
androidx-media3-ui = { group = "androidx.media3", name = "media3-ui", version.ref = "media3" }

# Storage + background
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
androidx-work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "workManager" }

# Permissions
accompanist-permissions = { group = "com.google.accompanist", name = "accompanist-permissions", version.ref = "accompanistPermissions" }
```

Thêm vào `[plugins]`:
```toml
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

### 1b. Sửa `build.gradle.kts` (root — file ở thư mục gốc)
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // thêm 3 dòng:
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
```
> Lưu ý: project chỉ đang khai báo `android-application` và `kotlin-compose`. Bạn có thể cũng cần thêm plugin `org.jetbrains.kotlin.android` vào catalog nếu build báo thiếu — khi đó thêm:
> `kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }` và `alias(libs.plugins.kotlin.android) apply false`.

### 1c. Sửa `app/build.gradle.kts`
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)   // +
    alias(libs.plugins.ksp)                     // +
    alias(libs.plugins.hilt)                    // +
}
```
Thêm vào khối `dependencies { ... }`:
```kotlin
    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // ViewModel + lifecycle cho Compose
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Network
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.serialization.converter)

    // Image
    implementation(libs.coil.compose)

    // Video
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    // Storage + background
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    // Permissions
    implementation(libs.accompanist.permissions)
```

### 1d. `AndroidManifest.xml` — thêm quyền + tên Application
```xml
<uses-permission android:name="android.permission.INTERNET" />
<!-- notification cho WorkManager (Android 13+) -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```
Trong thẻ `<application android:name=".VideoApp" ...>` (tên class ở Phase 4).

✅ **Checkpoint:** Sync Gradle thành công, build chưa lỗi. Chưa cần viết code gì mới.

---

## 2. PHASE 2 — Cấu trúc package (MVVM phân tầng)

Tạo cây thư mục dưới `app/src/main/java/com/example/video_app_jc/`:
```
VideoApp.kt                 # Application + @HiltAndroidApp
MainActivity.kt             # 1 Activity duy nhất

core/
  Result.kt                 # sealed interface thay cho Async<T> của MVRx

data/
  remote/
    dto/                    # data class ánh xạ JSON (DTO)
    VideoApi.kt             # interface Retrofit
  repository/
    VideoRepository.kt      # interface
    VideoRepositoryImpl.kt  # logic upload + polling

di/
  NetworkModule.kt          # Hilt cung cấp Retrofit/OkHttp
  RepositoryModule.kt       # Hilt bind repository

ui/
  navigation/AppNav.kt
  generate/
    GenerateViewModel.kt
    GenerateUiState.kt
    GenerateScreen.kt
  player/
    VideoPlayer.kt          # composable bọc ExoPlayer
  theme/                    # đã có sẵn từ template
```
> Tư duy phân tầng giống hệt Clean/MVVM bên XML: **UI → ViewModel → Repository → Api**. Không có gì mới về kiến trúc.

---

## 3. PHASE 3 — Network layer (Retrofit + kotlinx.serialization)

### 3a. DTO — `data/remote/dto/`
Giả định API của bạn (đổi field cho khớp server thật):
```kotlin
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Response khi upload ảnh xong, server trả về job
@Serializable
data class CreateJobResponse(
    @SerialName("job_id") val jobId: String,
    val status: String
)

// Response khi poll trạng thái
@Serializable
data class JobStatusResponse(
    @SerialName("job_id") val jobId: String,
    val status: String,                    // PENDING | PROCESSING | SUCCEEDED | FAILED
    val progress: Int = 0,                 // 0..100
    @SerialName("video_url") val videoUrl: String? = null,
    val error: String? = null
)
```

### 3b. Api interface — `data/remote/VideoApi.kt`
```kotlin
import okhttp3.MultipartBody
import retrofit2.http.*

interface VideoApi {

    @Multipart
    @POST("api/v1/image-to-video")
    suspend fun createJob(
        @Part image: MultipartBody.Part,
        @Part("prompt") prompt: okhttp3.RequestBody   // nếu server cần prompt mô tả
    ): CreateJobResponse

    @GET("api/v1/jobs/{id}")
    suspend fun getJobStatus(@Path("id") jobId: String): JobStatusResponse
}
```
> `suspend fun` = Retrofit tự chạy trên coroutine, không cần callback. Đây là điểm sướng nhất so với code cũ.

✅ **Checkpoint:** Code compile. Chưa gọi được vì chưa có DI (Phase 4).

---

## 4. PHASE 4 — Dependency Injection với Hilt

### 4a. `VideoApp.kt`
```kotlin
import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class VideoApp : Application()
```
(Nhớ đã khai báo `android:name=".VideoApp"` trong Manifest ở Phase 1d.)

### 4b. `di/NetworkModule.kt`
```kotlin
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "https://your-server.com/"  // đổi URL server của bạn

    @Provides @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides @Singleton
    fun provideOkHttp(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()

    @Provides @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides @Singleton
    fun provideVideoApi(retrofit: Retrofit): VideoApi =
        retrofit.create(VideoApi::class.java)
}
```

### 4c. MainActivity cần `@AndroidEntryPoint`
```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() { ... }
```

✅ **Checkpoint:** Build chạy, app mở được (chưa có màn hình mới). Hilt đã sẵn sàng inject.

---

## 5. PHASE 5 — Repository + logic POLLING (quan trọng nhất)

Đây là "trái tim" của app. Polling = lặp gọi API status cho tới khi xong/lỗi/hết giờ.

### 5a. `core/Result.kt`
```kotlin
sealed interface AppResult<out T> {
    data object Loading : AppResult<Nothing>
    data class Success<T>(val data: T) : AppResult<T>
    data class Error(val message: String) : AppResult<Nothing>
}
```

### 5b. `data/repository/VideoRepository.kt`
```kotlin
import kotlinx.coroutines.flow.Flow

// Trạng thái tiến trình gen video, emit dần ra UI
sealed interface GenProgress {
    data object Uploading : GenProgress
    data class Processing(val percent: Int) : GenProgress
    data class Done(val videoUrl: String) : GenProgress
    data class Failed(val reason: String) : GenProgress
}

interface VideoRepository {
    fun generateVideo(imageBytes: ByteArray, fileName: String, prompt: String): Flow<GenProgress>
}
```
> `Flow<GenProgress>` thay cho `Async<T>` của MVRx — nhưng mạnh hơn: emit được nhiều lần (Uploading → Processing 10% → 40% → ... → Done). UI chỉ việc render giá trị mới nhất.

### 5c. `data/repository/VideoRepositoryImpl.kt`
```kotlin
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

        // 1) Upload ảnh (multipart)
        val imagePart = MultipartBody.Part.createFormData(
            name = "image",
            filename = fileName,
            body = imageBytes.toRequestBody("image/*".toMediaTypeOrNull())
        )
        val promptBody = prompt.toRequestBody("text/plain".toMediaTypeOrNull())
        val created = api.createJob(imagePart, promptBody)

        // 2) POLLING vòng lặp
        val maxAttempts = 150            // 150 x 2s = 5 phút timeout
        var attempt = 0
        while (attempt < maxAttempts) {
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
                else -> {   // PENDING / PROCESSING
                    emit(GenProgress.Processing(status.progress))
                }
            }
            delay(2000)      // chờ 2s rồi hỏi lại
            attempt++
        }
        emit(GenProgress.Failed("Quá thời gian chờ (timeout)"))
    }
}
```
**Điểm học:**
- `flow { emit(...) }` = tạo luồng dữ liệu "nóng dần". Mỗi `emit` là một state mới đẩy lên UI.
- `delay()` là hàm `suspend` — nó KHÔNG block thread (khác `Thread.sleep`). Đây là lý do coroutines thay thế được RxJava/callback.
- Nên bọc gọi mạng trong `try/catch` (lược bớt ở trên cho gọn) để convert exception → `GenProgress.Failed`.

### 5d. `di/RepositoryModule.kt`
```kotlin
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton
    abstract fun bindVideoRepository(impl: VideoRepositoryImpl): VideoRepository
}
```

✅ **Checkpoint:** Build OK. Có thể viết unit test cho repository nếu muốn (fake VideoApi).

---

## 6. PHASE 6 — UiState + ViewModel

### 6a. `ui/generate/GenerateUiState.kt`
```kotlin
import android.net.Uri

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
```
> Đây chính là "state class" kiểu MVRx — một `data class` bất biến, mọi thứ UI cần đều nằm trong đây.

### 6b. `ui/generate/GenerateViewModel.kt`
```kotlin
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GenerateViewModel @Inject constructor(
    private val repository: VideoRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(GenerateUiState())
    val uiState: StateFlow<GenerateUiState> = _uiState.asStateFlow()

    fun onImagePicked(uri: Uri?) = _uiState.update { it.copy(selectedImage = uri) }
    fun onPromptChange(text: String) = _uiState.update { it.copy(prompt = text) }

    fun generate() {
        val uri = _uiState.value.selectedImage ?: return

        viewModelScope.launch {
            // đọc bytes từ Uri (ảnh user chọn)
            val bytes = appContext.contentResolver.openInputStream(uri)
                ?.use { it.readBytes() } ?: return@launch

            repository.generateVideo(bytes, "input.jpg", _uiState.value.prompt)
                .collect { progress ->
                    when (progress) {
                        is GenProgress.Uploading -> _uiState.update {
                            it.copy(stage = GenerateUiState.Stage.Uploading, errorMessage = null)
                        }
                        is GenProgress.Processing -> _uiState.update {
                            it.copy(stage = GenerateUiState.Stage.Processing, progress = progress.percent)
                        }
                        is GenProgress.Done -> _uiState.update {
                            it.copy(stage = GenerateUiState.Stage.Done, videoUrl = progress.videoUrl)
                        }
                        is GenProgress.Failed -> _uiState.update {
                            it.copy(stage = GenerateUiState.Stage.Failed, errorMessage = progress.reason)
                        }
                    }
                }
        }
    }
}
```
**Điểm học:**
- `MutableStateFlow` + `_uiState.update { it.copy(...) }` = cách cập nhật state bất biến (giống `setState` của MVRx).
- `viewModelScope.launch` = coroutine tự hủy khi ViewModel chết (giống `disposable` của Rx nhưng tự động).
- `.collect { }` = lắng nghe từng emit của Flow polling.

---

## 7. PHASE 7 — Compose UI

### 7a. Chọn ảnh bằng Photo Picker (không cần xin quyền)
Trong `GenerateScreen.kt`:
```kotlin
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@Composable
fun rememberImagePicker(onPicked: (Uri?) -> Unit) =
    rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> onPicked(uri) }
// Gọi: launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
```

### 7b. Màn hình chính
```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage

@Composable
fun GenerateScreen(viewModel: GenerateViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val picker = rememberImagePicker { viewModel.onImagePicked(it) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Ảnh đã chọn
        AsyncImage(
            model = state.selectedImage,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().height(240.dp)
        )

        Button(onClick = {
            picker.launch(PickVisualMediaRequest(
                ActivityResultContracts.PickVisualMedia.ImageOnly
            ))
        }) { Text("Chọn ảnh") }

        OutlinedTextField(
            value = state.prompt,
            onValueChange = viewModel::onPromptChange,
            label = { Text("Mô tả (prompt)") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = viewModel::generate,
            enabled = state.canSubmit,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Tạo video") }

        // Hiển thị tiến độ theo state
        when (state.stage) {
            GenerateUiState.Stage.Uploading ->
                ProgressRow("Đang upload ảnh...", null)
            GenerateUiState.Stage.Processing ->
                ProgressRow("Đang xử lý... ${state.progress}%", state.progress / 100f)
            GenerateUiState.Stage.Failed ->
                Text("Lỗi: ${state.errorMessage}", color = MaterialTheme.colorScheme.error)
            GenerateUiState.Stage.Done ->
                state.videoUrl?.let { VideoPlayer(url = it, modifier = Modifier.fillMaxWidth().height(260.dp)) }
            GenerateUiState.Stage.Idle -> {}
        }
    }
}

@Composable
private fun ProgressRow(label: String, progress: Float?) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (progress == null) CircularProgressIndicator()
        else CircularProgressIndicator(progress = { progress })
        Text(label)
    }
}
```
**Điểm học:**
- `collectAsStateWithLifecycle()` = cầu nối Flow → Compose. State đổi → chỉ phần UI đọc state đó vẽ lại.
- `when (state.stage)` = UI điều khiển hoàn toàn bằng state. Không có dòng nào "ẩn/hiện view" thủ công.

### 7c. ExoPlayer trong Compose — `ui/player/VideoPlayer.kt`
```kotlin
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@Composable
fun VideoPlayer(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // Tạo player 1 lần, tự release khi composable biến mất
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    AndroidView(
        modifier = modifier,
        factory = { PlayerView(it).apply { this.player = player } }
    )
}
```
**Điểm học:**
- `AndroidView` = cách nhúng View cũ (PlayerView) vào Compose. Chỉ cần khi lib chưa có bản Composable.
- `DisposableEffect ... onDispose` = dọn tài nguyên (giống `onDestroy`), tránh leak player.

✅ **Checkpoint:** Chọn ảnh → bấm Tạo → thấy progress chạy → video phát. App chạy end-to-end!

---

## 8. PHASE 8 (tùy chọn) — Điều hướng nhiều màn hình

Nếu muốn tách màn Home / Lịch sử / Kết quả, dùng Navigation Compose.
```kotlin
import androidx.navigation.compose.*
import kotlinx.serialization.Serializable

@Serializable object GenerateRoute
@Serializable data class ResultRoute(val videoUrl: String)

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = GenerateRoute) {
        composable<GenerateRoute> { GenerateScreen(/* onDone = { nav.navigate(ResultRoute(it)) } */) }
        composable<ResultRoute> { entry ->
            val args = entry.toRoute<ResultRoute>()
            VideoPlayer(args.videoUrl)
        }
    }
}
```
> Nguyên tắc: truyền **id/url (dữ liệu nhỏ)** qua route, đừng truyền cả object lớn.

---

## 9. PHASE 9 (tùy chọn, nâng cao) — WorkManager để job sống khi thoát app

Vấn đề: gen video mất vài phút. Nếu user thoát app, `viewModelScope` bị hủy → polling dừng. Giải pháp: chuyển vòng polling vào một `CoroutineWorker`, và bắn notification khi xong.

Khi nào cần: khi bạn muốn user tắt app vẫn nhận được video. Ban đầu học thì **bỏ qua Phase này**, dùng polling trong ViewModel là đủ. Quay lại làm sau khi đã quen Compose.

Ý tưởng:
```kotlin
class VideoGenWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        // lặp gọi api.getJobStatus(...) y như repository
        // setProgress(workDataOf("percent" to p)) để báo tiến độ
        // xong → show notification + trả Result.success(workDataOf("url" to url))
    }
}
```

---

## 10. PHASE 10 (tùy chọn) — Lưu video về máy (gallery)

Dùng **MediaStore** (không cần quyền WRITE_EXTERNAL_STORAGE trên Android 10+):
```kotlin
val values = ContentValues().apply {
    put(MediaStore.Video.Media.DISPLAY_NAME, "gen_${System.currentTimeMillis()}.mp4")
    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/VideoAI")
}
val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)!!
// Tải video từ videoUrl (OkHttp) rồi copy stream vào contentResolver.openOutputStream(uri)
```

---

## Thứ tự học đề xuất

1. **Phase 1** (deps) → sync được là xong.
2. **Phase 2–4** (cấu trúc + network + Hilt) → chưa có UI nhưng nền tảng vững.
3. **Phase 5–7** → chạy được end-to-end. Đây là lúc "aha" về Compose + polling.
4. **Phase 8** khi cần nhiều màn.
5. **Phase 9–10** khi đã thoải mái, muốn app "thật" hơn.

## Mẹo khi học Compose
- Lỗi hay gặp: quên `collectAsStateWithLifecycle()` → UI không update. Nhớ: đọc state qua `by ... collectAsState...`.
- Dùng `@Preview` để xem UI không cần chạy máy ảo (tách composable "stateless" nhận tham số thuần để preview dễ).
- Đừng gọi API/logic trong composable body — mọi logic đặt ở ViewModel. Composable chỉ nhận state + bắn event lên.
- `remember { }` = giữ giá trị qua các lần recomposition (giống biến "sống" của màn hình).

---
*File hướng dẫn — cập nhật version & field API cho khớp server thật khi code.*
