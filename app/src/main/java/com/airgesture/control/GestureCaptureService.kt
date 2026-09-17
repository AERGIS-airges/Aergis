package com.airgesture.control

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Size
import kotlin.math.hypot
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.framework.image.MediaImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt

class GestureCaptureService : LifecycleService() {
    private val cameraExecutor: ExecutorService =
        Executors.newSingleThreadExecutor()
    private val mainHandler =
        Handler(Looper.getMainLooper())
    private val visionWatchdogRunnable =
        object : Runnable {
            override fun run() {
                checkVisionWatchdog(
                    SystemClock.uptimeMillis()
                )
                if (
                    sessionRequested
                ) {
                    mainHandler.postDelayed(
                        this,
                        VISION_WATCHDOG_POLL_MS
                    )
                }
            }
        }
    private val interpreter =
        GestureInterpreter(
            signalEnabled = ::isSignalEnabled
        )
    private val pointerTracker = PointerTracker()
    private val pointerAimEstimator = PointerAimEstimator()
    private val pointerKinematicFilter = PointerKinematicFilter()
    private val landmarkPoseRecognizer = LandmarkPoseRecognizer()
    private val worldGeometryTemporalGuard =
        WorldGeometryTemporalGuard()
    private val controlHandSelector = ControlHandSelector()
    private val performanceMonitor = VisionPerformanceMonitor()
    private val pipelineBenchmark =
        VisionPipelineBenchmark()
    private val framePacer = VisionFramePacer()
    private val imageProcessingOptionsByRotation =
        mapOf(
            0 to imageProcessingOptions(0),
            90 to imageProcessingOptions(90),
            180 to imageProcessingOptions(180),
            270 to imageProcessingOptions(270)
        )
    private val recognizerLock = Any()
    private val submittedBitmapLock = Any()
    private val submittedBitmaps =
        mutableMapOf<Long, Bitmap>()
    private val submittedImageProxies =
        mutableMapOf<Long, ImageProxy>()
    private val submittedFrameRotations =
        mutableMapOf<Long, Int>()
    private val submittedFrameDimensions =
        mutableMapOf<Long, Pair<Int, Int>>()
    private val submittedFramePipelines =
        mutableMapOf<Long, VisionPipelineKey>()
    private val gestureExecutionGeneration =
        AtomicLong(0L)
    private val sessionGeneration =
        AtomicLong(0L)
    private val recognizerEpoch =
        AtomicLong(0L)
    private val lastDirectMediaSubmissionEpoch =
        AtomicLong(Long.MIN_VALUE)
    private lateinit var mappingStore: ActionMappingStore
    private var cameraProvider: ProcessCameraProvider? = null
    @Volatile
    private var recognizer: GestureRecognizer? = null
    @Volatile
    private var sessionRequested = false
    private var cameraStarted = false
    private var lastSubmittedFrameMs = 0L
    @Volatile
    private var firstSubmittedFrameMs = 0L
    @Volatile
    private var lastResultReceivedMs = 0L
    @Volatile
    private var lastUsableResultMs = 0L
    private var unusableGapFailSafeActive = false
    private var lastProcessedResultTimestampMs = Long.MIN_VALUE
    private var lastPerformancePublishMs = 0L
    private var lastPointerLandmarkMs: Long? = null
    private var pointerEstimatorResetForLoss = false
    @Volatile
    private var consecutiveFrameFailures = 0
    private var lastVisionInputMode: VisionInputMode? = null
    private var lastVisionRotationDegrees: Int? = null
    @Volatile
    private var directMediaDisabledForSession = false
    @Volatile
    private var directMediaRecoveryInProgress = false
    @Volatile
    private var gpuDisabledForSession = false
    @Volatile
    private var delegateRecoveryInProgress = false
    @Volatile
    private var activeRecognizerDelegate =
        VisionDelegateMode.CPU
    @Volatile
    private var visionStallReported = false
    @Volatile
    private var submittedFramesQuarantined = false

    private val calibrationUpdateLock = Any()
    private var pendingPointerCalibration: PointerCalibration? = null
    private var calibrationUpdatePosted = false

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (
                intent?.action == Intent.ACTION_SCREEN_OFF &&
                sessionRequested
            ) {
                stopSession(
                    "Air control stopped when the screen turned off"
                )
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        mappingStore = ActionMappingStore(this)
        val initialHandPreference =
            mappingStore.controlHandPreference()
        controlHandSelector.updatePreference(
            initialHandPreference
        )
        pointerTracker.updateCalibration(
            mappingStore.pointerCalibration(
                currentCalibrationContext(
                    initialHandPreference
                )
            )
        )
        instance = this
        createNotificationChannel()

        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun currentCalibrationContext(
        preference: ControlHandPreference =
            controlHandSelector.currentPreference()
    ): PointerCalibrationContext =
        PointerCalibrationContext
            .forOrientation(
                handPreference = preference,
                landscape =
                    resources.configuration
                        .orientation ==
                        Configuration
                            .ORIENTATION_LANDSCAPE
            )

    override fun onConfigurationChanged(
        newConfig: Configuration
    ) {
        super.onConfigurationChanged(newConfig)

        val context =
            PointerCalibrationContext
                .forOrientation(
                    handPreference =
                        controlHandSelector
                            .currentPreference(),
                    landscape =
                        newConfig.orientation ==
                            Configuration
                                .ORIENTATION_LANDSCAPE
                )
        val calibration =
            mappingStore
                .pointerCalibration(context)

        synchronized(this) {
            pointerAimEstimator.reset()
            pointerKinematicFilter.reset()
            pointerTracker
                .resetForControlHandChange()
        }
        enqueuePointerCalibration(
            calibration
        )
        AirRuntime.update {
            it.copy(
                lastMessage =
                    "Pointer calibration context changed • " +
                        context.orientation.name
                            .lowercase()
            )
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent?.action == ACTION_STOP) {
            stopSession()
            return Service.START_NOT_STICKY
        }

        val decision = SessionStartPolicy.evaluate(
            action = intent?.action,
            expectedStartAction = ACTION_START,
            hasCameraPermission =
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED,
            accessibilityReady = AirAccessibilityService.isReady()
        )

        if (!decision.allowed) {
            stopSession(
                decision.message
                    ?: "Air control could not start"
            )
            return Service.START_NOT_STICKY
        }

        startSession()
        return Service.START_NOT_STICKY
    }

    private fun isSignalEnabled(
        signal: GestureSignal
    ): Boolean {
        if (!::mappingStore.isInitialized) {
            return false
        }

        val action = mappingStore.actionFor(signal)
        return action != AirAction.NONE &&
            AirActionCapabilities.isSupported(
                action = action,
                sdkInt = Build.VERSION.SDK_INT
            )
    }

    private fun startSession() {
        if (sessionRequested) return
        if (submittedFramesQuarantined) {
            AirRuntime.update {
                it.copy(
                    lastMessage =
                        "Air control blocked after native vision shutdown failure • restart the app process"
                )
            }
            stopSelf()
            return
        }
        val generation =
            sessionGeneration.incrementAndGet()
        sessionRequested = true
        TrackingMirrorBridge.setActive(true)
        performanceMonitor.reset()
        pipelineBenchmark.reset()
        AirRuntime.recordVisionPipelineBenchmark(
            pipelineBenchmark.summary()
        )
        lastPerformancePublishMs = 0L
        lastSubmittedFrameMs = 0L
        firstSubmittedFrameMs = 0L
        lastResultReceivedMs = 0L
        lastUsableResultMs = 0L
        unusableGapFailSafeActive = false
        lastProcessedResultTimestampMs = Long.MIN_VALUE
        lastPointerLandmarkMs = null
        pointerEstimatorResetForLoss = false
        consecutiveFrameFailures = 0
        lastVisionInputMode = null
        lastVisionRotationDegrees = null
        directMediaDisabledForSession = false
        directMediaRecoveryInProgress = false
        gpuDisabledForSession = false
        delegateRecoveryInProgress = false
        activeRecognizerDelegate =
            VisionDelegateMode.CPU
        lastDirectMediaSubmissionEpoch
            .set(Long.MIN_VALUE)
        visionStallReported = false
        startVisionWatchdog()
        gestureExecutionGeneration.incrementAndGet()
        synchronized(this) {
            pointerAimEstimator.reset()
            pointerKinematicFilter.reset()
            pointerTracker.resetForControlHandChange()
            interpreter.resetForControlHandChange()
            controlHandSelector.resetTracking()
            worldGeometryTemporalGuard.reset()
        }
        AirRuntime.recordVisionPerformance(
            performanceMonitor.snapshot()
        )
        AirRuntime.recordVisionPipelineBenchmark(
            pipelineBenchmark.summary()
        )

        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildSessionNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            )
        } catch (t: Throwable) {
            sessionRequested = false
            stopSession(
                "Android blocked the camera foreground session"
            )
            return
        }

        AirRuntime.update {
            it.copy(
                sessionActive = true,
                lastMessage = "Starting hand tracking…"
            )
        }

        cameraExecutor.execute {
            val ready =
                setupRecognizer(generation)
            ContextCompat.getMainExecutor(this).execute {
                if (
                    !isCurrentSession(generation)
                ) {
                    return@execute
                }

                if (!ready) {
                    failSession(
                        "Gesture recognizer could not start"
                    )
                    return@execute
                }

                if (!cameraStarted) {
                    startCamera(generation)
                }
            }
        }
    }

    private fun setupRecognizer(
        generation: Long
    ): Boolean {
        synchronized(recognizerLock) {
            if (recognizer != null) {
                return true
            }
        }

        val preferredDelegate =
            VisionDelegatePolicy.preferred(
                pointerActive =
                    mappingStore.pointerEnabled(),
                gpuAllowed =
                    !gpuDisabledForSession
            )
        val attempts =
            if (
                preferredDelegate ==
                VisionDelegateMode.GPU
            ) {
                listOf(
                    VisionDelegateMode.GPU,
                    VisionDelegateMode.CPU
                )
            } else {
                listOf(
                    VisionDelegateMode.CPU
                )
            }

        var gpuInitializationFailed = false
        var lastFailure: Throwable? = null

        for (delegateMode in attempts) {
            if (
                !isCurrentSession(generation)
            ) {
                return false
            }

            val epoch =
                recognizerEpoch.incrementAndGet()

            val created =
                try {
                    val baseOptions =
                        BaseOptions.builder()
                            .setDelegate(
                                when (delegateMode) {
                                    VisionDelegateMode.GPU ->
                                        Delegate.GPU
                                    VisionDelegateMode.CPU ->
                                        Delegate.CPU
                                }
                            )
                            .setModelAssetPath(
                                MODEL_ASSET
                            )
                            .build()

                    val options =
                        GestureRecognizer
                            .GestureRecognizerOptions
                            .builder()
                            .setBaseOptions(baseOptions)
                            .setNumHands(
                                if (mappingStore.pointerEnabled()) 1 else 2
                            )
                            .setMinHandDetectionConfidence(
                                0.45f
                            )
                            .setMinHandPresenceConfidence(
                                0.42f
                            )
                            .setMinTrackingConfidence(
                                0.45f
                            )
                            .setRunningMode(
                                RunningMode.LIVE_STREAM
                            )
                            .setResultListener {
                                    result,
                                    input ->
                                onResult(
                                    generation =
                                        generation,
                                    epoch = epoch,
                                    result = result,
                                    input = input
                                )
                            }
                            .setErrorListener { error ->
                                onRecognizerError(
                                    generation =
                                        generation,
                                    epoch = epoch,
                                    delegateMode =
                                        delegateMode,
                                    error = error
                                )
                            }
                            .build()

                    GestureRecognizer
                        .createFromOptions(
                            this,
                            options
                        )
                } catch (t: Throwable) {
                    lastFailure = t
                    if (
                        delegateMode ==
                        VisionDelegateMode.GPU
                    ) {
                        gpuDisabledForSession = true
                        gpuInitializationFailed = true
                        AirRuntime.recordVisionFallback(
                            "GPU init → CPU • " +
                                (
                                    t.message
                                        ?: t.javaClass
                                            .simpleName
                                    )
                        )
                        null
                    } else {
                        null
                    }
                }

            if (created == null) {
                if (
                    delegateMode ==
                        VisionDelegateMode.GPU
                ) {
                    continue
                }
                break
            }

            var accepted = false
            synchronized(recognizerLock) {
                if (
                    isCurrentSession(generation) &&
                    recognizer == null
                ) {
                    recognizer = created
                    activeRecognizerDelegate =
                        delegateMode
                    accepted = true
                }
            }

            if (!accepted) {
                created.close()
                return false
            }

            AirRuntime.recordVisionDelegate(
                delegateMode
            )
            AirRuntime.update {
                it.copy(
                    recognizerReady = true,
                    lastMessage =
                        if (
                            gpuInitializationFailed &&
                            delegateMode ==
                            VisionDelegateMode.CPU
                        ) {
                            "GPU unavailable • CPU inference active"
                        } else {
                            "Gesture recognizer ready"
                        }
                )
            }
            return true
        }

        synchronized(recognizerLock) {
            recognizer = null
        }
        val failureReason =
            lastFailure
                ?.message
                ?.lineSequence()
                ?.firstOrNull()
                .orEmpty()
                .take(72)
        AirRuntime.update {
            it.copy(
                recognizerReady = false,
                lastMessage =
                    if (
                        failureReason.isBlank()
                    ) {
                        "Gesture recognizer could not load"
                    } else {
                        "Gesture recognizer could not load • " +
                            failureReason
                    }
            )
        }
        return false
    }

    private fun startCamera(
        generation: Long
    ) {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            {
                try {
                    if (
                        !isCurrentSession(generation)
                    ) {
                        return@addListener
                    }

                    val provider = future.get()
                    if (
                        !provider.hasCamera(
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        )
                    ) {
                        failSession("No front camera is available")
                        return@addListener
                    }

                    cameraProvider = provider

                    val resolutionSelector =
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    Size(640, 480),
                                    ResolutionStrategy
                                        .FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                                )
                            )
                            .build()

                    val analysis = ImageAnalysis.Builder()
                        .setResolutionSelector(resolutionSelector)
                        // Native YUV avoids the forced YUV->RGBA analysis conversion.
                        .setOutputImageFormat(
                            ImageAnalysis
                                .OUTPUT_IMAGE_FORMAT_YUV_420_888
                        )
                        .setBackpressureStrategy(
                            ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                        )
                        .build()

                    analysis.setAnalyzer(
                        cameraExecutor
                    ) { image ->
                        analyzeFrame(
                            generation = generation,
                            image = image
                        )
                    }

                    if (
                        !isCurrentSession(generation)
                    ) {
                        return@addListener
                    }

                    provider.unbindAll()
                    provider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        analysis
                    )

                    if (
                        !isCurrentSession(generation)
                    ) {
                        provider.unbindAll()
                        return@addListener
                    }

                    cameraStarted = true
                    AirRuntime.update {
                        it.copy(
                            cameraReady = true,
                            lastMessage = "Air control is listening for gestures"
                        )
                    }
                } catch (t: Throwable) {
                    failSession("Front camera could not start")
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun analyzeFrame(
        generation: Long,
        image: ImageProxy
    ) {
        val recognizerSnapshot =
            synchronized(recognizerLock) {
                recognizer to
                    recognizerEpoch.get()
            }
        val activeRecognizer =
            recognizerSnapshot.first
        val activeRecognizerEpoch =
            recognizerSnapshot.second
        if (
            activeRecognizer == null ||
            !isCurrentRecognizer(
                generation,
                activeRecognizerEpoch
            )
        ) {
            image.close()
            return
        }

        val now = SystemClock.uptimeMillis()
        val pointerLatencyPriority =
            mappingStore.pointerEnabled()
        val frameIntervalMs =
            framePacer.targetIntervalMs(
                snapshot =
                    performanceMonitor.snapshot(),
                pointerActive =
                    pointerLatencyPriority
            )
        val maxInFlight =
            VisionLatencyPolicy.maxInFlight(
                pointerActive =
                    pointerLatencyPriority
            )
        val inFlightBackpressure =
            submittedFrameCount() >=
                maxInFlight
        val throttled =
            inFlightBackpressure ||
                now - lastSubmittedFrameMs <
                    frameIntervalMs

        performanceMonitor.onCameraFrame(
            timestampMs = now,
            submitted = !throttled
        )

        if (throttled) {
            publishPerformanceIfDue(now)
            checkVisionWatchdog(now)
            image.close()
            return
        }

        lastSubmittedFrameMs = now

        var pendingImage: MPImage? = null
        var submittedTimestamp: Long? = null
        var imageProxyOwnershipTransferred = false
        var fallbackBitmap: Bitmap? = null
        var attemptedInputMode =
            VisionInputMode.BITMAP
        var inferenceSubmitted = false
        var submissionAttempted = false

        try {
            val rotation =
                VisionCoordinateMapper
                    .normalizeRotation(
                        image.imageInfo.rotationDegrees
                    )
            if (
                rotation !=
                lastVisionRotationDegrees
            ) {
                lastVisionRotationDegrees =
                    rotation
                AirRuntime.recordVisionRotation(
                    rotation
                )
            }
            val mediaImage = image.image

            val inputMode =
                VisionInputPolicy.choose(
                    pointerActive =
                        pointerLatencyPriority,
                    mediaImageAvailable =
                        mediaImage != null,
                    directMediaAllowed =
                        !directMediaDisabledForSession
                )
            attemptedInputMode = inputMode
            if (inputMode != lastVisionInputMode) {
                lastVisionInputMode = inputMode
                AirRuntime.recordVisionInputMode(
                    inputMode
                )
            }

            if (
                inputMode ==
                VisionInputMode.DIRECT_MEDIA_IMAGE
            ) {
                // Pointer mode gets the lowest-copy live-stream path: wrap
                // CameraX's native YUV android.media.Image directly instead of
                // materializing a Bitmap. The ImageProxy stays open until
                // MediaPipe returns the matching result so its backing buffers
                // remain valid. Gesture-only mode intentionally keeps the
                // existing Bitmap path and two-in-flight throughput behavior.
                pendingImage =
                    MediaImageBuilder(mediaImage)
                        .build()
                registerSubmittedImageProxy(
                    timestampMs = now,
                    image = image,
                    rotationDegrees = rotation,
                    width = image.width,
                    height = image.height,
                    pipelineKey =
                        VisionPipelineKey(
                            delegate =
                                activeRecognizerDelegate,
                            inputMode = inputMode
                        )
                )
                imageProxyOwnershipTransferred = true
            } else {
                // Gesture-only mode and pointer-mode device fallback retain
                // the v0.18.4 Bitmap path. This keeps the already-working
                // gesture throughput semantics and prevents a device that does
                // not expose android.media.Image from losing tracking.
                val bitmap = image.toBitmap()
                fallbackBitmap = bitmap
                pendingImage =
                    BitmapImageBuilder(bitmap)
                        .build()
                registerSubmittedBitmap(
                    timestampMs = now,
                    bitmap = bitmap,
                    rotationDegrees = rotation,
                    width = image.width,
                    height = image.height,
                    pipelineKey =
                        VisionPipelineKey(
                            delegate =
                                activeRecognizerDelegate,
                            inputMode = inputMode
                        )
                )
            }
            submittedTimestamp = now

            synchronized(recognizerLock) {
                if (
                    !isCurrentRecognizer(
                        generation,
                        activeRecognizerEpoch
                    ) ||
                    recognizer !== activeRecognizer
                ) {
                    throw IllegalStateException(
                        "Recognizer changed before frame submission"
                    )
                }

                val processingOptions =
                    imageProcessingOptionsByRotation[rotation]
                        ?: throw IllegalArgumentException(
                            "Unsupported camera rotation: $rotation"
                        )
                submissionAttempted = true
                activeRecognizer.recognizeAsync(
                    pendingImage,
                    processingOptions,
                    now
                )
                inferenceSubmitted = true
                if (
                    attemptedInputMode ==
                    VisionInputMode
                        .DIRECT_MEDIA_IMAGE
                ) {
                    lastDirectMediaSubmissionEpoch
                        .set(activeRecognizerEpoch)
                }
            }

            // Once recognizeAsync accepts the frame, MediaPipe owns the MPImage
            // wrapper until its callback. Debug-preview failures must never
            // close/recycle an in-flight inference frame.
            pendingImage = null
            submittedTimestamp = null

            // Debug builds may still show the camera mirror, but only after
            // inference submission and only at the mirror's low display rate.
            // Release builds return false here and perform no Bitmap work.
            if (
                TrackingMirrorBridge
                    .shouldCaptureFrame(now)
            ) {
                runCatching {
                    if (imageProxyOwnershipTransferred) {
                        publishMirrorFrameSafely(
                            timestampMs = now,
                            image = image,
                            rotationDegrees = rotation
                        )
                    } else {
                        fallbackBitmap?.let { bitmap ->
                            publishMirrorFrameSafely(
                                timestampMs = now,
                                bitmap = bitmap,
                                rotationDegrees = rotation
                            )
                        }
                    }
                }
            }
            consecutiveFrameFailures = 0
            if (firstSubmittedFrameMs == 0L) {
                firstSubmittedFrameMs = now
            }
            checkVisionWatchdog(now)
        } catch (t: Throwable) {
            runCatching {
                pendingImage?.close()
            }
            submittedTimestamp?.let(
                ::releaseSubmittedFrame
            )

            // Errors from a detached graph cannot affect its replacement.
            if (!isCurrentRecognizer(generation, activeRecognizerEpoch)) return

            if (
                !inferenceSubmitted &&
                attemptedInputMode ==
                    VisionInputMode
                        .DIRECT_MEDIA_IMAGE &&
                !directMediaDisabledForSession
            ) {
                requestDirectMediaFallback(
                    generation = generation,
                    epoch = activeRecognizerEpoch,
                    reason =
                        t.message
                            ?: t.javaClass.simpleName
                )
                return
            }

            // The native delegate may throw directly from recognizeAsync,
            // without invoking the asynchronous error listener.
            if (
                submissionAttempted && !inferenceSubmitted &&
                activeRecognizerDelegate == VisionDelegateMode.GPU &&
                !gpuDisabledForSession
            ) {
                requestGpuFallback(
                    generation = generation,
                    epoch = activeRecognizerEpoch,
                    reason = t.message ?: t.javaClass.simpleName
                )
                return
            }

            if (isCurrentRecognizer(generation, activeRecognizerEpoch)) {
                consecutiveFrameFailures++
                AirRuntime.update {
                    it.copy(
                        lastMessage =
                            "Frame analysis failed (" +
                                consecutiveFrameFailures +
                                "/" +
                                MAX_CONSECUTIVE_FRAME_FAILURES +
                                "): " +
                                (
                                    t.message
                                        ?: t.javaClass
                                            .simpleName
                                    )
                    )
                }

                if (
                    consecutiveFrameFailures >=
                    MAX_CONSECUTIVE_FRAME_FAILURES
                ) {
                    ContextCompat
                        .getMainExecutor(this)
                        .execute mainRecovery@{
                            if (
                                isCurrentRecognizer(generation, activeRecognizerEpoch) &&
                                consecutiveFrameFailures >= MAX_CONSECUTIVE_FRAME_FAILURES
                            ) {
                                failSession(
                                    "Hand tracking repeatedly failed"
                                )
                            }
                        }
                }
            }
        } finally {
            if (!imageProxyOwnershipTransferred) {
                image.close()
            }
        }
    }

    private fun imageProcessingOptions(
        rotationDegrees: Int
    ): ImageProcessingOptions =
        ImageProcessingOptions
            .builder()
            .setRotationDegrees(rotationDegrees)
            .build()

    private fun rotateAndMirror(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
            postScale(-1f, 1f)
        }
        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            false
        )
    }

    @Synchronized
    private fun onResult(
        generation: Long,
        epoch: Long,
        result: GestureRecognizerResult,
        input: MPImage
    ) {
        // LIVE_STREAM hands the submitted MPImage back after inference.
        // Release its native wrapper immediately so frame resources do not
        // accumulate while the session runs for long periods.
        runCatching {
            input.close()
        }

        val timestamp = result.timestampMs()
        val rotationDegrees =
            submittedFrameRotation(
                timestamp
            )
        val imageAspectRatio =
            submittedFrameCanonicalAspectRatio(
                timestamp
            ) ?: 1f
        val pipelineKey =
            submittedFramePipeline(
                timestamp
            )
        releaseSubmittedFramesThrough(
            timestamp
        )

        if (
            !isCurrentRecognizer(
                generation,
                epoch
            )
        ) {
            return
        }

        if (
            timestamp <=
            lastProcessedResultTimestampMs
        ) {
            return
        }
        lastProcessedResultTimestampMs = timestamp

        val resultNow = SystemClock.uptimeMillis()
        lastResultReceivedMs = resultNow
        visionStallReported = false
        performanceMonitor.onResult(
            sourceTimestampMs = timestamp,
            nowMs = resultNow
        )

        val pointerLatencyPriority =
            mappingStore.pointerEnabled()
        val resultAgeMs =
            (resultNow - timestamp)
                .coerceAtLeast(0L)

        val resultFresh =
            VisionLatencyPolicy.resultIsFreshEnough(
                pointerActive =
                    pointerLatencyPriority,
                resultAgeMs = resultAgeMs
            )
        pipelineKey?.let {
            pipelineBenchmark.onResult(
                key = it,
                analysisToResultMs =
                    resultAgeMs,
                stale = !resultFresh
            )
        }

        if (!resultFresh) {
            // Never move the cursor or execute a gesture using a result that
            // describes where the hand was hundreds of milliseconds ago.
            // Releasing it immediately allows the latest camera frame to
            // become the next inference candidate.
            performanceMonitor
                .onStaleResultDropped()
            handleUnusableVisionGap(
                nowMs = resultNow,
                generation = generation,
                epoch = epoch,
                pointerEnabled =
                    pointerLatencyPriority
            )
            publishPerformanceIfDue(
                resultNow
            )
            return
        }

        publishPerformanceIfDue(resultNow)

        val gestures = result.gestures()
        val landmarks = result.landmarks()
        val worldLandmarks = result.worldLandmarks()
        val handedness = result.handedness()

        fun worldPoseFor(index: Int): List<PoseLandmark>? =
            worldLandmarks
                .getOrNull(index)
                ?.takeIf { it.size >= 21 }
                ?.map {
                    PoseLandmark(
                        x = it.x(),
                        y = it.y(),
                        z = it.z()
                    )
                }

        if (
            landmarks.isNotEmpty() &&
            rotationDegrees == null
        ) {
            // A result without its source-frame rotation cannot be mapped
            // safely. Dropping it is preferable to moving the pointer on the
            // wrong axis or executing a gesture in the wrong direction.
            handleUnusableVisionGap(
                nowMs = resultNow,
                generation = generation,
                epoch = epoch,
                pointerEnabled =
                    pointerLatencyPriority
            )
            AirRuntime.update {
                it.copy(
                    lastMessage =
                        "Dropped vision result: missing frame rotation metadata"
                )
            }
            return
        }

        lastUsableResultMs = resultNow
        unusableGapFailSafeActive = false

        val canonicalLandmarks =
            if (rotationDegrees != null) {
                landmarks.map { hand ->
                    hand.map { landmark ->
                        VisionCoordinateMapper
                            .canonicalLandmark(
                                rawX = landmark.x(),
                                rawY = landmark.y(),
                                rawZ = landmark.z(),
                                rotationDegrees =
                                    rotationDegrees
                            )
                    }
                }
            } else {
                emptyList()
            }
        val handPreference =
            controlHandSelector.currentPreference()

        if (landmarks.isEmpty()) {
            controlHandSelector.select(
                timestampMs = timestamp,
                candidates = emptyList()
            )
            AirRuntime.recordControlHand(
                preference = handPreference,
                selection = null,
                detectedCount = 0
            )
            AirRuntime.recordRawPointer(present = false)
            AirRuntime.recordPointerAim(present = false)
            handlePointerTrackingLoss(timestamp)

            interpreter.evaluate(
                HandFrame(
                    timestampMs = timestamp,
                    handPresent = false
                )
            )
            AirRuntime.recordTracking(interpreter.telemetry())
            TrackingMirrorBridge.publishNoHand(
                timestampMs = timestamp,
                trackingState =
                    interpreter.telemetry().state
            )

            val pointerEnabled = mappingStore.pointerEnabled()
            val pointerDecision =
                pointerTracker.update(
                    PointerFrame(
                        timestampMs = timestamp,
                        handPresent = false
                    )
                )
            AirRuntime.recordPointer(
                decision = pointerDecision,
                enabled = pointerEnabled
            )
            AirAccessibilityService.updatePointer(
                decision = pointerDecision,
                enabled = pointerEnabled,
                sourceTimestampUptimeMs =
                    timestamp,
                resultQueuedAtUptimeMs =
                    resultNow,
                shouldApply = {
                    isCurrentRecognizer(generation, epoch)
                }
            )
            return
        }

        val candidates =
            canonicalLandmarks
                .mapIndexedNotNull { index, candidateHand ->
                if (candidateHand.size < 21) {
                    return@mapIndexedNotNull null
                }

                val validLandmarks =
                    candidateHand.take(21).all {
                        LandmarkGeometryGuard
                            .landmarkPlausible(
                                x = it.x,
                                y = it.y,
                                z = it.z
                            )
                    }
                if (!validLandmarks) {
                    return@mapIndexedNotNull null
                }

                // Use the knuckle centroid as the primary translation anchor.
                // The wrist receives only a small weight because wrist flex
                // and forearm rotation can move landmark 0 substantially even
                // when the palm itself has barely translated.
                val mcpCenterX =
                    (
                        candidateHand[5].x +
                            candidateHand[9].x +
                            candidateHand[13].x +
                            candidateHand[17].x
                        ) * 0.25f
                val mcpCenterY =
                    (
                        candidateHand[5].y +
                            candidateHand[9].y +
                            candidateHand[13].y +
                            candidateHand[17].y
                        ) * 0.25f
                val centerX =
                    mcpCenterX * 0.82f +
                        candidateHand[0].x * 0.18f
                val centerY =
                    mcpCenterY * 0.82f +
                        candidateHand[0].y * 0.18f

                val candidateImagePose =
                    candidateHand
                        .take(21)
                        .map {
                            PoseLandmark(
                                x = it.x,
                                y = it.y,
                                z = it.z
                            )
                        }
                val candidatePlanarGeometry =
                    WorldHandGeometryValidator
                        .imageGeometry(
                            image =
                                candidateImagePose,
                            aspectRatio =
                                imageAspectRatio
                        )

                fun candidateDistance(
                    a: Int,
                    b: Int
                ): Float {
                    val pa =
                        candidatePlanarGeometry
                            .getOrNull(a)
                            ?: return 0.12f
                    val pb =
                        candidatePlanarGeometry
                            .getOrNull(b)
                            ?: return 0.12f
                    val dx = pa.x - pb.x
                    val dy = pa.y - pb.y
                    return hypot(dx, dy)
                }

                val scale =
                    (
                        candidateDistance(5, 17) +
                            candidateDistance(0, 9)
                        ) * 0.5f

                if (
                    !LandmarkGeometryGuard
                        .candidatePlausible(
                            centerX = centerX,
                            centerY = centerY,
                            rawScale = scale
                        )
                ) {
                    return@mapIndexedNotNull null
                }

                val handCategory =
                    handedness
                        .getOrNull(index)
                        ?.firstOrNull()
                val candidateWorldPose =
                    worldPoseFor(index)
                val candidateWorldValidation =
                    WorldHandGeometryValidator
                        .validate(
                            world =
                                candidateWorldPose,
                            image =
                                candidateImagePose,
                            imageAspectRatio =
                                imageAspectRatio
                        )
                val identitySignature =
                    if (
                        candidateWorldValidation
                            .reliable &&
                        candidateWorldPose != null
                    ) {
                        HandIdentitySignature
                            .fromGeometry(
                                candidateWorldPose
                            )
                    } else {
                        HandIdentitySignature
                            .fromGeometry(
                                candidatePlanarGeometry
                            )
                    }

                HandCandidate(
                    index = index,
                    centerX = centerX,
                    centerY = centerY,
                    scale = scale.coerceIn(0.035f, 0.45f),
                    handedness =
                        VisionCoordinateMapper
                            .canonicalHandedness(
                                handCategory
                                    ?.categoryName()
                                    ?: "Unknown"
                            ),
                    handednessScore =
                        handCategory
                            ?.score()
                            ?.takeIf { it.isFinite() }
                            ?: 0f,
                    identitySignature =
                        identitySignature
                )
            }

        val controlSelection =
            controlHandSelector.select(
                timestampMs = timestamp,
                candidates = candidates
            )

        AirRuntime.recordControlHand(
            preference = handPreference,
            selection = controlSelection,
            detectedCount = candidates.size
        )

        val pointerEnabled = mappingStore.pointerEnabled()

        // Pointer follows only the continuity-vetted control hand.
        // A single discontinuous detector frame is allowed to coast instead
        // of falling back to another visible hand and producing a cursor jump.
        val pointerCandidate =
            controlSelection?.let { selection ->
                candidates.firstOrNull {
                    it.index == selection.index
                }
            }

        if (
            controlSelection != null &&
            !controlSelection.continuityLocked
        ) {
            worldGeometryTemporalGuard.reset()
        }

        val pointerHand =
            pointerCandidate?.let { candidate ->
                canonicalLandmarks
                    .getOrNull(candidate.index)
            }

        val pointerCategories =
            pointerCandidate?.let { candidate ->
                gestures
                    .getOrNull(candidate.index)
                    .orEmpty()
            }.orEmpty()
        val pointerTopCategory =
            pointerCategories.firstOrNull()
        val pointerModelCategory =
            pointerTopCategory?.categoryName() ?: "None"
        val pointerModelScore =
            pointerTopCategory?.score() ?: 0f
        val pointerImagePose =
            pointerHand
                ?.takeIf { it.size >= 21 }
                ?.map {
                    PoseLandmark(
                        x = it.x,
                        y = it.y,
                        z = it.z
                    )
                }
        val pointerWorldPose =
            pointerCandidate
                ?.let { candidate ->
                    worldPoseFor(
                        candidate.index
                    )
                }
        val pointerWorldValidation =
            WorldHandGeometryValidator
                .validate(
                    world = pointerWorldPose,
                    image = pointerImagePose,
                    imageAspectRatio =
                        imageAspectRatio
                )
        val pointerWorldReliable =
            pointerWorldValidation.reliable &&
                worldGeometryTemporalGuard
                    .accept(
                        timestampMs = timestamp,
                        validation =
                            pointerWorldValidation
                    )
        val trustedPointerWorldPose =
            pointerWorldPose
                ?.takeIf {
                    pointerWorldReliable
                }
        val pointerThumbEvidence =
            pointerImagePose
                ?.let { imagePose ->
                    landmarkPoseRecognizer
                        .thumbExtensionEvidence(
                            landmarks = imagePose,
                            worldLandmarks =
                                trustedPointerWorldPose,
                            imageAspectRatio =
                                imageAspectRatio
                        )
                }
        val pointerCustomPose =
            pointerImagePose
                ?.let { imagePose ->
                    landmarkPoseRecognizer.recognize(
                        landmarks = imagePose,
                        worldLandmarks =
                            trustedPointerWorldPose,
                        imageAspectRatio =
                            imageAspectRatio
                    )
                }
        val pointerEffectivePose =
            PoseCategoryArbiter.choose(
                modelCategory = pointerModelCategory,
                modelScore = pointerModelScore,
                custom = pointerCustomPose,
                physicalThumb =
                    pointerThumbEvidence
            )
        val pointerCustomSignal =
            LandmarkPoseRecognizer.signalForCategory(
                pointerEffectivePose.category
            )
        val pointerCustomActionable =
            pointerCustomSignal?.let {
                mappingStore.actionFor(it) !=
                    AirAction.NONE
            } ?: true
        val pointerPressAllowed =
            if (
                pointerCustomSignal != null &&
                !pointerCustomActionable
            ) {
                true
            } else {
                InteractionArbiter.pointerPressAllowed(
                    category = pointerEffectivePose.category,
                    categoryScore = pointerEffectivePose.score
                )
            }

        val pointerDecision =
            if (
                pointerEnabled &&
                pointerHand != null &&
                pointerHand.size > 12
            ) {
                lastPointerLandmarkMs = timestamp
                pointerEstimatorResetForLoss = false

                AirRuntime.recordRawPointer(
                    present = true,
                    x = pointerHand[8].x,
                    y = pointerHand[8].y
                )

                fun aimLandmark(index: Int): AimLandmark =
                    AimLandmark(
                        x = pointerHand[index].x,
                        y = pointerHand[index].y,
                        z = pointerHand[index].z
                    )

                if (
                    !controlSelection.continuityLocked
                ) {
                    pointerAimEstimator.reset()
                    pointerKinematicFilter.reset()
                    pointerTracker
                        .onControlHandDiscontinuity()
                }

                val pointerImageHandScale =
                    pointerImagePose
                        ?.let {
                            WorldHandGeometryValidator
                                .imageHandScale(
                                    image = it,
                                    aspectRatio =
                                        imageAspectRatio
                                )
                        }
                        ?.takeIf {
                            it.isFinite() &&
                                it > 0f
                        }
                        ?: pointerCandidate.scale

                val worldAimHand =
                    trustedPointerWorldPose
                        ?.map {
                            AimLandmark(
                                x = it.x,
                                y = it.y,
                                z = it.z
                            )
                        }

                // Positional pointer channel: exactly MediaPipe landmark 8.
                // No palm/wrist/other-finger coordinates enter the estimator.
                val rawAim =
                    pointerAimEstimator.estimate(
                        PointerAimInput(
                            indexTip = aimLandmark(8)
                        )
                    )
                // The kinematic estimator is downstream of the #8 measurement.
                // It may smooth/reject the measurement, but it can never replace
                // it with palm, wrist, another finger, or a future prediction.
                val filteredAimPosition =
                    pointerKinematicFilter.update(
                        measuredX = rawAim.x,
                        measuredY = rawAim.y,
                        timestampMs = timestamp,
                        confidence = rawAim.confidence
                    )
                val aim =
                    rawAim.copy(
                        x = filteredAimPosition.first,
                        y = filteredAimPosition.second,
                        rayX = filteredAimPosition.first,
                        rayY = filteredAimPosition.second,
                        reasoning =
                            rawAim.reasoning +
                                " • causal kinematic filter"
                    )

                // Click channel: exactly index tip #8 <-> middle tip #12
                // separation, normalized by validated hand scale. This channel
                // cannot redirect or replace pointer X/Y.
                val pointerImageAimHand =
                    pointerHand
                        .take(21)
                        .mapIndexed { index, _ ->
                            aimLandmark(index)
                        }
                val tipContact =
                    PointerTipContactEstimator.estimate(
                        imageHand = pointerImageAimHand,
                        imageHandScale = pointerImageHandScale,
                        imageAspectRatio = imageAspectRatio,
                        trustedWorldHand = worldAimHand,
                        worldHandScale =
                            pointerWorldValidation
                                .handScale
                                .takeIf {
                                    pointerWorldReliable
                                }
                    )

                AirRuntime.recordPointerAim(
                    present = true,
                    x = aim.x,
                    y = aim.y,
                    confidence = aim.confidence
                )

                pointerTracker.update(
                    PointerFrame(
                        timestampMs = timestamp,
                        handPresent = true,
                        aimX = aim.x,
                        aimY = aim.y,
                        aimConfidence = aim.confidence,
                        aimStable = aim.stable,
                        indexMiddleTipSeparation =
                            tipContact.normalizedSeparation,
                        clickContactReliable =
                            tipContact.reliable,
                        pressAllowed = pointerPressAllowed,
                        resultAgeMs = resultAgeMs
                    )
                )
            } else {
                pointerKinematicFilter.reset()
                AirRuntime.recordRawPointer(present = false)
                AirRuntime.recordPointerAim(present = false)
                handlePointerTrackingLoss(timestamp)
                pointerTracker.update(
                    PointerFrame(
                        timestampMs = timestamp,
                        handPresent = false
                    )
                )
            }

        AirRuntime.recordPointer(
            decision = pointerDecision,
            enabled = pointerEnabled
        )
        AirAccessibilityService.updatePointer(
            decision = pointerDecision,
            enabled = pointerEnabled,
            sourceTimestampUptimeMs =
                timestamp,
            resultQueuedAtUptimeMs =
                resultNow,
            actionDeadlineUptimeMs =
                timestamp +
                    VisionLatencyPolicy
                        .hardMaxResultAgeMs(
                            pointerEnabled
                        ),
            shouldApply = {
                isCurrentRecognizer(generation, epoch)
            }
        )

        if (controlSelection == null) {
            interpreter.evaluate(
                HandFrame(
                    timestampMs = timestamp,
                    handPresent = false
                )
            )
            AirRuntime.recordTracking(
                interpreter.telemetry()
            )
            TrackingMirrorBridge.publishNoHand(
                timestampMs = timestamp,
                trackingState =
                    interpreter.telemetry().state
            )
            return
        }

        if (!controlSelection.continuityLocked) {
            interpreter.resetForControlHandChange()
        }

        val hand =
            canonicalLandmarks[
                controlSelection.index
            ]
        val selectedCandidate =
            candidates.first {
                it.index == controlSelection.index
            }
        val x = selectedCandidate.centerX
        val y = selectedCandidate.centerY
        val handScale = selectedCandidate.scale

        val categories =
            gestures
                .getOrNull(controlSelection.index)
                .orEmpty()
        val topCategory = categories.firstOrNull()
        val modelCategory =
            topCategory?.categoryName() ?: "None"
        val modelScore =
            topCategory?.score() ?: 0f
        val controlImagePose =
            hand
                .takeIf { it.size >= 21 }
                ?.map {
                    PoseLandmark(
                        x = it.x,
                        y = it.y,
                        z = it.z
                    )
                }
        val rawControlWorldPose =
            worldPoseFor(
                controlSelection.index
            )
        val sameAsPointer =
            pointerCandidate?.index ==
                controlSelection.index
        val controlWorldPose =
            if (sameAsPointer) {
                trustedPointerWorldPose
            } else {
                rawControlWorldPose
                    ?.takeIf {
                        WorldHandGeometryValidator
                            .validate(
                                world = it,
                                image =
                                    controlImagePose,
                                imageAspectRatio =
                                    imageAspectRatio
                            )
                            .reliable
                    }
            }
        val customPose =
            if (sameAsPointer) {
                pointerCustomPose
            } else {
                controlImagePose
                    ?.let { imagePose ->
                        landmarkPoseRecognizer.recognize(
                            landmarks = imagePose,
                            worldLandmarks =
                                controlWorldPose,
                            imageAspectRatio =
                                imageAspectRatio
                        )
                    }
            }
        val controlThumbEvidence =
            if (sameAsPointer) {
                pointerThumbEvidence
            } else {
                controlImagePose
                    ?.let { imagePose ->
                        landmarkPoseRecognizer
                            .thumbExtensionEvidence(
                                landmarks = imagePose,
                                worldLandmarks =
                                    controlWorldPose,
                                imageAspectRatio =
                                    imageAspectRatio
                            )
                    }
            }
        val effectivePose =
            PoseCategoryArbiter.choose(
                modelCategory = modelCategory,
                modelScore = modelScore,
                custom = customPose,
                physicalThumb =
                    controlThumbEvidence
            )
        val category = effectivePose.category
        val score = effectivePose.score
        val openPalmScore =
            categories
                .firstOrNull {
                    it.categoryName() == "Open_Palm"
                }
                ?.score()
                ?: if (
                    modelCategory == "Open_Palm"
                ) {
                    modelScore
                } else {
                    0f
                }

        val decision = interpreter.evaluate(
            HandFrame(
                timestampMs = timestamp,
                handPresent = true,
                centerX = x,
                centerY = y,
                handScale = handScale,
                category = category,
                categoryScore = score,
                openPalmScore = openPalmScore
            )
        )

        if (
            TrackingMirrorBridge
                .isCorpusRecordingEnabled() &&
            controlImagePose != null
        ) {
            TrackingMirrorBridge
                .publishCorpusFrame(
                    LandmarkReplayFrame(
                        timestampMs = timestamp,
                        groundTruth = null,
                        modelCategory =
                            modelCategory,
                        modelScore =
                            modelScore,
                        imageAspectRatio =
                            imageAspectRatio,
                        imageLandmarks =
                            controlImagePose,
                        // Preserve raw synchronized world estimates so replay
                        // can re-evaluate the validator instead of recording
                        // only evidence already accepted by today's code.
                        worldLandmarks =
                            rawControlWorldPose,
                        handedness =
                            controlSelection
                                .handedness,
                        handednessScore =
                            controlSelection
                                .handednessScore
                    )
                )
        }

        val trackingTelemetry =
            interpreter.telemetry()
        AirRuntime.recordTracking(
            telemetry = trackingTelemetry,
            poseGeometry =
                PoseGeometryTelemetry(
                    worldBacked =
                        controlThumbEvidence
                            ?.worldBacked == true,
                    thumbExtensionScore =
                        controlThumbEvidence
                            ?.score,
                    thumbWorldScore =
                        controlThumbEvidence
                            ?.worldScore
                )
        )
        if (TrackingMirrorBridge.isCaptureEnabled()) {
            val mirrorPoints =
                FloatArray(42)
            for (index in 0 until 21) {
                mirrorPoints[index * 2] =
                    hand[index].x
                mirrorPoints[index * 2 + 1] =
                    hand[index].y
            }

            TrackingMirrorBridge.publishTracking(
                timestampMs = timestamp,
                points = mirrorPoints,
                centerX = x,
                centerY = y,
                handLabel =
                    controlSelection.handedness,
                handConfidence =
                    controlSelection.handednessScore,
                category = category,
                categoryScore = score,
                trackingState =
                    trackingTelemetry.state,
                gestureLabel =
                    decision?.signal?.label
            )
        }
        decision ?: return

        if (
            !InteractionArbiter.gestureExecutionAllowed(
                pointerEnabled = pointerEnabled,
                pointerState = pointerDecision.state,
                signal = decision.signal
            )
        ) {
            return
        }

        val signal = decision.signal
        val executionGeneration =
            gestureExecutionGeneration
                .incrementAndGet()
        val action = mappingStore.actionFor(signal)

        if (action == AirAction.NONE) {
            AirRuntime.recordGesture(
                gesture = signal,
                action = action,
                success = null,
                message =
                    "${signal.label} recognized • disabled",
                confidence = decision.confidence,
                reasoning = decision.reasoning
            )
            return
        }

        AirAccessibilityService.executeAsync(
            action = action,
            sourceTimestampUptimeMs =
                timestamp,
            resultQueuedAtUptimeMs =
                resultNow,
            deadlineUptimeMs =
                timestamp +
                    VisionLatencyPolicy
                        .hardMaxResultAgeMs(
                            pointerEnabled
                        ),
            shouldExecute = {
                isCurrentRecognizer(generation, epoch) &&
                    gestureExecutionGeneration
                        .get() ==
                    executionGeneration
            }
        ) { resultAction ->
            if (
                !isCurrentRecognizer(generation, epoch) ||
                gestureExecutionGeneration.get() !=
                    executionGeneration
            ) {
                return@executeAsync
            }

            AirRuntime.recordGesture(
                gesture = signal,
                action = action,
                success = resultAction.success,
                message = resultAction.message,
                confidence = decision.confidence,
                reasoning = decision.reasoning
            )
        }
    }

    private fun enqueuePointerCalibration(
        calibration: PointerCalibration
    ) {
        var shouldPost = false
        synchronized(calibrationUpdateLock) {
            pendingPointerCalibration =
                calibration.sanitized()
            if (!calibrationUpdatePosted) {
                calibrationUpdatePosted = true
                shouldPost = true
            }
        }

        if (shouldPost) {
            val queued =
                runCatching {
                    cameraExecutor.execute(
                        ::drainPointerCalibration
                    )
                }.isSuccess

            if (!queued) {
                synchronized(calibrationUpdateLock) {
                    calibrationUpdatePosted = false
                    pendingPointerCalibration = null
                }
            }
        }
    }

    private fun drainPointerCalibration() {
        val value =
            synchronized(calibrationUpdateLock) {
                val latest =
                    pendingPointerCalibration
                pendingPointerCalibration = null
                latest
            }

        if (
            value != null &&
            sessionRequested
        ) {
            synchronized(this) {
                if (sessionRequested) {
                    pointerTracker.updateCalibration(value)
                }
            }
        }

        var repost = false
        synchronized(calibrationUpdateLock) {
            calibrationUpdatePosted = false
            if (
                pendingPointerCalibration != null &&
                sessionRequested
            ) {
                calibrationUpdatePosted = true
                repost = true
            }
        }

        if (repost) {
            val queued =
                runCatching {
                    cameraExecutor.execute(
                        ::drainPointerCalibration
                    )
                }.isSuccess

            if (!queued) {
                synchronized(calibrationUpdateLock) {
                    calibrationUpdatePosted = false
                    pendingPointerCalibration = null
                }
            }
        }
    }

    private fun clearPendingCalibrationUpdates() {
        synchronized(calibrationUpdateLock) {
            pendingPointerCalibration = null
            calibrationUpdatePosted = false
        }
    }

    private fun registerSubmittedBitmap(
        timestampMs: Long,
        bitmap: Bitmap,
        rotationDegrees: Int,
        width: Int,
        height: Int,
        pipelineKey: VisionPipelineKey
    ) {
        val displaced =
            synchronized(submittedBitmapLock) {
                submittedFrameRotations[
                    timestampMs
                ] = rotationDegrees
                submittedFrameDimensions[
                    timestampMs
                ] = width to height
                submittedFramePipelines[
                    timestampMs
                ] = pipelineKey
                submittedBitmaps.put(
                    timestampMs,
                    bitmap
                )
            }

        if (
            displaced != null &&
            displaced !== bitmap &&
            !displaced.isRecycled
        ) {
            displaced.recycle()
        }
    }

    private fun registerSubmittedImageProxy(
        timestampMs: Long,
        image: ImageProxy,
        rotationDegrees: Int,
        width: Int,
        height: Int,
        pipelineKey: VisionPipelineKey
    ) {
        val displaced =
            synchronized(submittedBitmapLock) {
                submittedFrameRotations[
                    timestampMs
                ] = rotationDegrees
                submittedFrameDimensions[
                    timestampMs
                ] = width to height
                submittedFramePipelines[
                    timestampMs
                ] = pipelineKey
                submittedImageProxies.put(
                    timestampMs,
                    image
                )
            }

        if (
            displaced != null &&
            displaced !== image
        ) {
            displaced.close()
        }
    }

    private fun submittedFrameRotation(
        timestampMs: Long
    ): Int? =
        synchronized(submittedBitmapLock) {
            submittedFrameRotations[
                timestampMs
            ]
        }

    private fun submittedFrameCanonicalAspectRatio(
        timestampMs: Long
    ): Float? =
        synchronized(submittedBitmapLock) {
            val rotation =
                submittedFrameRotations[
                    timestampMs
                ] ?: return@synchronized null
            val dimensions =
                submittedFrameDimensions[
                    timestampMs
                ] ?: return@synchronized null
            val width =
                dimensions.first.coerceAtLeast(1)
            val height =
                dimensions.second.coerceAtLeast(1)
            val canonicalWidth =
                if (
                    rotation == 90 ||
                    rotation == 270
                ) {
                    height
                } else {
                    width
                }
            val canonicalHeight =
                if (
                    rotation == 90 ||
                    rotation == 270
                ) {
                    width
                } else {
                    height
                }
            canonicalWidth.toFloat() /
                canonicalHeight.toFloat()
        }

    private fun submittedFramePipeline(
        timestampMs: Long
    ): VisionPipelineKey? =
        synchronized(submittedBitmapLock) {
            submittedFramePipelines[
                timestampMs
            ]
        }

    private fun submittedFrameCount(): Int =
        synchronized(submittedBitmapLock) {
            submittedBitmaps.size +
                submittedImageProxies.size
        }

    private fun releaseSubmittedFrame(
        timestampMs: Long
    ) {
        val resources =
            synchronized(submittedBitmapLock) {
                submittedFrameRotations.remove(
                    timestampMs
                )
                submittedFrameDimensions.remove(
                    timestampMs
                )
                submittedFramePipelines.remove(
                    timestampMs
                )
                submittedBitmaps.remove(
                    timestampMs
                ) to
                    submittedImageProxies.remove(
                        timestampMs
                    )
            }

        resources.first?.let { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        resources.second?.close()
    }

    private fun publishMirrorFrameSafely(
        timestampMs: Long,
        bitmap: Bitmap,
        rotationDegrees: Int
    ) {
        synchronized(submittedBitmapLock) {
            val stillOwned =
                submittedBitmaps[timestampMs] ===
                    bitmap
            if (
                stillOwned &&
                !bitmap.isRecycled
            ) {
                publishMirrorBitmap(
                    timestampMs = timestampMs,
                    bitmap = bitmap,
                    rotationDegrees = rotationDegrees
                )
            }
        }
    }

    private fun publishMirrorFrameSafely(
        timestampMs: Long,
        image: ImageProxy,
        rotationDegrees: Int
    ) {
        synchronized(submittedBitmapLock) {
            val stillOwned =
                submittedImageProxies[
                    timestampMs
                ] === image
            if (!stillOwned) {
                return
            }

            val bitmap =
                runCatching {
                    image.toBitmap()
                }.getOrNull()
                    ?: return

            try {
                publishMirrorBitmap(
                    timestampMs = timestampMs,
                    bitmap = bitmap,
                    rotationDegrees = rotationDegrees
                )
            } finally {
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        }
    }

    private fun publishMirrorBitmap(
        timestampMs: Long,
        bitmap: Bitmap,
        rotationDegrees: Int
    ) {
        val mirrorBitmap =
            rotateAndMirror(
                bitmap,
                rotationDegrees
            )
        try {
            TrackingMirrorBridge.publishFrame(
                source = mirrorBitmap,
                timestampMs = timestampMs
            )
        } finally {
            if (
                mirrorBitmap !== bitmap &&
                !mirrorBitmap.isRecycled
            ) {
                mirrorBitmap.recycle()
            }
        }
    }

    private fun releaseSubmittedFramesThrough(
        timestampMs: Long
    ) {
        synchronized(submittedBitmapLock) {
            val bitmapIterator =
                submittedBitmaps.entries
                    .iterator()

            while (bitmapIterator.hasNext()) {
                val entry = bitmapIterator.next()
                if (entry.key <= timestampMs) {
                    val bitmap = entry.value
                    bitmapIterator.remove()
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                }
            }

            val rotationIterator =
                submittedFrameRotations
                    .entries
                    .iterator()

            while (
                rotationIterator.hasNext()
            ) {
                if (
                    rotationIterator
                        .next()
                        .key <= timestampMs
                ) {
                    rotationIterator.remove()
                }
            }

            val dimensionIterator =
                submittedFrameDimensions.entries
                    .iterator()
            while (
                dimensionIterator.hasNext()
            ) {
                if (
                    dimensionIterator
                        .next()
                        .key <= timestampMs
                ) {
                    dimensionIterator.remove()
                }
            }

            val pipelineIterator =
                submittedFramePipelines.entries
                    .iterator()
            while (
                pipelineIterator.hasNext()
            ) {
                if (
                    pipelineIterator
                        .next()
                        .key <= timestampMs
                ) {
                    pipelineIterator.remove()
                }
            }

            val imageIterator =
                submittedImageProxies.entries
                    .iterator()

            while (imageIterator.hasNext()) {
                val entry = imageIterator.next()
                if (entry.key <= timestampMs) {
                    val image = entry.value
                    imageIterator.remove()
                    image.close()
                }
            }
        }
    }

    private fun closeRecognizerBeforeFrameRelease(
        detached: GestureRecognizer?,
        context: String
    ): Boolean {
        if (detached == null) return true

        val outcome =
            NativeGraphCloseGuard.attempt {
                detached.close()
            }
        if (outcome.safeToReleaseFrames) {
            return true
        }

        submittedFramesQuarantined = true
        val detail =
            outcome.failureMessage
                ?.takeIf { it.isNotBlank() }
                ?.let { " • $it" }
                .orEmpty()
        AirRuntime.recordVisionFallback(
            "Native graph close failed • $context$detail"
        )
        AirRuntime.update {
            it.copy(
                recognizerReady = false,
                lastMessage =
                    "Native vision shutdown failed • session stopped safely"
            )
        }
        return false
    }

    private fun releaseAllSubmittedFrames() {
        if (submittedFramesQuarantined) {
            return
        }

        val pendingBitmaps: List<Bitmap>
        val pendingImages: List<ImageProxy>

        synchronized(submittedBitmapLock) {
            pendingBitmaps =
                submittedBitmaps.values
                    .toList()
            pendingImages =
                submittedImageProxies.values
                    .toList()
            submittedBitmaps.clear()
            submittedImageProxies.clear()
            submittedFrameRotations.clear()
            submittedFrameDimensions.clear()
            submittedFramePipelines.clear()
        }

        pendingBitmaps.forEach { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        pendingImages.forEach(
            ImageProxy::close
        )
    }

    private fun startVisionWatchdog() {
        mainHandler.removeCallbacks(
            visionWatchdogRunnable
        )
        mainHandler.postDelayed(
            visionWatchdogRunnable,
            VISION_WATCHDOG_POLL_MS
        )
    }

    private fun stopVisionWatchdog() {
        mainHandler.removeCallbacks(
            visionWatchdogRunnable
        )
    }

    private fun visionIsStalled(nowMs: Long): Boolean =
        VisionWatchdogPolicy.isStalled(
            firstSubmittedMs = firstSubmittedFrameMs,
            lastResultMs = lastUsableResultMs,
            nowMs = nowMs,
            timeoutMs = VISION_STALL_TIMEOUT_MS,
            recovering = directMediaRecoveryInProgress || delegateRecoveryInProgress
        )

    private fun checkVisionWatchdog(nowMs: Long) {
        val generation = sessionGeneration.get()
        val epoch = recognizerEpoch.get()
        if (
            !isCurrentRecognizer(generation, epoch) ||
            visionStallReported || !visionIsStalled(nowMs)
        ) return

        visionStallReported = true
        ContextCompat.getMainExecutor(this).execute {
            // Recheck both identity and liveness at execution time: a result,
            // delegate rebuild or Stop→Start may have overtaken this message.
            try {
                if (
                    isCurrentRecognizer(generation, epoch) &&
                    visionIsStalled(SystemClock.uptimeMillis())
                ) {
                    failSession("Hand tracking stalled • restart Air Control")
                }
            } finally {
                // Even a stale queued task must release the pending flag.
                // Polling continues so cancellation cannot disable the watchdog.
                visionStallReported = false
            }
        }
    }

    private fun handlePointerTrackingLoss(
        timestampMs: Long
    ) {
        val lastSeen = lastPointerLandmarkMs

        if (
            lastSeen == null ||
            timestampMs - lastSeen >
                POINTER_ESTIMATOR_LOSS_RESET_MS
        ) {
            if (!pointerEstimatorResetForLoss) {
                pointerAimEstimator.reset()
                pointerEstimatorResetForLoss = true
            }
        }
    }

    private fun handleUnusableVisionGap(
        nowMs: Long,
        generation: Long,
        epoch: Long,
        pointerEnabled: Boolean
    ) {
        if (
            !UsableVisionGapPolicy
                .shouldFailSafeReset(
                    firstSubmittedMs =
                        firstSubmittedFrameMs,
                    lastUsableResultMs =
                        lastUsableResultMs,
                    nowMs = nowMs,
                    pointerActive =
                        pointerEnabled,
                    alreadyApplied =
                        unusableGapFailSafeActive
                )
        ) {
            return
        }

        unusableGapFailSafeActive = true
        gestureExecutionGeneration
            .incrementAndGet()

        val pointerDecision =
            synchronized(this) {
                pointerAimEstimator.reset()
                interpreter.evaluate(
                    HandFrame(
                        timestampMs = nowMs,
                        handPresent = false
                    )
                )
                controlHandSelector.select(
                    timestampMs = nowMs,
                    candidates = emptyList()
                )
                pointerTracker.update(
                    PointerFrame(
                        timestampMs = nowMs,
                        handPresent = false
                    )
                )
            }

        AirRuntime.recordControlHand(
            preference =
                controlHandSelector
                    .currentPreference(),
            selection = null,
            detectedCount = 0
        )
        AirRuntime.recordRawPointer(
            present = false
        )
        AirRuntime.recordPointerAim(
            present = false
        )
        AirRuntime.recordTracking(
            interpreter.telemetry()
        )
        AirRuntime.recordPointer(
            decision = pointerDecision,
            enabled = pointerEnabled
        )
        AirAccessibilityService.setPointerEnabled(false)
        AirAccessibilityService.updatePointer(
            decision = pointerDecision,
            enabled = pointerEnabled,
            shouldApply = {
                isCurrentRecognizer(
                    generation,
                    epoch
                )
            }
        )
        AirRuntime.update {
            it.copy(
                lastMessage =
                    "Fresh tracking data lost • pointer and pending intent reset"
            )
        }
    }

    private fun publishPerformanceIfDue(nowMs: Long) {
        if (
            nowMs - lastPerformancePublishMs <
            PERFORMANCE_PUBLISH_INTERVAL_MS
        ) {
            return
        }

        lastPerformancePublishMs = nowMs
        AirRuntime.recordVisionPerformance(
            performanceMonitor.snapshot()
        )
        AirRuntime.recordVisionPipelineBenchmark(
            pipelineBenchmark.summary()
        )
    }

    private fun onRecognizerError(
        generation: Long,
        epoch: Long,
        delegateMode: VisionDelegateMode,
        error: RuntimeException
    ) {
        if (
            !isCurrentRecognizer(
                generation,
                epoch
            )
        ) {
            return
        }

        if (
            !directMediaDisabledForSession &&
            lastDirectMediaSubmissionEpoch
                .get() == epoch
        ) {
            requestDirectMediaFallback(
                generation = generation,
                epoch = epoch,
                reason =
                    error.message
                        ?: error.javaClass
                            .simpleName
            )
            return
        }

        if (
            delegateMode ==
                VisionDelegateMode.GPU &&
            !gpuDisabledForSession
        ) {
            requestGpuFallback(
                generation = generation,
                epoch = epoch,
                reason =
                    error.message
                        ?: error.javaClass
                            .simpleName
            )
            return
        }

        ContextCompat.getMainExecutor(this).execute {
            if (
                isCurrentRecognizer(
                    generation,
                    epoch
                )
            ) {
                failSession(
                    "Gesture recognition stopped unexpectedly"
                )
            }
        }
    }

    private fun resetVisionAfterRecognizerRebuild() {
        performanceMonitor.reset()
        lastPerformancePublishMs = 0L
        lastSubmittedFrameMs = 0L
        firstSubmittedFrameMs = 0L
        lastResultReceivedMs = 0L
        lastProcessedResultTimestampMs =
            Long.MIN_VALUE
        lastVisionInputMode = null
        lastVisionRotationDegrees = null
        lastPointerLandmarkMs = null
        pointerEstimatorResetForLoss = false
        consecutiveFrameFailures = 0
        visionStallReported = false

        synchronized(this) {
            pointerAimEstimator.reset()
            pointerTracker
                .resetForControlHandChange()
            interpreter
                .resetForControlHandChange()
            controlHandSelector
                .resetTracking()
        }

        AirRuntime.recordVisionPerformance(
            performanceMonitor.snapshot()
        )
        AirRuntime.update {
            it.copy(
                visionInputMode = "—",
                visionRotationDegrees = null
            )
        }
    }

    private fun requestDirectMediaFallback(
        generation: Long,
        epoch: Long,
        reason: String
    ) {
        var oldRecognizer: GestureRecognizer? =
            null

        synchronized(recognizerLock) {
            if (
                !isCurrentSession(generation) ||
                recognizerEpoch.get() != epoch ||
                directMediaDisabledForSession ||
                directMediaRecoveryInProgress ||
                delegateRecoveryInProgress
            ) {
                return
            }

            directMediaDisabledForSession = true
            directMediaRecoveryInProgress = true

            // Invalidate callbacks from the old graph before detaching it.
            recognizerEpoch.incrementAndGet()
            oldRecognizer = recognizer
            recognizer = null

            // Keep detach and recovery submission atomic with teardown.
            // onDestroy acquires this lock before shutting the executor down;
            // it must not strand a graph between detachment and queueing.

            AirRuntime.recordVisionInputMode(
                VisionInputMode.BITMAP
            )
            AirRuntime.recordVisionFallback(
                "Direct media → Bitmap • " +
                    reason
            )
            val diagnosticReason =
                reason
                    .lineSequence()
                    .firstOrNull()
                    .orEmpty()
                    .take(72)
            AirRuntime.update {
                it.copy(
                    lastMessage =
                        if (
                            diagnosticReason
                                .isBlank()
                        ) {
                            "Direct camera input unavailable • switching to compatibility mode"
                        } else {
                            "Direct camera input unavailable • " +
                                diagnosticReason
                        }
                )
            }

            val queued =
                runCatching {
                    cameraExecutor.execute recovery@{
                        if (
                            !closeRecognizerBeforeFrameRelease(
                                detached = oldRecognizer,
                                context =
                                    "direct-media recovery"
                            )
                        ) {
                            directMediaRecoveryInProgress =
                                false
                            ContextCompat
                                .getMainExecutor(this)
                                .execute {
                                    if (
                                        isCurrentSession(
                                            generation
                                        )
                                    ) {
                                        failSession(
                                            "Native vision shutdown failed during camera recovery"
                                        )
                                    }
                                }
                            return@recovery
                        }
                        releaseAllSubmittedFrames()

                        resetVisionAfterRecognizerRebuild()

                        if (
                            !isCurrentSession(
                                generation
                            )
                        ) {
                            directMediaRecoveryInProgress =
                                false
                            return@recovery
                        }

                        val recovered =
                            setupRecognizer(
                                generation
                            )
                        directMediaRecoveryInProgress =
                            false

                        ContextCompat
                            .getMainExecutor(this)
                            .execute mainRecovery@{
                                if (
                                    !isCurrentSession(
                                        generation
                                    )
                                ) {
                                    return@mainRecovery
                                }

                                if (recovered) {
                                    AirRuntime.update {
                                        it.copy(
                                            recognizerReady =
                                                true,
                                            lastMessage =
                                                "Compatibility camera mode active"
                                        )
                                    }
                                } else {
                                    failSession(
                                        "Gesture recognizer could not recover"
                                    )
                                }
                            }
                    }
                }.isSuccess

            if (!queued) {
                directMediaRecoveryInProgress =
                    false
                ContextCompat
                    .getMainExecutor(this)
                    .execute {
                        if (
                            isCurrentSession(
                                generation
                            )
                        ) {
                            failSession(
                                "Vision compatibility recovery failed"
                            )
                        }
                    }
            }
        }
    }

    private fun requestGpuFallback(
        generation: Long,
        epoch: Long,
        reason: String
    ) {
        var oldRecognizer: GestureRecognizer? =
            null

        synchronized(recognizerLock) {
            if (
                !isCurrentSession(generation) ||
                recognizerEpoch.get() != epoch ||
                gpuDisabledForSession ||
                delegateRecoveryInProgress ||
                directMediaRecoveryInProgress ||
                activeRecognizerDelegate !=
                    VisionDelegateMode.GPU
            ) {
                return
            }

            gpuDisabledForSession = true
            delegateRecoveryInProgress = true

            // Invalidate every callback from the failed GPU graph before it is
            // detached. The replacement CPU recognizer gets a fresh epoch.
            recognizerEpoch.incrementAndGet()
            oldRecognizer = recognizer
            recognizer = null

            // Keep detach and recovery submission atomic with teardown.
            // onDestroy acquires this lock before shutting the executor down;
            // it must not strand a graph between detachment and queueing.

            AirRuntime.recordVisionDelegate(
                VisionDelegateMode.CPU
            )
            AirRuntime.recordVisionFallback(
                "GPU → CPU • " +
                    reason
            )
            val diagnosticReason =
                reason
                    .lineSequence()
                    .firstOrNull()
                    .orEmpty()
                    .take(72)
            AirRuntime.update {
                it.copy(
                    lastMessage =
                        if (
                            diagnosticReason.isBlank()
                        ) {
                            "GPU inference unavailable • switching to CPU"
                        } else {
                            "GPU inference unavailable • " +
                                diagnosticReason
                        }
                )
            }

            val queued =
                runCatching {
                    cameraExecutor.execute gpuRecovery@{
                        if (
                            !closeRecognizerBeforeFrameRelease(
                                detached = oldRecognizer,
                                context = "GPU fallback"
                            )
                        ) {
                            delegateRecoveryInProgress =
                                false
                            ContextCompat
                                .getMainExecutor(this)
                                .execute {
                                    if (
                                        isCurrentSession(
                                            generation
                                        )
                                    ) {
                                        failSession(
                                            "Native vision shutdown failed during GPU fallback"
                                        )
                                    }
                                }
                            return@gpuRecovery
                        }
                        releaseAllSubmittedFrames()
                        resetVisionAfterRecognizerRebuild()

                        if (
                            !isCurrentSession(generation)
                        ) {
                            delegateRecoveryInProgress =
                                false
                            return@gpuRecovery
                        }

                        val recovered =
                            setupRecognizer(
                                generation
                            )
                        delegateRecoveryInProgress =
                            false

                        ContextCompat
                            .getMainExecutor(this)
                            .execute mainGpuRecovery@{
                                if (
                                    !isCurrentSession(
                                        generation
                                    )
                                ) {
                                    return@mainGpuRecovery
                                }

                                if (recovered) {
                                    AirRuntime.update {
                                        it.copy(
                                            recognizerReady =
                                                true,
                                            lastMessage =
                                                "CPU inference fallback active"
                                        )
                                    }
                                } else {
                                    failSession(
                                        "Gesture recognizer could not recover on CPU"
                                    )
                                }
                            }
                    }
                }.isSuccess

            if (!queued) {
                delegateRecoveryInProgress = false
                ContextCompat
                    .getMainExecutor(this)
                    .execute {
                        if (
                            isCurrentSession(
                                generation
                            )
                        ) {
                            failSession(
                                "GPU fallback recovery failed"
                            )
                        }
                    }
            }
        }
    }

    private fun reconcileRecognizerDelegate(
        pointerEnabled: Boolean
    ) {
        if (
            !sessionRequested ||
            directMediaRecoveryInProgress ||
            delegateRecoveryInProgress
        ) {
            return
        }

        val desired =
            VisionDelegatePolicy.preferred(
                pointerActive = pointerEnabled,
                gpuAllowed =
                    !gpuDisabledForSession
            )

        val generation =
            sessionGeneration.get()
        var oldRecognizer: GestureRecognizer? =
            null

        synchronized(recognizerLock) {
            if (
                !isCurrentSession(generation) ||
                recognizer == null ||
                activeRecognizerDelegate ==
                    desired
            ) {
                return
            }

            delegateRecoveryInProgress = true
            recognizerEpoch.incrementAndGet()
            oldRecognizer = recognizer
            recognizer = null
        }

        if (
            !closeRecognizerBeforeFrameRelease(
                detached = oldRecognizer,
                context = "delegate switch"
            )
        ) {
            delegateRecoveryInProgress = false
            ContextCompat
                .getMainExecutor(this)
                .execute {
                    if (
                        isCurrentSession(
                            generation
                        )
                    ) {
                        failSession(
                            "Native vision shutdown failed during inference-mode switch"
                        )
                    }
                }
            return
        }
        releaseAllSubmittedFrames()
        resetVisionAfterRecognizerRebuild()

        if (!isCurrentSession(generation)) {
            delegateRecoveryInProgress = false
            return
        }

        AirRuntime.update {
            it.copy(
                recognizerReady = false,
                lastMessage =
                    if (
                        desired ==
                            VisionDelegateMode.GPU
                    ) {
                        "Switching pointer inference to GPU…"
                    } else {
                        "Switching gesture inference to CPU…"
                    }
            )
        }

        val recovered =
            setupRecognizer(
                generation
            )
        delegateRecoveryInProgress = false

        if (!recovered) {
            ContextCompat
                .getMainExecutor(this)
                .execute {
                    if (
                        isCurrentSession(
                            generation
                        )
                    ) {
                        failSession(
                            "Gesture recognizer could not switch inference mode"
                        )
                    }
                }
        }
    }

    private fun isCurrentRecognizer(
        generation: Long,
        epoch: Long
    ): Boolean =
        isCurrentSession(generation) &&
            recognizerEpoch.get() == epoch

    private fun isCurrentSession(
        generation: Long
    ): Boolean =
        sessionRequested &&
            sessionGeneration.get() == generation

    private fun failSession(message: String) {
        stopSession(message)
    }

    /**
     * Detach the active MediaPipe graph immediately so no new frame can
     * submit to it, then close it on the camera executor where recognizer
     * creation and recognizeAsync() also run.
     *
     * Frame resources are released only after close() completes because a
     * live-stream graph may still be reading their backing buffers.
     */
    private fun detachRecognizerAndQueueCleanup() {
        val detached =
            synchronized(recognizerLock) {
                val current = recognizer
                recognizer = null
                current
            }

        val queued =
            runCatching {
                cameraExecutor.execute {
                    if (
                        closeRecognizerBeforeFrameRelease(
                            detached = detached,
                            context = "session teardown"
                        )
                    ) {
                        releaseAllSubmittedFrames()
                    }
                }
            }.isSuccess

        if (!queued) {
            // Executor rejection is an abnormal teardown path. Prefer an
            // emergency synchronous close/release over leaking the camera
            // graph or ImageProxy buffers. Normal lifecycle always uses the
            // thread-affine executor path above.
            if (
                closeRecognizerBeforeFrameRelease(
                    detached = detached,
                    context =
                        "emergency synchronous teardown"
                )
            ) {
                releaseAllSubmittedFrames()
            }
        }
    }

    private fun stopSession(
        message: String = "Air control stopped"
    ) {
        sessionRequested = false
        stopVisionWatchdog()
        TrackingMirrorBridge.setActive(false)
        sessionGeneration.incrementAndGet()
        recognizerEpoch.incrementAndGet()
        cameraProvider?.unbindAll()
        cameraProvider = null
        cameraStarted = false
        gestureExecutionGeneration
            .incrementAndGet()
        detachRecognizerAndQueueCleanup()
        clearPendingCalibrationUpdates()
        synchronized(this) {
            pointerAimEstimator.reset()
            pointerTracker.resetForControlHandChange()
            interpreter.resetForControlHandChange()
            controlHandSelector.resetTracking()
            worldGeometryTemporalGuard.reset()
        }
        lastPointerLandmarkMs = null
        pointerEstimatorResetForLoss = false

        AirAccessibilityService.setPointerEnabled(false)
        AirRuntime.clearSessionTransientState(message)

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        sessionRequested = false
        stopVisionWatchdog()
        TrackingMirrorBridge.setActive(false)
        sessionGeneration.incrementAndGet()
        recognizerEpoch.incrementAndGet()
        if (instance === this) {
            instance = null
        }
        runCatching {
            unregisterReceiver(screenOffReceiver)
        }
        cameraProvider?.unbindAll()
        detachRecognizerAndQueueCleanup()
        // Graceful shutdown preserves the queued recognizer close and frame
        // release. shutdownNow() could discard that task and leak GPU/native
        // resources once a hardware delegate is introduced.
        cameraExecutor.shutdown()
        AirAccessibilityService.setPointerEnabled(false)
        AirRuntime.clearSessionTransientState()
        super.onDestroy()
    }

    private fun buildSessionNotification(): android.app.Notification {
        val stopIntent = Intent(
            this,
            GestureCaptureService::class.java
        ).setAction(ACTION_STOP)

        val stopPendingIntent = PendingIntent.getService(
            this,
            NOTIFICATION_ID,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        val openPendingIntent =
            PendingIntent.getActivity(
                this,
                NOTIFICATION_ID + 1,
                Intent(
                    this,
                    MainActivity::class.java
                ).apply {
                    flags =
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_air_control)
            .setContentTitle("Aergis active")
            .setContentText("Front camera hand tracking is running")
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop",
                stopPendingIntent
            )
            .build()
    }

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Air Gesture Control session",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description =
                        "Visible while the camera is recognizing air gestures"
                }
            )
    }

    companion object {
        @Volatile
        private var instance: GestureCaptureService? = null

        fun updateControlHandPreference(
            preference: ControlHandPreference
        ) {
            val service = instance ?: return
            runCatching {
                service.cameraExecutor.execute {
                    if (instance === service) {
                        synchronized(service) {
                            service.controlHandSelector
                                .updatePreference(
                                    preference
                                )
                            service.interpreter
                                .resetForControlHandChange()
                            service.pointerAimEstimator
                                .reset()
                            service.pointerTracker
                                .resetForControlHandChange()
                            service.pointerTracker
                                .updateCalibration(
                                    service.mappingStore
                                        .pointerCalibration(
                                            service.currentCalibrationContext(
                                                preference
                                            )
                                        )
                                )
                        }
                        AirRuntime.recordControlHand(
                            preference = preference,
                            selection = null,
                            detectedCount = 0
                        )
                    }
                }
            }
        }

        fun updatePointerMode() {
            val service = instance ?: return
            runCatching {
                service.cameraExecutor.execute {
                    if (
                        instance === service &&
                        service.sessionRequested
                    ) {
                        service
                            .reconcileRecognizerDelegate(
                                pointerEnabled = service.mappingStore.pointerEnabled()
                            )
                    }
                }
            }
        }

        fun updatePointerCalibration(
            calibration: PointerCalibration
        ) {
            val service = instance ?: return
            if (instance === service) {
                service.enqueuePointerCalibration(
                    calibration
                )
            }
        }

        const val ACTION_START = "com.airgesture.control.START"
        const val ACTION_STOP = "com.airgesture.control.STOP"
        const val CHANNEL_ID = "air_gesture_camera"
        const val NOTIFICATION_ID = 814
        private const val MODEL_ASSET = "gesture_recognizer.task"
        private const val POINTER_ESTIMATOR_LOSS_RESET_MS = 260L
        private const val PERFORMANCE_PUBLISH_INTERVAL_MS = 500L
        private const val VISION_STALL_TIMEOUT_MS = 1800L
        private const val VISION_WATCHDOG_POLL_MS = 450L
        private const val MAX_CONSECUTIVE_FRAME_FAILURES = 5
    }
}

