package com.snap.camerakit.sample.basic.helper
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.ImageReader
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import android.util.SizeF
import android.view.Surface
import android.view.WindowManager
import androidx.annotation.MainThread
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraSelector.LensFacing
import androidx.camera.core.DisplayOrientedMeteringPointFactory
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.core.ZoomState
import androidx.camera.core.impl.LensFacingCameraFilter
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.snap.camerakit.ImageProcessor
import com.snap.camerakit.Source
import com.snap.camerakit.common.Consumer
import com.snap.camerakit.connectOutput
import com.snap.camerakit.invoke
import com.snap.camerakit.processBitmap
import com.snap.camerakit.support.camera.AllowsCameraFlash
import com.snap.camerakit.support.camera.AllowsCameraFocus
import com.snap.camerakit.support.camera.AllowsCameraPreview
import com.snap.camerakit.support.camera.AllowsCameraZoom
import com.snap.camerakit.support.camera.AllowsPhotoCapture
import com.snap.camerakit.support.camera.AllowsSnapshotCapture
import com.snap.camerakit.support.camera.AllowsVideoCapture
import com.snap.camerakit.support.camera.Crop
import com.snap.camerakit.support.camera.captureSize
import com.snap.camerakit.support.camera.rotatedTextureSize
import com.snap.camerakit.support.camera.waitFor
import com.snap.camerakit.toBitmap
import java.io.Closeable
import java.io.File
import java.lang.Math.toDegrees
import java.lang.Math.toRadians
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan
import com.snap.camerakit.support.camera.AspectRatio as ConfigurationAspectRatio

internal const val TAG = "CameraXImageProcSrc"
private const val DEFAULT_PREVIEW_STOP_TIMEOUT_SECONDS = 5L
private val EMPTY_CLOSEABLE = Closeable { }
private val CENTER_FOCUS_POINT: FocusMeteringAction = FocusMeteringAction.Builder(
    SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(0.5f, 0.5f)
).disableAutoCancel().build()

private typealias InputWithOptions = Pair<ImageProcessor.Input, Set<ImageProcessor.Input.Option>>

/**
 * A simple implementation of [Source] for [ImageProcessor] which allows to start camera preview streaming frames
 * that are delivered to [com.snap.camerakit.Session.processor]. It demonstrates how to take a snapshot
 * image (photo) using [ImageProcessor.toBitmap] helper method that utilizes [ImageReader] under the hood. Also, since
 * CameraKit has a built-in support to render processed output to a video file, this class leverages it in [takeVideo].
 * If the CameraKit built-in video recording support is not suitable, one can implement it using a similar approach to
 * photo snapshot by connecting [android.media.MediaRecorder] surface as another output to record continuous frames
 * into a video file.
 */
class WebcamImageProcessorSource @JvmOverloads constructor(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    private val executorService: ExecutorService = Executors.newSingleThreadExecutor(),
    private val videoOutputDirectory: File =
        context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir,
    private inline val mainLooperProvider: () -> Looper = { Looper.getMainLooper() }
) : Source<ImageProcessor>,
    AllowsCameraPreview,
    AllowsSnapshotCapture,
    AllowsPhotoCapture,
    AllowsVideoCapture,
    AllowsCameraFocus,
    AllowsCameraZoom,
    AllowsCameraFlash {

    private val applicationContext: Context = context.applicationContext
    private val lifecycleOwnerWeakRef = WeakReference(lifecycleOwner)
    private val mainExecutor = ContextCompat.getMainExecutor(applicationContext)
    private val lastImageProcessor = AtomicReference<ImageProcessor>()
    private var imageProcessorInputConnection = AtomicReference<Closeable>()
    private val connectedImageProcessorInput = AtomicReference<InputWithOptions>()
    private val waitingForImageProcessorTask = AtomicReference<Future<*>>()
    private val userOperationTask = AtomicReference<Future<*>>()

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var previewOutput: PreviewOutput? = null
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var zoomState: ZoomState? = null
    private var activePreviewRequest: PreviewRequest? = null
    private var flashConfiguration: AllowsCameraFlash.FlashConfiguration = AllowsCameraFlash.FlashConfiguration.Disabled
    private var previousFocusAction: FocusMeteringAction = CENTER_FOCUS_POINT

    override fun attach(processor: ImageProcessor): Closeable {
        lastImageProcessor.set(processor)
        return Closeable {
            if (lastImageProcessor.compareAndSet(processor, null)) {
                val mainLooper = mainLooperProvider()
                if (mainLooper.thread === Thread.currentThread()) {
                    stopPreview()
                } else {
                    val latch = CountDownLatch(1)
                    Handler(mainLooper).postAtFrontOfQueue {
                        stopPreview()
                        latch.countDown()
                    }
                    if (!latch.await(DEFAULT_PREVIEW_STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        Log.w(TAG, "Timed out while waiting to stop camera preview")
                    }
                }
            } else {
                throw IllegalStateException("Unexpected ImageProcessor set before it was cleared")
            }
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    @MainThread
    override fun startPreview(
        configuration: AllowsCameraPreview.Configuration,
        inputOptions: Set<ImageProcessor.Input.Option>,
        callback: (succeeded: Boolean) -> Unit
    ) {
        val previewRequest = PreviewRequest(configuration, inputOptions)
        if (activePreviewRequest != previewRequest) {
            stopPreview()
            activePreviewRequest = previewRequest
        } else {
            // Preview is already running or initializing.
            callback(true)
            return
        }
        val cameraProviderFuture = ProcessCameraProvider.getInstance(applicationContext)
        cameraProviderFuture.addListener(
            {
                lifecycleOwnerWeakRef.get()?.let { lifecycleOwner ->
                    if (lifecycleOwner.lifecycle.currentState != Lifecycle.State.DESTROYED &&
                        activePreviewRequest == previewRequest
                    ) {
                        cameraProviderFuture.get().let { cameraProvider ->
                            this.cameraProvider = cameraProvider
                            val lensFacing = if (configuration.facingFront) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                            val cameraSelector = CameraSelector.Builder()
                                .apply {
                                    try {
                                        // Some devices (Android TV) might have only a single (usb) camera attached
                                        // resulting in requireLensFacing failing due to no camera with a specified
                                        // lens facing found - the lens facing for such cameras is typically null
                                        // and we would like to be able to open them nevertheless.
                                        addCameraFilter(WebcamCameraFilter())
                                    } catch (e: ClassNotFoundException) {
                                        Log.w(
                                            TAG,
                                            "Failed to create LenientLensFacingCameraFilter, " +
                                                    "falling back to requireLensFacing($lensFacing)",
                                            e
                                        )
                                        requireLensFacing(lensFacing)
                                    }
                                }
                                .build()

                            @AspectRatio.Ratio val aspectRatio = when (configuration) {
                                is AllowsCameraPreview.Configuration.Default -> {
                                    configurationRatioToCameraXRatio(configuration.aspectRatio)
                                }
                                else -> {
                                    AspectRatio.RATIO_16_9
                                }
                            }

                            val preview = Preview.Builder()
                                .setTargetAspectRatio(aspectRatio)
                                .setTargetRotation(applicationContext.displayRotation)
                                .build()
                                .apply {
                                    setSurfaceProvider(
                                        createSurfaceProviderFor(cameraSelector, previewRequest)
                                    )
                                }
                            this.preview = preview

                            val imageCapture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                .setTargetAspectRatio(aspectRatio)
                                .build()
                            this.imageCapture = imageCapture

                            val crop = when (configuration) {
                                is AllowsCameraPreview.Configuration.Default -> configuration.crop
                                else -> Crop.None
                            }

                            val viewPort = when (crop) {
                                is Crop.Center -> {
                                    ViewPort.Builder(
                                        crop.aspectRatio,
                                        preview.targetRotation
                                    ).build()
                                }
                                else -> null
                            }

                            val useCaseGroup = UseCaseGroup.Builder()
                                .addUseCase(preview)
                                .addUseCase(imageCapture)
                                .apply {
                                    if (viewPort != null) {
                                        setViewPort(viewPort)
                                    }
                                }
                                .build()

                            camera = cameraProvider
                                .bindToLifecycle(lifecycleOwner, cameraSelector, useCaseGroup).apply {
                                    cameraInfo.zoomState.observe(lifecycleOwner) {
                                        // NOTE: ZoomState is not synchronized with Camera2 frame results,
                                        // for more information see:
                                        //     https://partnerissuetracker.corp.google.com/issues/169938468
                                        zoomState = it
                                    }
                                }
                            callback(true)
                        }
                    } else {
                        callback(false)
                    }
                } ?: callback(false)
            },
            mainExecutor
        )
    }

    @MainThread
    override fun stopPreview() {
        waitingForImageProcessorTask.getAndSet(null)?.cancel(true)
        imageProcessorInputConnection.getAndSet(null)?.close()
        connectedImageProcessorInput.set(null)
        val processCameraProvider = cameraProvider
        val useCasesToUnbind = listOfNotNull(imageCapture, preview)
        activePreviewRequest = null
        cameraProvider = null
        preview = null
        previewOutput = null
        imageCapture = null
        camera = null
        zoomState = null
        processCameraProvider?.unbind(*useCasesToUnbind.toTypedArray())
    }

    @MainThread
    override fun takeVideo(onAvailable: (File) -> Unit): Closeable {
        return takeVideo(Consumer(onAvailable))
    }

    @MainThread
    fun takeVideo(onAvailable: Consumer<File>): Closeable {
        return previewOutput?.run {
            val rotationRelativeToDisplay = getRotationRelativeToDisplay(rotationDegrees, facingFront)
            val size = processedTextureSize.captureSize(
                rotationDegrees = rotationRelativeToDisplay,
                useDisplayRatio = true,
                context = applicationContext
            )
            var resultFile: File? = null
            var connection = EMPTY_CLOSEABLE
            var flash = EMPTY_CLOSEABLE

            if (flashConfiguration is AllowsCameraFlash.FlashConfiguration.Enabled && !facingFront) {
                flash = enableCameraFlash()
            }

            userOperationTask.getAndSet(
                executorService.submit {
                    lastImageProcessor.waitFor { processor ->
                        val outputFile = File(videoOutputDirectory, "${UUID.randomUUID()}.mp4")
                        val hasAudioRecordingPermission = ContextCompat.checkSelfPermission(
                            applicationContext, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        connection = processor.connectOutput(
                            outputFile, size.width, size.height, captureAudio = hasAudioRecordingPermission
                        )
                        resultFile = outputFile
                    }
                }
            )?.cancel(true)
            Closeable {
                userOperationTask.getAndSet(
                    executorService.submit {
                        connection.close()
                        resultFile?.let(onAvailable::accept)
                    }
                )?.cancel(true)

                flash.close()
            }
        } ?: EMPTY_CLOSEABLE
    }

    @MainThread
    override fun takePhoto(onAvailable: (Bitmap) -> Unit) {
        takePhoto(Consumer(onAvailable))
    }

    @MainThread
    fun takePhoto(onAvailable: Consumer<Bitmap> = Consumer {}) {
        imageCapture?.run {
            val enableFlashAndFocus = if (flashConfiguration is AllowsCameraFlash.FlashConfiguration.Enabled) {
                flashMode = ImageCapture.FLASH_MODE_ON
                focusAndMeterOn(previousFocusAction)
            } else {
                flashMode = ImageCapture.FLASH_MODE_OFF
                EMPTY_CLOSEABLE
            }

            takePicture(
                executorService,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        enableFlashAndFocus.close()

                        lastImageProcessor.waitFor { processor ->
                            connectedImageProcessorInput.waitFor { (input, options) ->
                                val bitmap = image.use { it.toBitmap() }
                                val resultBitmap = processor.processBitmap(
                                    input,
                                    bitmap,
                                    mirrorHorizontally = options.contains(
                                        ImageProcessor.Input.Option.MirrorFramesHorizontally
                                    ),
                                    mirrorVertically = options.contains(
                                        ImageProcessor.Input.Option.MirrorFramesVertically
                                    ),
                                    allowDownscaling = true
                                )
                                // processBitmap returns the source bitmap as a result if there is no effect applied
                                // and source bitmap should not be transformed. In this case, the source bitmap should
                                // not be recycled.
                                if (resultBitmap !== bitmap) {
                                    bitmap.recycle()
                                }
                                if (resultBitmap != null) {
                                    onAvailable.accept(resultBitmap)
                                }
                            }
                        }
                    }
                }
            )
        }
    }

    @MainThread
    override fun takeSnapshot(onAvailable: (Bitmap) -> Unit) {
        takeSnapshot(Consumer(onAvailable))
    }

    @MainThread
    fun takeSnapshot(onAvailable: Consumer<Bitmap> = Consumer {}) {
        previewOutput?.run {
            var enableFlashAndFocus = EMPTY_CLOSEABLE

            if (flashConfiguration is AllowsCameraFlash.FlashConfiguration.Enabled) {
                camera?.let {
                    val focus = focusAndMeterOn(previousFocusAction)

                    enableFlashAndFocus = if (!facingFront) {
                        val flash = enableCameraFlash()
                        Closeable {
                            focus.close()
                            flash.close()
                        }
                    } else {
                        // Only focus if front facing since front facing flash should be turned on in UI
                        focus
                    }
                }
            }

            val rotationRelativeToDisplay = getRotationRelativeToDisplay(rotationDegrees, facingFront)
            val size = processedTextureSize.captureSize(rotationRelativeToDisplay)
            userOperationTask.getAndSet(
                executorService.submit {
                    // Adding delay to allow for flash to turn on and camera to adjust before taking picture.
                    // Only if flash enabled and camera is back facing, UI handles front flash.
                    if (flashConfiguration is AllowsCameraFlash.FlashConfiguration.Enabled && !facingFront) {
                        // Using TimeUnit#sleep() to create delay since
                        // ScheduledExecutorService is not available in public API.
                        val flashEnabled = flashConfiguration as AllowsCameraFlash.FlashConfiguration.Enabled
                        val duration = flashEnabled.delayDuration
                        val timeUnit = flashEnabled.delayDurationTimeUnit
                        timeUnit.sleep(duration)
                    }

                    lastImageProcessor.waitFor { processor ->
                        val bitmap = processor.toBitmap(size.width, size.height)
                        if (bitmap != null) {
                            onAvailable.accept(bitmap)
                        }
                    }

                    enableFlashAndFocus.close()
                }
            )?.cancel(true)
        }
    }

    /**
     * @since 1.8.0
     */
    @MainThread
    override fun focusAndMeterOn(x: Float, y: Float, viewSize: Size): Closeable {
        return camera?.run {
            previewOutput?.let { previewOutput ->
                val windowManager = applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val display = windowManager.defaultDisplay

                val textureSize = previewOutput.textureSize.rotatedTextureSize(previewOutput.rotationDegrees)
                val viewAspectRatio = viewSize.width.toFloat() / viewSize.height.toFloat()
                val cameraPreviewAspectRatio = textureSize.width.toFloat() / textureSize.height.toFloat()

                val scaleFactor = viewAspectRatio / cameraPreviewAspectRatio

                var fullWidth = viewSize.width.toFloat()
                var fullHeight = viewSize.height.toFloat()

                if (scaleFactor < 1f) {
                    fullWidth /= scaleFactor
                } else {
                    fullHeight *= scaleFactor
                }

                val factory = DisplayOrientedMeteringPointFactory(display, cameraInfo, fullWidth, fullHeight)
                val meteringPoint = factory.createPoint(x, y)
                val focusAction = FocusMeteringAction.Builder(meteringPoint)
                    .disableAutoCancel()
                    .build()
                previousFocusAction = focusAction

                cameraControl.startFocusAndMetering(focusAction)
            }

            Closeable {
                previousFocusAction = CENTER_FOCUS_POINT
                cameraControl.cancelFocusAndMetering()
            }
        } ?: EMPTY_CLOSEABLE
    }

    /**
     * @since 1.8.3
     */
    @MainThread
    fun focusAndMeterOn(focusMeteringAction: FocusMeteringAction): Closeable {
        return camera?.run {
            cameraControl.startFocusAndMetering(focusMeteringAction)
            previousFocusAction = focusMeteringAction
            Closeable {
                previousFocusAction = CENTER_FOCUS_POINT
                cameraControl.cancelFocusAndMetering()
            }
        } ?: EMPTY_CLOSEABLE
    }

    @MainThread
    override fun zoomBy(factor: Float) {
        zoomState?.let { state ->
            val nextZoomRatio = max(state.minZoomRatio, min(state.maxZoomRatio, state.zoomRatio * factor))
            camera?.run {
                cameraControl.setZoomRatio(nextZoomRatio)
            }
        }
    }

    /**
     * @since 1.8.3
     */
    @MainThread
    override fun useFlashConfiguration(flashConfiguration: AllowsCameraFlash.FlashConfiguration) {
        this.flashConfiguration = flashConfiguration
    }

    /**
     * @since 1.8.3
     */
    @MainThread
    override fun enableCameraFlash(): Closeable {
        return camera?.run {
            cameraControl.enableTorch(true)

            Closeable {
                cameraControl.enableTorch(false)
            }
        } ?: EMPTY_CLOSEABLE
    }

    @SuppressLint("RestrictedApi") // no other way to get camera info from CameraX
    @ExperimentalCamera2Interop
    private fun createSurfaceProviderFor(
        cameraSelector: CameraSelector,
        previewRequest: PreviewRequest
    ) = Preview.SurfaceProvider { surfaceRequest ->
        if (this.activePreviewRequest !== previewRequest) {
            Log.w(TAG, "Concurrent start camera preview requests.")
            surfaceRequest.willNotProvideSurface()
            return@SurfaceProvider
        }

        val cameraProvider = cameraProvider
        if (cameraProvider == null) {
            Log.w(TAG, "No camera provider present to get camera info.")
            surfaceRequest.willNotProvideSurface()
            return@SurfaceProvider
        }

        for ( cam in cameraProvider.availableCameraInfos) {
            Log.d("Camera!", "Camera found")
        }

        val cameraInfo = cameraSelector.filter(cameraProvider.availableCameraInfos).firstOrNull()
        if (cameraInfo == null) {
            Log.w(TAG, "Could not find camera info that matches the camera selector.")
            surfaceRequest.willNotProvideSurface()
            return@SurfaceProvider
        }

        val cameraId = Camera2CameraInfo.from(cameraInfo).cameraId
        val cameraManager: CameraManager = applicationContext
            .getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val fieldOfView = characteristics.fieldOfView(DEFAULT_FIELD_OF_VIEW)

        val cropRect = preview?.viewPortCropRect
        val resolution = surfaceRequest.resolution
        previewOutput = PreviewOutput(
            previewRequest.configuration.facingFront,
            resolution,
            cropRect,
            cameraInfo.sensorRotationDegrees
        )

        val surfaceTexture = SurfaceTexture(0).apply {
            setDefaultBufferSize(resolution.width, resolution.height)
            detachFromGLContext()
        }
        val surface = Surface(surfaceTexture)
        surfaceRequest.provideSurface(surface, mainExecutor) {
            surface.release()
            surfaceTexture.release()
        }

        val inputOptions = if (cropRect == null) {
            previewRequest.inputOptions
        } else {
            previewRequest.inputOptions + ImageProcessor.Input.Option.Crop.Center(cropRect.width(), cropRect.height())
        }

        tryConnect(
            ImageProcessor.Input(
                surfaceTexture,
                resolution.width,
                resolution.height,
                cameraInfo.getSensorRotationDegrees(applicationContext.displayRotation),
                previewRequest.configuration.facingFront,
                { applyZoomRatio(fieldOfView.width) },
                { applyZoomRatio(fieldOfView.height) }
            ),
            inputOptions
        )
    }

    private fun tryConnect(input: ImageProcessor.Input, options: Set<ImageProcessor.Input.Option>) {
        try {
            waitingForImageProcessorTask.getAndSet(
                executorService.submit {
                    lastImageProcessor.waitFor { processor ->
                        imageProcessorInputConnection.getAndSet(processor.connectInput(input, options))?.close()
                        connectedImageProcessorInput.set(input to options)
                    }
                }
            )?.cancel(true)
        } catch (e: RejectedExecutionException) {
            Log.w(
                TAG,
                "Could not connect new Input to ImageProcessor due to ExecutorService shutdown", e
            )
        }
    }

    private fun getRotationRelativeToDisplay(rotationDegrees: Int, facingFront: Boolean): Int {
        val displayRotation = surfaceRotationToDegrees(applicationContext.displayRotation)
        return getRelativeImageRotation(
            displayRotation,
            rotationDegrees,
            !facingFront
        )
    }

    private fun applyZoomRatio(fov: Float): Float {
        return zoomState?.zoomRatio?.let { zoomRatio ->
            (2 * toDegrees(atan(tan(toRadians(fov / 2.toDouble())) / zoomRatio))).toFloat()
        } ?: fov
    }
}

private val DEFAULT_FIELD_OF_VIEW = SizeF(59f, 42f)

private data class PreviewRequest(
    val configuration: AllowsCameraPreview.Configuration,
    val inputOptions: Set<ImageProcessor.Input.Option>,
)

@AspectRatio.Ratio
private fun configurationRatioToCameraXRatio(configurationRatio: ConfigurationAspectRatio): Int {
    return when (configurationRatio) {
        ConfigurationAspectRatio.RATIO_16_9 -> {
            AspectRatio.RATIO_16_9
        }
        ConfigurationAspectRatio.RATIO_4_3 -> {
            AspectRatio.RATIO_4_3
        }
    }
}

private data class PreviewOutput(
    val facingFront: Boolean,
    val textureSize: Size,
    val textureCrop: Rect?,
    val rotationDegrees: Int
)

private val PreviewOutput.processedTextureSize: Size get() = textureCrop?.run {
    Size(width(), height())
} ?: textureSize

private fun CameraCharacteristics.fieldOfView(defaultFieldOfView: SizeF): SizeF {
    val focalLengths = get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
    val reportedSensorSize = get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
    if (focalLengths == null || focalLengths.isEmpty() || reportedSensorSize == null) {
        return defaultFieldOfView
    }
    val sensorSize = SizeF(reportedSensorSize.width, reportedSensorSize.height)
    return fieldOfViewDegrees(sensorSize, focalLengths[0], defaultFieldOfView)
}

private fun fieldOfViewDegrees(
    sensorSizeMillimeters: SizeF,
    focalLengthMillimeters: Float,
    defaultDegrees: SizeF
): SizeF {
    if (focalLengthMillimeters <= 0f ||
        sensorSizeMillimeters.width <= 0f ||
        sensorSizeMillimeters.height <= 0f
    ) {
        return defaultDegrees
    }
    return SizeF(
        (
                2 * Math.toDegrees(
                    atan2((sensorSizeMillimeters.width / 2).toDouble(), focalLengthMillimeters.toDouble())
                )
                ).toFloat(),
        (
                2 * Math.toDegrees(
                    atan2((sensorSizeMillimeters.height / 2).toDouble(), focalLengthMillimeters.toDouble())
                )
                ).toFloat()
    )
}

private val Context.displayRotation: Int get() {
    return (getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay?.rotation ?: Surface.ROTATION_0
}

private fun surfaceRotationToDegrees(rotation: Int): Int {
    return when (rotation) {
        Surface.ROTATION_0 -> 0
        Surface.ROTATION_90 -> 90
        Surface.ROTATION_180 -> 180
        Surface.ROTATION_270 -> 270
        else -> throw IllegalArgumentException("Unsupported surface rotation: $rotation")
    }
}

private fun getRelativeImageRotation(
    destRotationDegrees: Int,
    sourceRotationDegrees: Int,
    isOppositeFacing: Boolean
): Int {
    return if (isOppositeFacing) {
        (sourceRotationDegrees - destRotationDegrees + 360) % 360
    } else {
        (sourceRotationDegrees + destRotationDegrees) % 360
    }
}

private fun ImageProxy.toBitmap(): Bitmap {
    val buffer = planes[0].buffer
    val bytes = ByteArray(buffer.capacity())
    buffer[bytes]
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

@SuppressLint("RestrictedApi")
private class LenientLensFacingCameraFilter(@LensFacing lensFacing: Int) : LensFacingCameraFilter(lensFacing) {

    override fun filter(cameraInfos: List<CameraInfo>): List<CameraInfo> {
        val filtered = super.filter(cameraInfos)
        return if (filtered.isEmpty()) {
            cameraInfos
        } else {
            filtered
        }
    }
}
