package com.example

import android.accounts.Account
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

enum class AspectRatioMode(val displayName: String, val ratioValue: Float) {
    RATIO_4_3("4:3", 4f / 3f),
    RATIO_16_9("16:9", 16f / 9f),
    RATIO_1_1("1:1", 1f),
    RATIO_9_16("9:16", 9f / 16f)
}

enum class AppScreen {
    CAMERA,
    ALBUM,
    EDITOR
}

class CameraViewModel : ViewModel() {

    private val _photos = MutableStateFlow<List<File>>(emptyList())
    val photos: StateFlow<List<File>> = _photos.asStateFlow()

    private val _currentScreen = MutableStateFlow(AppScreen.CAMERA)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _driveAccessToken = MutableStateFlow("")
    val driveAccessToken: StateFlow<String> = _driveAccessToken.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private val _lensFacing = MutableStateFlow(CameraSelector.LENS_FACING_BACK)
    val lensFacing: StateFlow<Int> = _lensFacing.asStateFlow()

    private val _flashMode = MutableStateFlow(ImageCapture.FLASH_MODE_OFF)
    val flashMode: StateFlow<Int> = _flashMode.asStateFlow()

    private val _aspectRatio = MutableStateFlow(AspectRatioMode.RATIO_4_3)
    val aspectRatio: StateFlow<AspectRatioMode> = _aspectRatio.asStateFlow()

    private val _isGridVisible = MutableStateFlow(true)
    val isGridVisible: StateFlow<Boolean> = _isGridVisible.asStateFlow()

    private val _zoomRatio = MutableStateFlow(1f)
    val zoomRatio: StateFlow<Float> = _zoomRatio.asStateFlow()

    private val _timerSeconds = MutableStateFlow(0) // 0, 3, 10
    val timerSeconds: StateFlow<Int> = _timerSeconds.asStateFlow()

    private val _isCountdownRunning = MutableStateFlow(false)
    val isCountdownRunning: StateFlow<Boolean> = _isCountdownRunning.asStateFlow()

    private val _countdownRemaining = MutableStateFlow(0)
    val countdownRemaining: StateFlow<Int> = _countdownRemaining.asStateFlow()

    // Active full screen photo modal
    private val _selectedPhoto = MutableStateFlow<File?>(null)
    val selectedPhoto: StateFlow<File?> = _selectedPhoto.asStateFlow()

    private val _selectedFilter = MutableStateFlow(FilterType.ORIGINAL)
    val selectedFilter: StateFlow<FilterType> = _selectedFilter.asStateFlow()

    // Trigger local screen shutter flash animation in flow
    private val _shutterFlashChannel = MutableSharedFlow<Unit>()
    val shutterFlashChannel: SharedFlow<Unit> = _shutterFlashChannel.asSharedFlow()

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun saveDriveToken(context: Context, token: String) {
        _driveAccessToken.value = token
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("drive_access_token", token).apply()
    }

    fun retrieveAndSaveDriveToken(
        context: Context, 
        signInAccount: GoogleSignInAccount, 
        onRecoverableException: (android.content.Intent) -> Unit,
        onComplete: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val email = signInAccount.email ?: ""
                if (email.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        onComplete(false, "无法获取绑定的 Google 邮箱地址")
                    }
                    return@launch
                }
                
                // Construct standard Google account
                val account = Account(email, "com.google")
                val scope = "oauth2:https://www.googleapis.com/auth/drive"
                
                // Clear any existing cached token first to prevent expired tokens
                try {
                    val existingToken = _driveAccessToken.value
                    if (existingToken.isNotEmpty()) {
                        GoogleAuthUtil.clearToken(context, existingToken)
                    }
                } catch (ignored: Exception) {}

                val token = GoogleAuthUtil.getToken(context, account, scope)
                if (token != null) {
                    withContext(Dispatchers.Main) {
                        saveDriveToken(context, token)
                        onComplete(true, "谷歌账号授权成功！已绑定 $email")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onComplete(false, "未能获取有效的 Google Access Token")
                    }
                }
            } catch (e: UserRecoverableAuthException) {
                withContext(Dispatchers.Main) {
                    val recoveryIntent = e.intent
                    if (recoveryIntent != null) {
                        onRecoverableException(recoveryIntent)
                    } else {
                        onComplete(false, "谷歌账号安全认证需要进一步操作，但未能获取到验证页面")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onComplete(false, "谷歌账号授权失败: ${e.message}")
                }
            }
        }
    }

    fun loadDriveToken(context: Context) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        _driveAccessToken.value = prefs.getString("drive_access_token", "") ?: ""
    }

    fun uploadToDrive(context: Context, file: File, onResult: (Boolean, String) -> Unit) {
        val token = _driveAccessToken.value
        if (token.isEmpty()) {
            onResult(false, "请先在相册中设置 Google Drive 授权 Token！")
            return
        }
        _isUploading.value = true
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!file.exists()) {
                    withContext(Dispatchers.Main) {
                        _isUploading.value = false
                        onResult(false, "上传失败：本地照片文件不存在")
                    }
                    return@launch
                }
                
                GoogleDriveService.uploadPhoto(
                    file = file,
                    accessToken = token,
                    onSuccess = { fileId ->
                        viewModelScope.launch(Dispatchers.Main) {
                            _isUploading.value = false
                            onResult(true, "上传 Google Drive 成功！文件 id: $fileId")
                        }
                    },
                    onError = { exception ->
                        viewModelScope.launch(Dispatchers.Main) {
                            _isUploading.value = false
                            onResult(false, "上传 Google Drive 失败: ${exception.message}")
                        }
                    }
                )
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isUploading.value = false
                    onResult(false, "启动上传时遇到错误: ${e.message}")
                }
            }
        }
    }

    fun loadLocalPhotos(context: Context) {
        loadDriveToken(context)
        viewModelScope.launch(Dispatchers.IO) {
            val dir = File(context.filesDir, "captures")
            if (!dir.exists()) dir.mkdirs()
            val files = dir.listFiles { file ->
                val ext = file.extension.lowercase()
                ext == "jpg" || ext == "jpeg"
            }?.sortedByDescending { it.lastModified() } ?: emptyList()
            _photos.value = files
        }
    }

    fun toggleLens() {
        _lensFacing.value = if (_lensFacing.value == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
    }

    fun toggleFlash() {
        _flashMode.value = when (_flashMode.value) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
            else -> ImageCapture.FLASH_MODE_OFF
        }
    }

    fun setAspectRatio(mode: AspectRatioMode) {
        _aspectRatio.value = mode
    }

    fun toggleGrid() {
        _isGridVisible.value = !_isGridVisible.value
    }

    fun setZoom(ratio: Float) {
        _zoomRatio.value = ratio.coerceIn(1f, 8f)
    }

    fun toggleTimer() {
        _timerSeconds.value = when (_timerSeconds.value) {
            0 -> 3
            3 -> 10
            else -> 0
        }
    }

    fun selectPhoto(file: File?) {
        _selectedPhoto.value = file
        // Reset filter to Original when opening a photo
        _selectedFilter.value = FilterType.ORIGINAL
    }

    fun selectFilter(filter: FilterType) {
        _selectedFilter.value = filter
    }

    fun triggerShutterFlash() {
        viewModelScope.launch {
            _shutterFlashChannel.emit(Unit)
        }
    }

    fun startTimerCountdown(onFinished: () -> Unit) {
        val seconds = _timerSeconds.value
        if (seconds == 0) {
            onFinished()
            return
        }

        viewModelScope.launch {
            _isCountdownRunning.value = true
            _countdownRemaining.value = seconds
            while (_countdownRemaining.value > 0) {
                delay(1000)
                _countdownRemaining.value -= 1
            }
            _isCountdownRunning.value = false
            onFinished()
        }
    }

    fun addCapturedPhoto(context: Context, file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            // Re-load to append and update UI flow
            loadLocalPhotos(context)
        }
    }

    fun deletePhoto(context: Context, file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            if (file.exists()) {
                file.delete()
            }
            loadLocalPhotos(context)
            if (_selectedPhoto.value?.absolutePath == file.absolutePath) {
                withContext(Dispatchers.Main) {
                    _selectedPhoto.value = null
                }
            }
        }
    }

    fun savePhotoToPublicGallery(
        context: Context,
        file: File,
        filter: FilterType,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val originalBitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (originalBitmap == null) {
                    withContext(Dispatchers.Main) { onResult(false, "解码原始图片失败") }
                    return@launch
                }

                val isFrontPhoto = file.name.startsWith("IMG_FRONT_")

                // Apply rotation metadata if necessary, or load straight
                val mutableBitmap = Bitmap.createBitmap(
                    originalBitmap.width,
                    originalBitmap.height,
                    Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(mutableBitmap)
                val paint = android.graphics.Paint()

                if (filter != FilterType.ORIGINAL) {
                    val androidMatrix = android.graphics.ColorMatrix(filter.getAndroidMatrixValues())
                    paint.colorFilter = android.graphics.ColorMatrixColorFilter(androidMatrix)
                }

                if (isFrontPhoto) {
                    val mirrorMatrix = android.graphics.Matrix().apply {
                        postScale(-1f, 1f)
                        postTranslate(originalBitmap.width.toFloat(), 0f)
                    }
                    canvas.drawBitmap(originalBitmap, mirrorMatrix, paint)
                } else {
                    canvas.drawBitmap(originalBitmap, 0f, 0f, paint)
                }

                val resolver = context.contentResolver
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
                val displayName = "Camera_${timeStamp}.jpg"

                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Camera")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }

                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }

                val uri = resolver.insert(collection, contentValues)
                if (uri == null) {
                    withContext(Dispatchers.Main) { onResult(false, "创建媒体存储路径失败") }
                    return@launch
                }

                resolver.openOutputStream(uri).use { outputStream ->
                    if (outputStream == null) {
                        withContext(Dispatchers.Main) { onResult(false, "打开媒体写入流失败") }
                        return@use
                    }
                    val success = mutableBitmap.compress(Bitmap.CompressFormat.JPEG, 97, outputStream)
                    if (!success) {
                        withContext(Dispatchers.Main) { onResult(false, "JPEG图片压缩失败") }
                        return@use
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }

                originalBitmap.recycle()
                mutableBitmap.recycle()

                withContext(Dispatchers.Main) {
                    onResult(true, "照片已成功保存至系统相册！")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onResult(false, "保存遇到错误: ${e.message}")
                }
            }
        }
    }
}
