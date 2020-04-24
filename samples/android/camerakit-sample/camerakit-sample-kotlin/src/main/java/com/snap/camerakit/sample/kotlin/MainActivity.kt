package com.snap.camerakit.sample.kotlin

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
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
import com.snap.camerakit.lenses.observe
import com.snap.camerakit.lenses.whenApplied
import com.snap.camerakit.lenses.whenHasSome
import com.snap.camerakit.lenses.whenIdle
import com.snap.camerakit.support.camerax.CameraXImageProcessorSource
import com.snap.camerakit.support.widget.SnapButtonView
import java.io.Closeable

private const val TAG = "MainActivity"
private const val REQUEST_CODE_PERMISSIONS = 10
private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
private val OPTIONAL_PERMISSIONS = arrayOf(Manifest.permission.RECORD_AUDIO)
private const val BUNDLE_ARG_APPLIED_LENS_ID = "applied_lens_id"
private const val BUNDLE_ARG_CAMERA_FACING_FRONT = "camera_facing_front"
private val LENS_GROUPS = arrayOf(
    LENS_GROUP_ID_BUNDLED, // lens group for bundled lenses available in lenses-bundle-partner artifact.
    "1" // temporary lens group for testing
)

/**
 * A simple activity which demonstrates how to use [CameraKit] to apply/remove lenses onto a camera preview.
 * Camera is managed through the use of CameraX library which we use to implement a [com.snap.camerakit.Source]
 * to deliver streaming frames to lenses.
 */
class MainActivity : AppCompatActivity(), LifecycleOwner {

    private lateinit var mainLayout: ViewGroup
    private lateinit var imageProcessorSource: CameraXImageProcessorSource
    private lateinit var cameraKitSession: Session

    private var appliedLensId: String? = null
    private var cameraFacingFront: Boolean = true
    private var miniPreviewOutput: Closeable = Closeable {}
    private var availableLensesQuery = Closeable {}
    private var lensesProcessorEvents = Closeable {}
    private var videoRecording: Closeable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            context = this, lifecycleOwner = this
        )

        // This block configures and creates a new CameraKit instance that is the main entry point to all its features.
        // The CameraKit instance must be closed when appropriate to avoid leaking any resources.
        cameraKitSession = Session(this) {
            imageProcessorSource(imageProcessorSource)
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
            cameraKitSession.lenses.processor.apply(lens) { success ->
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
        rootLayout.findViewById<ToggleButton>(R.id.mini_preview_toggle).apply {
            setOnCheckedChangeListener { _, isChecked ->
                miniPreviewOutput.close()
                if (isChecked) {
                    miniPreviewOutput = cameraKitSession.processor.connectOutput(miniPreview)
                } else {
                    (miniPreview.parent as? ViewGroup)?.let { parent ->
                        val index = parent.indexOfChild(miniPreview)
                        parent.removeView(miniPreview)
                        parent.addView(miniPreview, index)
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
        cameraKitSession.close()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (requiredPermissionsGranted()) {
                onRequiredPermissionsGranted()
            } else {
                Toast.makeText(this, "Required permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
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
                                shareVideoExternally(file)
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
                            imageProcessorSource.takeSnapshot { bitmap ->
                                shareImageExternally(bitmap)
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
