package com.example.ui.screens

import com.example.ui.theme.NeutralSurfaceLight
import com.example.ui.theme.NeutralSurfaceMedium
import com.example.ui.theme.NeutralTrack


import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auth.BiometricAuthManager
import com.example.auth.BiometricAuthResult
import com.example.ui.components.AppLogoBadge
import com.example.ui.theme.BorderGrey
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.ForestGreenLightFill
import com.example.ui.theme.ForestGreenPrimary
import com.example.ui.theme.LineIconBlack
import com.example.ui.theme.LineIconGrey
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun AppLockScreen(
  groupName: String = "Susu Group",
  onVerifyPin: ((enteredPin: String, onResult: (Boolean) -> Unit) -> Unit)? = null,
  onUnlockSuccess: () -> Unit
) {
  val context = LocalContext.current
  val activity = context as? Activity
  val biometricManager = remember { BiometricAuthManager(context) }
  val scope = rememberCoroutineScope()

  var enteredPin by remember { mutableStateOf("") }
  var isAuthenticatingBiometric by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var isBiometricMode by remember { mutableStateOf(false) }

  fun triggerBiometrics() {
    if (activity == null) return
    isAuthenticatingBiometric = true
    errorMessage = null

    scope.launch {
      try {
        val result = biometricManager.authenticateWithBiometrics(
          activity = activity,
          title = "Unlock SusuLedger",
          subtitle = "Scan fingerprint or face to access ledger books"
        )
        isAuthenticatingBiometric = false

        when (result) {
          is BiometricAuthResult.Success -> {
            onUnlockSuccess()
          }
          is BiometricAuthResult.FallbackToPin -> {
            errorMessage = result.reason
          }
          is BiometricAuthResult.Cancelled -> {
            // Stay on PIN mode
          }
          is BiometricAuthResult.Error -> {
            errorMessage = result.errorMessage
          }
        }
      } catch (e: CancellationException) {
        // Normal cancellation when composition ends
        isAuthenticatingBiometric = false
      }
    }
  }

  // Auto-prompt biometrics when lock screen mounts
  LaunchedEffect(Unit) {
    if (activity != null) {
      isAuthenticatingBiometric = true
      errorMessage = null
      try {
        val result = biometricManager.authenticateWithBiometrics(
          activity = activity,
          title = "Unlock SusuLedger",
          subtitle = "Scan fingerprint or face to access ledger books"
        )
        isAuthenticatingBiometric = false

        when (result) {
          is BiometricAuthResult.Success -> {
            onUnlockSuccess()
          }
          is BiometricAuthResult.FallbackToPin -> {
            errorMessage = result.reason
          }
          is BiometricAuthResult.Cancelled -> {
            // Stay on PIN mode
          }
          is BiometricAuthResult.Error -> {
            errorMessage = result.errorMessage
          }
        }
      } catch (e: CancellationException) {
        // Normal cancellation when composition ends
        isAuthenticatingBiometric = false
      }
    }
  }

  Surface(
    modifier = Modifier
      .fillMaxSize()
      .testTag("app_lock_screen"),
    color = PureWhite
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 24.dp, vertical = 28.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      // Top Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(32.dp)
              .clip(CircleShape)
              .background(ForestGreenLightFill),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.Security,
              contentDescription = null,
              tint = ForestGreenPrimary,
              modifier = Modifier.size(18.dp)
            )
          }
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = "Susu Ledger",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
            )
            Text(
              text = groupName,
              style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
            )
          }
        }
      }

      // Middle: Lock status & PIN / Biometric entry
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        AppLogoBadge(size = 72.dp)

        Spacer(modifier = Modifier.height(16.dp))

        Text(
          text = "Officer Verification Required",
          style = MaterialTheme.typography.headlineSmall.copy(
            fontWeight = FontWeight.Bold,
            color = TextPrimary
          ),
          textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
          text = "Enter your 4-digit PIN or authenticate with fingerprint",
          style = MaterialTheme.typography.bodyMedium.copy(
            color = TextSecondary,
            textAlign = TextAlign.Center
          )
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 4 PIN Indicator Dots
        Row(
          horizontalArrangement = Arrangement.spacedBy(16.dp),
          modifier = Modifier.padding(vertical = 10.dp)
        ) {
          for (i in 0 until 4) {
            val isFilled = i < enteredPin.length
            Box(
              modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (isFilled) ForestGreenPrimary else NeutralTrack)
                .border(1.5.dp, if (isFilled) ForestGreenPrimary else BorderGrey, CircleShape)
            )
          }
        }

        if (errorMessage != null) {
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = errorMessage ?: "",
            fontSize = 12.sp,
            color = ErrorRed,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
          )
        } else {
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "Enter your 4-digit security PIN",
            fontSize = 11.sp,
            color = TextSecondary
          )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Biometric scanning state indicator
        if (isAuthenticatingBiometric) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 8.dp)
          ) {
            CircularProgressIndicator(
              modifier = Modifier.size(16.dp),
              color = ForestGreenPrimary,
              strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Awaiting biometric scan...", fontSize = 12.sp, color = ForestGreenPrimary)
          }
        }

        // Numeric Keypad
        LockNumericKeypad(
          onDigitClick = { digit ->
            if (enteredPin.length < 4) {
              val newPin = enteredPin + digit
              enteredPin = newPin
              errorMessage = null
              if (newPin.length == 4) {
                if (onVerifyPin != null) {
                  onVerifyPin(newPin) { isValid ->
                    if (isValid) {
                      onUnlockSuccess()
                    } else {
                      errorMessage = "Incorrect 4-digit PIN. Please try again."
                      enteredPin = ""
                    }
                  }
                } else {
                  val isValid = BiometricAuthManager.verifyPin(newPin)
                  if (isValid) {
                    onUnlockSuccess()
                  } else {
                    errorMessage = "Incorrect 4-digit PIN. Please try again."
                    enteredPin = ""
                  }
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
      }

      // Bottom Actions: Scan Fingerprint / Biometric
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Button(
          onClick = { triggerBiometrics() },
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("lock_screen_biometric_btn"),
          shape = RoundedCornerShape(8.dp),
          colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary)
        ) {
          Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(22.dp), tint = PureWhite)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Scan Biometrics (Fingerprint / Face)", color = PureWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))
      }
    }
  }
}

@Composable
private fun LockNumericKeypad(
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
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    for (row in rows) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        for (key in row) {
          if (key.isEmpty()) {
            Spacer(modifier = Modifier.weight(1f))
          } else if (key == "DEL") {
            Box(
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(NeutralSurfaceMedium)
                .clickable(onClick = onBackspace)
                .testTag("keypad_del"),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.Backspace,
                contentDescription = "Delete",
                tint = LineIconBlack,
                modifier = Modifier.size(20.dp)
              )
            }
          } else {
            Box(
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(NeutralSurfaceLight)
                .border(1.dp, BorderGrey, RoundedCornerShape(8.dp))
                .clickable { onDigitClick(key) }
                .testTag("keypad_$key"),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = key,
                fontSize = 20.sp,
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
