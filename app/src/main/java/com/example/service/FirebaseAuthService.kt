package com.example.service

import android.app.Activity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

class FirebaseAuthService {

  private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

  fun sendSmsOtp(
    activity: Activity,
    phoneNumber: String,
    onCodeSent: (verificationId: String, token: PhoneAuthProvider.ForceResendingToken) -> Unit,
    onVerificationCompleted: (PhoneAuthCredential) -> Unit,
    onVerificationFailed: (String) -> Unit
  ) {
    val options = PhoneAuthOptions.newBuilder(auth)
      .setPhoneNumber(phoneNumber)
      .setTimeout(60L, TimeUnit.SECONDS)
      .setActivity(activity)
      .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
          onVerificationCompleted(credential)
        }

        override fun onVerificationFailed(e: FirebaseException) {
          val msg = e.localizedMessage ?: "Firebase SMS OTP sending failed. Please check phone format or network."
          onVerificationFailed(msg)
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
  }

  fun signInWithPhoneCredential(
    credential: PhoneAuthCredential,
    onResult: (Boolean, String?) -> Unit
  ) {
    auth.signInWithCredential(credential)
      .addOnCompleteListener { task ->
        if (task.isSuccessful) {
          onResult(true, null)
        } else {
          val errorMsg = task.exception?.localizedMessage ?: "Invalid or expired SMS OTP code."
          onResult(false, errorMsg)
        }
      }
  }

  fun verifySmsCode(
    verificationId: String,
    code: String,
    onResult: (Boolean, String?) -> Unit
  ) {
    if (verificationId.isEmpty() || code.length < 6) {
      onResult(false, "Please enter a valid 6-digit SMS verification code.")
      return
    }

    try {
      val credential = PhoneAuthProvider.getCredential(verificationId, code)
      signInWithPhoneCredential(credential, onResult)
    } catch (e: Exception) {
      onResult(false, "Error verifying SMS code: ${e.localizedMessage}")
    }
  }

  fun getCurrentUserPhone(): String? {
    return auth.currentUser?.phoneNumber
  }
}
