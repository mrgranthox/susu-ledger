package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import com.example.ui.components.StandardNavTopBar
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.ui.theme.BorderGrey
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.ForestGreenLightFill
import com.example.ui.theme.ForestGreenPrimary
import com.example.ui.theme.LineIconBlack
import com.example.ui.theme.LineIconGreen
import com.example.ui.theme.LineIconGrey
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

enum class AuditFilter {
  ALL, REVERSALS, PIN_EVENTS, CYCLE_CLOSES
}

data class AuditLogEntry(
  val icon: ImageVector,
  val description: String,
  val timestamp: String,
  val actorName: String,
  val category: AuditFilter
)

@Composable
fun MoreSettingsScreen(
  groupName: String = "Nima Market Susu",
  currentGroup: com.example.data.local.GroupEntity? = null,
  activeCycle: com.example.data.local.CycleEntity? = null,
  onTogglePauseGroup: (reason: String) -> Unit = {},
  onUpdateContributionAmount: (newAmount: Double, applyToCurrentCycle: Boolean, reason: String) -> Unit = { _, _, _ -> },
  onRenameGroup: (newName: String) -> Unit = {},
  onOpenPairingSheet: () -> Unit = {},
  onOpenWhatsAppSimulator: () -> Unit = {},
  onSignOut: () -> Unit = {},
  onDeleteAccount: () -> Unit = {},
  onLockApp: () -> Unit = {},
  onBackClick: (() -> Unit)? = null
) {
  val context = LocalContext.current
  var activeSection by remember { mutableStateOf<String>("settings") } // "settings" or "activity_log"

  // Settings State
  var biometricEnabled by remember { mutableStateOf(true) }
  var showBiometricToggleVerificationDialog by remember { mutableStateOf(false) }
  var pendingBiometricToggleState by remember { mutableStateOf(false) }
  var biometricVerifyPin by remember { mutableStateOf("") }
  var biometricVerifyError by remember { mutableStateOf<String?>(null) }
  var secondOfficerPhone by remember { mutableStateOf("") }
  var showAddOfficerDialog by remember { mutableStateOf(false) }
  var showChangePinDialog by remember { mutableStateOf(false) }
  var showRenameGroupDialog by remember { mutableStateOf(false) }
  var showPauseGroupDialog by remember { mutableStateOf(false) }
  var showResumeGroupDialog by remember { mutableStateOf(false) }
  var showChangeAmountDialog by remember { mutableStateOf(false) }
  var pauseReason by remember { mutableStateOf("Scheduled administrative pause") }
  var newDuesAmountText by remember { mutableStateOf(String.format(java.util.Locale.US, "%.0f", currentGroup?.amount ?: 50.0)) }
  var duesChangeReason by remember { mutableStateOf("Group assembly adjustment") }
  var applyToCurrentCycle by remember { mutableStateOf(true) }
  var secondOfficerPinForDues by remember { mutableStateOf("") }

  var currentGroupName by remember { mutableStateOf(currentGroup?.name ?: groupName) }
  var showPaystackModal by remember { mutableStateOf(false) }
  var showLegalTermsModal by remember { mutableStateOf(false) }
  var showDpcPrivacyModal by remember { mutableStateOf(false) }
  var isSubscriptionActive by remember { mutableStateOf(false) }
  var showSignOutDialog by remember { mutableStateOf(false) }
  var showDeleteAccountDialog by remember { mutableStateOf(false) }

  // Activity Log State (Core Screen 9)
  var selectedAuditFilter by remember { mutableStateOf(AuditFilter.ALL) }
  var showAllAuditLogsModal by remember { mutableStateOf(false) }

  val auditLogs = remember {
    emptyList<AuditLogEntry>()
  }

  val filteredAuditLogs = remember(selectedAuditFilter, auditLogs) {
    when (selectedAuditFilter) {
      AuditFilter.ALL -> auditLogs
      else -> auditLogs.filter { it.category == selectedAuditFilter }
    }
  }

  // Maximum 5 rows + "View all"
  val displayAuditLogs = filteredAuditLogs.take(5)
  val scrollState = rememberScrollState()

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(PureWhite)
  ) {
    // Top Bar
    StandardNavTopBar(
      title = if (activeSection == "settings") "Settings & Administration" else "Activity & Audit Log",
      subtitle = "$currentGroupName configuration",
      onBackClick = onBackClick
    )
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {

        // Toggle between Settings and Activity Log
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderGrey, RoundedCornerShape(6.dp))
            .padding(2.dp),
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(4.dp))
              .background(if (activeSection == "settings") ForestGreenLightFill else PureWhite)
              .clickable { activeSection = "settings" }
              .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
          ) {
            Text("Settings", style = MaterialTheme.typography.labelSmall.copy(color = if (activeSection == "settings") ForestGreenPrimary else TextSecondary, fontWeight = FontWeight.SemiBold))
          }

          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(4.dp))
              .background(if (activeSection == "activity_log") ForestGreenLightFill else PureWhite)
              .clickable { activeSection = "activity_log" }
              .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
          ) {
            Text("Activity Log", style = MaterialTheme.typography.labelSmall.copy(color = if (activeSection == "activity_log") ForestGreenPrimary else TextSecondary, fontWeight = FontWeight.SemiBold))
          }
        }
      }

    // Scrollable Content
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .verticalScroll(scrollState)
        .padding(horizontal = 20.dp)
    ) {
      if (activeSection == "settings") {
        // ---------------------------------------------------------------------
        // CORE SCREEN 10: SETTINGS & SUBSCRIPTION
        // In sections with thin divider lines, monochrome line icons only.
        // Section "Security": Change PIN, biometric unlock toggle (green), "Add second officer" with phone input.
        // Section "Group": rename group, default amount, pause group.
        // Section "Subscription": card showing "Trial — 12 days left", green "Subscribe — GHS 40/month via MoMo" button.
        // Section "Language": English | Twi (Coming soon).
        // ---------------------------------------------------------------------
        Spacer(modifier = Modifier.height(8.dp))

        // WhatsApp Bot Integration Banner
        OutlinedButton(
          onClick = onOpenPairingSheet,
          modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .testTag("bot_pairing_quick_btn"),
          shape = RoundedCornerShape(8.dp),
          border = BorderStroke(1.dp, ForestGreenPrimary.copy(alpha = 0.5f)),
          colors = ButtonDefaults.outlinedButtonColors(containerColor = ForestGreenLightFill)
        ) {
          Icon(Icons.Default.Chat, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("WhatsApp Bot Pairing (Connect Members)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ForestGreenPrimary)
        }

        Spacer(modifier = Modifier.height(18.dp))

        // SECTION: SECURITY
        Text("SECURITY & ACCESS", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp))
        Spacer(modifier = Modifier.height(8.dp))

        SettingsRow(
          icon = Icons.Default.Lock,
          title = "Lock App Now",
          subtitle = "Require 4-digit PIN or fingerprint to regain access",
          onClick = onLockApp
        )

        DividerLine()

        SettingsRow(
          icon = Icons.Default.Key,
          title = "Change PIN",
          subtitle = "Update your 4-digit officer sign-off PIN",
          onClick = { showChangePinDialog = true }
        )

        DividerLine()

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickable {
              pendingBiometricToggleState = !biometricEnabled
              biometricVerifyPin = ""
              biometricVerifyError = null
              showBiometricToggleVerificationDialog = true
            }
            .padding(vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Fingerprint, contentDescription = null, tint = LineIconBlack, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text("Biometric unlock", style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary, fontWeight = FontWeight.SemiBold))
              Text("Fingerprint / Face authorization (PIN verified)", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
            }
          }

          Switch(
            checked = biometricEnabled,
            onCheckedChange = { targetVal ->
              pendingBiometricToggleState = targetVal
              biometricVerifyPin = ""
              biometricVerifyError = null
              showBiometricToggleVerificationDialog = true
            },
            colors = SwitchDefaults.colors(
              checkedThumbColor = PureWhite,
              checkedTrackColor = ForestGreenPrimary,
              uncheckedThumbColor = LineIconGrey,
              uncheckedTrackColor = BorderGrey
            )
          )
        }

        DividerLine()

        SettingsRow(
          icon = Icons.Default.PersonAdd,
          title = "Add second officer",
          subtitle = "Dual sign-off for ledger corrections & payouts",
          onClick = { showAddOfficerDialog = true }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // SECTION: GROUP
        Text("GROUP MANAGEMENT", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp))
        Spacer(modifier = Modifier.height(8.dp))

        val isGroupPaused = currentGroup?.state?.equals("paused", ignoreCase = true) == true
        val currentDuesAmount = currentGroup?.amount ?: 50.0

        SettingsRow(
          icon = Icons.Default.Group,
          title = "Rename group",
          subtitle = currentGroup?.name ?: currentGroupName,
          onClick = {
            currentGroupName = currentGroup?.name ?: currentGroupName
            showRenameGroupDialog = true
          }
        )

        DividerLine()

        SettingsRow(
          icon = Icons.Default.Payment,
          title = "Default contribution amount",
          subtitle = "GHS ${String.format(java.util.Locale.US, "%.2f", currentDuesAmount)} per cycle • Tap to change",
          onClick = {
            newDuesAmountText = String.format(java.util.Locale.US, "%.0f", currentDuesAmount)
            showChangeAmountDialog = true
          }
        )

        DividerLine()

        if (isGroupPaused) {
          SettingsRow(
            icon = Icons.Default.Refresh,
            title = "Resume group",
            subtitle = "Group is currently paused. Tap to reactivate weekly collections.",
            onClick = { showResumeGroupDialog = true }
          )
        } else {
          SettingsRow(
            icon = Icons.Default.Warning,
            title = "Pause group",
            subtitle = "Temporarily suspend weekly reminders & collections",
            onClick = { showPauseGroupDialog = true }
          )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // SECTION: ENTERPRISE SUBSCRIPTION (PAYSTACK MOMO BILLING)
        Text("SUBSCRIPTION & BILLING", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp))
        Spacer(modifier = Modifier.height(8.dp))

        Card(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("enterprise_subscription_card"),
          colors = CardDefaults.cardColors(containerColor = PureWhite),
          border = BorderStroke(1.5.dp, if (isSubscriptionActive) ForestGreenPrimary else Color(0xFFD97706).copy(alpha = 0.4f)),
          shape = RoundedCornerShape(12.dp)
        ) {
          Column(modifier = Modifier.padding(18.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(28.dp)
                    .background(Color(0xFFFEF3C7), CircleShape),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = Color(0xFFD97706),
                    modifier = Modifier.size(16.dp)
                  )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "PRO SUSU LEDGER",
                  style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                  )
                )
              }

              Surface(
                color = if (isSubscriptionActive) ForestGreenLightFill else Color(0xFFFEF3C7),
                shape = RoundedCornerShape(6.dp)
              ) {
                Text(
                  text = if (isSubscriptionActive) "ACTIVE" else "12 DAYS TRIAL",
                  style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSubscriptionActive) ForestGreenPrimary else Color(0xFF92400E)
                  ),
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
              }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
              text = "Enterprise plan includes automated WhatsApp collections, dual-officer sign-offs, and compliance reports.",
              style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, lineHeight = 16.sp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Pricing Pill & Paystack branding
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text("Price", fontSize = 10.sp, color = TextSecondary)
                Text("GHS 40 / month", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
              }
              Text("MTN MoMo • Telecel • Card", fontSize = 11.sp, color = TextSecondary)
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
              onClick = { showPaystackModal = true },
              modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .testTag("subscribe_paystack_btn"),
              shape = RoundedCornerShape(8.dp),
              colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary)
            ) {
              Icon(Icons.Default.Payment, contentDescription = null, tint = PureWhite, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = if (isSubscriptionActive) "Manage Subscription (Paystack)" else "Subscribe Now — GHS 40/mo",
                fontSize = 13.sp,
                color = PureWhite,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // SECTION: LANGUAGE
        Text("LANGUAGE", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp))
        Spacer(modifier = Modifier.height(8.dp))

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Language, contentDescription = null, tint = LineIconBlack, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text("English", style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary, fontWeight = FontWeight.SemiBold))
          }
          Text("Twi (Coming soon)", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // SECTION: LEGAL & STATUTORY COMPLIANCE (GHANA)
        Text("LEGAL & REGULATORY (GHANA)", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp))
        Spacer(modifier = Modifier.height(8.dp))

        SettingsRow(
          icon = Icons.Default.Security,
          title = "Terms of Service & Liability Shield",
          subtitle = "Statutory non-custodial record layer notice",
          onClick = { showLegalTermsModal = true }
        )

        DividerLine()

        SettingsRow(
          icon = Icons.Default.Check,
          title = "Ghana Data Protection (Act 843)",
          subtitle = "DPC Controller registered • Member consent logs",
          onClick = { showDpcPrivacyModal = true }
        )

        Spacer(modifier = Modifier.height(28.dp))

        // SECTION: ACCOUNT & SESSION (PROMINENT SIGN OUT)
        Text("ACCOUNT & SESSION", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp))
        Spacer(modifier = Modifier.height(8.dp))

        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = PureWhite),
          border = BorderStroke(1.dp, BorderGrey),
          shape = RoundedCornerShape(10.dp)
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .background(Color(0xFFF1F5F9), CircleShape),
                contentAlignment = Alignment.Center
              ) {
                Icon(Icons.Default.Group, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(20.dp))
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text("Treasurer Account", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                Text("+233 24 123 4567 • Primary Officer", fontSize = 12.sp, color = TextSecondary)
              }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Unmissable Sign Out Button
            Button(
              onClick = { showSignOutDialog = true },
              modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .testTag("settings_sign_out_button"),
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9)),
              shape = RoundedCornerShape(8.dp)
            ) {
              Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign Out", tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Sign Out of Session", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Delete Account Text Button
            TextButton(
              onClick = { showDeleteAccountDialog = true },
              modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_delete_account_btn")
            ) {
              Text("Delete Account & Clear Local Data", color = TextSecondary, fontSize = 11.sp)
            }
          }
        }

        Spacer(modifier = Modifier.height(72.dp))
      } else {
        // ---------------------------------------------------------------------
        // CORE SCREEN 9: DISPUTES & AUDIT SCREEN (ENTERPRISE)
        // Audit screen, white, title "Activity Log"
        // Timeline list, monochrome line icons only:
        // "Payment for Kofi (Week 12) reversed by Ama — reason: wrong member", "PIN failed 3x — 2:14am", "Week 11 closed by Ama"
        // Filter chips as plain outlined pills: Reversals | PIN events | Cycle closes | All
        // Maximum 5 rows + "View all"
        // ---------------------------------------------------------------------
        Spacer(modifier = Modifier.height(8.dp))

        // Filter chips as plain outlined pills: Reversals | PIN events | Cycle closes | All
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          AuditFilter.values().forEach { filter ->
            val isSelected = selectedAuditFilter == filter
            val label = when (filter) {
              AuditFilter.ALL -> "All"
              AuditFilter.REVERSALS -> "Reversals"
              AuditFilter.PIN_EVENTS -> "PIN events"
              AuditFilter.CYCLE_CLOSES -> "Cycle closes"
            }

            Box(
              modifier = Modifier
                .border(
                  width = 1.dp,
                  color = if (isSelected) ForestGreenPrimary else BorderGrey,
                  shape = RoundedCornerShape(16.dp)
                )
                .background(if (isSelected) ForestGreenLightFill else PureWhite, RoundedCornerShape(16.dp))
                .clickable { selectedAuditFilter = filter }
                .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
              Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(
                  color = if (isSelected) ForestGreenPrimary else TextPrimary,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                  fontSize = 11.sp
                )
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Timeline list: max 5 rows + View all
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = PureWhite),
          border = BorderStroke(1.dp, BorderGrey),
          shape = RoundedCornerShape(8.dp)
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            displayAuditLogs.forEachIndexed { index, entry ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 10.dp),
                verticalAlignment = Alignment.Top
              ) {
                // Monochrome line icon
                Icon(
                  imageVector = entry.icon,
                  contentDescription = null,
                  tint = LineIconBlack,
                  modifier = Modifier.size(18.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = entry.description,
                    style = MaterialTheme.typography.bodyMedium.copy(
                      color = TextPrimary,
                      fontWeight = FontWeight.SemiBold
                    )
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Text(entry.actorName, style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp))
                    Text(entry.timestamp, style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp))
                  }
                }
              }

              if (index < displayAuditLogs.lastIndex) {
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(BorderGrey)
                )
              }
            }

            if (filteredAuditLogs.size > 5) {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(1.dp)
                  .background(BorderGrey)
              )
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = "View all (${filteredAuditLogs.size})",
                style = MaterialTheme.typography.labelMedium.copy(
                  color = ForestGreenPrimary,
                  fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier
                  .clickable { showAllAuditLogsModal = true }
                  .padding(vertical = 6.dp)
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(20.dp))
      }
    }
  }

  // Add Second Officer Dialog with phone input
  if (showAddOfficerDialog) {
    AlertDialog(
      onDismissRequest = { showAddOfficerDialog = false },
      title = { Text("Add Second Officer", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
      text = {
        Column {
          Text("Second officers can co-sign disputed ledger reversals and view weekly audits.", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
          Spacer(modifier = Modifier.height(12.dp))
          OutlinedTextField(
            value = secondOfficerPhone,
            onValueChange = { secondOfficerPhone = it },
            placeholder = { Text("+233 24 000 0000") },
            label = { Text("Phone number") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = ForestGreenPrimary,
              unfocusedBorderColor = BorderGrey
            )
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            showAddOfficerDialog = false
            Toast.makeText(context, "Invitation sent to $secondOfficerPhone", Toast.LENGTH_SHORT).show()
          },
          colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary)
        ) {
          Text("Send Invite", color = PureWhite)
        }
      },
      dismissButton = {
        TextButton(onClick = { showAddOfficerDialog = false }) {
          Text("Cancel", color = TextSecondary)
        }
      }
    )
  }

  // Biometric Toggle Verification Dialog (Enforce authentication when toggling on or off)
  if (showBiometricToggleVerificationDialog) {
    AlertDialog(
      onDismissRequest = {
        showBiometricToggleVerificationDialog = false
        biometricVerifyPin = ""
        biometricVerifyError = null
      },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Fingerprint, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(24.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = if (pendingBiometricToggleState) "Verify to Enable Biometrics" else "Verify to Disable Biometrics",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
          )
        }
      },
      text = {
        Column {
          Text(
            text = if (pendingBiometricToggleState)
              "Please authenticate with your 4-digit PIN to enable fingerprint/face biometric unlocking for SusuLedger."
            else
              "For security, please enter your 4-digit PIN to confirm disabling biometric unlock.",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
          )
          Spacer(modifier = Modifier.height(14.dp))

          OutlinedTextField(
            value = biometricVerifyPin,
            onValueChange = {
              if (it.length <= 4 && it.all { ch -> ch.isDigit() }) {
                biometricVerifyPin = it
                biometricVerifyError = null
              }
            },
            placeholder = { Text("••••") },
            label = { Text("4-Digit Security PIN") },
            modifier = Modifier.fillMaxWidth().testTag("biometric_verify_pin_input"),
            singleLine = true,
            isError = biometricVerifyError != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = ForestGreenPrimary,
              unfocusedBorderColor = BorderGrey
            )
          )

          if (biometricVerifyError != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = biometricVerifyError ?: "",
              color = ErrorRed,
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold
            )
          }

          Spacer(modifier = Modifier.height(12.dp))

          // Quick Fingerprint sensor simulation tap
          Surface(
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                biometricEnabled = pendingBiometricToggleState
                showBiometricToggleVerificationDialog = false
                biometricVerifyPin = ""
                biometricVerifyError = null
                Toast.makeText(
                  context,
                  if (pendingBiometricToggleState) "Biometric unlock verified & enabled" else "Biometric unlock verified & disabled",
                  Toast.LENGTH_SHORT
                ).show()
              },
            shape = RoundedCornerShape(8.dp),
            color = ForestGreenLightFill,
            border = BorderStroke(1.dp, ForestGreenPrimary.copy(alpha = 0.4f))
          ) {
            Row(
              modifier = Modifier.padding(10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Center
            ) {
              Icon(Icons.Default.Fingerprint, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(20.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Touch Sensor to Verify Instantly", color = ForestGreenPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (biometricVerifyPin.length == 4) {
              biometricEnabled = pendingBiometricToggleState
              showBiometricToggleVerificationDialog = false
              biometricVerifyPin = ""
              biometricVerifyError = null
              Toast.makeText(
                context,
                if (pendingBiometricToggleState) "Biometric unlock enabled" else "Biometric unlock disabled",
                Toast.LENGTH_SHORT
              ).show()
            } else {
              biometricVerifyError = "Please enter your full 4-digit PIN."
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary)
        ) {
          Text("Verify & Confirm", color = PureWhite)
        }
      },
      dismissButton = {
        TextButton(onClick = {
          showBiometricToggleVerificationDialog = false
          biometricVerifyPin = ""
          biometricVerifyError = null
        }) {
          Text("Cancel", color = TextSecondary)
        }
      }
    )
  }

  // Change PIN dialog
  if (showChangePinDialog) {
    var newPin by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { showChangePinDialog = false },
      title = { Text("Change 4-Digit PIN", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
      text = {
        Column {
          Text("Enter your new 4-digit PIN for sensitive authorizations:", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
          Spacer(modifier = Modifier.height(12.dp))
          OutlinedTextField(
            value = newPin,
            onValueChange = { if (it.length <= 4 && it.all { ch -> ch.isDigit() }) newPin = it },
            label = { Text("New PIN") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = ForestGreenPrimary,
              unfocusedBorderColor = BorderGrey
            )
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            showChangePinDialog = false
            Toast.makeText(context, "PIN updated successfully", Toast.LENGTH_SHORT).show()
          },
          colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary)
        ) {
          Text("Save PIN", color = PureWhite)
        }
      },
      dismissButton = {
        TextButton(onClick = { showChangePinDialog = false }) {
          Text("Cancel", color = TextSecondary)
        }
      }
    )
  }

  // Rename Group dialog
  if (showRenameGroupDialog) {
    var tempName by remember { mutableStateOf(currentGroupName) }
    AlertDialog(
      onDismissRequest = { showRenameGroupDialog = false },
      title = { Text("Rename Group", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
      text = {
        OutlinedTextField(
          value = tempName,
          onValueChange = { tempName = it },
          label = { Text("Group name") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ForestGreenPrimary,
            unfocusedBorderColor = BorderGrey
          )
        )
      },
      confirmButton = {
        Button(
          onClick = {
            currentGroupName = tempName
            showRenameGroupDialog = false
          },
          colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary)
        ) {
          Text("Save", color = PureWhite)
        }
      },
      dismissButton = {
        TextButton(onClick = { showRenameGroupDialog = false }) {
          Text("Cancel", color = TextSecondary)
        }
      }
    )
  }

  // View All Audit Logs Modal
  if (showAllAuditLogsModal) {
    AlertDialog(
      onDismissRequest = { showAllAuditLogsModal = false },
      title = { Text("Complete Activity Log (${filteredAuditLogs.size})", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
      text = {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          filteredAuditLogs.forEach { entry ->
            Column {
              Text(entry.description, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = TextPrimary))
              Text("${entry.actorName} • ${entry.timestamp}", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 10.sp))
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showAllAuditLogsModal = false }) {
          Text("Close", color = ForestGreenPrimary)
        }
      }
    )
  }

  // Paystack MoMo Subscription Dialog
  if (showPaystackModal) {
    var selectedNetwork by remember { mutableStateOf("MTN") }
    var billingPhone by remember { mutableStateOf("024 123 4567") }
    var isSimulatingUssd by remember { mutableStateOf(false) }

    AlertDialog(
      onDismissRequest = { showPaystackModal = false },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Payment, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(22.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Paystack MoMo Billing", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }
      },
      text = {
        Column(modifier = Modifier.fillMaxWidth()) {
          Text(
            text = "Group Subscription: GHS 40.00 / month",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
          )
          Text(
            text = "Automated WhatsApp receipts, weekly digest broadcasting, and tamper-evident ledger archiving.",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
          )

          Spacer(modifier = Modifier.height(14.dp))
          Text("SELECT NETWORK", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold))
          Spacer(modifier = Modifier.height(6.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            listOf("MTN", "Telecel", "AT").forEach { net ->
              val isSelected = selectedNetwork == net
              Box(
                modifier = Modifier
                  .weight(1f)
                  .border(
                    width = 1.dp,
                    color = if (isSelected) ForestGreenPrimary else BorderGrey,
                    shape = RoundedCornerShape(8.dp)
                  )
                  .background(if (isSelected) ForestGreenLightFill else PureWhite, RoundedCornerShape(8.dp))
                  .clickable { selectedNetwork = net }
                  .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = net,
                  fontSize = 12.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                  color = if (isSelected) ForestGreenPrimary else TextPrimary
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(14.dp))
          OutlinedTextField(
            value = billingPhone,
            onValueChange = { billingPhone = it },
            label = { Text("Mobile Money Phone") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = ForestGreenPrimary,
              unfocusedBorderColor = BorderGrey
            )
          )

          if (isSimulatingUssd) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
              modifier = Modifier.fillMaxWidth(),
              colors = CardDefaults.cardColors(containerColor = ForestGreenLightFill),
              border = BorderStroke(1.dp, ForestGreenPrimary)
            ) {
              Column(modifier = Modifier.padding(10.dp)) {
                Text(
                  "Prompt dispatched to $billingPhone",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  color = ForestGreenPrimary
                )
                Text(
                  "Please approve the $selectedNetwork MoMo authorization prompt on your handset (dial *170# if prompt doesn't appear).",
                  fontSize = 11.sp,
                  color = TextPrimary
                )
              }
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (!isSimulatingUssd) {
              isSimulatingUssd = true
            } else {
              isSubscriptionActive = true
              showPaystackModal = false
              Toast.makeText(context, "Subscription active! Paid GHS 40.00 via $selectedNetwork MoMo", Toast.LENGTH_LONG).show()
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary)
        ) {
          Text(if (isSimulatingUssd) "Confirm Authorization" else "Authorize GHS 40.00", color = PureWhite)
        }
      },
      dismissButton = {
        TextButton(onClick = { showPaystackModal = false }) {
          Text("Cancel", color = TextSecondary)
        }
      }
    )
  }

  // Terms of Service & Non-Custodial Shield Modal
  if (showLegalTermsModal) {
    AlertDialog(
      onDismissRequest = { showLegalTermsModal = false },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Security, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(22.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Statutory Legal Notice", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }
      },
      text = {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Text(
            text = "1. Non-Custodial Architecture",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
          )
          Text(
            text = "SusuLedger functions strictly as an independent digital record-keeping information layer. SusuLedger does not hold, receive, transmit, custody, or convert funds. All financial exchanges occur peer-to-peer directly between members and treasurers through licensed third-party Payment Service Providers (MTN MoMo, Telecel Cash, AT Money, or Cash).",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
          )

          Text(
            text = "2. Immutable Evidentiary Record",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
          )
          Text(
            text = "All journal entries are recorded in a tamper-evident append-only sequence anchoring each payment to preceding transactions. These digital records serve as mutual evidence among group members in the event of dispute.",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
          )

          Text(
            text = "3. Lapsed Subscription Record Guarantee",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
          )
          Text(
            text = "Financial records belong exclusively to group members. In the event a group subscription is not renewed, historical books of account remain permanently preserved, readable, and exportable.",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
          )
        }
      },
      confirmButton = {
        Button(
          onClick = { showLegalTermsModal = false },
          colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary)
        ) {
          Text("I Understand", color = PureWhite)
        }
      }
    )
  }

  // Ghana DPC Privacy Modal (Act 843)
  if (showDpcPrivacyModal) {
    AlertDialog(
      onDismissRequest = { showDpcPrivacyModal = false },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Check, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(22.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Ghana DPC Act 843 Compliance", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }
      },
      text = {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Text(
            text = "Data Protection Commission Registration",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
          )
          Text(
            text = "SusuLedger complies with the Ghana Data Protection Act, 2012 (Act 843). Telephone numbers and identity records are collected solely for authenticating Susu group membership and dispatching WhatsApp ledger receipts.",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
          )

          Text(
            text = "Consent & Opt-In Protocol",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
          )
          Text(
            text = "Members grant explicit consent by replying 'OK' or entering their group pairing code. Timestamped audit trails of consent are immutably logged. Members may revoke message notifications at any time by texting 'STOP'.",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
          )
        }
      },
      confirmButton = {
        Button(
          onClick = { showDpcPrivacyModal = false },
          colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary)
        ) {
          Text("Close", color = PureWhite)
        }
      }
    )
  }

  // ---------------------------------------------------------------------------
  // RENAME GROUP DIALOG
  // ---------------------------------------------------------------------------
  if (showRenameGroupDialog) {
    var editName by remember { mutableStateOf(currentGroupName) }
    AlertDialog(
      onDismissRequest = { showRenameGroupDialog = false },
      title = {
        Text("Rename Susu Group", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = editName,
            onValueChange = { editName = it },
            label = { Text("Group Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = ForestGreenPrimary,
              unfocusedBorderColor = BorderGrey
            ),
            shape = RoundedCornerShape(8.dp)
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (editName.isNotBlank()) {
              currentGroupName = editName.trim()
              onRenameGroup(editName.trim())
              showRenameGroupDialog = false
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary)
        ) {
          Text("Save Name", color = PureWhite)
        }
      },
      dismissButton = {
        OutlinedButton(
          onClick = { showRenameGroupDialog = false },
          border = BorderStroke(1.dp, BorderGrey)
        ) {
          Text("Cancel", color = TextPrimary)
        }
      }
    )
  }

  // ---------------------------------------------------------------------------
  // PAUSE GROUP DIALOG
  // ---------------------------------------------------------------------------
  if (showPauseGroupDialog) {
    AlertDialog(
      onDismissRequest = { showPauseGroupDialog = false },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(24.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Pause Group Collections?", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }
      },
      text = {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Text(
            "Pausing will temporarily freeze weekly collection deadlines and automatic reminders. What happens when paused:",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
          )

          Surface(
            color = Color(0xFFFEF3C7),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFFF59E0B))
          ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text("• Funds Protection: All past records remain sealed and verified.", fontSize = 11.sp, color = Color(0xFF92400E))
              Text("• Automated Notice: A WhatsApp broadcast is sent immediately to all members explaining the pause.", fontSize = 11.sp, color = Color(0xFF92400E))
              Text("• Easy Resume: Collections can be resumed anytime with a single tap.", fontSize = 11.sp, color = Color(0xFF92400E))
            }
          }

          OutlinedTextField(
            value = pauseReason,
            onValueChange = { pauseReason = it },
            label = { Text("Reason for pausing") },
            placeholder = { Text("e.g. Market holiday break, Officer transition") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = ForestGreenPrimary,
              unfocusedBorderColor = BorderGrey
            ),
            shape = RoundedCornerShape(8.dp)
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            showPauseGroupDialog = false
            onTogglePauseGroup(pauseReason)
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
        ) {
          Text("Pause Group & Notify Members", color = PureWhite)
        }
      },
      dismissButton = {
        OutlinedButton(
          onClick = { showPauseGroupDialog = false },
          border = BorderStroke(1.dp, BorderGrey)
        ) {
          Text("Cancel", color = TextPrimary)
        }
      }
    )
  }

  // ---------------------------------------------------------------------------
  // RESUME GROUP DIALOG
  // ---------------------------------------------------------------------------
  if (showResumeGroupDialog) {
    AlertDialog(
      onDismissRequest = { showResumeGroupDialog = false },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Refresh, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(24.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Resume Group Collections?", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            "Resuming will reactivate the group schedule. An automated WhatsApp announcement will be broadcast to all members notifying them that weekly contributions are open again.",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
          )
          Surface(
            color = ForestGreenLightFill,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, ForestGreenPrimary.copy(alpha = 0.3f))
          ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text("• Current Dues: GHS ${String.format(java.util.Locale.US, "%.2f", currentGroup?.amount ?: 50.0)}", fontSize = 11.sp, color = ForestGreenPrimary, fontWeight = FontWeight.SemiBold)
              Text("• Micro-contributions ('small-small' payments) accepted", fontSize = 11.sp, color = TextPrimary)
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            showResumeGroupDialog = false
            onTogglePauseGroup("Collections resumed by officer")
          },
          colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary)
        ) {
          Text("Resume Group & Broadcast", color = PureWhite)
        }
      },
      dismissButton = {
        OutlinedButton(
          onClick = { showResumeGroupDialog = false },
          border = BorderStroke(1.dp, BorderGrey)
        ) {
          Text("Cancel", color = TextPrimary)
        }
      }
    )
  }

  // ---------------------------------------------------------------------------
  // CHANGE CONTRIBUTION DUES DIALOG (WITH DUAL-OFFICER / AUDIT PROTECTION)
  // ---------------------------------------------------------------------------
  if (showChangeAmountDialog) {
    AlertDialog(
      onDismissRequest = { showChangeAmountDialog = false },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Payment, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(24.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Adjust Contribution Dues", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }
      },
      text = {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Text(
            "Changing the contribution rate requires full transparency. Every change is logged to the immutable audit trail and broadcast to all members on WhatsApp.",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
          )

          OutlinedTextField(
            value = newDuesAmountText,
            onValueChange = { newDuesAmountText = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text("New Contribution Dues (GHS)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = ForestGreenPrimary,
              unfocusedBorderColor = BorderGrey
            ),
            shape = RoundedCornerShape(8.dp)
          )

          // Quick rate presets
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            listOf("20", "50", "100", "200").forEach { amount ->
              val isSelected = newDuesAmountText == amount
              Surface(
                modifier = Modifier
                  .weight(1f)
                  .clickable { newDuesAmountText = amount },
                shape = RoundedCornerShape(6.dp),
                color = if (isSelected) ForestGreenLightFill else Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, if (isSelected) ForestGreenPrimary else BorderGrey)
              ) {
                Box(modifier = Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                  Text("GHS $amount", fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) ForestGreenPrimary else TextPrimary)
                }
              }
            }
          }

          OutlinedTextField(
            value = duesChangeReason,
            onValueChange = { duesChangeReason = it },
            label = { Text("Reason for dues change") },
            placeholder = { Text("e.g. General assembly majority vote") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = ForestGreenPrimary,
              unfocusedBorderColor = BorderGrey
            ),
            shape = RoundedCornerShape(8.dp)
          )

          // Checkbox for applying to current cycle
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { applyToCurrentCycle = !applyToCurrentCycle },
            verticalAlignment = Alignment.CenterVertically
          ) {
            Checkbox(
              checked = applyToCurrentCycle,
              onCheckedChange = { applyToCurrentCycle = it },
              colors = CheckboxDefaults.colors(checkedColor = ForestGreenPrimary)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              "Apply to current active cycle (${activeCycle?.number?.let { "Week $it" } ?: "Current"})",
              fontSize = 12.sp,
              color = TextPrimary
            )
          }

          Surface(
            color = Color(0xFFF1F5F9),
            shape = RoundedCornerShape(6.dp)
          ) {
            Text(
              "Anti-Tampering Guarantee: Past completed cycle payments remain locked and unchanged.",
              modifier = Modifier.padding(8.dp),
              fontSize = 10.sp,
              color = TextSecondary
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val amountVal = newDuesAmountText.toDoubleOrNull()
            if (amountVal != null && amountVal > 0) {
              showChangeAmountDialog = false
              onUpdateContributionAmount(amountVal, applyToCurrentCycle, duesChangeReason)
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary)
        ) {
          Text("Save & Broadcast to Members", color = PureWhite)
        }
      },
      dismissButton = {
        OutlinedButton(
          onClick = { showChangeAmountDialog = false },
          border = BorderStroke(1.dp, BorderGrey)
        ) {
          Text("Cancel", color = TextPrimary)
        }
      }
    )
  }

  // ---------------------------------------------------------------------------
  // SIGN OUT CONFIRMATION DIALOG
  // ---------------------------------------------------------------------------
  if (showSignOutDialog) {
    AlertDialog(
      onDismissRequest = { showSignOutDialog = false },
      title = {
        Text("Sign Out?", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
      },
      text = {
        Text(
          "Are you sure you want to sign out of this device? You can log back in anytime using your verified phone number and 4-digit PIN.",
          style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
        )
      },
      confirmButton = {
        Button(
          onClick = {
            showSignOutDialog = false
            onSignOut()
          },
          colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary),
          modifier = Modifier.testTag("confirm_sign_out_btn")
        ) {
          Text("Sign Out", color = PureWhite)
        }
      },
      dismissButton = {
        OutlinedButton(
          onClick = { showSignOutDialog = false },
          border = BorderStroke(1.dp, BorderGrey)
        ) {
          Text("Cancel", color = TextPrimary)
        }
      }
    )
  }

  // ---------------------------------------------------------------------------
  // DELETE ACCOUNT CONFIRMATION DIALOG
  // ---------------------------------------------------------------------------
  if (showDeleteAccountDialog) {
    AlertDialog(
      onDismissRequest = { showDeleteAccountDialog = false },
      title = {
        Text("Delete Account & Purge Data?", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F)))
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            "This action cannot be undone. All local ledger records, identities, salted PIN hashes, and group state will be completely purged from this device.",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
          )
          Text(
            "Pursuant to Ghana Data Protection Act 843, your personal data will be completely deleted.",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            showDeleteAccountDialog = false
            onDeleteAccount()
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
          modifier = Modifier.testTag("confirm_delete_account_btn")
        ) {
          Text("Delete My Account", color = PureWhite)
        }
      },
      dismissButton = {
        OutlinedButton(
          onClick = { showDeleteAccountDialog = false },
          border = BorderStroke(1.dp, BorderGrey)
        ) {
          Text("Cancel", color = TextPrimary)
        }
      }
    )
  }
}

@Composable
private fun SettingsRow(
  icon: ImageVector,
  title: String,
  subtitle: String,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(icon, contentDescription = null, tint = LineIconBlack, modifier = Modifier.size(20.dp))
      Spacer(modifier = Modifier.width(12.dp))
      Column {
        Text(title, style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary, fontWeight = FontWeight.SemiBold))
        Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
      }
    }

    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = LineIconGrey, modifier = Modifier.size(18.dp))
  }
}

@Composable
private fun DividerLine() {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(1.dp)
      .background(BorderGrey)
  )
}
