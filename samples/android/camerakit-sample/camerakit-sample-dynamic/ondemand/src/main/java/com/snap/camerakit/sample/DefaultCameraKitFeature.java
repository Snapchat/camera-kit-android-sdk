package com.snap.camerakit.sample;

import android.content.Context;

import com.snap.camerakit.Session;
import com.snap.camerakit.Sessions;

/**
 * Simple implementation of {@link CameraKitFeature} which simply delegates all methods to the CameraKit SDK.
 */
public final class DefaultCameraKitFeature implements CameraKitFeature {

    @Override
    public boolean supported(Context context) {
        return Sessions.supported(context);
    }

    @Override
    public Session.Builder newSessionBuilder(Context context) {
        return Sessions.newBuilder(context);
    }
}
