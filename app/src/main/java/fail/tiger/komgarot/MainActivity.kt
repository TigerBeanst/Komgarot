package fail.tiger.komgarot

import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import fail.tiger.komgarot.ui.navigation.AppNavGraph
import fail.tiger.komgarot.ui.reader.ReaderPhysicalKeyDispatcher
import fail.tiger.komgarot.ui.theme.KomgarotTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private var backgroundedAt = 0L
    private val locked = mutableStateOf(false)
    private val privacyCovered = mutableStateOf(false)
    private var promptShowing = false
    private var lockCheckJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as KomgarotApp
        privacyCovered.value = app.authPreferences.appLockEnabledBlocking
        enableEdgeToEdge()
        setContent {
            KomgarotTheme {
                val einkMode by app.authPreferences.einkMode.collectAsStateWithLifecycle(initialValue = false)
                val privacyActive = locked.value || privacyCovered.value
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().then(if (privacyActive && !einkMode) Modifier.blur(20.dp) else Modifier)) {
                        AppNavGraph(app)
                    }
                    if (privacyActive && einkMode) {
                        Box(Modifier.fillMaxSize().background(Color.Black))
                    } else if (privacyActive) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)))
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (promptShowing || lockCheckJob?.isActive == true) return
        val prefs = (application as KomgarotApp).authPreferences

        lockCheckJob = lifecycleScope.launch {
            val appLockEnabled = prefs.appLockEnabled.first()
            val appLockTimeout = prefs.appLockTimeout.first()
            if (!appLockEnabled || promptShowing) return@launch

            val timeoutMs = appLockTimeout * 60_000L
            val elapsed = SystemClock.elapsedRealtime() - backgroundedAt
            if (backgroundedAt > 0 && timeoutMs > 0 && elapsed < timeoutMs) {
                revealUnlockedContent()
                return@launch
            }

            locked.value = true
            privacyCovered.value = true
            promptShowing = true
            showBiometricPrompt()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!locked.value && !privacyCovered.value && ReaderPhysicalKeyDispatcher.dispatch(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onPause() {
        coverForAppLockIfEnabled()
        super.onPause()
        lockCheckJob?.cancel()
        lockCheckJob = null
    }

    override fun onStop() {
        super.onStop()
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    private fun coverForAppLockIfEnabled() {
        if ((application as KomgarotApp).authPreferences.appLockEnabledBlocking) {
            privacyCovered.value = true
        }
    }

    private fun revealUnlockedContent() {
        locked.value = false
        privacyCovered.value = false
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                promptShowing = false
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    finish()
                } else {
                    revealUnlockedContent()
                }
            }
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                promptShowing = false
                backgroundedAt = 0L
                revealUnlockedContent()
            }
            override fun onAuthenticationFailed() {}
        })

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_title))
            .setSubtitle(getString(R.string.biometric_subtitle))
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        prompt.authenticate(info)
    }
}
