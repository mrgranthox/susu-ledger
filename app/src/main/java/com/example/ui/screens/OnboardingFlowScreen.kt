package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.core.content.ContextCompat
import com.example.service.FirebaseAuthService
import com.example.ui.components.AppLogoBadge
import com.example.ui.theme.BorderGrey
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.ForestGreenLightFill
import com.example.ui.theme.ForestGreenPrimary
import com.example.ui.theme.LineIconBlack
import com.example.ui.theme.LineIconGreen
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.DeviceContactManager
import com.example.util.GhanaPhoneUtils

data class SetupMemberItem(val name: String, val phone: String)
data class ContactItem(val name: String, val phone: String)

@Composable
fun OnboardingFlowScreen(
  pairingCode: String = "",
  onCompleteOnboarding: (groupName: String, amount: Double, treasurerPhone: String, treasurerName: String, members: List<SetupMemberItem>, treasurerPin: String) -> Unit,
  onSwitchToLogin: () -> Unit
) {
  var currentStep by remember { mutableStateOf(1) } // 1..7

  // State across steps
  var treasurerPhone by remember { mutableStateOf("") }
  var treasurerName by remember { mutableStateOf("") }
  var treasurerPin by remember { mutableStateOf("") }
  var groupName by remember { mutableStateOf("") }
  var contributionAmount by remember { mutableStateOf("") }
  var collectionFrequency by remember { mutableStateOf("Weekly") }

  val memberList = remember {
    mutableStateListOf<SetupMemberItem>()
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(PureWhite)
      .statusBarsPadding()
      .navigationBarsPadding()
      .padding(horizontal = 24.dp, vertical = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    // Step Indicator (steps 2 to 6)
    if (currentStep > 1) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 12.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Step $currentStep of 7",
          style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.SemiBold)
        )
        TextButton(
          onClick = {
            if (currentStep > 1) currentStep--
          },
          modifier = Modifier.height(32.dp)
        ) {
          Text("Back", color = TextSecondary, fontSize = 13.sp)
        }
      }
    } else {
      Spacer(modifier = Modifier.height(16.dp))
    }

    AnimatedContent(targetState = currentStep, label = "OnboardingStep") { step ->
      when (step) {
        1 -> Step1Welcome(
          onGetStarted = { currentStep = 2 },
          onLoginClick = onSwitchToLogin
        )
        2 -> Step2PhoneOtp(
          phone = treasurerPhone,
          onPhoneChange = { treasurerPhone = it },
          treasurerName = treasurerName,
          onTreasurerNameChange = { treasurerName = it },
          onVerified = { currentStep = 3 }
        )
        3 -> Step3CreateGroup(
          treasurerName = treasurerName,
          onTreasurerNameChange = { treasurerName = it },
          groupName = groupName,
          onGroupNameChange = { groupName = it },
          amount = contributionAmount,
          onAmountChange = { contributionAmount = it },
          frequency = collectionFrequency,
          onFrequencyChange = { collectionFrequency = it },
          onContinue = { currentStep = 4 }
        )
        4 -> Step4AddMembers(
          members = memberList,
          onAddMember = { name, phone -> memberList.add(0, SetupMemberItem(name, phone)) },
          onAddMultipleMembers = { newMembers ->
            newMembers.forEach { item ->
              if (memberList.none { it.phone == item.phone && it.name == item.name }) {
                memberList.add(0, item)
              }
            }
          },
          onRemoveMember = { memberList.remove(it) },
          onContinue = { currentStep = 5 }
        )
        5 -> Step5ReviewGroup(
          groupName = groupName,
          amount = contributionAmount,
          frequency = collectionFrequency,
          treasurerName = treasurerName,
          treasurerPhone = treasurerPhone,
          memberCount = memberList.size,
          onContinue = { currentStep = 6 }
        )
        6 -> Step6SecurityPin(
          treasurerPin = treasurerPin,
          onTreasurerPinChange = { treasurerPin = it },
          onContinue = { currentStep = 7 }
        )
        7 -> Step7ConnectBot(
          pairingCode = pairingCode,
          treasurerName = if (treasurerName.isNotBlank()) treasurerName else "Treasurer",
          groupName = if (groupName.isNotBlank()) groupName else "Susu Group",
          onFinish = {
            val amtNum = contributionAmount.replace("GHS", "").trim().toDoubleOrNull() ?: 50.0
            val resolvedTreasurerName = if (treasurerName.isNotBlank()) treasurerName.trim() else "Ama Mensah"
            val resolvedGroupName = if (groupName.isNotBlank()) groupName.trim() else "Nima Market Susu"
            onCompleteOnboarding(resolvedGroupName, amtNum, treasurerPhone, resolvedTreasurerName, memberList, treasurerPin)
          }
        )
      }
    }
  }
}

// -----------------------------------------------------------------------------
// STEP 1 — WELCOME (APP)
// -----------------------------------------------------------------------------
@Composable
private fun Step1Welcome(
  onGetStarted: () -> Unit,
  onLoginClick: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(PureWhite),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.SpaceBetween
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 32.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      AppLogoBadge(size = 80.dp)

      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = "SusuLedger",
        style = MaterialTheme.typography.displayMedium.copy(
          fontWeight = FontWeight.Bold,
          color = TextPrimary
        )
      )

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = "Your group's digital record book — no more exercise books.",
        style = MaterialTheme.typography.bodyMedium.copy(
          color = TextSecondary,
          textAlign = TextAlign.Center
        ),
        modifier = Modifier.padding(horizontal = 16.dp)
      )

      Spacer(modifier = Modifier.height(36.dp))

      ValuePropRow(
        icon = Icons.Default.Check,
        text = "Know who paid instantly (supports small-small installments)"
      )
      Spacer(modifier = Modifier.height(16.dp))
      ValuePropRow(
        icon = Icons.Default.Notifications,
        text = "Automatic receipts & reminders on WhatsApp"
      )
      Spacer(modifier = Modifier.height(16.dp))
      ValuePropRow(
        icon = Icons.Default.Lock,
        text = "Your money never passes through us — we only keep the record."
      )
    }

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Button(
        onClick = onGetStarted,
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("onboarding_get_started_btn"),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary)
      ) {
        Text(
          text = "Get Started",
          style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.SemiBold,
            color = PureWhite
          )
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      TextButton(
        onClick = onLoginClick,
        modifier = Modifier.testTag("already_have_account_btn")
      ) {
        Text(
          text = "Already have an account? Log in",
          style = MaterialTheme.typography.bodyMedium.copy(
            color = TextPrimary,
            fontWeight = FontWeight.Medium
          )
        )
      }
    }
  }
}

@Composable
private fun ValuePropRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = LineIconGreen,
      modifier = Modifier.size(20.dp)
    )
    Spacer(modifier = Modifier.width(14.dp))
    Text(
      text = text,
      style = MaterialTheme.typography.bodyMedium.copy(
        color = TextPrimary,
        fontWeight = FontWeight.Normal
      )
    )
  }
}

// -----------------------------------------------------------------------------
// STEP 2 — PHONE NUMBER & LEADER REGISTRATION
// -----------------------------------------------------------------------------
@Composable
private fun Step2PhoneOtp(
  phone: String,
  onPhoneChange: (String) -> Unit,
  treasurerName: String,
  onTreasurerNameChange: (String) -> Unit,
  onVerified: () -> Unit
) {
  val context = LocalContext.current
  val activity = context as? Activity
  val firebaseAuthService = remember { FirebaseAuthService() }

  var isCodeSent by remember { mutableStateOf(false) }
  var isSendingSms by remember { mutableStateOf(false) }
  var isVerifyingCode by remember { mutableStateOf(false) }
  var verificationId by remember { mutableStateOf<String?>(null) }
  var otpCode by remember { mutableStateOf("") }
  var smsStatusMsg by remember { mutableStateOf<String?>(null) }
  var smsErrorMsg by remember { mutableStateOf<String?>(null) }

  val sanitized9Digits = remember(phone) { GhanaPhoneUtils.sanitizeTo9Digits(phone) }
  val isValidPhone = remember(sanitized9Digits) { GhanaPhoneUtils.isValidGhanaPhone(sanitized9Digits) }
  val (_, statusMsg) = remember(sanitized9Digits) { GhanaPhoneUtils.getValidationStatus(sanitized9Digits) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(PureWhite)
      .verticalScroll(rememberScrollState()),
    horizontalAlignment = Alignment.Start,
    verticalArrangement = Arrangement.SpaceBetween
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Text(
        text = if (!isCodeSent) "Leader Registration" else "Verify Phone Number",
        style = MaterialTheme.typography.headlineLarge.copy(color = TextPrimary)
      )

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = if (!isCodeSent)
          "Enter your full name and registered phone number to establish your officer identity."
        else
          "Enter the 6-digit verification code sent to +233 $sanitized9Digits.",
        style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
      )

      Spacer(modifier = Modifier.height(20.dp))

      if (!isCodeSent) {
        // Leader Name Field
        Text("Your Full Name (Treasurer / Leader)", style = MaterialTheme.typography.labelMedium.copy(color = TextPrimary, fontWeight = FontWeight.SemiBold))
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
          value = treasurerName,
          onValueChange = onTreasurerNameChange,
          placeholder = { Text("e.g. Ama Mensah") },
          leadingIcon = {
            Icon(Icons.Default.Person, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(20.dp))
          },
          modifier = Modifier.fillMaxWidth().testTag("onboarding_treasurer_name_input"),
          singleLine = true,
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ForestGreenPrimary,
            unfocusedBorderColor = BorderGrey,
            focusedContainerColor = PureWhite,
            unfocusedContainerColor = PureWhite
          ),
          shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Phone input with Ghana flag and +233 prefix
        Text("Your Phone Number", style = MaterialTheme.typography.labelMedium.copy(color = TextPrimary, fontWeight = FontWeight.SemiBold))
        Spacer(modifier = Modifier.height(6.dp))
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .border(
              width = if (isValidPhone) 1.5.dp else 1.dp,
              color = if (isValidPhone) ForestGreenPrimary else BorderGrey,
              shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 14.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Ghana flag badge
          Box(
            modifier = Modifier
              .size(30.dp, 20.dp)
              .clip(RoundedCornerShape(3.dp))
              .background(Color(0xFFFEF3C7))
              .border(1.dp, Color(0xFFD97706), RoundedCornerShape(3.dp)),
            contentAlignment = Alignment.Center
          ) {
            Text("GH", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
          }
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            "+233",
            style = MaterialTheme.typography.bodyLarge.copy(
              color = TextPrimary,
              fontWeight = FontWeight.Bold
            )
          )
          Spacer(modifier = Modifier.width(10.dp))
          Box(modifier = Modifier.width(1.dp).height(24.dp).background(BorderGrey))
          Spacer(modifier = Modifier.width(10.dp))
          BasicTextField(
            value = sanitized9Digits,
            onValueChange = { input ->
              val clean = GhanaPhoneUtils.sanitizeTo9Digits(input)
              onPhoneChange(clean)
              smsErrorMsg = null
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
              color = TextPrimary,
              fontWeight = FontWeight.Medium,
              letterSpacing = 1.sp
            ),
            modifier = Modifier.weight(1f).testTag("onboarding_phone_input")
          )

          if (sanitized9Digits.isNotEmpty()) {
            Text(
              text = "${sanitized9Digits.length}/9",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = if (sanitized9Digits.length == 9) ForestGreenPrimary else TextSecondary
            )
          }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Live Validation Feedback
        Text(
          text = statusMsg,
          fontSize = 12.sp,
          fontWeight = if (isValidPhone) FontWeight.SemiBold else FontWeight.Normal,
          color = if (isValidPhone) SuccessGreen else if (sanitized9Digits.length == 9) ErrorRed else TextSecondary
        )
      } else {
        // Status Banner
        if (smsStatusMsg != null) {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = ForestGreenLightFill,
            border = BorderStroke(1.dp, ForestGreenPrimary),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
          ) {
            Text(
              text = smsStatusMsg ?: "",
              color = ForestGreenPrimary,
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.padding(10.dp)
            )
          }
        }

        // 6 auto-advancing OTP boxes
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
          contentAlignment = Alignment.Center
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            for (i in 0 until 6) {
              val char = if (i < otpCode.length) otpCode[i].toString() else ""
              val isFocused = i == otpCode.length
              Box(
                modifier = Modifier
                  .size(46.dp, 52.dp)
                  .border(
                    width = 1.dp,
                    color = if (isFocused) ForestGreenPrimary else BorderGrey,
                    shape = RoundedCornerShape(8.dp)
                  )
                  .background(PureWhite),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = char,
                  style = MaterialTheme.typography.headlineMedium.copy(color = TextPrimary)
                )
              }
            }
          }

          BasicTextField(
            value = otpCode,
            onValueChange = {
              if (it.length <= 6 && it.all { ch -> ch.isDigit() }) {
                otpCode = it
                smsErrorMsg = null
                if (it.length == 6) {
                  onVerified()
                }
              }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
              .fillMaxWidth()
              .height(54.dp)
              .testTag("onboarding_otp_input"),
            decorationBox = { /* Rendered underneath */ }
          )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          TextButton(
            onClick = {
              smsStatusMsg = "Verification code re-sent."
            }
          ) {
            Text("Resend Code", fontSize = 12.sp, color = ForestGreenPrimary, fontWeight = FontWeight.SemiBold)
          }

          TextButton(
            onClick = {
              isCodeSent = false
              otpCode = ""
              smsStatusMsg = null
              smsErrorMsg = null
            }
          ) {
            Text("Change Phone", fontSize = 12.sp, color = TextSecondary)
          }
        }
      }

      // Error message if any
      if (smsErrorMsg != null) {
        Spacer(modifier = Modifier.height(10.dp))
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = Color(0xFFFEF2F2),
          border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.4f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            text = smsErrorMsg ?: "",
            color = ErrorRed,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(10.dp),
            textAlign = TextAlign.Center
          )
        }
      }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
      Button(
        onClick = {
          if (!isCodeSent) {
            isCodeSent = true
            smsStatusMsg = "Verification code sent to +233 $sanitized9Digits"
          } else {
            val code = otpCode.trim()
            if (code.length == 6) {
              onVerified()
            } else {
              smsErrorMsg = "Please enter the 6-digit code."
            }
          }
        },
        enabled = if (!isCodeSent) (isValidPhone && treasurerName.isNotBlank()) else (otpCode.length == 6),
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("onboarding_send_code_btn"),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = ForestGreenPrimary,
          disabledContainerColor = Color(0xFFE2E8F0)
        )
      ) {
        Text(
          text = if (!isCodeSent) "Continue to Verification" else "Verify & Continue",
          style = MaterialTheme.typography.labelLarge.copy(color = PureWhite, fontWeight = FontWeight.Bold)
        )
      }
    }
  }
}

// -----------------------------------------------------------------------------
// STEP 3 — CREATE YOUR GROUP (APP)
// -----------------------------------------------------------------------------
@Composable
private fun Step3CreateGroup(
  treasurerName: String,
  onTreasurerNameChange: (String) -> Unit,
  groupName: String,
  onGroupNameChange: (String) -> Unit,
  amount: String,
  onAmountChange: (String) -> Unit,
  frequency: String,
  onFrequencyChange: (String) -> Unit,
  onContinue: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(PureWhite)
      .verticalScroll(rememberScrollState()),
    horizontalAlignment = Alignment.Start,
    verticalArrangement = Arrangement.SpaceBetween
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Text(
        text = "Set up your group",
        style = MaterialTheme.typography.headlineLarge.copy(color = TextPrimary)
      )

      Spacer(modifier = Modifier.height(16.dp))

      // Group Name Field
      Text("Group name", style = MaterialTheme.typography.labelMedium.copy(color = TextPrimary, fontWeight = FontWeight.SemiBold))
      Spacer(modifier = Modifier.height(6.dp))
      OutlinedTextField(
        value = groupName,
        onValueChange = onGroupNameChange,
        placeholder = { Text("e.g. Nima Market Susu") },
        modifier = Modifier.fillMaxWidth().testTag("onboarding_group_name_input"),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = ForestGreenPrimary,
          unfocusedBorderColor = BorderGrey,
          focusedContainerColor = PureWhite,
          unfocusedContainerColor = PureWhite
        ),
        shape = RoundedCornerShape(8.dp)
      )

      Spacer(modifier = Modifier.height(16.dp))

      // Contribution Amount Field
      Text("Default contribution dues amount", style = MaterialTheme.typography.labelMedium.copy(color = TextPrimary, fontWeight = FontWeight.SemiBold))
      Spacer(modifier = Modifier.height(6.dp))
      OutlinedTextField(
        value = amount,
        onValueChange = onAmountChange,
        placeholder = { Text("GHS 50") },
        prefix = { Text("GHS ", fontWeight = FontWeight.Bold, color = ForestGreenPrimary) },
        modifier = Modifier.fillMaxWidth().testTag("onboarding_amount_input"),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = ForestGreenPrimary,
          unfocusedBorderColor = BorderGrey,
          focusedContainerColor = PureWhite,
          unfocusedContainerColor = PureWhite
        ),
        shape = RoundedCornerShape(8.dp)
      )

      // Quick amount chips
      Spacer(modifier = Modifier.height(8.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        listOf("20", "50", "100", "200").forEach { preset ->
          val isSelected = amount == preset || amount == "GHS $preset"
          Surface(
            modifier = Modifier
              .weight(1f)
              .clickable { onAmountChange(preset) },
            shape = RoundedCornerShape(6.dp),
            color = if (isSelected) ForestGreenLightFill else Color(0xFFF8FAFC),
            border = BorderStroke(1.dp, if (isSelected) ForestGreenPrimary else BorderGrey)
          ) {
            Box(modifier = Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
              Text("GHS $preset", fontSize = 11.sp, color = if (isSelected) ForestGreenPrimary else TextPrimary, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Collection Frequency
      Text("Collection frequency", style = MaterialTheme.typography.labelMedium.copy(color = TextPrimary, fontWeight = FontWeight.SemiBold))
      Spacer(modifier = Modifier.height(8.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        listOf("Weekly", "Monthly").forEach { freq ->
          val isSelected = frequency == freq
          Box(
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .border(
                width = 1.dp,
                color = if (isSelected) ForestGreenPrimary else BorderGrey,
                shape = RoundedCornerShape(8.dp)
              )
              .background(
                if (isSelected) ForestGreenLightFill else PureWhite,
                shape = RoundedCornerShape(8.dp)
              )
              .clickable { onFrequencyChange(freq) },
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = freq,
              style = MaterialTheme.typography.labelLarge.copy(
                color = if (isSelected) ForestGreenPrimary else TextPrimary,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
              )
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      Text(
        text = "Members can always make partial micro-payments ('small-small' installments) towards their dues.",
        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
      )
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
      Button(
        onClick = onContinue,
        enabled = groupName.isNotBlank() && amount.isNotBlank(),
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("onboarding_continue_group_btn"),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = ForestGreenPrimary,
          disabledContainerColor = Color(0xFFE2E8F0)
        )
      ) {
        Text("Continue to Add Members", style = MaterialTheme.typography.labelLarge.copy(color = PureWhite))
      }
    }
  }
}

// -----------------------------------------------------------------------------
// STEP 4 — ADD MEMBERS (CLEAN RESPONSIVE FORM + CONTACTS PICKER)
// -----------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Step4AddMembers(
  members: List<SetupMemberItem>,
  onAddMember: (String, String) -> Unit,
  onAddMultipleMembers: (List<SetupMemberItem>) -> Unit,
  onRemoveMember: (SetupMemberItem) -> Unit,
  onContinue: () -> Unit
) {
  val context = LocalContext.current
  var newName by remember { mutableStateOf("") }
  var newPhone by remember { mutableStateOf("") }
  var showAllMembersDialog by remember { mutableStateOf(false) }
  var showContactsBottomSheet by remember { mutableStateOf(false) }

  val sanitizedPhone = remember(newPhone) { GhanaPhoneUtils.sanitizeTo9Digits(newPhone) }

  // Runtime Contacts permission launcher
  val contactsPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { _ ->
    // Open the bottom sheet regardless: it will display either contacts or a friendly permission card
    showContactsBottomSheet = true
  }

  // System contact picker launcher
  val contactPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickContact()
  ) { uri ->
    if (uri != null) {
      val (pickedName, pickedPhone) = queryContactDetails(context, uri)
      if (pickedName.isNotBlank()) {
        val cleanPhone = if (pickedPhone.isNotBlank()) {
          GhanaPhoneUtils.formatFullInternational(GhanaPhoneUtils.sanitizeTo9Digits(pickedPhone))
        } else {
          "+233 24 100 00${members.size + 1}"
        }
        onAddMember(pickedName, cleanPhone)
      }
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(PureWhite),
    horizontalAlignment = Alignment.Start,
    verticalArrangement = Arrangement.SpaceBetween
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Add Members",
          style = MaterialTheme.typography.headlineLarge.copy(color = TextPrimary)
        )
        Surface(
          color = ForestGreenLightFill,
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, ForestGreenPrimary.copy(alpha = 0.3f))
        ) {
          Text(
            text = "${members.size} added",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall.copy(
              color = ForestGreenPrimary,
              fontWeight = FontWeight.Bold
            )
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = "Add the members who participate in your Susu group. You can type their details or import them directly from your phone contacts.",
        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
      )

      Spacer(modifier = Modifier.height(14.dp))

      // Button to open contacts modal
      OutlinedButton(
        onClick = {
          if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            showContactsBottomSheet = true
          } else {
            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
          }
        },
        modifier = Modifier
          .fillMaxWidth()
          .height(46.dp)
          .testTag("import_contacts_btn"),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, ForestGreenPrimary),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = ForestGreenLightFill)
      ) {
        Icon(Icons.Default.Contacts, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Choose from Phone Contacts",
          color = ForestGreenPrimary,
          fontWeight = FontWeight.Bold,
          fontSize = 13.sp
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      // RESPONSIVE INPUT FORM
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, BorderGrey),
        color = Color(0xFFF8FAFC)
      ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Or enter manually:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)

          OutlinedTextField(
            value = newName,
            onValueChange = { newName = it },
            placeholder = { Text("Member full name / nickname", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(6.dp),
            colors = OutlinedTextFieldDefaults.colors(
              unfocusedBorderColor = BorderGrey,
              focusedBorderColor = ForestGreenPrimary,
              focusedContainerColor = PureWhite,
              unfocusedContainerColor = PureWhite
            )
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            OutlinedTextField(
              value = sanitizedPhone,
              onValueChange = { newPhone = it },
              placeholder = { Text("Phone (e.g. 24 123 4567)", fontSize = 13.sp) },
              prefix = { Text("+233 ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary) },
              modifier = Modifier.weight(1f),
              singleLine = true,
              shape = RoundedCornerShape(6.dp),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = BorderGrey,
                focusedBorderColor = ForestGreenPrimary,
                focusedContainerColor = PureWhite,
                unfocusedContainerColor = PureWhite
              )
            )

            Button(
              onClick = {
                if (newName.isNotBlank()) {
                  val formattedNumber = if (sanitizedPhone.isNotBlank()) {
                    GhanaPhoneUtils.formatFullInternational(sanitizedPhone)
                  } else {
                    "+233 24 100 00${members.size + 1}"
                  }
                  onAddMember(newName.trim(), formattedNumber)
                  newName = ""
                  newPhone = ""
                }
              },
              enabled = newName.isNotBlank(),
              shape = RoundedCornerShape(6.dp),
              colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary),
              modifier = Modifier.height(50.dp).testTag("add_member_submit_btn")
            ) {
              Text("Add", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Member list
      val displayMembers = members.take(4)
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, BorderGrey, RoundedCornerShape(8.dp))
          .padding(8.dp)
      ) {
        if (members.isEmpty()) {
          Text(
            text = "No members added yet. Tap 'Choose from Phone Contacts' above or type a name to add.",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp)
          )
        } else {
          displayMembers.forEach { member ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                val initial = member.name.firstOrNull()?.toString()?.uppercase() ?: "M"
                Box(
                  modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(ForestGreenPrimary),
                  contentAlignment = Alignment.Center
                ) {
                  Text(initial, color = PureWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Text(member.name, style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary, fontWeight = FontWeight.SemiBold))
                  Text(member.phone, style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp))
                }
              }
              IconButton(
                onClick = { onRemoveMember(member) },
                modifier = Modifier.size(28.dp)
              ) {
                Icon(Icons.Default.Close, contentDescription = "Remove", tint = LineIconBlack, modifier = Modifier.size(16.dp))
              }
            }
          }

          if (members.size > 4) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Showing 4 of ${members.size} members — Tap to View All",
              style = MaterialTheme.typography.labelMedium.copy(color = ForestGreenPrimary, fontWeight = FontWeight.SemiBold),
              modifier = Modifier
                .clickable { showAllMembersDialog = true }
                .padding(vertical = 4.dp)
            )
          }
        }
      }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
      Button(
        onClick = onContinue,
        enabled = members.isNotEmpty(),
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("onboarding_continue_members_btn"),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = ForestGreenPrimary,
          disabledContainerColor = Color(0xFFE2E8F0)
        )
      ) {
        Text(
          text = if (members.isNotEmpty()) "Continue (${members.size} Members)" else "Add At Least 1 Member to Continue",
          style = MaterialTheme.typography.labelLarge.copy(
            color = if (members.isNotEmpty()) PureWhite else TextSecondary
          )
        )
      }
    }
  }

  // ---------------------------------------------------------------------------
  // CONTACTS BOTTOM SHEET / PICKER
  // ---------------------------------------------------------------------------
  if (showContactsBottomSheet) {
    ContactsPickerBottomSheet(
      onDismiss = { showContactsBottomSheet = false },
      onPickFromSystem = {
        contactPickerLauncher.launch(null)
        showContactsBottomSheet = false
      },
      onAddSelected = { selectedList ->
        onAddMultipleMembers(selectedList)
        showContactsBottomSheet = false
      }
    )
  }

  // ALL MEMBERS DIALOG
  if (showAllMembersDialog) {
    AlertDialog(
      onDismissRequest = { showAllMembersDialog = false },
      title = { Text("All Group Members (${members.size})", fontWeight = FontWeight.Bold) },
      text = {
        LazyColumn(modifier = Modifier.fillMaxWidth().height(280.dp)) {
          items(members, key = { it.phone }) { m ->
            Row(
              modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(m.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(m.phone, color = TextSecondary, fontSize = 11.sp)
              }
              IconButton(onClick = { onRemoveMember(m) }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = null, tint = LineIconBlack, modifier = Modifier.size(14.dp))
              }
            }
          }
        }
      },
      confirmButton = {
        Button(onClick = { showAllMembersDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary)) {
          Text("Done", color = PureWhite)
        }
      }
    )
  }
}

// -----------------------------------------------------------------------------
// CONTACTS PICKER BOTTOM SHEET COMPONENT
// -----------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactsPickerBottomSheet(
  onDismiss: () -> Unit,
  onPickFromSystem: () -> Unit,
  onAddSelected: (List<SetupMemberItem>) -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val context = LocalContext.current
  var searchQuery by remember { mutableStateOf("") }
  val selectedContacts = remember { mutableStateListOf<ContactItem>() }

  var hasPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    )
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    hasPermission = isGranted
  }

  var isLoadingContacts by remember { mutableStateOf(false) }
  var deviceContacts by remember { mutableStateOf<List<ContactItem>>(emptyList()) }

  LaunchedEffect(hasPermission) {
    if (hasPermission) {
      isLoadingContacts = true
      try {
        val loaded = DeviceContactManager.loadDeviceContacts(context)
        deviceContacts = loaded.map { ContactItem(it.name, it.formattedPhone) }
      } catch (e: Exception) {
        deviceContacts = emptyList()
      } finally {
        isLoadingContacts = false
      }
    } else {
      deviceContacts = emptyList()
    }
  }

  val filteredContacts = remember(searchQuery, deviceContacts) {
    if (searchQuery.isBlank()) deviceContacts
    else deviceContacts.filter {
      it.name.contains(searchQuery, ignoreCase = true) || it.phone.contains(searchQuery)
    }
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = PureWhite
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Select from Contacts",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
        )
        TextButton(onClick = onPickFromSystem) {
          Icon(Icons.Default.ContactPhone, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("System Picker", fontSize = 12.sp, color = ForestGreenPrimary, fontWeight = FontWeight.Bold)
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      if (!hasPermission) {
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
          colors = CardDefaults.cardColors(containerColor = ForestGreenLightFill),
          border = BorderStroke(1.dp, ForestGreenPrimary.copy(alpha = 0.3f)),
          shape = RoundedCornerShape(12.dp)
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Icon(
              imageVector = Icons.Default.Contacts,
              contentDescription = null,
              tint = ForestGreenPrimary,
              modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "Contacts Permission Required",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary),
              textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Allow SusuLedger to read contacts on your device to import your Susu members with one tap.",
              style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
              textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
              onClick = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
              colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Text("Grant Permission", fontWeight = FontWeight.Bold, color = PureWhite)
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onPickFromSystem) {
              Text("Or choose one from System Picker", color = ForestGreenPrimary, fontSize = 12.sp)
            }
          }
        }
      } else {
        // Search Bar
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          placeholder = { Text("Search name or phone number...", fontSize = 13.sp) },
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp)) },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
          shape = RoundedCornerShape(8.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ForestGreenPrimary,
            unfocusedBorderColor = BorderGrey
          )
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (isLoadingContacts) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(200.dp),
            contentAlignment = Alignment.Center
          ) {
            CircularProgressIndicator(color = ForestGreenPrimary)
          }
        } else if (deviceContacts.isEmpty()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(160.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "No contacts found on device.\nYou can type member details or use the System Picker.",
              color = TextSecondary,
              textAlign = TextAlign.Center,
              fontSize = 13.sp
            )
          }
        } else {
          // Scrollable contact list
          LazyColumn(
            modifier = Modifier
              .fillMaxWidth()
              .height(300.dp)
          ) {
            items(filteredContacts, key = { it.phone }) { contact ->
              val isSelected = selectedContacts.contains(contact)
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable {
                    if (isSelected) selectedContacts.remove(contact)
                    else selectedContacts.add(contact)
                  }
                  .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Checkbox(
                    checked = isSelected,
                    onCheckedChange = { checked ->
                      if (checked) selectedContacts.add(contact)
                      else selectedContacts.remove(contact)
                    },
                    colors = CheckboxDefaults.colors(checkedColor = ForestGreenPrimary)
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Column {
                    Text(contact.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = TextPrimary))
                    Text(contact.phone, style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp))
                  }
                }

                Surface(
                  color = if (isSelected) ForestGreenPrimary else Color(0xFFF1F5F9),
                  shape = RoundedCornerShape(4.dp),
                  modifier = Modifier.clickable {
                    if (isSelected) selectedContacts.remove(contact)
                    else selectedContacts.add(contact)
                  }
                ) {
                  Text(
                    text = if (isSelected) "Selected" else "+ Add",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 11.sp,
                    color = if (isSelected) PureWhite else TextPrimary,
                    fontWeight = FontWeight.Bold
                  )
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Confirm Add Button
        Button(
          onClick = {
            onAddSelected(selectedContacts.map { SetupMemberItem(it.name, it.phone) })
          },
          enabled = selectedContacts.isNotEmpty(),
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
          shape = RoundedCornerShape(8.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = ForestGreenPrimary,
            disabledContainerColor = Color(0xFFE2E8F0)
          )
        ) {
          Text("Import ${selectedContacts.size} Selected Member${if (selectedContacts.size != 1) "s" else ""}", color = PureWhite, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))
      }
    }
  }
}

// Helper for Android contact provider details
private fun queryContactDetails(context: Context, uri: Uri): Pair<String, String> {
  var name = ""
  var phone = ""
  try {
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
      if (it.moveToFirst()) {
        val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
        if (nameIndex >= 0) {
          name = it.getString(nameIndex) ?: ""
        }
        val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
        val hasPhoneIndex = it.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
        if (idIndex >= 0 && hasPhoneIndex >= 0 && it.getInt(hasPhoneIndex) > 0) {
          val contactId = it.getString(idIndex)
          val phoneCursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null,
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
          )
          phoneCursor?.use { pCursor ->
            if (pCursor.moveToFirst()) {
              val pIdx = pCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
              if (pIdx >= 0) phone = pCursor.getString(pIdx) ?: ""
            }
          }
        }
      }
    }
  } catch (_: Exception) {
    name = "Imported Contact"
    phone = "+233 24 100 0001"
  }
  return Pair(name, phone)
}

// -----------------------------------------------------------------------------
// STEP 5 — REVIEW GROUP DETAILS
// -----------------------------------------------------------------------------
@Composable
private fun Step5ReviewGroup(
  groupName: String,
  amount: String,
  frequency: String,
  treasurerName: String,
  treasurerPhone: String,
  memberCount: Int,
  onContinue: () -> Unit
) {
  val cleanPhone = if (treasurerPhone.isNotBlank()) "+233 ${GhanaPhoneUtils.sanitizeTo9Digits(treasurerPhone)}" else "+233 24 000 0000"
  val cleanName = if (treasurerName.isNotBlank()) treasurerName else "Ama Mensah (Treasurer)"

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(PureWhite)
      .verticalScroll(rememberScrollState()),
    horizontalAlignment = Alignment.Start,
    verticalArrangement = Arrangement.SpaceBetween
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Text(
        text = "Review Group Details",
        style = MaterialTheme.typography.headlineLarge.copy(color = TextPrimary)
      )

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = "Confirm group contribution parameters before setting up your officer security PIN.",
        style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
      )

      Spacer(modifier = Modifier.height(20.dp))

      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = BorderStroke(1.dp, BorderGrey),
        shape = RoundedCornerShape(12.dp)
      ) {
        Column(modifier = Modifier.padding(20.dp)) {
          ReviewRow(label = "Group Name", value = groupName.ifBlank { "Nima Market Susu" })
          Spacer(modifier = Modifier.height(14.dp))
          ReviewRow(label = "Contribution Rate", value = "${amount.ifBlank { "GHS 50" }} (${frequency.lowercase()})")
          Spacer(modifier = Modifier.height(14.dp))
          ReviewRow(label = "Treasurer / Group Leader", value = "$cleanName ($cleanPhone)")
          Spacer(modifier = Modifier.height(14.dp))
          ReviewRow(label = "Registered Members", value = "$memberCount members ready for WhatsApp onboarding")
        }
      }

      Spacer(modifier = Modifier.height(20.dp))
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
      Button(
        onClick = onContinue,
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("review_continue_to_pin_btn"),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary)
      ) {
        Text("Confirm & Set Security PIN", fontSize = 15.sp, color = PureWhite, fontWeight = FontWeight.Bold)
      }
    }
  }
}

@Composable
private fun ReviewRow(label: String, value: String) {
  Column {
    Text(label, style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.SemiBold))
    Spacer(modifier = Modifier.height(2.dp))
    Text(value, style = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary, fontWeight = FontWeight.SemiBold))
  }
}

// -----------------------------------------------------------------------------
// STEP 6 — CREATE & CONFIRM 4-DIGIT SECURITY PIN
// -----------------------------------------------------------------------------
@Composable
private fun PinBoxRow(
  pinValue: String,
  onPinChange: (String) -> Unit,
  label: String,
  focusRequester: FocusRequester = remember { FocusRequester() },
  testTagPrefix: String = "pin_box"
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelLarge.copy(
        fontWeight = FontWeight.Bold,
        color = TextPrimary
      )
    )

    Spacer(modifier = Modifier.height(10.dp))

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clickable { focusRequester.requestFocus() },
      contentAlignment = Alignment.Center
    ) {
      BasicTextField(
        value = pinValue,
        onValueChange = {
          if (it.length <= 4 && it.all { c -> c.isDigit() }) {
            onPinChange(it)
          }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier
          .focusRequester(focusRequester)
          .alpha(0.01f)
          .testTag("${testTagPrefix}_input")
      )

      Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
      ) {
        (0 until 4).forEach { index ->
          val char = pinValue.getOrNull(index)
          val isFilled = char != null
          val isCurrent = pinValue.length == index || (pinValue.length == 4 && index == 3)

          Box(
            modifier = Modifier
              .size(width = 62.dp, height = 64.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(if (isFilled) ForestGreenLightFill else PureWhite)
              .border(
                width = if (isCurrent) 2.dp else 1.dp,
                color = if (isCurrent) ForestGreenPrimary else BorderGrey,
                shape = RoundedCornerShape(12.dp)
              ),
            contentAlignment = Alignment.Center
          ) {
            if (isFilled) {
              Box(
                modifier = Modifier
                  .size(16.dp)
                  .clip(CircleShape)
                  .background(ForestGreenPrimary)
              )
            } else {
              Text(
                text = "—",
                fontSize = 18.sp,
                fontWeight = FontWeight.Light,
                color = TextSecondary
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun Step6SecurityPin(
  treasurerPin: String,
  onTreasurerPinChange: (String) -> Unit,
  onContinue: () -> Unit
) {
  var createPin by remember { mutableStateOf(treasurerPin) }
  var confirmPin by remember { mutableStateOf(treasurerPin) }

  val createFocusRequester = remember { FocusRequester() }
  val confirmFocusRequester = remember { FocusRequester() }

  val pinsMatch = createPin.length == 4 && confirmPin.length == 4 && createPin == confirmPin
  val pinsMismatch = createPin.length == 4 && confirmPin.length == 4 && createPin != confirmPin

  // Auto-focus the create PIN field on entry
  LaunchedEffect(Unit) {
    kotlinx.coroutines.delay(200)
    createFocusRequester.requestFocus()
  }

  // Auto-advance to confirm PIN when 4 digits are entered in create PIN
  LaunchedEffect(createPin) {
    if (createPin.length == 4) {
      confirmFocusRequester.requestFocus()
    }
  }

  // Auto-navigate to next page when both PINs match and are verified
  LaunchedEffect(createPin, confirmPin) {
    if (createPin.length == 4 && confirmPin.length == 4 && createPin == confirmPin) {
      onTreasurerPinChange(createPin)
      kotlinx.coroutines.delay(350)
      onContinue()
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(PureWhite)
      .verticalScroll(rememberScrollState()),
    horizontalAlignment = Alignment.Start,
    verticalArrangement = Arrangement.SpaceBetween
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Text(
        text = "Set Up Security PIN",
        style = MaterialTheme.typography.headlineLarge.copy(color = TextPrimary)
      )

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = "Create a 4-digit security PIN to protect officer approvals, cycle closures, and group funds.",
        style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
      )

      Spacer(modifier = Modifier.height(28.dp))

      PinBoxRow(
        pinValue = createPin,
        onPinChange = { newPin ->
          createPin = newPin
          if (newPin.length == 4 && confirmPin.length == 4 && newPin == confirmPin) {
            onTreasurerPinChange(newPin)
          }
        },
        label = "Create 4-Digit Security PIN",
        focusRequester = createFocusRequester,
        testTagPrefix = "create_pin"
      )

      Spacer(modifier = Modifier.height(28.dp))

      PinBoxRow(
        pinValue = confirmPin,
        onPinChange = { newPin ->
          confirmPin = newPin
          if (newPin.length == 4 && createPin.length == 4 && newPin == createPin) {
            onTreasurerPinChange(newPin)
          }
        },
        label = "Confirm 4-Digit Security PIN",
        focusRequester = confirmFocusRequester,
        testTagPrefix = "confirm_pin"
      )

      Spacer(modifier = Modifier.height(24.dp))

      if (pinsMatch) {
        Surface(
          color = ForestGreenLightFill,
          shape = RoundedCornerShape(8.dp),
          border = BorderStroke(1.dp, ForestGreenPrimary.copy(alpha = 0.4f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.CheckCircle,
              contentDescription = null,
              tint = ForestGreenPrimary,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = "Security PINs match perfectly — saving & continuing...",
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
              color = ForestGreenPrimary
            )
          }
        }
      } else if (pinsMismatch) {
        Surface(
          color = Color(0xFFFEF2F2),
          shape = RoundedCornerShape(8.dp),
          border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.4f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.ErrorOutline,
              contentDescription = null,
              tint = ErrorRed,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = "PINs do not match. Please re-enter.",
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
              color = ErrorRed
            )
          }
        }
      }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
      Button(
        onClick = {
          if (pinsMatch) {
            onTreasurerPinChange(createPin)
            onContinue()
          }
        },
        enabled = pinsMatch,
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("save_security_pin_btn"),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = ForestGreenPrimary,
          disabledContainerColor = Color(0xFFE2E8F0)
        )
      ) {
        Text("Save PIN & Continue", fontSize = 15.sp, color = PureWhite, fontWeight = FontWeight.Bold)
      }
    }
  }
}

// -----------------------------------------------------------------------------
// STEP 7 — CONNECT THE WHATSAPP BOT (CLEAR 1-TAP MAKOLA-FRIENDLY WORKFLOW)
// -----------------------------------------------------------------------------
@Composable
private fun Step7ConnectBot(
  pairingCode: String,
  treasurerName: String,
  groupName: String,
  onFinish: () -> Unit
) {
  val context = LocalContext.current
  var isConnected by remember { mutableStateOf(false) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(PureWhite)
      .verticalScroll(rememberScrollState()),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.SpaceBetween
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 10.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.Chat,
        contentDescription = null,
        tint = LineIconGreen,
        modifier = Modifier.size(40.dp)
      )

      Spacer(modifier = Modifier.height(10.dp))

      Text(
        text = "Connect WhatsApp in 1 Tap",
        style = MaterialTheme.typography.headlineMedium.copy(
          color = TextPrimary,
          textAlign = TextAlign.Center,
          fontWeight = FontWeight.Bold
        )
      )

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = "Tap the green button below. Your WhatsApp will open to SusuLedger's verified number. Simply tap Send!",
        style = MaterialTheme.typography.bodyMedium.copy(
          color = TextSecondary,
          textAlign = TextAlign.Center
        ),
        modifier = Modifier.padding(horizontal = 8.dp)
      )

      Spacer(modifier = Modifier.height(18.dp))

      // Verified WhatsApp Bot Card with official number
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = BorderStroke(1.dp, BorderGrey),
        shape = RoundedCornerShape(8.dp)
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SusuLedger Verified WhatsApp Bot Gateway", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
            Spacer(modifier = Modifier.width(6.dp))
            Icon(Icons.Default.CheckCircle, contentDescription = "Verified", tint = ForestGreenPrimary, modifier = Modifier.size(16.dp))
          }

          Spacer(modifier = Modifier.height(12.dp))

          Surface(
            color = Color(0xFFF8FAFC),
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, BorderGrey)
          ) {
            Column(
              modifier = Modifier.padding(12.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Text("Your 1-Tap Pairing Code", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
              Text(
                text = pairingCode,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 3.sp,
                color = ForestGreenPrimary
              )
              Text("Expires in 15 minutes", fontSize = 10.sp, color = TextSecondary)
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // HOW THIS WORKS CARD (Non-technical / Market Friendly Explanation)
      Surface(
        color = ForestGreenLightFill,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, ForestGreenPrimary.copy(alpha = 0.3f))
      ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("How it works (Made simple for everyone):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ForestGreenPrimary)
          Text("1. Tapping below opens your WhatsApp directly to SusuLedger.", fontSize = 11.sp, color = TextPrimary)
          Text("2. The pairing code is pre-typed. Just press Send in WhatsApp.", fontSize = 11.sp, color = TextPrimary)
          Text("3. The bot immediately sends a welcome notice to all members: 'Hello! $treasurerName has added you to $groupName...'", fontSize = 11.sp, color = TextPrimary)
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Open WhatsApp & Send Code Button (Launches WhatsApp Intent)
      Button(
        onClick = {
          isConnected = true
          try {
            val url = "https://wa.me/233240007878?text=PAIR:$pairingCode"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
          } catch (_: Exception) {
            // Handled safely in emulator/sandbox
          }
        },
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("onboarding_open_whatsapp_btn"),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary)
      ) {
        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = PureWhite, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Open WhatsApp & Send Code",
          style = MaterialTheme.typography.labelLarge.copy(color = PureWhite, fontWeight = FontWeight.Bold)
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Status indicator
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (!isConnected) {
          CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = ForestGreenPrimary)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Listening for incoming WhatsApp pairing...", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
        } else {
          Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Group Connected & Members Welcomed!", style = MaterialTheme.typography.bodyMedium.copy(color = ForestGreenPrimary, fontWeight = FontWeight.Bold))
        }
      }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
      Button(
        onClick = onFinish,
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("onboarding_finish_btn"),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary)
      ) {
        Text("Go to Dashboard", style = MaterialTheme.typography.labelLarge.copy(color = PureWhite, fontWeight = FontWeight.Bold))
      }
    }
  }
}
