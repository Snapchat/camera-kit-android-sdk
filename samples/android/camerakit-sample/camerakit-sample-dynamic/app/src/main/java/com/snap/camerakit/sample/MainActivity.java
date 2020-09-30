package com.snap.camerakit.sample;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.play.core.splitinstall.SplitInstallManager;
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory;
import com.google.android.play.core.splitinstall.SplitInstallRequest;
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus;
import com.google.android.play.core.tasks.Task;
import com.snap.camerakit.Session;
import com.snap.camerakit.lenses.LensesComponent;
import com.snap.camerakit.lenses.LensesComponent.Repository.QueryCriteria.Available;
import com.snap.camerakit.sample.dynamic.app.BuildConfig;
import com.snap.camerakit.sample.dynamic.app.R;

import static com.snap.camerakit.sample.dynamic.app.BuildConfig.LENS_GROUP_ID_TEST;

/**
 * A simple activity that demonstrates loading CameraKit implementation library on demand both as a plugin that lives in
 * a separate apk installation as well as a dynamic feature using Google Play's {@link SplitInstallManager}.
 * When user clicks on "INSTALL CAMERAKIT" button we attempt to install {@link CameraKitFeature} and, it it succeeds,
 * a group lenses is requested and displayed in a list of items with details such lens name, icon etc.
 */
public final class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private SplitInstallManager splitInstallManager;
    private Task<Integer> installTask;
    private CameraKitFeature cameraKitFeature;

    private ContentLoadingProgressBar loadingIndicator;
    private RecyclerView lensesListView;
    private Button installCameraKitButton;
    private View lensesUnavailableView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        splitInstallManager = SplitInstallManagerFactory.create(this);

        setContentView(R.layout.activity_main);
        loadingIndicator = findViewById(R.id.loading_indicator);
        loadingIndicator.hide();

        lensesListView = findViewById(R.id.lenses_list);
        lensesListView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        DividerItemDecoration itemDecoration = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        itemDecoration.setDrawable(getDrawable(R.drawable.divider));
        lensesListView.addItemDecoration(itemDecoration);

        installCameraKitButton = findViewById(R.id.install_camerakit_button);
        installCameraKitButton.setOnClickListener(v -> {
            v.setEnabled(false);
            loadingIndicator.show();
            tryInstallCameraKitFeature();
        });

        lensesUnavailableView = findViewById(R.id.lenses_unavailable);
    }

    private void tryInstallCameraKitFeature() {
        // Attempt to get CameraKitFeature.Loader if plugin application is installed on the device, otherwise fallback
        // to Google Play split module install approach.
        CameraKitFeature.Loader loader =
                CameraKitFeature.Loader.Factory.pathClassLoader(this, BuildConfig.DYNAMIC_PLUGIN_CAMERAKIT);
        if (loader != null) {
            Toast.makeText(
                    this,
                    getString(R.string.message_camerakit_load_plugin, BuildConfig.DYNAMIC_PLUGIN_CAMERAKIT),
                    Toast.LENGTH_LONG)
                    .show();
            onCameraKitFeatureInstalled(loader);
        } else if (splitInstallManager.getInstalledModules().contains(BuildConfig.DYNAMIC_FEATURE_CAMERAKIT)) {
            Toast.makeText(this, R.string.message_camerakit_load_feature, Toast.LENGTH_LONG).show();
            onCameraKitFeatureInstalled(CameraKitFeature.Loader.Factory.serviceLoader());
        } else if (installTask == null) {
            SplitInstallRequest installRequest = SplitInstallRequest
                    .newBuilder()
                    .addModule(BuildConfig.DYNAMIC_FEATURE_CAMERAKIT)
                    .build();
            installTask = splitInstallManager.startInstall(installRequest)
                    .addOnFailureListener(e -> {
                        installTask = null;
                        runOnUiThread(() -> {
                            Toast.makeText(
                                    this,
                                    getString(R.string.message_camerakit_install_failure, e.getMessage()),
                                    Toast.LENGTH_LONG).show();
                            loadingIndicator.hide();
                            installCameraKitButton.setEnabled(true);
                        });
                    });
            splitInstallManager.registerListener(state -> {
                if (state.status() == SplitInstallSessionStatus.INSTALLED) {
                    Toast.makeText(this, R.string.message_camerakit_load_feature, Toast.LENGTH_LONG).show();
                    onCameraKitFeatureInstalled(CameraKitFeature.Loader.Factory.serviceLoader());
                }
            });
        } else {
            Log.w(TAG, "CameraKit feature install task may be running already");
        }
    }

    private void onCameraKitFeatureInstalled(CameraKitFeature.Loader loader) {
        if (cameraKitFeature != null) {
            Log.w(TAG, "CameraKit feature has been setup already");
            loadingIndicator.hide();
            installCameraKitButton.setVisibility(View.GONE);
        } else {
            cameraKitFeature = loader.load();
            if (cameraKitFeature.supported(getApplicationContext())) {
                Session cameraKitSession = cameraKitFeature
                        .newSessionBuilder(getApplicationContext())
                        .build();
                cameraKitSession.getLenses().getRepository().get(new Available(LENS_GROUP_ID_TEST), result -> {
                    Log.d(TAG, "Lenses query result: " + result);
                    runOnUiThread(() -> {
                        loadingIndicator.hide();
                        installCameraKitButton.setVisibility(View.GONE);
                        lensesUnavailableView.setVisibility(View.GONE);
                        if (result instanceof LensesComponent.Repository.Result.Some) {
                            LensListAdapter lensListAdapter =
                                    new LensListAdapter(((LensesComponent.Repository.Result.Some) result).getLenses());
                            lensesListView.setAdapter(lensListAdapter);
                        } else {
                            lensesUnavailableView.setVisibility(View.VISIBLE);
                        }
                    });
                    cameraKitSession.close();
                });
            } else {
                Toast.makeText(this, R.string.message_camerakit_unsupported, Toast.LENGTH_SHORT).show();
                loadingIndicator.hide();
                installCameraKitButton.setVisibility(View.GONE);
            }
        }
    }
}
