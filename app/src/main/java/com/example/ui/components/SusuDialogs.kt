package com.example.ui.components

import com.example.ui.theme.DangerRed


import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BorderGrey
import com.example.ui.theme.ForestGreenPrimary
import com.example.ui.theme.InputBorderUnfocused
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.GhanaPhoneUtils

@Composable
fun NewWeekDialog(
  currentWeekNumber: Int,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    icon = {
      Icon(
        imageVector = Icons.Default.CalendarMonth,
        contentDescription = null,
        tint = ForestGreenPrimary
      )
    },
    title = {
      Text(
        text = "Start Week ${currentWeekNumber + 1}?",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
      )
    },
    text = {
      Column {
        Text(
          text = "This action will archive Week $currentWeekNumber records and open Week ${currentWeekNumber + 1} for collection.",
          style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = "A WhatsApp broadcast message will be automatically sent to all members with due dates and payment prompts.",
          style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = ForestGreenPrimary)
        )
      }
    },
    confirmButton = {
      Button(
        onClick = onConfirm,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = ForestGreenPrimary,
          contentColor = PureWhite
        ),
        modifier = Modifier.testTag("confirm_new_week_button")
      ) {
        Text("Start Week ${currentWeekNumber + 1}", fontWeight = FontWeight.Bold, color = PureWhite)
      }
    },
    dismissButton = {
      OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(8.dp)) {
        Text("Cancel")
      }
    }
  )
}

@Composable
fun AddMemberDialog(
  onDismiss: () -> Unit,
  onConfirm: (alias: String, phone: String) -> Unit,
  onOpenContacts: (() -> Unit)? = null
) {
  var name by remember { mutableStateOf("") }
  var rawPhone by remember { mutableStateOf("") }
  var isError by remember { mutableStateOf(false) }

  val sanitizedPhone = remember(rawPhone) { GhanaPhoneUtils.sanitizeTo9Digits(rawPhone) }
  val isValidPhone = remember(sanitizedPhone) { GhanaPhoneUtils.isValidGhanaPhone(sanitizedPhone) }
  val (isValid, statusMsg) = remember(sanitizedPhone) { GhanaPhoneUtils.getValidationStatus(sanitizedPhone) }

  AlertDialog(
    onDismissRequest = onDismiss,
    icon = {
      Icon(
        imageVector = Icons.Default.PersonAdd,
        contentDescription = null,
        tint = ForestGreenPrimary
      )
    },
    title = {
      Text(
        text = "Add Member to Susu Group",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .imePadding()
          .verticalScroll(rememberScrollState())
      ) {
        Text(
          text = "Enter the new member details. A Member Equity ledger account will be initialized.",
          style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
        )

        if (onOpenContacts != null) {
          Spacer(modifier = Modifier.height(10.dp))
          OutlinedButton(
            onClick = {
              onDismiss()
              onOpenContacts()
            },
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().testTag("pick_from_contacts_btn")
          ) {
            Icon(Icons.Default.Contacts, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Select from Phone Contacts", color = ForestGreenPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
          value = name,
          onValueChange = {
            name = it
            isError = false
          },
          label = { Text("Member Full Name") },
          placeholder = { Text("e.g. Abena Serwaa") },
          modifier = Modifier.fillMaxWidth().testTag("new_member_name_input"),
          singleLine = true,
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ForestGreenPrimary,
            unfocusedBorderColor = InputBorderUnfocused
          )
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
          value = sanitizedPhone,
          onValueChange = {
            rawPhone = it
            isError = false
          },
          label = { Text("Phone Number (+233)") },
          prefix = { Text("+233 ", fontWeight = FontWeight.Bold, color = TextSecondary) },
          placeholder = { Text("24 000 0000 (9 digits)") },
          modifier = Modifier.fillMaxWidth().testTag("new_member_phone_input"),
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = if (isValidPhone) ForestGreenPrimary else InputBorderUnfocused,
            unfocusedBorderColor = InputBorderUnfocused
          )
        )

        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = statusMsg,
          fontSize = 11.sp,
          color = if (isValidPhone) SuccessGreen else if (isError) DangerRed else TextSecondary
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (name.isNotBlank() && isValidPhone) {
            val formatted = GhanaPhoneUtils.formatFullInternational(sanitizedPhone)
            onConfirm(name.trim(), formatted)
          } else {
            isError = true
          }
        },
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = ForestGreenPrimary,
          contentColor = PureWhite
        ),
        modifier = Modifier.testTag("save_member_button")
      ) {
        Text("Add Member", fontWeight = FontWeight.Bold, color = PureWhite)
      }
    },
    dismissButton = {
      OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(8.dp)) {
        Text("Cancel")
      }
    }
  )
}
