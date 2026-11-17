package app.roll.gallery.ui.locked

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.roll.gallery.R
import app.roll.gallery.data.Media
import app.roll.gallery.ui.GalleryViewModel
import app.roll.gallery.ui.components.rememberRequestLauncher

/** True when the phone has a PIN, pattern or password; the Locked folder needs one. */
fun Context.hasScreenLock(): Boolean = getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

/**
 * Shows Android's own unlock prompt (fingerprint, face, or the phone's PIN, pattern or
 * password) and calls [onUnlocked] if it's passed. Uses Android's built-in prompt directly, so
 * no extra library is needed; it needs the USE_BIOMETRIC permission. If the prompt can't be
 * shown at all, the folder just stays locked rather than closing the app.
 */
fun Context.askScreenLock(onUnlocked: () -> Unit) {
    runCatching { showScreenLockPrompt(onUnlocked) }
}

private fun Context.showScreenLockPrompt(onUnlocked: () -> Unit) {
    val prompt = BiometricPrompt.Builder(this)
        .setTitle(getString(R.string.locked_unlock_title))
        .setSubtitle(getString(R.string.locked_unlock_subtitle))
        .setAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL)
        .build()
    prompt.authenticate(
        CancellationSignal(),
        mainExecutor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onUnlocked()
            }
        },
    )
}

/**
 * Keeps what's on screen private while shown: no screenshots or screen recording, and a blank
 * preview in the recent apps list.
 */
@Composable
fun SecureWindow() {
    val window = (LocalContext.current as? Activity)?.window ?: return
    DisposableEffect(window) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

/**
 * Something that moves photos into the Locked folder: Gallery copies them in, then Android asks
 * to delete the originals (unless Media management is on). If that's refused, the copies are
 * removed again. [onDone] runs once the request has been answered.
 */
@Composable
fun rememberMoveToLocked(viewModel: GalleryViewModel, onDone: () -> Unit = {}): (List<Media>) -> Unit {
    var pending by remember { mutableStateOf<List<Media>?>(null) }
    val request = rememberRequestLauncher { agreed ->
        pending?.let { viewModel.finishLocking(it, deleted = agreed) }
        pending = null
        onDone()
    }
    return { items ->
        viewModel.startLocking(items) { originals, copies ->
            pending = copies
            request(viewModel.deleteRequest(originals))
        }
    }
}
