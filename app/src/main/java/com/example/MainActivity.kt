package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaActionSound
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.ui.theme.MyApplicationTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class MainActivity : ComponentActivity() {
    private val viewModel: CameraViewModel by viewModels()

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    val cameraPermissionState = rememberPermissionState(
                        permission = Manifest.permission.CAMERA
                    )

                    LaunchedEffect(Unit) {
                        viewModel.loadLocalPhotos(this@MainActivity)
                    }

                    if (cameraPermissionState.status.isGranted) {
                        CameraAppScreen(viewModel = viewModel)
                    } else {
                        PermissionPlaceholderScreen(
                            onRequestPermission = { cameraPermissionState.launchPermissionRequest() }
                        )
                    }
                }
            }
        }
    }
}

// Custom extension to async fetch clean ProcessCameraProvider references
suspend fun Context.getCameraProvider(): ProcessCameraProvider = suspendCancellableCoroutine { continuation ->
    val future = ProcessCameraProvider.getInstance(this)
    future.addListener({
        continuation.resume(future.get())
    }, ContextCompat.getMainExecutor(this))
}

@Composable
fun FrostedGlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "frosted_button_scale"
    )

    Box(
        modifier = modifier
            .size(46.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            .clickable(
                interactionSource = interactionSource,
                onClick = onClick,
                indication = null
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("ClickableViewAccessibility")
@Composable
fun CameraAppScreen(viewModel: CameraViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Observe StateFLow values from VM
    val lensFacing by viewModel.lensFacing.collectAsState()
    val flashMode by viewModel.flashMode.collectAsState()
    val aspectRatio by viewModel.aspectRatio.collectAsState()
    val isGridVisible by viewModel.isGridVisible.collectAsState()
    val zoomRatio by viewModel.zoomRatio.collectAsState()
    val timerSeconds by viewModel.timerSeconds.collectAsState()
    val isCountdownRunning by viewModel.isCountdownRunning.collectAsState()
    val countdownRemaining by viewModel.countdownRemaining.collectAsState()
    val photos by viewModel.photos.collectAsState()
    val selectedPhoto by viewModel.selectedPhoto.collectAsState()

    var cameraInstance by remember { mutableStateOf<Camera?>(null) }
    var imageCaptureUseCase by remember { mutableStateOf<ImageCapture?>(null) }
    
    // Hold reference to PreviewView
    val previewView = remember { PreviewView(context) }

    // Shutter Click Sound Manager
    val mediaShutterSound = remember { MediaActionSound() }

    // On-screen focus states
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    val focusRingScale = remember { Animatable(1.5f) }
    val focusRingAlpha = remember { Animatable(0f) }

    // Shutter flash animation visual state
    var isShutterFlashing by remember { mutableStateOf(false) }

    // Pinch Zoom visual toast overlay
    var showZoomToast by remember { mutableStateOf(false) }

    // Rebind use-cases whenever Lens Selector changes
    LaunchedEffect(lensFacing) {
        val cameraProvider = context.getCameraProvider()
        
        val preview = Preview.Builder().build().apply {
            setSurfaceProvider(previewView.surfaceProvider)
        }

        val capture = ImageCapture.Builder()
            .setFlashMode(flashMode)
            .build()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        try {
            cameraProvider.unbindAll()
            cameraInstance = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                capture
            )
            imageCaptureUseCase = capture
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "绑定相机发生故障: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // Set physical flash mode directly on the imageCapture object when state in VM changes
    LaunchedEffect(flashMode, imageCaptureUseCase) {
        imageCaptureUseCase?.flashMode = flashMode
    }

    // Set physical zoom ratio directly on the camera instance when state in VM changes
    LaunchedEffect(zoomRatio, cameraInstance) {
        cameraInstance?.cameraControl?.setZoomRatio(zoomRatio)
    }

    // Capture Local Shutter Screen white flash triggers
    LaunchedEffect(Unit) {
        viewModel.shutterFlashChannel.collect {
            isShutterFlashing = true
            delay(60)
            isShutterFlashing = false
        }
    }

    // Reticle animation on Tap To Focus
    LaunchedEffect(focusPoint) {
        if (focusPoint != null) {
            focusRingScale.snapTo(1.6f)
            focusRingAlpha.snapTo(1f)
            focusRingScale.animateTo(1.0f, animationSpec = tween(220, easing = FastOutSlowInEasing))
            delay(1200)
            focusRingAlpha.animateTo(0f, animationSpec = tween(250))
            focusPoint = null
        }
    }

    // Trigger Zoom Toast fade out on inactivity
    LaunchedEffect(zoomRatio) {
        showZoomToast = true
        delay(1500)
        showZoomToast = false
    }

    // Single click trigger to capture image with storage
    fun executeImageCapture() {
        val activeCapture = imageCaptureUseCase
        if (activeCapture == null) {
            Toast.makeText(context, "相机未就绪，请重试", Toast.LENGTH_SHORT).show()
            return
        }

        val capturesDirectory = File(context.filesDir, "captures")
        if (!capturesDirectory.exists()) {
            capturesDirectory.mkdirs()
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val targetFile = File(capturesDirectory, "IMG_$timestamp.jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(targetFile).build()

        // Trigger visual flash + play shutter sound
        viewModel.triggerShutterFlash()
        mediaShutterSound.play(MediaActionSound.SHUTTER_CLICK)

        activeCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Toast.makeText(context, "快门合上，照片保存成功！", Toast.LENGTH_SHORT).show()
                    viewModel.addCapturedPhoto(context, targetFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    exception.printStackTrace()
                    Toast.makeText(context, "保存失败: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Beautiful gradient background brush mapping Slate to Indigo
    val backgroundBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF030712), // slate-950
            Color(0xFF0F172A), // slate-900
            Color(0xFF1E293B), // slate-800
            Color(0xFF1E1B4B)  // indigo-950
        ),
        start = Offset(0f, 0f),
        end = Offset(1000f, 2000f)
    )

    // Standard Scaffold with customized Frosted Glass design system styling
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                // Frosted Gradient-faded Top Controls bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.65f),
                                    Color.Black.copy(alpha = 0.3f),
                                    Color.Transparent
                                )
                            )
                        )
                        .statusBarsPadding()
                        .padding(top = 16.dp, bottom = 24.dp, start = 20.dp, end = 20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Flash Mode Selector
                        FrostedGlassIconButton(
                            onClick = { viewModel.toggleFlash() }
                        ) {
                            val flashIcon = when (flashMode) {
                                ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                                ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                                else -> Icons.Default.FlashOff
                            }
                            val flashColor = when (flashMode) {
                                ImageCapture.FLASH_MODE_OFF -> Color.White.copy(alpha = 0.8f)
                                else -> Color(0xFFFFD54F) // M3 Yellow
                            }
                            Icon(flashIcon, contentDescription = "闪光模式", tint = flashColor, modifier = Modifier.size(20.dp))
                        }

                        // Grid Toggle Overlay
                        FrostedGlassIconButton(
                            onClick = { viewModel.toggleGrid() }
                        ) {
                            val gridIcon = if (isGridVisible) Icons.Default.GridOn else Icons.Default.GridOff
                            Icon(
                                imageVector = gridIcon,
                                contentDescription = "参考线",
                                tint = if (isGridVisible) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Timer Mode Selector
                        FrostedGlassIconButton(
                            onClick = { viewModel.toggleTimer() }
                        ) {
                            val timerIconState = when (timerSeconds) {
                                3 -> Icons.Default.Timer3
                                10 -> Icons.Default.Timer10
                                else -> Icons.Default.Timer
                            }
                            Icon(
                                imageVector = timerIconState,
                                contentDescription = "延迟快门",
                                tint = if (timerSeconds > 0) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Viewport Aspect Ratio mode controller
                        FrostedGlassIconButton(
                            onClick = {
                                val nextMode = when (aspectRatio) {
                                    AspectRatioMode.RATIO_4_3 -> AspectRatioMode.RATIO_16_9
                                    AspectRatioMode.RATIO_16_9 -> AspectRatioMode.RATIO_1_1
                                    AspectRatioMode.RATIO_1_1 -> AspectRatioMode.RATIO_4_3
                                }
                                viewModel.setAspectRatio(nextMode)
                            }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(24.dp)
                                    .border(1.2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(3.dp))
                            ) {
                                Text(
                                    text = aspectRatio.displayName,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            },
            bottomBar = {
                // Elegant Frosted Glass Shutter Control Dashboard with background gradient fade
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.4f),
                                    Color.Black.copy(alpha = 0.85f),
                                    Color.Black
                                )
                            )
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(bottom = 20.dp)
                    ) {
                        // Inline Zoom Controls pill with frosted glass design
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val zoomOptions = listOf(1f, 2f, 4f, 8f)
                            Row(
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 4.dp, vertical = 3.dp)
                            ) {
                                zoomOptions.forEach { factor ->
                                    val isSelected = (zoomRatio == factor)
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(if (isSelected) Color.White else Color.Transparent)
                                            .clickable { viewModel.setZoom(factor) }
                                            .padding(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "${factor.toInt()}X",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isSelected) Color.Black else Color.White
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Camera Mode Selector (from design mockup)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "录像",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.padding(horizontal = 14.dp)
                            )
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 14.dp)
                            ) {
                                Text(
                                    text = "拍照",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Box(
                                    modifier = Modifier
                                        .size(3.dp)
                                        .background(Color.White, CircleShape)
                                )
                            }
                            Text(
                                text = "人像",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.padding(horizontal = 14.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Bottom Primary Shutter Cluster
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 30.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Glassy Gallery preview shortcut (Left)
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .border(1.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                    .testTag("gallery_preview_shortcut")
                                    .clickable {
                                        if (photos.isNotEmpty()) {
                                            viewModel.selectPhoto(photos.first())
                                        } else {
                                            Toast
                                                .makeText(context, "画廊空空如也，拍张照片试试吧！", Toast.LENGTH_SHORT)
                                                .show()
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (photos.isNotEmpty()) {
                                    AsyncImage(
                                        model = photos.first(),
                                        contentDescription = "最近相片",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = "画廊为空",
                                        tint = Color.White.copy(alpha = 0.4f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            // Premium Tactile Shutter Button (Center)
                            val interactionSource = remember { MutableInteractionSource() }
                            val isShutterPressed by interactionSource.collectIsPressedAsState()
                            val shutterScale by animateFloatAsState(
                                targetValue = if (isShutterPressed) 0.88f else 1.0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                ),
                                label = "shutter_spring"
                            )

                            Box(
                                modifier = Modifier
                                    .size(78.dp)
                                    .scale(shutterScale)
                                    .border(4.dp, Color.White, CircleShape)
                                    .padding(5.dp)
                                    .clip(CircleShape)
                                    .background(if (isCountdownRunning) Color.Red.copy(alpha = 0.3f) else Color.White)
                                    .testTag("shutter_trigger")
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = null,
                                        onClick = {
                                            if (isCountdownRunning) return@clickable
                                            viewModel.startTimerCountdown {
                                                executeImageCapture()
                                            }
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isCountdownRunning) {
                                    Text(
                                        text = countdownRemaining.toString(),
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        style = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
                                    )
                                } else {
                                    // Double ring design
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .border(2.dp, Color.Black.copy(alpha = 0.15f), CircleShape)
                                    )
                                }
                            }

                            // Glassy Lens switch button (Right)
                            var rotationDegree by remember { mutableStateOf(0f) }
                            val animatedAngle by animateFloatAsState(
                                targetValue = rotationDegree,
                                animationSpec = spring(stiffness = Spring.StiffnessLow),
                                label = "camera_rotation_spring"
                            )

                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                                    .clickable {
                                        rotationDegree += 180f
                                        viewModel.toggleLens()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cached,
                                    contentDescription = "切换镜头",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .graphicsLayer { rotationZ = animatedAngle }
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            // Middle interactive viewfinder container
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                // Constrain Aspect Ratio frame cleanly with beautiful letterboxing
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspectRatio.ratioValue)
                        .clip(RoundedCornerShape(if (aspectRatio == AspectRatioMode.RATIO_1_1) 16.dp else 0.dp))
                        .background(Color(0xFF0F172A))
                        .pointerInput(Unit) {
                            // Gesture detection: Pinch to zoom, double tap to rotate, and single tap to focus
                            detectTransformGestures { _, _, zoom, _ ->
                                if (zoom != 1.0f) {
                                    viewModel.setZoom(zoomRatio * zoom)
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    viewModel.toggleLens()
                                },
                                onTap = { localOffset ->
                                    focusPoint = localOffset
                                    // Call camera previewView focusing point triggers
                                    val factory = previewView.meteringPointFactory
                                    val point = factory.createPoint(localOffset.x, localOffset.y)
                                    val act = FocusMeteringAction
                                        .Builder(point, FocusMeteringAction.FLAG_AF)
                                        .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                        .build()
                                    cameraInstance?.cameraControl?.startFocusAndMetering(act)
                                }
                            )
                        }
                ) {
                    // Embed CameraX PreviewView
                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier.fillMaxSize()
                    )

                    // 3x3 high contrast grid overlay
                    if (isGridVisible) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height

                            // Grid line 1 (Vertical)
                            drawLine(
                                color = Color.White.copy(alpha = 0.4f),
                                start = Offset(w / 3f, 0f),
                                end = Offset(w / 3f, h),
                                strokeWidth = 1.dp.toPx()
                            )
                            // Grid line 2 (Vertical)
                            drawLine(
                                color = Color.White.copy(alpha = 0.4f),
                                start = Offset(w * 2f / 3f, 0f),
                                end = Offset(w * 2f / 3f, h),
                                strokeWidth = 1.dp.toPx()
                            )
                            // Grid line 3 (Horizontal)
                            drawLine(
                                color = Color.White.copy(alpha = 0.4f),
                                start = Offset(0f, h / 3f),
                                end = Offset(w, h / 3f),
                                strokeWidth = 1.dp.toPx()
                            )
                            // Grid line 4 (Horizontal)
                            drawLine(
                                color = Color.White.copy(alpha = 0.4f),
                                start = Offset(0f, h * 2f / 3f),
                                end = Offset(w, h * 2f / 3f),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    }

                    // Interactive tap-to-focus Yellow Reticle animations
                    focusPoint?.let { offset ->
                        Box(
                            modifier = Modifier
                                .offset(
                                    x = with(LocalDensity.current) { offset.x.toDp() } - 36.dp,
                                    y = with(LocalDensity.current) { offset.y.toDp() } - 36.dp
                                )
                                .size(72.dp)
                                .scale(focusRingScale.value)
                                .alpha(focusRingAlpha.value)
                                .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                                .padding(6.dp)
                                .border(1.5.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            // Tiny dot inside reticle
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color.White, CircleShape)
                            )
                        }
                    }

                    // On-screen Pinch Zoom floating pill overlays
                    AnimatedVisibility(
                        visible = showZoomToast,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = String.format(Locale.US, "%.1f X", zoomRatio),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Shutter Click Exposure screen-flash overlay
                    AnimatedVisibility(
                        visible = isShutterFlashing,
                        enter = fadeIn(animationSpec = tween(10)),
                        exit = fadeOut(animationSpec = tween(180))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White)
                        )
                    }
                }
            }

            // Big Timer Tick count screen central visualizer
            if (isCountdownRunning) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .border(2.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = countdownRemaining.toString(),
                            color = Color.White,
                            fontSize = 58.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }

    // Modern High-Fidelity Filters Overlay & Capture Explorer Modal (Full Screen Sheet Dialog)
    selectedPhoto?.let { photoFile ->
        val activeFilter by viewModel.selectedFilter.collectAsState()
        var showDeleteConfirm by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush) // Cohesive unified gradient background!
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top controls row with glassmorphism style buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp, horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back button
                    FrostedGlassIconButton(
                        onClick = { viewModel.selectPhoto(null) }
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "关闭", tint = Color.White, modifier = Modifier.size(20.dp))
                    }

                    Text(
                        text = "照片详情与滤镜",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Export/Save Photo button
                        FrostedGlassIconButton(
                            onClick = {
                                viewModel.savePhotoToPublicGallery(context, photoFile, activeFilter) { success, msg ->
                                    Toast.makeText(context, msg ?: "操作结果已出", Toast.LENGTH_LONG).show()
                                }
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Save, contentDescription = "导出至相册", tint = Color(0xFFFFD54F), modifier = Modifier.size(20.dp))
                        }

                        // Delete button
                        FrostedGlassIconButton(
                            onClick = { showDeleteConfirm = true }
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "删除该照片", tint = Color(0xFFEF5350), modifier = Modifier.size(20.dp))
                        }
                    }
                }

                // Middle area: High Res Photo with active real-time Color Matrix filter
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = photoFile,
                        contentDescription = "查看相片",
                        contentScale = ContentScale.Fit,
                        colorFilter = if (activeFilter != FilterType.ORIGINAL) {
                            ColorFilter.colorMatrix(activeFilter.getComposeMatrix())
                        } else null,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Navigation buttons inside the slider gallery
                    val photoIndex = photos.indexOf(photoFile)
                    if (photoIndex > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 16.dp)
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                                .clickable { viewModel.selectPhoto(photos[photoIndex - 1]) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = "前一张", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }

                    if (photoIndex < photos.size - 1) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 16.dp)
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                                .clickable { viewModel.selectPhoto(photos[photoIndex + 1]) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "后一张", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                }

                // Horizontal Filters Selector Reel (frosted block design)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                        )
                        .padding(vertical = 18.dp)
                ) {
                    Text(
                        text = "选择艺术滤镜 (点击应用)",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 20.dp, bottom = 12.dp)
                    )

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(FilterType.entries) { filterItem ->
                            val isChosen = (activeFilter == filterItem)
                            Column(
                                modifier = Modifier
                                    .width(74.dp)
                                    .clickable { viewModel.selectFilter(filterItem) },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Live mini thumbnail rendering with applied matrix!
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(
                                            width = if (isChosen) 2.dp else 1.dp,
                                            color = if (isChosen) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.18f),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                ) {
                                    AsyncImage(
                                        model = photoFile,
                                        contentDescription = filterItem.displayName,
                                        contentScale = ContentScale.Crop,
                                        colorFilter = if (filterItem != FilterType.ORIGINAL) {
                                            ColorFilter.colorMatrix(filterItem.getComposeMatrix())
                                        } else null,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = filterItem.displayName,
                                    fontSize = 10.sp,
                                    color = if (isChosen) Color(0xFFFFD54F) else Color.White,
                                    fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // High priority simple confirm delete dialog
            if (showDeleteConfirm) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = { Text(text = "删除此照片", fontWeight = FontWeight.Bold, color = Color.White) },
                    text = { Text(text = "确定要永久删除这张照片吗？该操作不可撤销。", color = Color.White.copy(alpha = 0.8f)) },
                    containerColor = Color(0xFF1E1E1E),
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteConfirm = false
                                viewModel.deletePhoto(context, photoFile)
                            }
                        ) {
                            Text(text = "删除", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirm = false }) {
                            Text(text = "取消", color = Color.White.copy(alpha = 0.6f))
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun PermissionPlaceholderScreen(onRequestPermission: () -> Unit) {
    val backgroundBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF030712), // slate-950
            Color(0xFF0F172A), // slate-900
            Color(0xFF1E293B), // slate-800
            Color(0xFF1E1B4B)  // indigo-950
        ),
        start = Offset(0f, 0f),
        end = Offset(1000f, 2000f)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(24.dp))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                .padding(vertical = 36.dp, horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .background(Color.White.copy(alpha = 0.1f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = "相机安全访问",
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "开启精彩摄影体验",
                    fontSize = 20.sp,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "请授权相机访问权限，以便开启画面实时预览和艺术相片抓拍。",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "授予相机权限",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
