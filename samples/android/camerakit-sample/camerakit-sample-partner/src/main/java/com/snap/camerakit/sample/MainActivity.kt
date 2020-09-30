package com.snap.camerakit.sample

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.LifecycleOwner
import com.snap.camerakit.Session
import com.snap.camerakit.configureLenses
import com.snap.camerakit.connectOutput
import com.snap.camerakit.invoke
import com.snap.camerakit.lenses.LENS_GROUP_ID_BUNDLED
import com.snap.camerakit.lenses.LensesComponent
import com.snap.camerakit.lenses.LensesComponent.Repository.QueryCriteria.Available
import com.snap.camerakit.lenses.apply
import com.snap.camerakit.lenses.configureCache
import com.snap.camerakit.lenses.configureCarousel
import com.snap.camerakit.lenses.configureHints
import com.snap.camerakit.lenses.invoke
import com.snap.camerakit.lenses.observe
import com.snap.camerakit.lenses.run
import com.snap.camerakit.lenses.whenApplied
import com.snap.camerakit.lenses.whenHasSome
import com.snap.camerakit.lenses.whenIdle
import com.snap.camerakit.support.camerax.CameraXImageProcessorSource
import com.snap.camerakit.support.widget.SnapButtonView
import com.snap.camerakit.supported
import java.io.Closeable
import java.util.Date
import java.util.concurrent.Executors

private const val TAG = "MainActivity"
private const val REQUEST_CODE_PERMISSIONS = 10
private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
private val OPTIONAL_PERMISSIONS = arrayOf(Manifest.permission.RECORD_AUDIO)
private const val BUNDLE_ARG_APPLIED_LENS_ID = "applied_lens_id"
private const val BUNDLE_ARG_CAMERA_FACING_FRONT = "camera_facing_front"
private val LENS_GROUPS = arrayOf(
    LENS_GROUP_ID_BUNDLED, // lens group for bundled lenses available in lenses-bundle-partner artifact.
    BuildConfig.LENS_GROUP_ID_TEST // temporary lens group for testing
)

/**
 * A simple activity which demonstrates how to use [CameraKit] to apply/remove lenses onto a camera preview.
 * Camera is managed through the use of CameraX library which we use to implement a [com.snap.camerakit.Source]
 * to deliver streaming frames to lenses.
 */
class MainActivity : AppCompatActivity(), LifecycleOwner {

    private val singleThreadExecutor = Executors.newSingleThreadExecutor()
    private lateinit var mainLayout: ViewGroup
    private lateinit var imageProcessorSource: CameraXImageProcessorSource
    private lateinit var cameraKitSession: Session

    private var appliedLensId: String? = null
    private var cameraFacingFront: Boolean = true
    private var capturePhoto: Boolean = true
    private var miniPreviewOutput: Closeable = Closeable {}
    private var availableLensesQuery = Closeable {}
    private var lensesProcessorEvents = Closeable {}
    private var videoRecording: Closeable? = null
    private var lensesPrefetch: Closeable = Closeable {}

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

        savedInstanceState?.let {
            appliedLensId = it.getString(BUNDLE_ARG_APPLIED_LENS_ID)
            cameraFacingFront = it.getBoolean(BUNDLE_ARG_CAMERA_FACING_FRONT)
        }

        setContentView(R.layout.activity_main)
        val rootLayout = findViewById<DrawerLayout>(R.id.root_layout)
        mainLayout = rootLayout.findViewById(R.id.main_layout)

        // This ViewStub is provided to CameraKit to inflate its views into when attached
        val cameraKitStub = findViewById<ViewStub>(R.id.camerakit_stub)

        // This sample implements camera Source through the use of CameraX library which simplifies quite a bit
        // of things related to Android camera management. CameraX is one of many options to implement Source,
        // anything that can provide image frames through a SurfaceTexture can be used by CameraKit.
        imageProcessorSource = CameraXImageProcessorSource(
            context = this,
            lifecycleOwner = this,
            executorService = singleThreadExecutor,
            videoOutputDirectory = cacheDir
        )

        // Some content may request additional data such as user name to personalize lenses. Providing this data is
        // optional, the MockUserProcessorSource class demonstrates a basic example to implement a source of the data.
        val mockUserProcessorSource = MockUserProcessorSource(
            userDisplayName = "Jane Doe",
            userBirthDate = Date(136985835000L)
        )

        // This block configures and creates a new CameraKit instance that is the main entry point to all its features.
        // The CameraKit instance must be closed when appropriate to avoid leaking any resources.
        cameraKitSession = Session(this) {
            imageProcessorSource(imageProcessorSource)
            userProcessorSource(mockUserProcessorSource)
            attachTo(cameraKitStub)
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
                    observedGroupIds = LENS_GROUPS.toSet()
                    heightDimenRes = R.dimen.lenses_carousel_height
                    marginBottomDimenRes = R.dimen.lenses_carousel_margin_bottom
                    closeButtonMarginBottomDimenRes = R.dimen.lenses_carousel_close_button_margin_bottom
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
        availableLensesQuery = cameraKitSession.lenses.repository.observe(Available(*LENS_GROUPS)) { available ->
            Log.d(TAG, "Available lenses: $available")
            available.whenHasSome { lenses ->
                appliedLensId?.let { id ->
                    lenses.find { it.id == id }?.let(applyLens)
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
            cameraKitSession.lenses.repository.observe(Available(*LENS_GROUPS)) { available ->
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
    }

    override fun onSaveInstanceState(outState: Bundle) {
        appliedLensId?.let {
            outState.putString(BUNDLE_ARG_APPLIED_LENS_ID, it)
        }
        outState.putBoolean(BUNDLE_ARG_CAMERA_FACING_FRONT, cameraFacingFront)
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
        lensesPrefetch.close()
        cameraKitSession.close()
        singleThreadExecutor.shutdown()
        super.onDestroy()
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
        mainLayout.findViewById<SnapButtonView>(R.id.capture_button).apply {
            onCaptureRequestListener = object : SnapButtonView.OnCaptureRequestListener {

                override fun onStart(captureType: SnapButtonView.CaptureType) {
                    if (captureType == SnapButtonView.CaptureType.CONTINUOUS) {
                        if (videoRecording == null) {
                            videoRecording = imageProcessorSource.takeVideo { file ->
                                PreviewActivity.startUsing(this@MainActivity, mainLayout, file, MIME_TYPE_VIDEO_MP4)
                            }
                        }
                    }
                }

                override fun onEnd(captureType: SnapButtonView.CaptureType) {
                    when (captureType) {
                        SnapButtonView.CaptureType.CONTINUOUS -> {
                            videoRecording?.let {
                                it.close()
                            }
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
                                imageProcessorSource.takePhoto(onBitmapAvailable)
                            } else {
                                imageProcessorSource.takeSnapshot(onBitmapAvailable)
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
        val previewGestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {

            override fun onDoubleTap(e: MotionEvent): Boolean {
                flipCamera()
                return true
            }
        })
        mainLayout.findViewById<View>(R.id.preview_gesture_handler).setOnTouchListener { _, event ->
            previewGestureDetector.onTouchEvent(event)
            true
        }
        mainLayout.findViewById<AppCompatImageButton>(R.id.button_flip_camera).setOnClickListener {
            flipCamera()
        }
    }

    private fun startPreviewForCurrentCameraFacing() {
        imageProcessorSource.startPreview(cameraFacingFront)
    }
}
