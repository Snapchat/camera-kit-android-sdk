package com.snap.camerakit.sample

import android.os.Handler
import android.os.Looper
import com.snap.camerakit.ImageProcessor
import com.snap.camerakit.Source
import com.snap.camerakit.common.Consumer
import com.snap.camerakit.support.arcore.ArCoreImageProcessorSource
import java.io.Closeable
import java.util.concurrent.atomic.AtomicReference

/**
 * A simple implementation of [Source] for [ImageProcessor] which uses [ImageProcessor.observeRequiredCapabilities]
 * method to attach different camera sources when requirements changed.
 *
 * @param defaultCameraSource The default source to be used when no additional capabilities are required.
 * @param surfaceTrackingSource The source which can provide surface tracking data and depth images.
 * Currently, only the [ArCoreImageProcessorSource] can be used as a [surfaceTrackingSource].
 * @param onSourceAttached The consumer which is notified once attached source is changed.
 * @param callbackHandler The handler on which [onSourceAttached] consumer is invoked.
 */
internal class SwitchForSurfaceTrackingImageProcessorSource(
    private val defaultCameraSource: Source<ImageProcessor>,
    private val surfaceTrackingSource: Source<ImageProcessor>,
    private val onSourceAttached: Consumer<Source<ImageProcessor>>,
    private val callbackHandler: Handler = Handler(Looper.getMainLooper())
) : Source<ImageProcessor> {

    private val attachedSourceCloseable = AtomicReference<Closeable>()
    private val attachedSourceReference = AtomicReference<Source<ImageProcessor>>()
    private val sourceAttachedRunnable = Runnable {
        onSourceAttached.accept(attachedSourceReference.get())
    }

    override fun attach(processor: ImageProcessor): Closeable {
        val capabilitiesSubscription = processor.observeRequiredCapabilities { capabilities ->
            if (capabilities.isEmpty() &&
                attachedSourceReference.getAndSet(defaultCameraSource) != defaultCameraSource) {
                attachedSourceCloseable.getAndSet(defaultCameraSource.attach(processor))?.close()
                notifyNewSourceAttached()
            } else if (capabilities.contains(ImageProcessor.Input.Capability.SURFACE_TRACKING) &&
                attachedSourceReference.getAndSet(surfaceTrackingSource) != surfaceTrackingSource) {
                attachedSourceCloseable.getAndSet(surfaceTrackingSource.attach(processor))?.close()
                notifyNewSourceAttached()
            }
        }
        return Closeable {
            capabilitiesSubscription.close()
            attachedSourceCloseable.getAndSet(null)?.close()
        }
    }

    private fun notifyNewSourceAttached() {
        callbackHandler.run {
            removeCallbacks(sourceAttachedRunnable)
            post(sourceAttachedRunnable)
        }
    }
}
