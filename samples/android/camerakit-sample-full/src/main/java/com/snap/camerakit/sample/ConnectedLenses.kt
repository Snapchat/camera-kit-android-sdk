package com.snap.camerakit.sample

import android.app.Activity
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.snap.camerakit.Source
import com.snap.camerakit.common.Consumer
import com.snap.camerakit.extension.auth.AuthTokenProvider
import com.snap.camerakit.extension.connected.lenses.ConnectedLensesService
import com.snap.camerakit.extension.connected.lenses.configureConnectedLenses
import com.snap.camerakit.lenses.LensesComponent
import java.io.Closeable
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "ConnectedLenses"

/**
 * Configures Connected Lenses feature for the [LensesComponent] if enabled.
 */
internal fun LensesComponent.Builder.configureConnectedLenses(
    activity: Activity,
    startButton: AtomicReference<View>,
    authTokenProvider: AuthTokenProvider,
    onConnectedLensSessionActive: (Boolean) -> Unit
) {
    val connectedLensesSessionLauncherSource = object : Source<ConnectedLensesService.SessionLauncher> {
        override fun attach(sessionLauncher: ConnectedLensesService.SessionLauncher): Closeable {
            return SessionLauncherAttachedToStartButton(
                sessionLauncher,
                startButton.get(),
                onConnectedLensSessionActive,
                activity
            )
        }
    }
    // Configure connected lenses service.
    configureConnectedLenses {
        authTokenProvider(authTokenProvider)
        sessionLauncherSource(connectedLensesSessionLauncherSource)
    }
}

/**
 * Attaches start to the Connected Lenses feature. If user clicks the start button, app opens a dialog where user
 * can enter a connected lens multiplayer session group id. All users who entered the same group id join the same
 * multiplayer session.
 */
private class SessionLauncherAttachedToStartButton(
    private val delegate: ConnectedLensesService.SessionLauncher,
    private val startButton: View,
    private val onConnectedLensSessionActive: (Boolean) -> Unit,
    activity: Activity
) : ConnectedLensesService.SessionLauncher by delegate, Closeable {

    init {
        startButton.post {
            startButton.visibility = View.VISIBLE
            startButton.setOnClickListener {
                startButton.visibility = View.GONE
                var groupId = ""
                val dialog = AlertDialog.Builder(activity)
                    .setView(R.layout.dialog_launch_multiplayer_session)
                    .setCancelable(true)
                    .setPositiveButton(R.string.connected_lenses_button_join) { _, _ ->
                        if (groupId.isNotEmpty()) {
                            joinSession(groupId) { result ->
                                Log.d(TAG, "Connected lenses session launch result $result")
                            }
                        }
                    }
                    .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                        startButton.visibility = View.VISIBLE
                        dialog.cancel()
                    }
                    .create()
                    .apply {
                        show()
                    }

                dialog.findViewById<EditText>(R.id.connected_lens_group_id_field)!!.apply {
                    addTextChangedListener(object : TextWatcher {

                        override fun afterTextChanged(s: Editable) {}

                        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

                        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                            groupId = s.toString()
                        }
                    })
                }
            }
        }
    }

    override fun joinSession(groupId: String, onResult: Consumer<ConnectedLensesService.SessionLauncher.Result>) {
        onConnectedLensSessionActive(true)
        // Hide Start button while joining connected lenses session.
        startButton.post { startButton.visibility = View.GONE }
        delegate.joinSession(groupId) { result ->
            onResult.accept(result)
            if (result is ConnectedLensesService.SessionLauncher.Result.Failure) {
                // If connected lenses session launch failed we want to show Start button again so
                // user could try to re-launch session.
                startButton.post { startButton.visibility = View.VISIBLE }
                onConnectedLensSessionActive(false)
            }
        }
    }

    override fun close() {
        onConnectedLensSessionActive(false)
        startButton.post {
            startButton.visibility = View.GONE
            startButton.setOnClickListener(null)
        }
    }
}
