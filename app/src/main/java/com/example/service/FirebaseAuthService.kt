package com.example.service

import android.app.Activity
import android.util.Log
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

class FirebaseAuthService {

  companion object {
    private const val TAG = "FirebaseAuthService"
  }

  private val auth: FirebaseAuth? by lazy {
    try {
      FirebaseAuth.getInstance()
    } catch (e: Exception) {
      Log.w(TAG, "FirebaseAuth initialization failed: ${e.message}")
      null
    }
  }

  fun sendSmsOtp(
    activity: Activity,
    phoneNumber: String,
    onCodeSent: (verificationId: String, token: PhoneAuthProvider.ForceResendingToken?) -> Unit,
    onVerificationCompleted: (PhoneAuthCredential) -> Unit,
    onVerificationFailed: (errorMessage: String) -> Unit
  ) {
    val firebaseAuth = auth
    if (firebaseAuth == null) {
      onVerificationFailed("SMS authentication is unavailable. Check Firebase configuration and try again.")
      return
    }

    try {
      val options = PhoneAuthOptions.newBuilder(firebaseAuth)
        .setPhoneNumber(phoneNumber)
        .setTimeout(60L, TimeUnit.SECONDS)
        .setActivity(activity)
        .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
          override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            onVerificationCompleted(credential)
          }

          override fun onVerificationFailed(e: FirebaseException) {
            val rawMsg = e.localizedMessage ?: e.message ?: "SMS OTP delivery failure."
            Log.e(TAG, "Firebase SMS failed: $rawMsg", e)

            onVerificationFailed(rawMsg)
          }

          override fun onCodeSent(
            verificationId: String,
            token: PhoneAuthProvider.ForceResendingToken
          ) {
            onCodeSent(verificationId, token)
          }
        })
        .build()

      PhoneAuthProvider.verifyPhoneNumber(options)
    } catch (e: Exception) {
      Log.e(TAG, "Exception initializing PhoneAuthProvider: ${e.message}", e)
      onVerificationFailed("Unable to request SMS verification. Please try again.")
    }
  }

  fun signInWithPhoneCredential(
    credential: PhoneAuthCredential,
    onResult: (Boolean, String?) -> Unit
  ) {
    val firebaseAuth = auth
    if (firebaseAuth == null) {
      onResult(false, "SMS authentication is unavailable.")
      return
    }

    try {
      firebaseAuth.signInWithCredential(credential)
        .addOnCompleteListener { task ->
          if (task.isSuccessful) {
            onResult(true, null)
          } else {
            val errorMsg = task.exception?.localizedMessage ?: "Invalid or expired SMS OTP code."
            onResult(false, errorMsg)
          }
        }
    } catch (e: Exception) {
      Log.w(TAG, "signInWithPhoneCredential exception: ${e.message}")
      onResult(false, "Unable to verify your phone. Please try again.")
    }
  }

  fun verifySmsCode(
    verificationId: String,
    code: String,
    onResult: (Boolean, String?) -> Unit
  ) {
    if (code.length != 6 || !code.all(Char::isDigit) || verificationId.isBlank()) {
      onResult(false, "Please enter a valid 6-digit verification code.")
      return
    }

    try {
      val credential = PhoneAuthProvider.getCredential(verificationId, code)
      signInWithPhoneCredential(credential, onResult)
    } catch (e: Exception) {
      onResult(false, "Unable to verify this code. Request another SMS and try again.")
    }
  }

  fun getCurrentUserPhone(): String? {
    return auth?.currentUser?.phoneNumber
  }
}
