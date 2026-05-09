package fail.tiger.komgarot

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import fail.tiger.komgarot.ui.navigation.AppNavGraph
import fail.tiger.komgarot.ui.theme.KomgarotTheme

class MainActivity : AppCompatActivity() {
    private var backgroundedAt = 0L
    private val locked = mutableStateOf(false)
    private var promptShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as KomgarotApp
        enableEdgeToEdge()
        setContent {
            KomgarotTheme {
                Box(Modifier.fillMaxSize().then(if (locked.value) Modifier.blur(20.dp) else Modifier)) {
                    AppNavGraph(app)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (promptShowing) return
        val prefs = (application as KomgarotApp).authPreferences
        if (!prefs.appLockEnabledBlocking) return

        val timeoutMs = prefs.appLockTimeoutBlocking * 60_000L
        val elapsed = SystemClock.elapsedRealtime() - backgroundedAt
        if (backgroundedAt > 0 && timeoutMs > 0 && elapsed < timeoutMs) return

        locked.value = true
        promptShowing = true
        showBiometricPrompt()
    }

    override fun onStop() {
        super.onStop()
        backgroundedAt = SystemClock.elapsedRealtime()
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
                    locked.value = false
                }
            }
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                promptShowing = false
                backgroundedAt = 0L
                locked.value = false
            }
            override fun onAuthenticationFailed() {}
        })

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("验证身份")
            .setSubtitle("使用屏幕锁或生物识别解锁")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        prompt.authenticate(info)
    }
}
