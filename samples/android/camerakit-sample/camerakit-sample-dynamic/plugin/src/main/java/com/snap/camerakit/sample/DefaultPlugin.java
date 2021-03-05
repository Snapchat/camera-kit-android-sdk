package com.snap.camerakit.sample;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.media.Image;
import android.view.Surface;
import android.view.TextureView;

import com.snap.camerakit.AudioProcessor;
import com.snap.camerakit.AudioProcessors;
import com.snap.camerakit.ImageProcessor;
import com.snap.camerakit.ImageProcessors;
import com.snap.camerakit.Session;
import com.snap.camerakit.Sessions;
import com.snap.camerakit.Source;
import com.snap.camerakit.lenses.LensesComponent;
import com.snap.camerakit.lenses.LensesLaunchData;
import com.snap.camerakit.plugin.Plugin;

import java.io.Closeable;
import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Simple implementation of {@link Plugin} that delegates all calls to CameraKit SDK.
 */
public final class DefaultPlugin extends Plugin {

    private Context context;

    @Override
    protected Plugin attach(Context context) {
        this.context = context;
        return this;
    }

    @Override
    protected boolean supported() {
        return Sessions.supported(context);
    }

    @Override
    public Session.Builder newSessionBuilder() {
        return Sessions.newBuilder(context);
    }

    @Override
    public ImageProcessor.Input imageProcessorInputFrom(
            SurfaceTexture surfaceTexture,
            int width,
            int height,
            int rotationDegrees,
            boolean facingFront,
            Callable<Float> horizontalFieldOfView,
            Callable<Float> verticalFieldOfView) {
        return ImageProcessors.inputFrom(
                surfaceTexture, width, height, rotationDegrees, facingFront, horizontalFieldOfView, verticalFieldOfView
        );
    }

    @Override
    public ImageProcessor.Output imageProcessorOutputFrom(
            SurfaceTexture surfaceTexture, ImageProcessor.Output.Purpose purpose, int rotationDegrees) {
        return ImageProcessors.outputFrom(surfaceTexture, purpose, rotationDegrees);
    }

    @Override
    public ImageProcessor.Output imageProcessorOutputFrom(
            Surface surface, ImageProcessor.Output.Purpose purpose, int rotationDegrees) {
        return ImageProcessors.outputFrom(surface, purpose, rotationDegrees);
    }

    @Override
    public <P extends ImageProcessor> Bitmap imageProcessorToBitmap(
            P processor, int width, int height, int rotationDegrees) {
        return ImageProcessors.toBitmap(processor, width, height, rotationDegrees);
    }

    @Override
    public <P extends ImageProcessor> Bitmap imageProcessorProcessBitmap(
            P processor, ImageProcessor.Input input, Bitmap bitmap, long timeout, TimeUnit timeoutUnit) {
        return ImageProcessors.processBitmap(processor, input, bitmap, Integer.MIN_VALUE, timeout, timeoutUnit);
    }

    @Override
    public <P extends ImageProcessor> Bitmap imageProcessorProcessBitmap(
            P processor, ImageProcessor.Input input, Bitmap bitmap, int rotation, long timeout, TimeUnit timeoutUnit) {
        return ImageProcessors.processBitmap(processor, input, bitmap, rotation, timeout, timeoutUnit);
    }

    @Override
    public <P extends ImageProcessor> Bitmap imageProcessorProcessImage(
            P processor, ImageProcessor.Input input, Image image, long timeout, TimeUnit timeoutUnit) {
        return ImageProcessors.processImage(processor, input, image, Integer.MIN_VALUE, timeout, timeoutUnit);
    }

    @Override
    public <P extends ImageProcessor> Bitmap imageProcessorProcessImage(
            P processor, ImageProcessor.Input input, Image image, int rotation, long timeout, TimeUnit timeoutUnit) {
        return ImageProcessors.processImage(processor, input, image, rotation, timeout, timeoutUnit);
    }

    @Override
    public <P extends ImageProcessor> Closeable imageProcessorConnectOutput(
            P processor, TextureView textureView) {
        return ImageProcessors.connectOutput(processor, textureView);
    }

    @Override
    public <P extends ImageProcessor> Closeable imageProcessorConnectOutput(
            P processor, File file, int width, int height, boolean captureAudio) {
        return ImageProcessors.connectOutput(processor, file, width, height, captureAudio);
    }

    @Override
    public <P extends ImageProcessor> Closeable imageProcessorConnectInput(
            P processor,
            File file,
            int rotationDegrees,
            boolean facingFront,
            float horizontalFieldOfView,
            float verticalFieldOfView) {
        return ImageProcessors.connectInput(
                processor, context, file, rotationDegrees, facingFront, horizontalFieldOfView, verticalFieldOfView);
    }

    @Override
    public Source<ImageProcessor> imageProcessorSourceFrom(
            File file,
            int rotationDegrees,
            boolean facingFront,
            float horizontalFieldOfView,
            float verticalFieldOfView) {
        return ImageProcessors.sourceFrom(
                context, file, rotationDegrees, facingFront, horizontalFieldOfView, verticalFieldOfView);
    }

    @Override
    public Source<AudioProcessor> audioProcessorMicrophoneSourceFor(ExecutorService executorService) {
        return AudioProcessors.microphoneSourceFor(executorService);
    }

    @Override
    public AudioProcessor.Input.Frame audioProcessorInputFrameFrom(byte[] buffer, int samplesCount) {
        return AudioProcessors.inputFrameFrom(buffer, samplesCount);
    }

    @Override
    public LensesComponent.Lens.LaunchData.Builder newLensLaunchDataBuilder() {
        return LensesLaunchData.newBuilder();
    }
}
