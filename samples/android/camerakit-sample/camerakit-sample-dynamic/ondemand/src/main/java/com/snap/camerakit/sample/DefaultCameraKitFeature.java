package com.snap.camerakit.sample;

import android.content.Context;

import com.snap.camerakit.ImageProcessor;
import com.snap.camerakit.ImageProcessors;
import com.snap.camerakit.Session;
import com.snap.camerakit.Sessions;
import com.snap.camerakit.Source;

import java.io.File;

/**
 * Simple implementation of {@link CameraKitFeature} which simply delegates all methods to the CameraKit SDK.
 */
public final class DefaultCameraKitFeature implements CameraKitFeature {

    private Context context;

    @Override
    public CameraKitFeature attach(Context context) {
        this.context = context;
        return this;
    }

    @Override
    public boolean supported() {
        return Sessions.supported(context);
    }

    @Override
    public Session.Builder newSessionBuilder() {
        return Sessions.newBuilder(context);
    }

    @Override
    public Source<ImageProcessor> sourceFrom(File videoFile) {
        return ImageProcessors.sourceFrom(context, videoFile);
    }
}
