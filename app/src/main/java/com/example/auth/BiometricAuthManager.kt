package com.example.auth

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.util.CryptoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

fun Context.findFragmentActivity(): FragmentActivity? {
  var current: Context? = this
  while (current is android.content.ContextWrapper) {
    if (current is FragmentActivity) return current
    current = current.baseContext
  }
  return null
}

sealed class BiometricAuthResult {
  data class Success(val method: String, val message: String) : BiometricAuthResult()
  data class FallbackToPin(val reason: String) : BiometricAuthResult()
  data class Error(val errorMessage: String) : BiometricAuthResult()
  object Cancelled : BiometricAuthResult()
}

class BiometricAuthManager(private val context: Context) {

  companion object {
    private const val TAG = "BiometricAuthManager"

    fun hashPin(pin: String, salt: String = ""): String {
      return CryptoUtils.hashPin(pin, salt)
    }

    /**
     * Strictly verifies the 4-digit PIN against the stored salted SHA-256 hash.
     * Prevents empty or permissive bypasses.
     */
    fun verifyPin(enteredPin: String, expectedHash: String = "", salt: String = ""): Boolean {
      if (enteredPin.length != 4 || expectedHash.isBlank()) return false
      return CryptoUtils.verifyPin(enteredPin, expectedHash, salt)
    }

    /**
     * Checks whether fingerprint or face biometrics can be used on this physical device.
     */
    fun canAuthenticateBiometrics(context: Context): Boolean {
      val bm = BiometricManager.from(context)
      val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG
      return bm.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Inspects the phone's hardware and OS settings to determine active security capabilities
     * (e.g., Fingerprint, Face ID, Hardware PIN, Pattern, or Device Screen Lock).
     */
    fun getDeviceSecurityType(context: Context): String {
      val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
      val bm = BiometricManager.from(context)

      val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG
      val canBiometric = bm.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
      val isDeviceSecure = km?.isDeviceSecure == true

      return when {
        canBiometric && isDeviceSecure -> "Fingerprint / Face & Device PIN/Pattern"
        canBiometric -> "Fingerprint / Face Biometrics Enrolled"
        isDeviceSecure -> "Hardware Screen Lock Active"
        else -> "Officer 4-Digit Security PIN"
      }
    }
  }

  /**
   * Shows native Android OS BiometricPrompt dialog for scanning fingerprint or face.
   * Runs natively via androidx.biometric.BiometricPrompt.
   */
  suspend fun authenticateWithBiometrics(
    activity: Activity? = null,
    title: String = "Unlock SusuLedger",
    subtitle: String = "Scan fingerprint or face to authenticate"
  ): BiometricAuthResult {
    val fragmentActivity = (activity as? FragmentActivity)
      ?: (activity?.baseContext as? FragmentActivity)
      ?: context.findFragmentActivity()

    if (fragmentActivity == null) {
      Log.w(TAG, "No FragmentActivity found in context hierarchy, cannot show native BiometricPrompt.")
      return BiometricAuthResult.FallbackToPin("Device context does not support biometric dialog.")
    }

    val bm = BiometricManager.from(fragmentActivity)
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG

    when (val canAuth = bm.canAuthenticate(authenticators)) {
      BiometricManager.BIOMETRIC_SUCCESS -> {
        // Proceed to show prompt
      }
      BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
        Log.i(TAG, "No biometrics enrolled on device.")
        return BiometricAuthResult.FallbackToPin("No fingerprint or face enrolled. Please enter your 4-digit PIN.")
      }
      BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
        Log.i(TAG, "No biometric hardware on device.")
        return BiometricAuthResult.FallbackToPin("Biometric sensor not available on this device.")
      }
      BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
        Log.i(TAG, "Biometric hardware currently unavailable.")
        return BiometricAuthResult.FallbackToPin("Biometric sensor temporarily unavailable.")
      }
      else -> {
        Log.w(TAG, "Biometric status code: $canAuth")
        return BiometricAuthResult.FallbackToPin("Biometrics not available (status code $canAuth).")
      }
    }

    return withContext(Dispatchers.Main) {
      suspendCancellableCoroutine { continuation ->
        val executor = ContextCompat.getMainExecutor(fragmentActivity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
          override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            Log.d(TAG, "Biometric authentication succeeded.")
            if (continuation.isActive) {
              continuation.resume(
                BiometricAuthResult.Success(
                  method = "FINGERPRINT_OR_FACE",
                  message = "Biometric authentication verified."
                )
              )
            }
          }

          override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            super.onAuthenticationError(errorCode, errString)
            Log.d(TAG, "Biometric authentication error $errorCode: $errString")
            if (continuation.isActive) {
              when (errorCode) {
                BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                BiometricPrompt.ERROR_USER_CANCELED -> {
                  continuation.resume(BiometricAuthResult.Cancelled)
                }
                BiometricPrompt.ERROR_NO_BIOMETRICS -> {
                  continuation.resume(BiometricAuthResult.FallbackToPin("No biometrics enrolled."))
                }
                else -> {
                  continuation.resume(BiometricAuthResult.Error(errString.toString()))
                }
              }
            }
          }

          override fun onAuthenticationFailed() {
            super.onAuthenticationFailed()
            Log.d(TAG, "Biometric scan failed (finger not recognized).")
            // BiometricPrompt UI displays "Not recognized. Try again."
          }
        }

        try {
          val prompt = BiometricPrompt(fragmentActivity, executor, callback)
          val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Use 4-Digit PIN")
            .setAllowedAuthenticators(authenticators)
            .build()

          prompt.authenticate(promptInfo)

          continuation.invokeOnCancellation {
            prompt.cancelAuthentication()
          }
        } catch (e: Exception) {
          Log.e(TAG, "Failed to launch BiometricPrompt: ${e.message}", e)
          if (continuation.isActive) {
            continuation.resume(BiometricAuthResult.FallbackToPin("Failed to initialize biometric prompt."))
          }
        }
      }
    }
  }
}
