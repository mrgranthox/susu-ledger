package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ClaimEntity
import com.example.data.local.MemberEntity
import com.example.ui.theme.BorderGrey
import com.example.ui.theme.ForestGreenLightFill
import com.example.ui.theme.ForestGreenPrimary
import com.example.ui.theme.LineIconBlack
import com.example.ui.theme.LineIconGreen
import com.example.ui.theme.LineIconGrey
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmPaymentSheet(
  sheetState: SheetState,
  members: List<MemberEntity>,
  initialMember: MemberEntity?,
  initialClaim: ClaimEntity?,
  cycleNumber: Int,
  cycleDueAmount: Double,
  onDismiss: () -> Unit,
  onConfirm: (
    memberId: String,
    amount: Double,
    method: String,
    momoRef: String?,
    sendWhatsAppReceipt: Boolean
  ) -> Unit
) {
  var selectedMemberId by remember {
    mutableStateOf(initialClaim?.memberId ?: initialMember?.id ?: members.firstOrNull()?.id ?: "")
  }

  val selectedMember = remember(selectedMemberId, members) {
    members.find { it.id == selectedMemberId } ?: initialMember ?: members.firstOrNull()
  }

  val memberFirstName = selectedMember?.alias?.split(" ")?.firstOrNull() ?: "Member"

  var amountText by remember {
    mutableStateOf(String.format(java.util.Locale.US, "%.0f", initialClaim?.claimedAmount ?: cycleDueAmount))
  }

  var selectedMethod by remember { mutableStateOf("MOMO") }
  var momoRef by remember { mutableStateOf(initialClaim?.evidenceMoMoId ?: "") }
  var sendReceipt by remember { mutableStateOf(true) }
  var memberDropdownExpanded by remember { mutableStateOf(false) }

  val parsedAmount by remember(amountText) {
    derivedStateOf { amountText.toDoubleOrNull() ?: cycleDueAmount }
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = PureWhite
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .background(PureWhite)
        .padding(horizontal = 24.dp, vertical = 8.dp)
        .padding(bottom = 36.dp)
    ) {
      // Top bar with close button
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Confirm payment — $memberFirstName",
          style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            color = TextPrimary
          )
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
          Icon(Icons.Default.Close, contentDescription = "Close", tint = LineIconBlack, modifier = Modifier.size(20.dp))
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Large black amount + editable amount field
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "Amount to Record",
            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.SemiBold)
          )
          Text(
            text = "GHS ${String.format(java.util.Locale.US, "%.2f", parsedAmount)}",
            style = MaterialTheme.typography.headlineMedium.copy(
              fontWeight = FontWeight.Bold,
              color = ForestGreenPrimary
            )
          )
        }

        // Quick indicator pill
        val isFullPayment = parsedAmount >= cycleDueAmount
        Surface(
          color = if (isFullPayment) ForestGreenLightFill else Color(0xFFFEF3C7),
          shape = RoundedCornerShape(16.dp),
          border = BorderStroke(1.dp, if (isFullPayment) ForestGreenPrimary.copy(alpha = 0.3f) else Color(0xFFF59E0B))
        ) {
          Text(
            text = if (isFullPayment) "FULL DUES (100%)" else "PARTIAL INSTALLMENT",
            style = MaterialTheme.typography.labelSmall.copy(
              color = if (isFullPayment) ForestGreenPrimary else Color(0xFF92400E),
              fontWeight = FontWeight.Bold,
              fontSize = 10.sp
            ),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Custom amount input field
      OutlinedTextField(
        value = amountText,
        onValueChange = { amountText = it.filter { char -> char.isDigit() || char == '.' } },
        label = { Text("Contribution Amount (GHS)") },
        placeholder = { Text("e.g. 10.00, 20.00, 50.00") },
        modifier = Modifier
          .fillMaxWidth()
          .testTag("payment_amount_input"),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = ForestGreenPrimary,
          unfocusedBorderColor = BorderGrey,
          focusedContainerColor = PureWhite,
          unfocusedContainerColor = PureWhite
        ),
        shape = RoundedCornerShape(8.dp)
      )

      Spacer(modifier = Modifier.height(10.dp))

      // Quick Installment Chips ("Small Small" micro-payments)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        listOf(
          "Full (GHS ${cycleDueAmount.toInt()})" to cycleDueAmount.toInt().toString(),
          "GHS 10" to "10",
          "GHS 20" to "20",
          "GHS 25" to "25",
          "GHS 50" to "50"
        ).forEach { (label, value) ->
          val isSelected = amountText == value
          Surface(
            modifier = Modifier
              .weight(1f)
              .clickable { amountText = value },
            shape = RoundedCornerShape(6.dp),
            color = if (isSelected) ForestGreenLightFill else Color(0xFFF8FAFC),
            border = BorderStroke(1.dp, if (isSelected) ForestGreenPrimary else BorderGrey)
          ) {
            Box(
              modifier = Modifier.padding(vertical = 6.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) ForestGreenPrimary else TextPrimary,
                maxLines = 1
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Remaining balance card
      val remainingBalance = (cycleDueAmount - parsedAmount).coerceAtLeast(0.0)
      Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFF8FAFC),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, BorderGrey)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Week $cycleNumber Dues: GHS ${String.format(java.util.Locale.US, "%.2f", cycleDueAmount)}",
            fontSize = 11.sp,
            color = TextSecondary
          )
          Text(
            text = if (remainingBalance <= 0) "Fully Settled" else "Remaining: GHS ${String.format(java.util.Locale.US, "%.2f", remainingBalance)}",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (remainingBalance <= 0) ForestGreenPrimary else Color(0xFFD97706)
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Member selector if multiple
      if (members.isNotEmpty()) {
        ExposedDropdownMenuBox(
          expanded = memberDropdownExpanded,
          onExpandedChange = { memberDropdownExpanded = !memberDropdownExpanded }
        ) {
          OutlinedTextField(
            value = "${selectedMember?.alias ?: "Select member"} (${selectedMember?.phone ?: ""})",
            onValueChange = {},
            readOnly = true,
            label = { Text("Member") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = memberDropdownExpanded) },
            modifier = Modifier
              .menuAnchor()
              .fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = ForestGreenPrimary,
              unfocusedBorderColor = BorderGrey,
              focusedContainerColor = PureWhite,
              unfocusedContainerColor = PureWhite
            ),
            shape = RoundedCornerShape(8.dp)
          )

          ExposedDropdownMenu(
            expanded = memberDropdownExpanded,
            onDismissRequest = { memberDropdownExpanded = false },
            modifier = Modifier.background(PureWhite)
          ) {
            members.forEach { m ->
              DropdownMenuItem(
                text = {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = m.alias, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = m.phone, fontSize = 12.sp, color = TextSecondary)
                  }
                },
                onClick = {
                  selectedMemberId = m.id
                  memberDropdownExpanded = false
                }
              )
            }
          }
        }
        Spacer(modifier = Modifier.height(18.dp))
      }

      // Payment method as 3 plain outlined buttons: "MoMo" | "Cash" | "Agent"
      // Selected: green border #1B5E3B, light green fill #E8F2EC
      Text(
        text = "Payment Method",
        style = MaterialTheme.typography.labelMedium.copy(
          fontWeight = FontWeight.SemiBold,
          color = TextPrimary
        )
      )

      Spacer(modifier = Modifier.height(8.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        listOf("MoMo" to "MOMO", "Cash" to "CASH", "Agent" to "AGENT").forEach { (label, key) ->
          val isSelected = selectedMethod == key
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
              .clickable { selectedMethod = key },
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = label,
              style = MaterialTheme.typography.labelLarge.copy(
                color = if (isSelected) ForestGreenPrimary else TextPrimary,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
              )
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Toggle row with simple green toggle: "Send receipt to Kofi on WhatsApp" (ON)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, BorderGrey, RoundedCornerShape(8.dp))
          .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Send receipt to $memberFirstName on WhatsApp",
            style = MaterialTheme.typography.bodyMedium.copy(
              color = TextPrimary,
              fontWeight = FontWeight.Medium
            )
          )
          Text(
            text = "Instant proof delivered to their chat",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
          )
        }

        Switch(
          checked = sendReceipt,
          onCheckedChange = { sendReceipt = it },
          colors = SwitchDefaults.colors(
            checkedThumbColor = PureWhite,
            checkedTrackColor = ForestGreenPrimary,
            uncheckedThumbColor = LineIconGrey,
            uncheckedTrackColor = BorderGrey
          ),
          modifier = Modifier.testTag("send_whatsapp_receipt_toggle")
        )
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Small grey text: "Confirmed payments are recorded permanently. Corrections are logged."
      Text(
        text = "Confirmed payments are recorded permanently. Corrections are logged.",
        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
      )

      Spacer(modifier = Modifier.height(20.dp))

      // Large green "Confirm" button, grey "Cancel"
      Button(
        onClick = {
          onConfirm(
            selectedMemberId,
            parsedAmount,
            selectedMethod,
            if (selectedMethod == "MOMO") momoRef else null,
            sendReceipt
          )
        },
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("confirm_payment_btn"),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary)
      ) {
        Text(
          text = "Confirm",
          style = MaterialTheme.typography.labelLarge.copy(
            color = PureWhite,
            fontWeight = FontWeight.SemiBold
          )
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      OutlinedButton(
        onClick = onDismiss,
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp)
          .testTag("cancel_payment_btn"),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, BorderGrey),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
      ) {
        Text("Cancel", style = MaterialTheme.typography.labelLarge.copy(color = TextSecondary))
      }
    }
  }
}
