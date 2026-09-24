package com.example.ui.screens

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.service.FirebaseAuthService
import com.example.ui.theme.ForestGreenPrimary
import com.example.ui.theme.InputBorderUnfocused
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextSecondary
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
  var isPinVisible by remember { mutableStateOf(false) }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }

  val scrollState = rememberScrollState()

  fun verified(success: Boolean, message: String?) {
    busy = false
    if (success && auth.getCurrentUserPhone() == GhanaPhoneUtils.toE164(phone)) {
      verifiedPhone = auth.getCurrentUserPhone()
      error = null
    } else error = message ?: "The verified number does not match this recovery request."
  }

  AlertDialog(
    modifier = Modifier.imePadding(),
    onDismissRequest = { if (!busy) onDismiss() },
    title = { Text("Restore Officer Account", fontWeight = FontWeight.Bold) },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        OutlinedTextField(
          value = phone,
          onValueChange = { phone = it },
          label = { Text("Registered phone") },
          enabled = !busy && verificationId.isBlank() && verifiedPhone == null,
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ForestGreenPrimary,
            unfocusedBorderColor = InputBorderUnfocused
          ),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
        )

        if (verificationId.isNotBlank() && verifiedPhone == null) {
          OutlinedTextField(
            value = otp,
            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) otp = it },
            label = { Text("SMS Verification Code") },
            enabled = !busy,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = ForestGreenPrimary,
              unfocusedBorderColor = InputBorderUnfocused
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
          )
          TextButton(
            enabled = !busy,
            onClick = { verificationId = ""; otp = ""; error = null }
          ) {
            Text("Request another code", color = ForestGreenPrimary)
          }
        }

        if (verifiedPhone != null) {
          OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) pin = it },
            label = { Text("New Device PIN") },
            enabled = !busy,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = ForestGreenPrimary,
              unfocusedBorderColor = InputBorderUnfocused
            ),
            visualTransformation = if (isPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            trailingIcon = {
              IconButton(onClick = { isPinVisible = !isPinVisible }) {
                Icon(
                  imageVector = if (isPinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                  contentDescription = if (isPinVisible) "Hide PIN" else "Show PIN",
                  tint = TextSecondary
                )
              }
            }
          )

          OutlinedTextField(
            value = confirmPin,
            onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) confirmPin = it },
            label = { Text("Confirm PIN") },
            enabled = !busy,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = ForestGreenPrimary,
              unfocusedBorderColor = InputBorderUnfocused
            ),
            visualTransformation = if (isPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
          )
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = ForestGreenPrimary)
      }
    },
    confirmButton = {
      Button(
        enabled = !busy,
        colors = ButtonDefaults.buttonColors(
          containerColor = ForestGreenPrimary,
          contentColor = PureWhite
        ),
        shape = RoundedCornerShape(8.dp),
        onClick = {
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
        }
      ) {
        Text(
          text = if (verifiedPhone != null) "Restore Ledger" else if (verificationId.isNotBlank()) "Verify Code" else "Send SMS Code",
          color = PureWhite,
          fontWeight = FontWeight.Bold
        )
      }
    },
    dismissButton = {
      TextButton(enabled = !busy, onClick = onDismiss) {
        Text("Cancel", color = TextSecondary)
      }
    }
  )
}
