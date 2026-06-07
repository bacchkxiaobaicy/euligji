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
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.media.FaceDetector
import android.media.ExifInterface

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

    // --- BEAUTY CONTROL STATES ---
    private val _isBeautyEnabled = MutableStateFlow(true)
    val isBeautyEnabled: StateFlow<Boolean> = _isBeautyEnabled.asStateFlow()

    private val _beautySmooth = MutableStateFlow(0.40f) // 磨皮 (Default 40%)
    val beautySmooth: StateFlow<Float> = _beautySmooth.asStateFlow()

    private val _beautySlim = MutableStateFlow(0.35f) // 瘦脸 (Default 35%)
    val beautySlim: StateFlow<Float> = _beautySlim.asStateFlow()

    private val _beautyBigEyes = MutableStateFlow(0.30f) // 大眼 (Default 30%)
    val beautyBigEyes: StateFlow<Float> = _beautyBigEyes.asStateFlow()

    private val _beautyWhiten = MutableStateFlow(0.25f) // 美白 (Default 25%)
    val beautyWhiten: StateFlow<Float> = _beautyWhiten.asStateFlow()

    private val _beautyRuddy = MutableStateFlow(0.20f) // 红润 (Default 20%)
    val beautyRuddy: StateFlow<Float> = _beautyRuddy.asStateFlow()

    fun setBeautyEnabled(enabled: Boolean) {
        _isBeautyEnabled.value = enabled
    }

    fun setBeautySmooth(value: Float) {
        _beautySmooth.value = value.coerceIn(0f, 1f)
    }

    fun setBeautySlim(value: Float) {
        _beautySlim.value = value.coerceIn(0f, 1f)
    }

    fun setBeautyBigEyes(value: Float) {
        _beautyBigEyes.value = value.coerceIn(0f, 1f)
    }

    fun setBeautyWhiten(value: Float) {
        _beautyWhiten.value = value.coerceIn(0f, 1f)
    }

    fun setBeautyRuddy(value: Float) {
        _beautyRuddy.value = value.coerceIn(0f, 1f)
    }

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
                val scope = "oauth2:https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/drive"
                
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
            try {
                processCapturedPhoto(context, file)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            // Re-load to append and update UI flow
            loadLocalPhotos(context)
        }
    }

    private fun processCapturedPhoto(context: Context, file: File) {
        if (!file.exists()) return

        // 1. Load raw bitmap
        val originalBitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return

        try {
            // 2. Resolve target device rotation & front camera horizontal flip
            val isFrontPhoto = file.name.startsWith("IMG_FRONT_")
            val exifInterface = try {
                ExifInterface(file.absolutePath)
            } catch (e: Exception) {
                null
            }
            val orientation = exifInterface?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                ?: ExifInterface.ORIENTATION_NORMAL

            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }

            val matrix = Matrix()
            if (degrees != 0) {
                matrix.postRotate(degrees.toFloat())
            }
            if (isFrontPhoto) {
                // Physical horizontal mirroring
                matrix.postScale(-1f, 1f)
            }

            val rotatedBitmap = Bitmap.createBitmap(
                originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true
            )
            originalBitmap.recycle()

            // 3. Apply beauty pipeline if enabled
            val isBeauty = _isBeautyEnabled.value
            val smoothVal = if (isBeauty) _beautySmooth.value else 0f
            val slimVal = if (isBeauty) _beautySlim.value else 0f
            val eyesVal = if (isBeauty) _beautyBigEyes.value else 0f
            val whitenVal = if (isBeauty) _beautyWhiten.value else 0f
            val rosyVal = if (isBeauty) _beautyRuddy.value else 0f

            // Step A: Face Slimming & Big Eyes (Mesh Deform)
            val warpedBitmap = if (slimVal > 0.01f || eyesVal > 0.01f) {
                // FaceDetector requires width to be even
                val targetWidth = if (rotatedBitmap.width % 2 == 0) rotatedBitmap.width else rotatedBitmap.width - 1
                val targetHeight = rotatedBitmap.height
                val bitmapEven = if (targetWidth != rotatedBitmap.width) {
                    Bitmap.createBitmap(rotatedBitmap, 0, 0, targetWidth, targetHeight)
                } else {
                    rotatedBitmap
                }
                
                val bitmap565 = bitmapEven.copy(Bitmap.Config.RGB_565, true)
                val maxFaces = 3
                val faceDetector = FaceDetector(bitmapEven.width, bitmapEven.height, maxFaces)
                val faces = arrayOfNulls<FaceDetector.Face>(maxFaces)
                val numFaces = faceDetector.findFaces(bitmap565, faces)
                bitmap565.recycle()

                val result = if (numFaces > 0) {
                    applyFaceWarp(bitmapEven, faces, numFaces, slimVal, eyesVal)
                } else {
                    applyDefaultPortraitWarp(bitmapEven, slimVal)
                }

                if (bitmapEven != rotatedBitmap) {
                    bitmapEven.recycle()
                }
                result
            } else {
                rotatedBitmap.copy(rotatedBitmap.config ?: Bitmap.Config.ARGB_8888, true)
            }
            rotatedBitmap.recycle()

            // Step B: Skin Smoothing / 磨皮 (Selective Blur)
            val smoothedBitmap = if (smoothVal > 0.01f) {
                applySkinSmoothing(warpedBitmap, smoothVal)
            } else {
                warpedBitmap.copy(warpedBitmap.config ?: Bitmap.Config.ARGB_8888, true)
            }
            warpedBitmap.recycle()

            // Step C: Skin Whitening & Rosy Hue / 美白红润 (Color Curves adaptation)
            val finalBitmap = if (whitenVal > 0.01f || rosyVal > 0.01f) {
                applyColorBeauty(smoothedBitmap, whitenVal, rosyVal)
            } else {
                smoothedBitmap.copy(smoothedBitmap.config ?: Bitmap.Config.ARGB_8888, true)
            }
            smoothedBitmap.recycle()

            // 4. Compress back and overwrite the physical file
            FileOutputStream(file).use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 97, out)
            }
            finalBitmap.recycle()

            // 5. Update exif orientation to normal (since we have already burned in the physical rotation)
            try {
                val finalExif = ExifInterface(file.absolutePath)
                finalExif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                finalExif.saveAttributes()
            } catch (ignored: Exception) {}

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applyFaceWarp(
        src: Bitmap,
        faces: Array<FaceDetector.Face?>,
        numFaces: Int,
        slimStrength: Float,
        bigEyesStrength: Float
    ): Bitmap {
        val width = src.width
        val height = src.height

        val meshWidth = 30
        val meshHeight = 30
        val numVertices = (meshWidth + 1) * (meshHeight + 1)
        val verts = FloatArray(numVertices * 2)

        // Generate clean original vertices
        var index = 0
        for (y in 0..meshHeight) {
            val fy = y.toFloat() / meshHeight * height
            for (x in 0..meshWidth) {
                val fx = x.toFloat() / meshWidth * width
                verts[index++] = fx
                verts[index++] = fy
            }
        }

        // Deform vertices for each detected face
        for (i in 0 until numFaces) {
            val face = faces[i] ?: continue
            val midPoint = PointF()
            face.getMidPoint(midPoint)
            val eyeDist = face.eyesDistance()

            if (eyeDist <= 5f) continue

            // Face landmarks estimations
            val eyeLeftX = midPoint.x - eyeDist * 0.5f
            val eyeLeftY = midPoint.y
            val eyeRightX = midPoint.x + eyeDist * 0.5f
            val eyeRightY = midPoint.y

            val cheekLeftX = midPoint.x - eyeDist * 0.75f
            val cheekLeftY = midPoint.y + eyeDist * 0.9f
            val cheekRightX = midPoint.x + eyeDist * 0.75f
            val cheekRightY = midPoint.y + eyeDist * 0.9f

            val faceCenterX = midPoint.x
            val faceCenterY = midPoint.y + eyeDist * 0.8f

            val slimRadius = eyeDist * 1.3f
            val eyeRadius = eyeDist * 0.55f

            index = 0
            for (y in 0..meshHeight) {
                for (x in 0..meshWidth) {
                    var vx = verts[index]
                    var vy = verts[index + 1]

                    // --- A. Face Slimming (Warp Cheeks inward towards center-lower face) ---
                    if (slimStrength > 0.01f) {
                        // Left Cheeks
                        val dxL = vx - cheekLeftX
                        val dyL = vy - cheekLeftY
                        val distL = Math.hypot(dxL.toDouble(), dyL.toDouble()).toFloat()
                        if (distL < slimRadius) {
                            val influence = (1.0f - distL / slimRadius) * (1.0f - distL / slimRadius)
                            val dirX = faceCenterX - cheekLeftX
                            val dirY = faceCenterY - cheekLeftY
                            vx += dirX * influence * slimStrength * 0.32f
                            vy += dirY * influence * slimStrength * 0.32f
                        }

                        // Right Cheeks
                        val dxR = vx - cheekRightX
                        val dyR = vy - cheekRightY
                        val distR = Math.hypot(dxR.toDouble(), dyR.toDouble()).toFloat()
                        if (distR < slimRadius) {
                            val influence = (1.0f - distR / slimRadius) * (1.0f - distR / slimRadius)
                            val dirX = faceCenterX - cheekRightX
                            val dirY = faceCenterY - cheekRightY
                            vx += dirX * influence * slimStrength * 0.32f
                            vy += dirY * influence * slimStrength * 0.32f
                        }
                    }

                    // --- B. Large Eyes (Expand outwards from eye centers) ---
                    if (bigEyesStrength > 0.01f) {
                        // Left Eye
                        val dxLE = vx - eyeLeftX
                        val dyLE = vy - eyeLeftY
                        val distLE = Math.hypot(dxLE.toDouble(), dyLE.toDouble()).toFloat()
                        if (distLE < eyeRadius && distLE > 0.1f) {
                            val influence = (1.0f - distLE / eyeRadius) * (1.0f - distLE / eyeRadius)
                            val dirX = dxLE / distLE
                            val dirY = dyLE / distLE
                            vx += dirX * influence * eyeRadius * bigEyesStrength * 0.22f
                            vy += dirY * influence * eyeRadius * bigEyesStrength * 0.22f
                        }

                        // Right Eye
                        val dxRE = vx - eyeRightX
                        val dyRE = vy - eyeRightY
                        val distRE = Math.hypot(dxRE.toDouble(), dyRE.toDouble()).toFloat()
                        if (distRE < eyeRadius && distRE > 0.1f) {
                            val influence = (1.0f - distRE / eyeRadius) * (1.0f - distRE / eyeRadius)
                            val dirX = dxRE / distRE
                            val dirY = dyRE / distRE
                            vx += dirX * influence * eyeRadius * bigEyesStrength * 0.22f
                            vy += dirY * influence * eyeRadius * bigEyesStrength * 0.22f
                        }
                    }

                    verts[index] = vx.coerceIn(0f, width.toFloat())
                    verts[index + 1] = vy.coerceIn(0f, height.toFloat())
                    index += 2
                }
            }
        }

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
        }
        canvas.drawBitmapMesh(src, meshWidth, meshHeight, verts, 0, null, 0, paint)
        return output
    }

    private fun applyDefaultPortraitWarp(src: Bitmap, slimStrength: Float): Bitmap {
        if (slimStrength <= 0.05f) return src.copy(src.config ?: Bitmap.Config.ARGB_8888, true)

        val width = src.width
        val height = src.height

        val meshWidth = 30
        val meshHeight = 30
        val numVertices = (meshWidth + 1) * (meshHeight + 1)
        val verts = FloatArray(numVertices * 2)

        val centerX = width * 0.5f
        val centerY = height * 0.58f
        val radiusX = width * 0.4f
        val radiusY = height * 0.26f

        var index = 0
        for (y in 0..meshHeight) {
            val fy = y.toFloat() / meshHeight * height
            for (x in 0..meshWidth) {
                val fx = x.toFloat() / meshWidth * width

                var vx = fx
                var vy = fy

                val dx = fx - centerX
                val dy = fy - centerY
                val normalizedDistSqr = (dx * dx) / (radiusX * radiusX) + (dy * dy) / (radiusY * radiusY)
                if (normalizedDistSqr < 1.0f) {
                    val influence = (1.0f - Math.sqrt(normalizedDistSqr.toDouble()).toFloat()) *
                            (1.0f - Math.sqrt(normalizedDistSqr.toDouble()).toFloat())
                    vx -= dx * influence * slimStrength * 0.16f
                }

                verts[index++] = vx.coerceIn(0f, width.toFloat())
                verts[index++] = vy.coerceIn(0f, height.toFloat())
            }
        }

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
        }
        canvas.drawBitmapMesh(src, meshWidth, meshHeight, verts, 0, null, 0, paint)
        return output
    }

    private fun applySkinSmoothing(src: Bitmap, smoothStrength: Float): Bitmap {
        if (smoothStrength <= 0.05f) return src.copy(src.config ?: Bitmap.Config.ARGB_8888, true)

        val width = src.width
        val height = src.height

        // Downscale-then-blur for incredible bilateral-like edge filtering reference
        val scale = 0.5f
        val sw = (width * scale).toInt().coerceAtLeast(1)
        val sh = (height * scale).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(src, sw, sh, true)
        val blurredSmall = applyBoxBlur(small, 3)
        val blurred = Bitmap.createScaledBitmap(blurredSmall, width, height, true)

        small.recycle()
        blurredSmall.recycle()

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val srcPixels = IntArray(width * height)
        val blurPixels = IntArray(width * height)
        val outPixels = IntArray(width * height)

        src.getPixels(srcPixels, 0, width, 0, 0, width, height)
        blurred.getPixels(blurPixels, 0, width, 0, 0, width, height)

        val threshold = 40f

        for (i in 0 until (width * height)) {
            val srcColor = srcPixels[i]
            val blurColor = blurPixels[i]

            val r1 = (srcColor shr 16) and 0xFF
            val g1 = (srcColor shr 8) and 0xFF
            val b1 = srcColor and 0xFF

            val r2 = (blurColor shr 16) and 0xFF
            val g2 = (blurColor shr 8) and 0xFF
            val b2 = blurColor and 0xFF

            val diff = (Math.abs(r1 - r2) + Math.abs(g1 - g2) + Math.abs(b1 - b2)) / 3.0f

            val factor = if (diff < threshold) {
                (1.0f - (diff / threshold)) * (1.0f - (diff / threshold)) * smoothStrength
            } else {
                0.0f
            }

            val r = (r1 * (1.0f - factor) + r2 * factor).toInt().coerceIn(0, 255)
            val g = (g1 * (1.0f - factor) + g2 * factor).toInt().coerceIn(0, 255)
            val b = (b1 * (1.0f - factor) + b2 * factor).toInt().coerceIn(0, 255)

            outPixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        result.setPixels(outPixels, 0, width, 0, 0, width, height)
        blurred.recycle()
        return result
    }

    private fun applyBoxBlur(src: Bitmap, radius: Int): Bitmap {
        val w = src.width
        val h = src.height
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        val outPixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        for (y in 0 until h) {
            for (x in 0 until w) {
                var rSum = 0
                var gSum = 0
                var bSum = 0
                var count = 0
                for (dx in -radius..radius) {
                    val nx = x + dx
                    if (nx in 0 until w) {
                        val p = pixels[y * w + nx]
                        rSum += (p shr 16) and 0xFF
                        gSum += (p shr 8) and 0xFF
                        bSum += p and 0xFF
                        count++
                    }
                }
                outPixels[y * w + x] = (0xFF shl 24) or ((rSum / count) shl 16) or ((gSum / count) shl 8) or (bSum / count)
            }
        }

        System.arraycopy(outPixels, 0, pixels, 0, pixels.size)
        for (x in 0 until w) {
            for (y in 0 until h) {
                var rSum = 0
                var gSum = 0
                var bSum = 0
                var count = 0
                for (dy in -radius..radius) {
                    val ny = y + dy
                    if (ny in 0 until h) {
                        val p = pixels[ny * w + x]
                        rSum += (p shr 16) and 0xFF
                        gSum += (p shr 8) and 0xFF
                        bSum += p and 0xFF
                        count++
                    }
                }
                outPixels[y * w + x] = (0xFF shl 24) or ((rSum / count) shl 16) or ((gSum / count) shl 8) or (bSum / count)
            }
        }

        result.setPixels(outPixels, 0, w, 0, 0, w, h)
        return result
    }

    private fun applyColorBeauty(src: Bitmap, whitenStrength: Float, rosyStrength: Float): Bitmap {
        val width = src.width
        val height = src.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val brightnessOffset = whitenStrength * 40f
        val redOffset = rosyStrength * 16f
        val greenOffset = rosyStrength * -4f
        val blueOffset = rosyStrength * 6f

        val matrixValues = floatArrayOf(
            1.0f + whitenStrength * 0.08f, 0f, 0f, 0f, brightnessOffset + redOffset,
            0f, 1.0f + whitenStrength * 0.08f, 0f, 0f, brightnessOffset + greenOffset,
            0f, 0f, 1.0f + whitenStrength * 0.08f, 0f, brightnessOffset + blueOffset,
            0f, 0f, 0f, 1.0f, 0f
        )

        paint.colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix(matrixValues))
        canvas.drawBitmap(src, 0f, 0f, paint)
        return output
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

                // Apply rotation metadata if necessary, or load straight
                val mutableBitmap = Bitmap.createBitmap(
                    originalBitmap.width,
                    originalBitmap.height,
                    Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(mutableBitmap)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)

                if (filter != FilterType.ORIGINAL) {
                    val androidMatrix = android.graphics.ColorMatrix(filter.getAndroidMatrixValues())
                    paint.colorFilter = android.graphics.ColorMatrixColorFilter(androidMatrix)
                }

                // Since we've preprocessed rotation and front-facing horizontal mirroring directly into the saved file on disk, we render it completely straight here!
                canvas.drawBitmap(originalBitmap, 0f, 0f, paint)

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
