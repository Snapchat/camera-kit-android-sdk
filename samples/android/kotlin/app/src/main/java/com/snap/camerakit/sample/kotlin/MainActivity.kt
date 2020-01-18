package com.snap.camerakit.sample.kotlin

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.snap.camerakit.CameraKit
import com.snap.camerakit.invoke

class MainActivity : AppCompatActivity() {

    private lateinit var cameraKit: CameraKit

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        cameraKit = CameraKit(this)
    }

    override fun onDestroy() {
        cameraKit.close()
        super.onDestroy()
    }
}
