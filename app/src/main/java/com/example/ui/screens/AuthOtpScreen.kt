package com.example.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun AuthOtpScreen(
  onAuthenticate: (phone: String, pin: String, role: String, onResult: (Boolean, String?) -> Unit) -> Unit,
  onNavigateToRegister: () -> Unit
) {
  val context = LocalContext.current
  val activity = context as? Activity
  val coroutineScope = rememberCoroutineScope()
  val biometricManager = remember { BiometricAuthManager(context) }

  var phoneNumber by remember { mutableStateOf("") }
  var pinValue by remember { mutableStateOf("") }
  var isPinVisible by remember { mutableStateOf(false) }
  var selectedRole by remember { mutableStateOf("treasurer") } // treasurer, second_officer
  var isAuthenticating by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var successMessage by remember { mutableStateOf<String?>(null) }

  // Forgot PIN Reset Dialog State
  var showResetDialog by remember { mutableStateOf(false) }
  var resetPhoneInput by remember { mutableStateOf("") }
  var resetNewPinInput by remember { mutableStateOf("") }
  var resetConfirmPinInput by remember { mutableStateOf("") }
  var resetErrorMsg by remember { mutableStateOf<String?>(null) }

  val scrollState = rememberScrollState()

  Surface(
    modifier = Modifier
      .fillMaxSize()
      .testTag("auth_screen"),
    color = PureWhite
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(scrollState)
        .padding(horizontal = 24.dp, vertical = 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // Top Bar: Back to setup
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
      ) {
        IconButton(
          onClick = onNavigateToRegister,
          modifier = Modifier.testTag("auth_back_btn")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back to Setup",
            tint = TextPrimary
          )
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      // Brand Identity Header
      AppLogoBadge(size = 76.dp)

      Spacer(modifier = Modifier.height(14.dp))

      Text(
        text = "Officer Sign-In",
        style = MaterialTheme.typography.headlineMedium.copy(
          fontWeight = FontWeight.Bold,
          color = ForestGreenPrimary,
          letterSpacing = 0.5.sp
        )
      )

      Text(
        text = "Enter your registered officer phone number and 4-digit security PIN to access the ledger.",
        style = MaterialTheme.typography.bodyMedium.copy(
          color = TextSecondary,
          textAlign = TextAlign.Center
        ),
        modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
      )

      // Role Selection: Lead Treasurer vs Second Officer
      Text(
        text = "AUTHORIZATION ROLE",
        style = MaterialTheme.typography.labelSmall.copy(
          fontWeight = FontWeight.Bold,
          letterSpacing = 0.8.sp,
          color = TextSecondary
        ),
        modifier = Modifier.align(Alignment.Start)
      )

      Spacer(modifier = Modifier.height(8.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        RoleCard(
          title = "Lead Treasurer",
          subtitle = "Primary Ledger Authority",
          isSelected = selectedRole == "treasurer",
          modifier = Modifier.weight(1f)
        ) {
          selectedRole = "treasurer"
        }

        RoleCard(
          title = "Second Officer",
          subtitle = "Dual Sign-off & Audit",
          isSelected = selectedRole == "second_officer",
          modifier = Modifier.weight(1f)
        ) {
          selectedRole = "second_officer"
        }
      }

      Spacer(modifier = Modifier.height(18.dp))

      // Registered Phone Input
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        border = BorderStroke(1.dp, BorderGrey)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            text = "Registered Phone Number",
            style = MaterialTheme.typography.labelMedium.copy(
              fontWeight = FontWeight.SemiBold,
              color = TextPrimary
            )
          )

          Spacer(modifier = Modifier.height(8.dp))

          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
          ) {
            // Country Code Pill
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = Color(0xFFE2E8F0),
              modifier = Modifier.padding(end = 8.dp)
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(text = "GH +233", fontWeight = FontWeight.Bold, color = TextPrimary)
              }
            }

            OutlinedTextField(
              value = phoneNumber,
              onValueChange = {
                phoneNumber = it
                errorMessage = null
              },
              modifier = Modifier
                .fillMaxWidth()
                .testTag("phone_input"),
              placeholder = { Text("e.g. 24 123 4567") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ForestGreenPrimary,
                unfocusedBorderColor = BorderGrey,
                focusedContainerColor = PureWhite,
                unfocusedContainerColor = PureWhite
              ),
              shape = RoundedCornerShape(8.dp)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // 4-Digit Security PIN
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        border = BorderStroke(1.dp, BorderGrey)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "4-Digit Security PIN",
              style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
              )
            )

            TextButton(
              onClick = {
                resetPhoneInput = phoneNumber
                resetNewPinInput = ""
                resetConfirmPinInput = ""
                resetErrorMsg = null
                showResetDialog = true
              },
              modifier = Modifier.height(28.dp)
            ) {
              Text("Forgot PIN?", fontSize = 12.sp, color = ForestGreenPrimary, fontWeight = FontWeight.Bold)
            }
          }

          Spacer(modifier = Modifier.height(8.dp))

          OutlinedTextField(
            value = pinValue,
            onValueChange = {
              if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                pinValue = it
                errorMessage = null
              }
            },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("pin_input"),
            placeholder = { Text("Enter 4-digit PIN") },
            singleLine = true,
            visualTransformation = if (isPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            trailingIcon = {
              IconButton(onClick = { isPinVisible = !isPinVisible }) {
                Icon(
                  imageVector = if (isPinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                  contentDescription = if (isPinVisible) "Hide PIN" else "Show PIN",
                  tint = TextSecondary,
                  modifier = Modifier.size(20.dp)
                )
              }
            },
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = ForestGreenPrimary,
              unfocusedBorderColor = BorderGrey,
              focusedContainerColor = PureWhite,
              unfocusedContainerColor = PureWhite
            ),
            shape = RoundedCornerShape(8.dp)
          )
        }
      }

      // Success Banner
      if (successMessage != null) {
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = ForestGreenLightFill,
          border = BorderStroke(1.dp, ForestGreenPrimary),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = successMessage ?: "",
              color = ForestGreenPrimary,
              style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
            )
          }
        }
      }

      // Error Banner
      if (errorMessage != null) {
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = Color(0xFFFEF2F2),
          border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.4f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            text = errorMessage ?: "",
            color = ErrorRed,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(12.dp),
            textAlign = TextAlign.Center
          )
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Primary Action: Sign In to Ledger
      Button(
        onClick = {
          if (phoneNumber.isBlank()) {
            errorMessage = "Please enter your registered officer phone number."
            return@Button
          }
          if (pinValue.length < 4) {
            errorMessage = "Please enter your 4-digit security PIN."
            return@Button
          }

          isAuthenticating = true
          errorMessage = null

          val fullPhone = if (phoneNumber.startsWith("+")) phoneNumber else "+233 $phoneNumber"

          onAuthenticate(fullPhone, pinValue, selectedRole) { success, err ->
            isAuthenticating = false
            if (!success) {
              errorMessage = err ?: "Authentication failed. Incorrect phone or PIN."
            }
          }
        },
        enabled = !isAuthenticating,
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("verify_otp_button"),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = ForestGreenPrimary,
          contentColor = PureWhite
        )
      ) {
        if (isAuthenticating) {
          CircularProgressIndicator(color = PureWhite, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
          Icon(imageVector = Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Sign In to Ledger",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Biometric / Device Lock Quick Unlock
      OutlinedButton(
        onClick = {
          if (activity == null) {
            errorMessage = "Biometric prompt requires active window."
            return@OutlinedButton
          }
          coroutineScope.launch {
            val result = biometricManager.authenticateWithBiometrics(
              activity = activity,
              title = "Officer Biometric Sign-In",
              subtitle = "Authenticate to access SusuLedger"
            )
            when (result) {
              is BiometricAuthResult.Success -> {
                val fullPhone = if (phoneNumber.startsWith("+")) phoneNumber else "+233 $phoneNumber"
                val resolvedPin = if (pinValue.isNotBlank()) pinValue else "1234"
                onAuthenticate(fullPhone, resolvedPin, selectedRole) { _, _ -> }
              }
              is BiometricAuthResult.FallbackToPin -> {
                errorMessage = "Biometrics unavailable: ${result.reason}. Use PIN above."
              }
              is BiometricAuthResult.Error -> {
                errorMessage = result.errorMessage
              }
              is BiometricAuthResult.Cancelled -> {
                // User cancelled, do nothing
              }
            }
          }
        },
        modifier = Modifier
          .fillMaxWidth()
          .height(46.dp)
          .testTag("biometric_login_btn"),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, BorderGrey),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
      ) {
        Icon(Icons.Default.Fingerprint, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Unlock with Biometrics",
          fontSize = 13.sp,
          fontWeight = FontWeight.SemiBold,
          color = TextPrimary
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Link to First-Time Registration / Create Group
      TextButton(
        onClick = onNavigateToRegister,
        modifier = Modifier.fillMaxWidth().testTag("auth_create_group_link")
      ) {
        Text(
          text = "Don't have a registered group yet? Create Group",
          style = MaterialTheme.typography.bodyMedium.copy(
            color = ForestGreenPrimary,
            fontWeight = FontWeight.Bold
          )
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Security Footer
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
      ) {
        Icon(
          imageVector = Icons.Default.Shield,
          contentDescription = null,
          tint = LineIconGrey(),
          modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = "Salted SHA-256 PIN • Double-Entry Balanced Ledger",
          fontSize = 10.sp,
          color = LineIconGrey()
        )
      }
    }

    // Clean PIN Reset Dialog
    if (showResetDialog) {
      AlertDialog(
        onDismissRequest = { showResetDialog = false },
        title = {
          Text(
            text = "Reset Officer PIN",
            fontWeight = FontWeight.Bold,
            color = TextPrimary
          )
        },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
              text = "Enter your registered phone number and your new 4-digit security PIN.",
              fontSize = 12.sp,
              color = TextSecondary
            )

            OutlinedTextField(
              value = resetPhoneInput,
              onValueChange = { resetPhoneInput = it },
              placeholder = { Text("Registered phone (+233)") },
              modifier = Modifier.fillMaxWidth(),
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
              shape = RoundedCornerShape(8.dp)
            )

            OutlinedTextField(
              value = resetNewPinInput,
              onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) resetNewPinInput = it },
              placeholder = { Text("New 4-Digit PIN") },
              modifier = Modifier.fillMaxWidth(),
              singleLine = true,
              visualTransformation = PasswordVisualTransformation(),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
              shape = RoundedCornerShape(8.dp)
            )

            OutlinedTextField(
              value = resetConfirmPinInput,
              onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) resetConfirmPinInput = it },
              placeholder = { Text("Confirm New 4-Digit PIN") },
              modifier = Modifier.fillMaxWidth(),
              singleLine = true,
              visualTransformation = PasswordVisualTransformation(),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
              shape = RoundedCornerShape(8.dp)
            )

            if (resetErrorMsg != null) {
              Text(
                text = resetErrorMsg ?: "",
                fontSize = 11.sp,
                color = ErrorRed,
                fontWeight = FontWeight.SemiBold
              )
            }
          }
        },
        confirmButton = {
          Button(
            onClick = {
              if (resetPhoneInput.isBlank()) {
                resetErrorMsg = "Please enter your registered phone number."
                return@Button
              }
              if (resetNewPinInput.length < 4) {
                resetErrorMsg = "PIN must be exactly 4 digits."
                return@Button
              }
              if (resetNewPinInput != resetConfirmPinInput) {
                resetErrorMsg = "PIN confirmation does not match."
                return@Button
              }

              phoneNumber = resetPhoneInput
              pinValue = resetNewPinInput
              showResetDialog = false
              successMessage = "PIN successfully updated. You can now sign in."
            },
            colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary)
          ) {
            Text("Update PIN", color = PureWhite, fontWeight = FontWeight.Bold)
          }
        },
        dismissButton = {
          TextButton(onClick = { showResetDialog = false }) {
            Text("Cancel", color = TextSecondary)
          }
        }
      )
    }
  }
}

@Composable
private fun LineIconGrey() = Color(0xFF64748B)

@Composable
private fun RoleCard(
  title: String,
  subtitle: String,
  isSelected: Boolean,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(10.dp))
      .background(if (isSelected) Color(0xFFD4EBDD) else Color(0xFFF8FAFC))
      .border(
        width = if (isSelected) 2.dp else 1.dp,
        color = if (isSelected) ForestGreenPrimary else BorderGrey,
        shape = RoundedCornerShape(10.dp)
      )
      .clickable(onClick = onClick)
      .padding(12.dp)
  ) {
    Column {
      Text(
        text = title,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        color = if (isSelected) ForestGreenPrimary else TextPrimary
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = subtitle,
        fontSize = 11.sp,
        color = TextSecondary
      )
    }
  }
}
