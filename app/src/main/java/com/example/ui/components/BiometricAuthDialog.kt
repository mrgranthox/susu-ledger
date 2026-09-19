package com.example.ui.components

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auth.BiometricAuthManager
import com.example.auth.BiometricAuthResult
import com.example.ui.theme.BorderGrey
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.ForestGreenPrimary
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun BiometricAuthDialog(
  title: String,
  subtitle: String,
  operationTag: String = "SENSITIVE_OPERATION",
  onDismiss: () -> Unit,
  onAuthorized: (methodUsed: String) -> Unit
) {
  val context = LocalContext.current
  val activity = context as? Activity
  val biometricManager = remember { BiometricAuthManager(context) }
  val scope = rememberCoroutineScope()

  var usePinMode by remember { mutableStateOf(false) }
  var enteredPin by remember { mutableStateOf("") }
  var isAuthenticatingBiometric by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var successMessage by remember { mutableStateOf<String?>(null) }

  fun triggerBiometricAuth() {
    if (activity == null) {
      usePinMode = true
      return
    }
    isAuthenticatingBiometric = true
    errorMessage = null

    scope.launch {
      try {
        val result = biometricManager.authenticateWithBiometrics(activity, title, subtitle)
        isAuthenticatingBiometric = false

        when (result) {
          is BiometricAuthResult.Success -> {
            successMessage = "Biometric Verified"
            onAuthorized("BIOMETRICS")
          }
          is BiometricAuthResult.FallbackToPin -> {
            usePinMode = true
            errorMessage = result.reason
          }
          is BiometricAuthResult.Cancelled -> {
            // Stay on dialog or user can choose PIN
          }
          is BiometricAuthResult.Error -> {
            errorMessage = result.errorMessage
            usePinMode = true
          }
        }
      } catch (e: CancellationException) {
        isAuthenticatingBiometric = false
      }
    }
  }

  // Attempt biometric when dialog first opens
  LaunchedEffect(Unit) {
    if (activity != null) {
      isAuthenticatingBiometric = true
      errorMessage = null
      try {
        val result = biometricManager.authenticateWithBiometrics(activity, title, subtitle)
        isAuthenticatingBiometric = false

        when (result) {
          is BiometricAuthResult.Success -> {
            successMessage = "Biometric Verified"
            onAuthorized("BIOMETRICS")
          }
          is BiometricAuthResult.FallbackToPin -> {
            usePinMode = true
            errorMessage = result.reason
          }
          is BiometricAuthResult.Cancelled -> {
            // Stay on dialog or user can choose PIN
          }
          is BiometricAuthResult.Error -> {
            errorMessage = result.errorMessage
            usePinMode = true
          }
        }
      } catch (e: CancellationException) {
        isAuthenticatingBiometric = false
      }
    } else {
      usePinMode = true
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.testTag("biometric_auth_dialog"),
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(0xFFD4EBDD)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = if (usePinMode) Icons.Default.Lock else Icons.Default.Fingerprint,
            contentDescription = null,
            tint = ForestGreenPrimary,
            modifier = Modifier.size(20.dp)
          )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
          Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
          )
          Text(
            text = "androidx.credentials Authentication",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = ForestGreenPrimary
          )
        }
      }
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, textAlign = TextAlign.Center),
          modifier = Modifier.padding(bottom = 12.dp)
        )

        if (!usePinMode) {
          // BIOMETRIC MODE (Fingerprint & Face)
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, BorderGrey)
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = Icons.Default.Fingerprint,
                  contentDescription = "Fingerprint",
                  tint = ForestGreenPrimary,
                  modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Icon(
                  imageVector = Icons.Default.Face,
                  contentDescription = "Face Unlock",
                  tint = ForestGreenPrimary,
                  modifier = Modifier.size(48.dp)
                )
              }

              Spacer(modifier = Modifier.height(12.dp))

              Text(
                text = "Biometric Verification",
                style = MaterialTheme.typography.titleSmall.copy(
                  fontWeight = FontWeight.Bold,
                  color = TextPrimary
                )
              )
              Text(
                text = "Touch fingerprint sensor or look at camera to authorize",
                style = MaterialTheme.typography.bodySmall.copy(
                  color = TextSecondary,
                  textAlign = TextAlign.Center
                )
              )

              Spacer(modifier = Modifier.height(14.dp))

              if (isAuthenticatingBiometric) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.Center
                ) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = ForestGreenPrimary,
                    strokeWidth = 2.dp
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Awaiting biometric scan...", fontSize = 12.sp, color = ForestGreenPrimary)
                }
              } else {
                Button(
                  onClick = { triggerBiometricAuth() },
                  colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary),
                  shape = RoundedCornerShape(8.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("scan_biometric_button")
                ) {
                  Icon(imageVector = Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Scan Fingerprint / Face", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Simulator quick-verify button for emulator/testing
                OutlinedButton(
                  onClick = {
                    onAuthorized("BIOMETRICS_VERIFIED")
                  },
                  shape = RoundedCornerShape(8.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("simulate_biometric_button")
                ) {
                  Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Simulate Biometric Success", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          // Switch to PIN mode button
          TextButton(
            onClick = { usePinMode = true },
            modifier = Modifier.testTag("switch_to_pin_button")
          ) {
            Icon(imageVector = Icons.Default.Pin, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Use 4-Digit Officer PIN Instead", color = ForestGreenPrimary, fontWeight = FontWeight.Bold)
          }

        } else {
          // 4-DIGIT PIN FALLBACK MODE
          Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Text(
              text = "Enter 4-Digit Officer PIN",
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
            )
            Text(
              text = "Secured Officer Verification",
              fontSize = 11.sp,
              color = TextSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 4 PIN Dots / Digits
            Row(
              horizontalArrangement = Arrangement.spacedBy(14.dp),
              modifier = Modifier.padding(vertical = 8.dp)
            ) {
              for (i in 0 until 4) {
                val isFilled = i < enteredPin.length
                Box(
                  modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (isFilled) ForestGreenPrimary else Color(0xFFE2E8F0))
                    .border(1.5.dp, if (isFilled) ForestGreenPrimary else BorderGrey, CircleShape)
                )
              }
            }

            if (errorMessage != null) {
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = errorMessage ?: "",
                fontSize = 11.sp,
                color = ErrorRed,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
              )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Numeric Keypad
            NumericKeypad(
              onDigitClick = { digit ->
                if (enteredPin.length < 4) {
                  val newPin = enteredPin + digit
                  enteredPin = newPin
                  if (newPin.length == 4) {
                    // Verify PIN
                    val isValid = BiometricAuthManager.verifyPin(newPin)
                    if (isValid) {
                      onAuthorized("OFFICER_PIN")
                    } else {
                      errorMessage = "Incorrect PIN. Please enter your 4-digit security PIN."
                      enteredPin = ""
                    }
                  }
                }
              },
              onBackspace = {
                if (enteredPin.isNotEmpty()) {
                  enteredPin = enteredPin.dropLast(1)
                  errorMessage = null
                }
              }
            )

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(onClick = {
              usePinMode = false
              errorMessage = null
              triggerBiometricAuth()
            }) {
              Icon(imageVector = Icons.Default.Fingerprint, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Switch Back to Biometrics", color = ForestGreenPrimary, fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = {
      OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(8.dp)) {
        Text("Cancel")
      }
    }
  )
}

@Composable
private fun NumericKeypad(
  onDigitClick: (String) -> Unit,
  onBackspace: () -> Unit
) {
  val rows = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
    listOf("", "0", "DEL")
  )

  Column(
    modifier = Modifier.fillMaxWidth(0.85f),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    for (row in rows) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        for (key in row) {
          if (key.isEmpty()) {
            Spacer(modifier = Modifier.weight(1f))
          } else if (key == "DEL") {
            Box(
              modifier = Modifier
                .weight(1f)
                .height(42.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFF1F5F9))
                .clickable(onClick = onBackspace),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.Backspace,
                contentDescription = "Delete",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
              )
            }
          } else {
            Box(
              modifier = Modifier
                .weight(1f)
                .height(42.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFF8FAFC))
                .border(1.dp, BorderGrey, RoundedCornerShape(8.dp))
                .clickable { onDigitClick(key) },
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = key,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
              )
            }
          }
        }
      }
    }
  }
}
