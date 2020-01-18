package com.snap.camerakit.sample.kotlin

import com.snap.camerakit.ImageProcessor
import com.snap.camerakit.Source
import java.io.Closeable

object NoopImageProcessorSource : Source<ImageProcessor> {

    override fun attach(processor: ImageProcessor): Closeable = Closeable {}
}
