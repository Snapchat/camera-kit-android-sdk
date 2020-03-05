package com.snap.camerakit.sample.kotlin

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.snap.camerakit.Session
import com.snap.camerakit.configureLenses
import com.snap.camerakit.connectOutput
import com.snap.camerakit.invoke
import com.snap.camerakit.lenses.LENS_GROUP_ID_BUNDLED
import com.snap.camerakit.lenses.LensesComponent
import com.snap.camerakit.lenses.LensesComponent.Repository.QueryCriteria.Available
import com.snap.camerakit.lenses.LensesComponent.Repository.QueryCriteria.ById
import com.snap.camerakit.lenses.apply
import com.snap.camerakit.lenses.get
import com.snap.camerakit.lenses.observe
import com.snap.camerakit.lenses.whenHasFirst
import com.snap.camerakit.lenses.whenHasSome
import com.snap.camerakit.support.camerax.CameraXImageProcessorSource
import java.io.Closeable
import kotlin.math.max
import kotlin.math.min

private const val TAG = "MainActivity"
private const val REQUEST_CODE_PERMISSIONS = 10
private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
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
    private lateinit var lensItemListAdapter: LensItemListAdapter
    private lateinit var imageProcessorSource: CameraXImageProcessorSource
    private lateinit var cameraKitSession: Session

    private var appliedLensId: String? = null
    private var cameraFacingFront: Boolean = true
    private var miniPreviewOutput: Closeable = Closeable {}

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
            context = this, lifecycleOwner =  this
        )

        // This block configures and creates a new CameraKit instance that is the main entry point to all its features.
        // The CameraKit instance must be closed when appropriate to avoid leaking any resources.
        cameraKitSession = Session(this) {
            attachTo(imageProcessorSource)
            attachTo(cameraKitStub)
            configureLenses {
                // When CameraKit is configured to manage its own views by providing a view stub (see above),
                // lenses touch handling might consume all events due to the fact that it needs to perform gesture
                // detection internally. If application needs to handle gestures on top of it then LensesComponent
                // provides a way to dispatch all touch events unhandled by active lens back.
                dispatchTouchEventsTo(mainLayout.findViewById(R.id.preview_gesture_handler))
            }
        }

        // We keep the last applied Lens reference here in order to update the RecyclerView adapter
        // as well as to use it when determining the next or previous lens to switch to.
        val applyLens = { lens: LensesComponent.Lens ->
            if (appliedLensId == lens.id) {
                Log.d(TAG, "Lens with ID [${lens.id}] has been applied already, ignoring")
                Unit
            } else {
                cameraKitSession.lenses.processor.apply(lens) { success ->
                    Log.d(TAG, "Apply lens [$lens] success: $success")
                    if (success) {
                        appliedLensId = lens.id
                        mainLayout.post {
                            lensItemListAdapter.select(lens.toLensItem())
                            Toast.makeText(
                                this, "Applied lens : ${lens.name ?: lens.id}", Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
        // Working with the CameraKit's lenses component we query for all lenses that are available. 
        // If we have an applied Lens ID saved previously we then try to find it in the list and apply it,
        // otherwise we apply the first one from the non-empty list.
        var availableLenses = emptyList<LensesComponent.Lens>()
        cameraKitSession.lenses.repository.observe(Available(*LENS_GROUPS)) { available ->
            Log.d(TAG, "Available lenses: $available")
            available.whenHasSome { lenses ->
                availableLenses = lenses
                appliedLensId?.let { id ->
                    lenses.find { it.id == id }?.let(applyLens)
                } ?: lenses.first().let(applyLens)
                mainLayout.post {
                    lensItemListAdapter.submitList(lenses.toLensItems())
                }
            }
        }

        // Simple previous/next button binding that finds lens in availableLenses list and applies it.
        val previousButton = mainLayout.findViewById<AppCompatImageButton>(R.id.button_previous)
        val nextButton = mainLayout.findViewById<AppCompatImageButton>(R.id.button_next)
        val lensButton = mainLayout.findViewById<AppCompatImageView>(R.id.button_lens)
        previousButton.setOnClickListener {
            val index = availableLenses.indexOfFirst { it.id == appliedLensId }
            if (index != -1 && availableLenses.isNotEmpty()) {
                val previousIndex = max(0, index - 1)
                val previous = availableLenses[
                        if (previousIndex == index) availableLenses.size - 1 else previousIndex
                ]
                applyLens(previous)
            }
        }
        nextButton.setOnClickListener {
            val index = availableLenses.indexOfFirst { it.id == appliedLensId }
            if (index != -1 && availableLenses.isNotEmpty()) {
                val nextIndex = min(availableLenses.size - 1, index + 1)
                val next = availableLenses[
                        if (nextIndex == index) 0 else nextIndex
                ]
                applyLens(next)
            }
        }
        lensButton.setOnClickListener {
            rootLayout.openDrawer(Gravity.LEFT)
        }

        // We create a RecyclerView adapter that notifies when a lens item in the list is selected. Using the clicked
        // lens item ID we query for matching Lens in LensComponent and if one is found we submit a request to apply it.
        lensItemListAdapter = LensItemListAdapter { lensItem ->
            cameraKitSession.lenses.repository.get(ById(lensItem.id, lensItem.groupId)) { result ->
                result.whenHasFirst(applyLens)
            }
        }
        val availableLensesList = findViewById<RecyclerView>(R.id.available_lenses_list)
        availableLensesList.adapter = lensItemListAdapter
        availableLensesList.layoutManager = LinearLayoutManager(this)

        // While CameraKit is capable (and does) render camera preview into an internal view, this demonstrates how
        // to connect another TextureView as rendering output.
        val miniPreview = mainLayout.findViewById<TextureView>(R.id.mini_preview)
        miniPreviewOutput = cameraKitSession.processor.connectOutput(miniPreview)

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
        if (allPermissionsGranted()) {
            onAllPermissionsGranted()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    override fun onDestroy() {
        miniPreviewOutput.close()
        cameraKitSession.close()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>,
            grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                onAllPermissionsGranted()
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun onAllPermissionsGranted() {
        mainLayout.post {
            startPreviewForCurrentCameraFacing()
        }
        val flipCamera = {
            cameraFacingFront = !cameraFacingFront
            startPreviewForCurrentCameraFacing()
        }
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent?): Boolean {
                flipCamera()
                return true
            }
        })
        mainLayout.findViewById<View>(R.id.preview_gesture_handler).setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
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
