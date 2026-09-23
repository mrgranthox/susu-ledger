package com.example.ui.screens

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.service.FirebaseAuthService
import com.example.util.GhanaPhoneUtils

@Composable
fun AccountRecoveryDialog(
  initialPhone: String,
  onDismiss: () -> Unit,
  onRestore: (String, (Boolean, String?) -> Unit) -> Unit
) {
  val activity = LocalContext.current as? Activity
  val auth = remember { FirebaseAuthService() }
  var phone by rememberSaveable { mutableStateOf(initialPhone) }
  var verificationId by rememberSaveable { mutableStateOf("") }
  var otp by remember { mutableStateOf("") }
  var verifiedPhone by remember { mutableStateOf<String?>(null) }
  var pin by remember { mutableStateOf("") }
  var confirmPin by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }

  fun verified(success: Boolean, message: String?) {
    busy = false
    if (success && auth.getCurrentUserPhone() == GhanaPhoneUtils.toE164(phone)) {
      verifiedPhone = auth.getCurrentUserPhone()
      error = null
    } else error = message ?: "The verified number does not match this recovery request."
  }

  AlertDialog(
    onDismissRequest = { if (!busy) onDismiss() },
    title = { Text("Restore account") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(phone, { phone = it }, label = { Text("Registered phone") },
          enabled = !busy && verificationId.isBlank() && verifiedPhone == null,
          singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
        if (verificationId.isNotBlank() && verifiedPhone == null) {
          OutlinedTextField(otp, { if (it.length <= 6 && it.all(Char::isDigit)) otp = it },
            label = { Text("SMS code") }, enabled = !busy, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
          TextButton(enabled = !busy, onClick = { verificationId = ""; otp = ""; error = null }) { Text("Request another code") }
        }
        if (verifiedPhone != null) {
          OutlinedTextField(pin, { if (it.length <= 4 && it.all(Char::isDigit)) pin = it },
            label = { Text("New device PIN") }, enabled = !busy, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
          OutlinedTextField(confirmPin, { if (it.length <= 4 && it.all(Char::isDigit)) confirmPin = it },
            label = { Text("Confirm PIN") }, enabled = !busy, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
      }
    },
    confirmButton = {
      TextButton(enabled = !busy, onClick = {
        error = null
        when {
          verifiedPhone != null -> {
            if (pin.length != 4 || pin != confirmPin) { error = "Enter matching four-digit PINs." }
            else if (auth.getCurrentUserPhone() != verifiedPhone) { error = "Your sign-in changed. Verify this phone again." }
            else {
              busy = true
              onRestore(pin) { success, message ->
                busy = false
                if (success) onDismiss() else error = message ?: "Recovery failed. Try again."
              }
            }
          }
          verificationId.isNotBlank() -> {
            busy = true
            auth.verifySmsCode(verificationId, otp, ::verified)
          }
          else -> {
            val normalized = runCatching { GhanaPhoneUtils.toE164(phone) }.getOrNull()
            if (normalized == null || activity == null) error = "Enter a valid Ghana phone number."
            else {
              phone = normalized
              busy = true
              auth.sendSmsOtp(activity, normalized,
                onCodeSent = { id, _ -> verificationId = id; busy = false },
                onVerificationCompleted = { credential -> auth.signInWithPhoneCredential(credential, ::verified) },
                onVerificationFailed = { busy = false; error = it })
            }
          }
        }
      }) { Text(if (verifiedPhone != null) "Restore ledger" else if (verificationId.isNotBlank()) "Verify code" else "Send SMS") }
    },
    dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } }
  )
}
