package com.snap.camerakit.sample.kotlin

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.TextureView
import android.view.ViewGroup
import android.view.ViewStub
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.snap.camerakit.CameraKit
import com.snap.camerakit.configureLenses
import com.snap.camerakit.connectOutput
import com.snap.camerakit.invoke
import com.snap.camerakit.lenses.LensesComponent.Repository.QueryCriteria.Available
import com.snap.camerakit.lenses.LensesComponent.Repository.QueryCriteria.ById
import com.snap.camerakit.lenses.apply
import com.snap.camerakit.lenses.query
import com.snap.camerakit.lenses.whenHasFirst
import com.snap.camerakit.lenses.whenHasSome
import com.snap.camerakit.support.camerax.CameraXImageProcessorSource
import java.io.Closeable

private const val TAG = "MainActivity"
private const val REQUEST_CODE_PERMISSIONS = 10
private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

/**
 * A simple activity which demonstrates how to use [CameraKit] to apply/remove lenses onto a camera preview.
 * Camera is managed through the use of CameraX library which we use to implement a [com.snap.camerakit.Source]
 * to deliver streaming frames to lenses.
 */
class MainActivity : AppCompatActivity(), LifecycleOwner {

    private lateinit var mainLayout: ViewGroup
    private lateinit var imageProcessorSource: CameraXImageProcessorSource
    private lateinit var cameraKit: CameraKit

    private var cameraFacingFront: Boolean = true
    private var miniPreviewOutput: Closeable = Closeable {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        mainLayout = findViewById(R.id.main_layout)

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
        cameraKit = CameraKit(this) {
            attachTo(imageProcessorSource)
            attachTo(cameraKitStub)
            configureLenses {
                useLensesFromAssets()
            }
        }

        // We create a RecyclerView adapter that notifies when a lens item in the list is selected. Using the selected
        // lens ID we query for matching Lens in LensComponent and if one is found we submit a request to apply it.
        val lensItemListAdapter = LensItemListAdapter { lensItem ->
            cameraKit.lenses.repository.query(ById(lensItem.id)) { result ->
                result.whenHasFirst { lens ->
                    cameraKit.lenses.processor.apply(lens) { result ->
                        Log.d(TAG, "Apply lens [$lens] success: $result")
                    }
                }
            }
        }
        val availableLensesList = findViewById<RecyclerView>(R.id.available_lenses_list)
        availableLensesList.adapter = lensItemListAdapter
        availableLensesList.layoutManager = LinearLayoutManager(this)

        // Working with the CameraKit's lenses component we query for all lenses that are available and the first found
        // is applied as soon as possible.
        cameraKit.lenses.repository.query(Available) { available ->
            Log.d(TAG, "Available lenses: $available")
            available.whenHasSome { lenses ->
                mainLayout.post {
                    lensItemListAdapter.submitList(lenses.toLensItems())
                }
            }
            available.whenHasFirst { lens ->
                cameraKit.lenses.processor.apply(lens) { result ->
                    Log.d(TAG, "Apply lens [$lens] success: $result")
                }
            }
        }

        // While CameraKit is capable (and does) render camera preview into an internal view, this demonstrates how
        // to connect another TextureView as rendering output.
        val miniPreview = mainLayout.findViewById<TextureView>(R.id.mini_preview)
        miniPreviewOutput = cameraKit.processor.connectOutput(miniPreview)
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
        cameraKit.close()
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
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent?): Boolean {
                cameraFacingFront = !cameraFacingFront
                startPreviewForCurrentCameraFacing()
                return true
            }
        })
        mainLayout.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun startPreviewForCurrentCameraFacing() {
        imageProcessorSource.startPreview(cameraFacingFront)
    }
}
