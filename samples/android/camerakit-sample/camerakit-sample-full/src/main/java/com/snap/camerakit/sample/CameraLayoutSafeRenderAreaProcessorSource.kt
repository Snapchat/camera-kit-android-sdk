package com.snap.camerakit.sample

import android.app.Activity
import android.graphics.Rect
import android.view.View
import com.snap.camerakit.SafeRenderAreaProcessor
import com.snap.camerakit.Source
import com.snap.camerakit.common.Consumer
import java.io.Closeable
import java.lang.ref.WeakReference

/**
 * Simple implementation of a [Source] for a [SafeRenderAreaProcessor] that calculates a safe render area [Rect] that is
 * between the camera flip and capture buttons present in the provided [Activity]'s layout.
 */
internal class CameraLayoutSafeRenderAreaProcessorSource(activity: Activity) : Source<SafeRenderAreaProcessor> {

    private val activityReference = WeakReference(activity)

    override fun attach(processor: SafeRenderAreaProcessor): Closeable {
        return processor.connectInput(object : SafeRenderAreaProcessor.Input {
            override fun subscribeTo(onSafeRenderAreaAvailable: Consumer<Rect>): Closeable {
                val activity = activityReference.get()
                val cameraPreviewView = activity?.findViewById<View>(R.id.main_layout)
                if (activity == null || cameraPreviewView == null) {
                    return Closeable {
                        // no-op
                    }
                } else {
                    fun updateSafeRenderRegionIfNecessary() {
                        val safeRenderRect = Rect()
                        if (cameraPreviewView.getGlobalVisibleRect(safeRenderRect)) {
                            val tmpRect = Rect()
                            activity.window.decorView.getWindowVisibleDisplayFrame(tmpRect)
                            val statusBarHeight = tmpRect.top
                            // Make the zone's top to start below the camera flip button where other camera controls
                            // or app's "top bar" might be located.
                            if (activity.findViewById<View>(R.id.button_flip_camera)
                                    ?.getGlobalVisibleRect(tmpRect) == true
                            ) {
                                safeRenderRect.top = tmpRect.bottom - statusBarHeight
                            }
                            // Make the zone's bottom to start above capture button - anything under or below it should
                            // not be considered safe to render to.
                            if (activity.findViewById<View>(R.id.capture_button)
                                    ?.getGlobalVisibleRect(tmpRect) == true
                            ) {
                                safeRenderRect.bottom = tmpRect.top - statusBarHeight
                            }
                            onSafeRenderAreaAvailable.accept(safeRenderRect)
                        }
                    }
                    // The processor might subscribe to the input when views are laid out already so we can attempt
                    // to calculate the safe render area already:
                    updateSafeRenderRegionIfNecessary()
                    // Otherwise we start listening for layout changes to update the safe render rect continuously:
                    val onLayoutChangeListener = View.OnLayoutChangeListener {
                        _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                        if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
                            updateSafeRenderRegionIfNecessary()
                        }
                    }
                    cameraPreviewView.addOnLayoutChangeListener(onLayoutChangeListener)
                    return Closeable {
                        cameraPreviewView.removeOnLayoutChangeListener(onLayoutChangeListener)
                    }
                }
            }
        })
    }
}
