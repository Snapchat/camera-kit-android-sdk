package com.snap.camerakit.sample

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import com.snap.camerakit.support.app.CameraActivity

private const val TAG = "MainActivity"
private val LENS_GROUP_IDS = arrayOf(BuildConfig.LENS_GROUP_ID_TEST)
private const val APPLY_LENS_BY_ID = "172e89fb-5d74-46af-9fea-f0ac458443f9"

/**
 * A simple activity that demonstrates how to launch the CameraKit's support [CameraActivity] and to get media results
 * back. [CameraActivity] exposes all the possible capture flow parameters through the [CameraActivity.Capture.Request]
 * which is passed to an [androidx.activity.result.ActivityResultLauncher] obtained by registering this activity to
 * receive results using the [CameraActivity.Capture] contract.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        val captureResultLabel = findViewById<TextView>(R.id.label_capture_result)
        val imageView = findViewById<ImageView>(R.id.image_preview)
        val videoView = findViewById<VideoView>(R.id.video_preview).apply {
            setOnPreparedListener { mediaPlayer ->
                mediaPlayer.isLooping = true
            }
        }
        val clearMediaPreviews = {
            videoView.visibility = View.GONE
            imageView.visibility = View.GONE
        }

        val captureLauncher = (this as ComponentActivity).registerForActivityResult(CameraActivity.Capture) { result ->
            Log.d(TAG, "Got capture result: $result")
            when (result) {
                is CameraActivity.Capture.Result.Success.Video -> {
                    videoView.visibility = View.VISIBLE
                    videoView.setVideoURI(result.uri)
                    videoView.start()
                    imageView.visibility = View.GONE
                }
                is CameraActivity.Capture.Result.Success.Image -> {
                    imageView.visibility = View.VISIBLE
                    imageView.setImageURI(result.uri)
                    videoView.visibility = View.GONE
                }
                is CameraActivity.Capture.Result.Cancelled -> {
                    captureResultLabel.text = getString(R.string.label_capture_result_none)
                    clearMediaPreviews()
                }
                is CameraActivity.Capture.Result.Failure -> {
                    captureResultLabel.text = getString(R.string.label_capture_result_failure, result.message)
                    clearMediaPreviews()
                }
            }
        }

        findViewById<Button>(R.id.button_capture_lenses).setOnClickListener {
            captureLauncher.launch(
                CameraActivity.Capture.Request.WithLenses(
                    lensGroupIds = LENS_GROUP_IDS
                )
            )
        }

        findViewById<Button>(R.id.button_capture_lenses_apply_by_id).setOnClickListener {
            captureLauncher.launch(
                CameraActivity.Capture.Request.WithLenses(
                    lensGroupIds = LENS_GROUP_IDS,
                    applyLensById = APPLY_LENS_BY_ID
                )
            )
        }

        findViewById<Button>(R.id.button_capture_lenses_prefetch_all).setOnClickListener {
            captureLauncher.launch(
                CameraActivity.Capture.Request.WithLenses(
                    lensGroupIds = LENS_GROUP_IDS,
                    prefetchLensByIdPattern = "\\S+"
                )
            )
        }

        findViewById<Button>(R.id.button_capture_lenses_idle_state).setOnClickListener {
            captureLauncher.launch(
                CameraActivity.Capture.Request.WithLenses(
                    lensGroupIds = LENS_GROUP_IDS,
                    disableIdleState = false
                )
            )
        }

        findViewById<Button>(R.id.button_capture_lenses_camera_facing_back).setOnClickListener {
            captureLauncher.launch(
                CameraActivity.Capture.Request.WithLenses(
                    lensGroupIds = LENS_GROUP_IDS,
                    applyLensById = APPLY_LENS_BY_ID,
                    cameraFacingFront = false
                )
            )
        }

        findViewById<Button>(R.id.button_capture_lens).setOnClickListener {
            captureLauncher.launch(
                CameraActivity.Capture.Request.WithLens(
                    lensGroupId = LENS_GROUP_IDS.first(),
                    lensId = APPLY_LENS_BY_ID
                )
            )
        }

        findViewById<Button>(R.id.button_capture_lens_no_icon).setOnClickListener {
            captureLauncher.launch(
                CameraActivity.Capture.Request.WithLens(
                    lensGroupId = LENS_GROUP_IDS.first(),
                    lensId = APPLY_LENS_BY_ID,
                    displayLensIcon = false
                )
            )
        }
    }
}
