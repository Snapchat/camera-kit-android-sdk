package com.snap.camerakit.sample

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.LifecycleOwner
import com.google.ar.core.ArCoreApk
import com.snap.camerakit.ImageProcessor
import com.snap.camerakit.Session
import com.snap.camerakit.Source
import com.snap.camerakit.configureLenses
import com.snap.camerakit.connectOutput
import com.snap.camerakit.invoke
import com.snap.camerakit.lenses.LENS_GROUP_ID_BUNDLED
import com.snap.camerakit.lenses.LensesComponent
import com.snap.camerakit.lenses.LensesComponent.Repository.QueryCriteria.Available
import com.snap.camerakit.lenses.apply
import com.snap.camerakit.lenses.configureCache
import com.snap.camerakit.lenses.configureCarousel
import com.snap.camerakit.lenses.configureEachItem
import com.snap.camerakit.lenses.configureHints
import com.snap.camerakit.lenses.configureMediaPicker
import com.snap.camerakit.lenses.get
import com.snap.camerakit.lenses.invoke
import com.snap.camerakit.lenses.observe
import com.snap.camerakit.lenses.run
import com.snap.camerakit.lenses.whenActivated
import com.snap.camerakit.lenses.whenApplied
import com.snap.camerakit.lenses.whenDeactivated
import com.snap.camerakit.lenses.whenHasSome
import com.snap.camerakit.lenses.whenIdle
import com.snap.camerakit.mediaStoreSourceFor
import com.snap.camerakit.support.arcore.ArCoreImageProcessorSource
import com.snap.camerakit.support.camerax.CameraXImageProcessorSource
import com.snap.camerakit.support.gms.location.GmsLocationProcessorSource
import com.snap.camerakit.support.widget.SnapButtonView
import com.snap.camerakit.supported
import java.io.Closeable
import java.io.File
import java.util.Date
import java.util.concurrent.Executors

private const val TAG = "MainActivity"
private const val REQUEST_CODE_PERMISSIONS = 10
private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
private val OPTIONAL_PERMISSIONS = arrayOf(Manifest.permission.RECORD_AUDIO)
private const val BUNDLE_ARG_APPLIED_LENS_ID = "applied_lens_id"
private const val BUNDLE_ARG_CAMERA_FACING_FRONT = "camera_facing_front"
private const val BUNDLE_ARG_LENS_GROUPS = "lens_groups"
private val LENS_GROUPS = arrayOf(
    LENS_GROUP_ID_BUNDLED, // lens group for bundled lenses available in lenses-bundle-partner artifact.
    *BuildConfig.LENS_GROUP_ID_TEST.split(',').toTypedArray() // temporary lens group for testing
)
private val LENS_GROUPS_ARCORE_AVAILABLE = arrayOf(
    *LENS_GROUPS,
    BuildConfig.LENS_GROUP_ID_AR_CORE // lens group containing lenses using ARCore functionality.
)

/**
 * A simple activity which demonstrates how to use [CameraKit] to apply/remove lenses onto a camera preview.
 * Camera is managed through the use of CameraX library which we use to implement a [com.snap.camerakit.Source]
 * to deliver streaming frames to lenses.
 */
class MainActivity : AppCompatActivity(), LifecycleOwner {

    private val singleThreadExecutor = Executors.newSingleThreadExecutor()
    private val threadPoolExecutor = Executors.newFixedThreadPool(4)
    private lateinit var mainLayout: ViewGroup
    private lateinit var captureButton: SnapButtonView
    private lateinit var activeSource: Source<ImageProcessor>
    private lateinit var cameraKitSession: Session
    private lateinit var lensGroups: Array<String>

    private var appliedLensId: String? = null
    private var cameraFacingFront: Boolean = true
    private var capturePhoto: Boolean = true
    private var miniPreviewOutput: Closeable = Closeable {}
    private var availableLensesQuery = Closeable {}
    private var lensesProcessorEvents = Closeable {}
    private var lensesCarouselEvents = Closeable {}
    private var videoRecording: Closeable? = null
    private var lensesPrefetch: Closeable = Closeable {}
    private var lensGroupsUpdated: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Use Sessions#supported method to check if CameraKit can run on a specific device.
        // CameraKit requires certain hardware/OS features such as OpenGL ES 3.0 to be available,
        // attempts to run CameraKit on an unsupported device may lead to undefined runtime behavior and exceptions.
        if (!supported(this)) {
            Toast.makeText(this, R.string.camera_kit_unsupported, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val metadata = packageManager.getActivityInfo(componentName, PackageManager.GET_META_DATA).metaData
        val lockPortraitOrientation = metadata?.getBoolean(getString(R.string.lock_portrait_orientation)) ?: false

        if (lockPortraitOrientation) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        savedInstanceState?.let {
            appliedLensId = it.getString(BUNDLE_ARG_APPLIED_LENS_ID)
            cameraFacingFront = it.getBoolean(BUNDLE_ARG_CAMERA_FACING_FRONT)
        }
        lensGroups = savedInstanceState?.getStringArray(BUNDLE_ARG_LENS_GROUPS) ?: if (arCoreSourceAvailable) {
            LENS_GROUPS_ARCORE_AVAILABLE
        } else {
            LENS_GROUPS
        }

        setContentView(R.layout.activity_main)
        val rootLayout = findViewById<DrawerLayout>(R.id.root_layout)
        mainLayout = rootLayout.findViewById(R.id.main_layout)
        captureButton = mainLayout.findViewById(R.id.capture_button)

        // This ViewStub is provided to CameraKit to inflate its views into when attached
        val cameraKitStub = findViewById<ViewStub>(R.id.camerakit_stub)

        // This sample implements camera Source through the use of CameraX library which simplifies quite a bit
        // of things related to Android camera management. CameraX is one of many options to implement Source,
        // anything that can provide image frames through a SurfaceTexture can be used by CameraKit.
        val cameraXImageProcessorSource = CameraXImageProcessorSource(
            context = this,
            lifecycleOwner = this,
            executorService = singleThreadExecutor,
            videoOutputDirectory = cacheDir
        )

        // Use cameraXImageProcessorSource as an active source by default.
        activeSource = cameraXImageProcessorSource

        val imageProcessorSource = if (arCoreSourceAvailable) {
            // ArCoreImageProcessorSource is the only currently supported option to provide surface tracking data or
            // depth data when required by applied lens.
            val arCoreSource = ArCoreImageProcessorSource(
                    context = this,
                    lifecycleOwner = this,
                    executorService = singleThreadExecutor,
                    videoOutputDirectory = cacheDir
            )
            // This is an implementation of Source<ImageProcessor> that attach ImageProcessor to one of the provided
            // sources according to ImageProcessor requirements to input capabilities.
            SwitchForSurfaceTrackingImageProcessorSource(cameraXImageProcessorSource, arCoreSource, { source ->
                if (activeSource != source) {
                    this.activeSource = source
                    // Call startPreview on attached Source to let it dispatch frames to ImageProcessor.
                    activeSource.startPreview(cameraFacingFront)
                }
            })
        } else {
            cameraXImageProcessorSource
        }

        // Some content may request additional data such as user name to personalize lenses. Providing this data is
        // optional, the MockUserProcessorSource class demonstrates a basic example to implement a source of the data.
        val mockUserProcessorSource = MockUserProcessorSource(
            userDisplayName = "Jane Doe",
            userBirthDate = Date(136985835000L)
        )

        // Provide location data to CameraKit through the Google Mobile Services backed Source<LocationProcessor>
        // that handles runtime permission requests internally.
        val locationProcessorSource = GmsLocationProcessorSource(context = this)

        // Provide the area between the camera flip and capture button as the "safe render" area to CameraKit
        // so that they do not overlap any UI elements rendered internally by lenses.
        val safeRenderAreaProcessorSource = CameraLayoutSafeRenderAreaProcessorSource(activity = this)

        // Use default implementation of Source<MediaProcessor> that gets media data from android.provider.MediaStore.
        // That implementation uses GMS FaceDetector so dependency com.google.android.gms:play-services-vision should
        // be included to enable that functionality.
        val mediaProcessorSource = mediaStoreSourceFor(context = this, executorService = threadPoolExecutor)

        // This block configures and creates a new CameraKit instance that is the main entry point to all its features.
        // The CameraKit instance must be closed when appropriate to avoid leaking any resources.
        cameraKitSession = Session(this) {
            imageProcessorSource(imageProcessorSource)
            userProcessorSource(mockUserProcessorSource)
            locationProcessorSource(locationProcessorSource)
            safeRenderAreaProcessorSource(safeRenderAreaProcessorSource)
            mediaProcessorSource(mediaProcessorSource)
            // The provided ViewStub will be used to inflate CameraKit's Session view hierarchy to handle touch events
            // as well as to render camera preview. In this example we set withPreview to true to have Session
            // render the camera preview - this might not be suitable for other use cases that manage camera preview
            // differently (SurfaceView, off-screen rendering) therefore it is possible to pass withPreview = false
            // and attach camera preview output separately.
            attachTo(cameraKitStub, withPreview = true)
            configureLenses {
                // When CameraKit is configured to manage its own views by providing a view stub (see above),
                // lenses touch handling might consume all events due to the fact that it needs to perform gesture
                // detection internally. If application needs to handle gestures on top of it then LensesComponent
                // provides a way to dispatch all touch events unhandled by active lens back.
                dispatchTouchEventsTo(mainLayout.findViewById(R.id.preview_gesture_handler))
                // An optional configuration to enable lenses carousel view. If the provided [observedGroupIds]
                // is not empty then the carousel will be visible and interactive otherwise it will simply
                // be disabled to not consume any resources.
                configureCarousel {
                    activateIdle = true
                    activateOnTap = true
                    deactivateOnClose = true
                    observedGroupIds = lensGroups.toSet()
                    heightDimenRes = R.dimen.lenses_carousel_height
                    marginBottomDimenRes = R.dimen.lenses_carousel_margin_bottom
                    closeButtonMarginBottomDimenRes = R.dimen.lenses_carousel_close_button_margin_bottom
                    // A lambda passed to configureEachItem can be used to customize position or appearance of each
                    // item in the lenses carousel.
                    configureEachItem {
                        if (lens.groupId == LENS_GROUP_ID_BUNDLED || index == 1) {
                            moveToLeft()
                        } else {
                            moveToRight()
                        }
                    }
                }
                configureMediaPicker {
                    enabled = true
                }
                // An optional configuration to enable lens hints view. When enabled, lens hints are shown using
                // built-in view that is horizontally and vertically centered on top of camera preview. It is possible
                // to provide a custom view that implements LensesComponent.Hints.View interface using this block.
                configureHints {
                    enabled = true
                }
                // Lens content resources are downloaded and managed by CameraKit internally, however it is possible
                // to configure certain aspects such as cache size through the LensesComponent.Builder as seen below.
                configureCache {
                    lensContentMaxSize = 128 * 1024 * 1024 // 128MB
                }
            }
        }

        // We keep the last applied Lens reference here in order to update the RecyclerView adapter
        // as well as to use it when determining the next or previous lens to switch to.
        val applyLens = { lens: LensesComponent.Lens ->
            // An example of how dynamic launch data can be used. Vendor specific metadata is added into LaunchData
            // so it can be used by lens on launch.
            val launchData = LensesComponent.Lens.LaunchData {
                lens.vendorData.forEach { (key, value) -> putString(key, value) }
            }
            cameraKitSession.lenses.processor.apply(lens, launchData) { success ->
                Log.d(TAG, "Apply lens [$lens] success: $success")
            }
        }

        val lensAttribution = mainLayout.findViewById<TextView>(R.id.lens_attribution)
        var skipLensesProcessorEvent = true
        // This block demonstrates how to receive and react to lens lifecycle events. When Applied event is received
        // we keep the ID of applied lens to persist and restore it via savedInstanceState later on.
        lensesProcessorEvents = cameraKitSession.lenses.processor.observe { event ->
            Log.d(TAG, "Observed lenses processor event: $event")
            // First event emitted by lenses processor is always Idle, we skip to avoid overriding appliedLensId
            // with null when it is persisted in savedInstanceState.
            if (skipLensesProcessorEvent) {
                skipLensesProcessorEvent = false
            } else {
                mainLayout.post {
                    event.whenApplied {
                        appliedLensId = it.lens.id
                        lensAttribution.text = it.lens.name
                        // Reapply lens to pass LaunchData If vendor metadata isn't empty.
                        if (it.lens.vendorData.isNotEmpty()) {
                            applyLens(it.lens)
                        }
                    }
                    event.whenIdle {
                        appliedLensId = null
                        lensAttribution.text = null
                    }
                }
            }
        }

        // Working with the CameraKit's lenses component we query for all lenses that are available.
        // If we have an applied Lens ID saved previously we then try to find it in the list and apply it.
        availableLensesQuery = cameraKitSession.lenses.repository.observe(Available(*lensGroups)) { available ->
            Log.d(TAG, "Available lenses: $available")
            available.whenHasSome { lenses ->
                appliedLensId?.let { id ->
                    lenses.find { it.id == id }?.let(applyLens)
                }
            }
        }

        // It is possible to implement custom lenses carousel activation logic by interacting with carousel through
        // the LensesComponent.Carousel interface which also allows to observe its state, useful to enable/disable
        // custom UI elements such as the lens button below.
        findViewById<ImageButton>(R.id.button_lens).let { lensButton ->
            cameraKitSession.lenses.apply {
                lensesCarouselEvents = carousel.observe { event ->
                    Log.d(TAG, "Observed lenses carousel event: $event")
                    mainLayout.post {
                        event.whenActivated {
                            lensButton.visibility = View.GONE
                        }
                        event.whenDeactivated {
                            lensButton.visibility = View.VISIBLE
                        }
                    }
                }
                // When lens button is clicked on, we get the first available lens used to activate lenses carousel
                // with: the lens appears selected and gets applied immediately.
                lensButton.setOnClickListener {
                    repository.get(Available(*lensGroups)) { available ->
                        available.whenHasSome { lenses ->
                            carousel.activate((lenses.first()))
                        }
                    }
                }
            }
        }

        // Present basic app version information to make it easier for QA to report it.
        rootLayout.findViewById<TextView>(R.id.version_info).apply {
            val versionNameAndCode = getString(
                R.string.version_info, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE
            )
            text = versionNameAndCode
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip: ClipData = ClipData.newPlainText("version_info", versionNameAndCode)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(
                    this@MainActivity,
                    "Copied to clipboard: $versionNameAndCode",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        // While CameraKit is capable (and does) render camera preview into an internal view, this demonstrates how
        // to connect another TextureView as rendering output.
        val miniPreview = mainLayout.findViewById<TextureView>(R.id.mini_preview)
        val setupMiniPreview = { connectOutput: Boolean ->
            miniPreviewOutput.close()
            if (connectOutput) {
                miniPreview.visibility = View.VISIBLE
                miniPreviewOutput = cameraKitSession.processor.connectOutput(miniPreview)
            } else {
                miniPreview.visibility = View.GONE
            }
        }
        rootLayout.findViewById<ToggleButton>(R.id.mini_preview_toggle).apply {
            setupMiniPreview(isChecked)
            setOnCheckedChangeListener { _, isChecked ->
                setupMiniPreview(isChecked)
            }
        }

        rootLayout.findViewById<ToggleButton>(R.id.capture_photo_toggle).apply {
            capturePhoto = isChecked
            setOnCheckedChangeListener { _, isChecked ->
                capturePhoto = isChecked
            }
        }

        rootLayout.findViewById<Button>(R.id.lenses_prefetch_button).setOnClickListener {
            cameraKitSession.lenses.repository.observe(Available(*lensGroups)) { available ->
                available.whenHasSome { lenses ->
                    // Cancel any running prefetch operation before submitting new one
                    lensesPrefetch.close()
                    // Prefetch available lenses content async
                    lensesPrefetch = cameraKitSession.lenses.prefetcher.run(lenses) { success ->
                        Log.d(TAG, "Finished prefetch of [${lenses.size}] lenses with success: $success")
                    }
                }
            }
        }

        findViewById<Button>(R.id.update_lens_groups_button).setOnClickListener {
            var updatedLensGroups = lensGroups
            val dialog = AlertDialog.Builder(this)
                .setView(R.layout.dialog_groups_edit)
                .setCancelable(true)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    if (updatedLensGroups.isNotEmpty() && !updatedLensGroups.contentEquals(lensGroups)) {
                        lensGroups = updatedLensGroups
                        lensGroupsUpdated = true
                        recreate()
                    }
                }
                .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                    dialog.cancel()
                }
                .create()
                .apply {
                    show()
                }

            dialog.findViewById<EditText>(R.id.lens_groups_field)!!.apply {
                setText(updatedLensGroups.joinToString())
                addTextChangedListener(object : TextWatcher {

                    override fun afterTextChanged(s: Editable) {}

                    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

                    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                        updatedLensGroups = s.toString().split(", ").filter { it.isNotBlank() }.toTypedArray()
                    }
                })
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        appliedLensId?.let {
            outState.putString(BUNDLE_ARG_APPLIED_LENS_ID, it)
        }
        outState.putBoolean(BUNDLE_ARG_CAMERA_FACING_FRONT, cameraFacingFront)
        if (lensGroupsUpdated && lensGroups.isNotEmpty()) {
            outState.putStringArray(BUNDLE_ARG_LENS_GROUPS, lensGroups)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (requiredPermissionsGranted()) {
            onRequiredPermissionsGranted()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS + OPTIONAL_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    override fun onDestroy() {
        miniPreviewOutput.close()
        availableLensesQuery.close()
        lensesProcessorEvents.close()
        lensesCarouselEvents.close()
        lensesPrefetch.close()
        cameraKitSession.close()
        singleThreadExecutor.shutdown()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return if (captureButton.dispatchKeyEvent(event)) {
            true
        } else {
            super.dispatchKeyEvent(event)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS && !requiredPermissionsGranted()) {
            Toast.makeText(this, R.string.required_permissions_not_granted, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun requiredPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun onRequiredPermissionsGranted() {
        mainLayout.post {
            startPreviewForCurrentCameraFacing()
        }

        // We use CameraKit provided SnapButtonView to implement a basic photo/video capture flow
        // that is similar to Snapchat app - single tap to take photo, press & hold to record video.
        captureButton.apply {

            // When user scrolls over SnapButtonView, we can re-dispatch touches to CameraKit root view to make
            // lenses carousel respond to the scroll gesture:
            fallbackTouchHandlerViewId = R.id.camerakit_root

            onCaptureRequestListener = object : SnapButtonView.OnCaptureRequestListener {

                override fun onStart(captureType: SnapButtonView.CaptureType) {
                    if (captureType == SnapButtonView.CaptureType.CONTINUOUS) {
                        if (videoRecording == null) {
                            videoRecording = activeSource.takeVideo { file ->
                                PreviewActivity.startUsing(this@MainActivity, mainLayout, file, MIME_TYPE_VIDEO_MP4)
                            }
                        }
                    }
                }

                override fun onEnd(captureType: SnapButtonView.CaptureType) {
                    when (captureType) {
                        SnapButtonView.CaptureType.CONTINUOUS -> {
                            videoRecording?.close()
                            videoRecording = null
                        }
                        SnapButtonView.CaptureType.SNAPSHOT -> {
                            val onBitmapAvailable: (Bitmap) -> Unit = { bitmap ->
                                PreviewActivity.startUsing(
                                    this@MainActivity,
                                    mainLayout,
                                    this@MainActivity.cacheJpegOf(bitmap),
                                    MIME_TYPE_IMAGE_JPEG
                                )
                            }
                            // CameraKit supports processing photo capture images directly using
                            // ImageProcessors.processBitmap/processImage extension that it is utilized in
                            // CameraXImageProcessorSource.
                            // It is recommended to use regular snapshot capture when low latency is more important
                            // than result image quality, this sample allows to test both approaches using a toggle
                            // button located in the debug drawer menu.
                            if (capturePhoto) {
                                activeSource.takePhoto(this@MainActivity, onBitmapAvailable)
                            } else {
                                activeSource.takeSnapshot(onBitmapAvailable)
                            }
                        }
                    }
                }
            }
        }

        val flipCamera = {
            cameraFacingFront = !cameraFacingFront
            startPreviewForCurrentCameraFacing()
        }
        val flipGestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {

            override fun onDoubleTap(e: MotionEvent): Boolean {
                flipCamera()
                return true
            }
        })

        val zoomGestureDetector = ScaleGestureDetector(
            this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    activeSource.zoomBy(detector.scaleFactor)
                    return true
                }
            })

        mainLayout.findViewById<View>(R.id.preview_gesture_handler).setOnTouchListener { _, event ->
            flipGestureDetector.onTouchEvent(event)
            zoomGestureDetector.onTouchEvent(event)
            true
        }
        mainLayout.findViewById<AppCompatImageButton>(R.id.button_flip_camera).setOnClickListener {
            flipCamera()
        }
    }

    private fun startPreviewForCurrentCameraFacing() {
        activeSource.startPreview(cameraFacingFront)
    }
}

private fun Source<ImageProcessor>.startPreview(cameraFacingFront: Boolean) {
    when (this) {
        is CameraXImageProcessorSource -> startPreview(cameraFacingFront)
        is ArCoreImageProcessorSource -> startPreview(cameraFacingFront)
    }
}

private fun Source<ImageProcessor>.takeSnapshot(onBitmapAvailable: (Bitmap) -> Unit) {
    when (this) {
        is CameraXImageProcessorSource -> takeSnapshot(onBitmapAvailable)
        is ArCoreImageProcessorSource -> takeSnapshot(onBitmapAvailable)
    }
}

private fun Source<ImageProcessor>.takePhoto(context: Context, onBitmapAvailable: (Bitmap) -> Unit) {
    when (this) {
        is CameraXImageProcessorSource -> takePhoto(onBitmapAvailable)
        is ArCoreImageProcessorSource -> Toast.makeText(
                context,
                context.getString(R.string.ar_core_take_photo_unsupported),
                Toast.LENGTH_SHORT
        ).show()
    }
}

private fun Source<ImageProcessor>.takeVideo(onAvailable: (File) -> Unit): Closeable {
    return when (this) {
        is CameraXImageProcessorSource -> takeVideo(onAvailable)
        is ArCoreImageProcessorSource -> takeVideo(onAvailable)
        else -> throw IllegalStateException("Unexpected source is running.")
    }
}

private fun Source<ImageProcessor>.zoomBy(scaleFactor: Float) {
    when (this) {
        is CameraXImageProcessorSource -> zoomBy(scaleFactor)
        is ArCoreImageProcessorSource -> Log.d(TAG, "ArCoreImageProcessorSource does not support zoom functionality.")
    }
}

private val Activity.arCoreSourceAvailable: Boolean get() {
    // Currently, ARCore is supported in portrait orientation only.
    return windowManager.defaultDisplay.rotation == Surface.ROTATION_0 && arCoreSupportedAndInstalled
}

private val Context.arCoreSupportedAndInstalled: Boolean get() {
    return ArCoreApk.getInstance().checkAvailability(applicationContext) == ArCoreApk.Availability.SUPPORTED_INSTALLED
}
