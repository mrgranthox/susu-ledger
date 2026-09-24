package com.example.ui.screens

import com.example.ui.theme.DangerRed
import com.example.ui.theme.NeutralSurfaceLight
import com.example.ui.theme.NeutralSurfaceMedium
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberBg
import com.example.ui.theme.WarningAmberBorder
import com.example.ui.theme.WarningAmberText


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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.example.ui.theme.BorderGrey
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.ForestGreenLightFill
import com.example.ui.theme.ForestGreenPrimary
import com.example.ui.theme.InputBorderUnfocused
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreSettingsScreen(
  groupName: String = "Susu Group",
  currentGroup: com.example.data.local.GroupEntity? = null,
  activeCycle: com.example.data.local.CycleEntity? = null,
  isBiometricEnabled: Boolean = true,
  onUpdateBiometricEnabled: (Boolean) -> Unit = {},
  onChangePin: (newPin: String, onResult: (Boolean, String) -> Unit) -> Unit = { _, res -> res(true, "PIN updated") },
  onTogglePauseGroup: (reason: String) -> Unit = {},
  onUpdateContributionAmount: (newAmount: Double, applyToCurrentCycle: Boolean, reason: String) -> Unit = { _, _, _ -> },
  onRenameGroup: (newName: String) -> Unit = {},
  onOpenPairingSheet: () -> Unit = {},
  isBotConnected: Boolean = false,
  onOpenWhatsAppSimulator: () -> Unit = {},
  onSignOut: () -> Unit = {},
  onDeleteAccount: () -> Unit = {},
  onLockApp: () -> Unit = {},
  onBackClick: (() -> Unit)? = null,
  auditLogEntities: List<com.example.data.local.AuditLogEntity> = emptyList()
) {
  val context = LocalContext.current
  var activeSection by remember { mutableStateOf<String>("settings") } // "settings" or "activity_log"

  // Settings State
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
  androidx.compose.runtime.LaunchedEffect(currentGroup?.name) {
    currentGroup?.name?.let { currentGroupName = it }
  }
  var showPaystackModal by remember { mutableStateOf(false) }
  var showLegalTermsModal by remember { mutableStateOf(false) }
  var showDpcPrivacyModal by remember { mutableStateOf(false) }
  var isSubscriptionActive by remember { mutableStateOf(false) }
  var showSignOutDialog by remember { mutableStateOf(false) }
  var showDeleteAccountDialog by remember { mutableStateOf(false) }

  // Activity Log State (Core Screen 9)
  var selectedAuditFilter by remember { mutableStateOf(AuditFilter.ALL) }
  var showAllAuditLogsModal by remember { mutableStateOf(false) }

  val timeFormatter = remember { java.text.SimpleDateFormat("dd MMM, h:mm a", java.util.Locale.getDefault()) }

  val auditLogs = remember(auditLogEntities) {
    auditLogEntities.map { entity ->
      val (category, icon, defaultDesc) = when (entity.action) {
        "PIN_UPDATED" -> Triple(AuditFilter.PIN_EVENTS, Icons.Default.Key, "Officer security PIN changed")
        "PIN_RESET" -> Triple(AuditFilter.PIN_EVENTS, Icons.Default.Key, "Officer security PIN reset")
        "PIN_FAILED" -> Triple(AuditFilter.PIN_EVENTS, Icons.Default.Warning, "Failed security PIN attempt")
        "PIN_AUTHENTICATED" -> Triple(AuditFilter.PIN_EVENTS, Icons.Default.Security, "Officer authenticated with PIN")
        "BIOMETRIC_TOGGLED" -> {
          val isEn = entity.payload?.contains("true") == true
          Triple(AuditFilter.PIN_EVENTS, Icons.Default.Fingerprint, if (isEn) "Biometric unlock enabled" else "Biometric unlock disabled")
        }
        "CYCLE_CLOSED" -> Triple(AuditFilter.CYCLE_CLOSES, Icons.Default.Check, "Weekly cycle closed & next cycle opened")
        "WEEK_OPENED" -> Triple(AuditFilter.CYCLE_CLOSES, Icons.Default.Check, "New weekly cycle opened")
        "PAYMENT_REVERSED" -> Triple(AuditFilter.REVERSALS, Icons.Default.History, "Payment reversed by officer")
        "CORRECTION" -> Triple(AuditFilter.REVERSALS, Icons.Default.History, "Ledger correction recorded")
        "GROUP_RENAMED" -> {
          val nameDesc = try {
            val idx = entity.payload?.indexOf("\"newName\":\"") ?: -1
            if (idx != -1) {
              val end = entity.payload?.indexOf("\"", idx + 11) ?: -1
              if (end != -1) "Group renamed to '${entity.payload?.substring(idx + 11, end)}'" else "Group renamed"
            } else "Group renamed"
          } catch (e: Exception) { "Group renamed" }
          Triple(AuditFilter.ALL, Icons.Default.Group, nameDesc)
        }
        "GROUP_PAUSED" -> Triple(AuditFilter.ALL, Icons.Default.Warning, "Group collections paused")
        "GROUP_RESUMED" -> Triple(AuditFilter.ALL, Icons.Default.Refresh, "Group collections resumed")
        "DUES_AMOUNT_UPDATED" -> Triple(AuditFilter.ALL, Icons.Default.Payment, "Weekly dues amount updated")
        else -> Triple(
          AuditFilter.ALL,
          Icons.Default.Security,
          entity.action.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
        )
      }

      val formattedTime = try {
        timeFormatter.format(java.util.Date(entity.createdAt))
      } catch (e: Exception) {
        "Recent"
      }

      AuditLogEntry(
        icon = icon,
        description = defaultDesc,
        timestamp = formattedTime,
        actorName = if (!entity.actorId.isNullOrBlank()) "Officer" else "System",
        category = category
      )
    }
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
          Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text(if (isBotConnected) "Reconnect WhatsApp" else "Connect WhatsApp", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ForestGreenPrimary)
        }

        Spacer(modifier = Modifier.height(18.dp))

        // SECTION: SECURITY
        Text("SECURITY & ACCESS", style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp))
        Spacer(modifier = Modifier.height(8.dp))

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
            .clickable { onUpdateBiometricEnabled(!isBiometricEnabled) }
            .padding(vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Fingerprint, contentDescription = null, tint = LineIconBlack, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text("Biometric unlock", style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary, fontWeight = FontWeight.SemiBold))
              Text("Fingerprint / Face authorization for sensitive operations", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
            }
          }

          Switch(
            checked = isBiometricEnabled,
            onCheckedChange = { targetVal -> onUpdateBiometricEnabled(targetVal) },
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
          border = BorderStroke(1.5.dp, if (isSubscriptionActive) ForestGreenPrimary else WarningAmber.copy(alpha = 0.4f)),
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
                    .background(WarningAmberBg, CircleShape),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = WarningAmber,
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
                color = if (isSubscriptionActive) ForestGreenLightFill else WarningAmberBg,
                shape = RoundedCornerShape(6.dp)
              ) {
                Text(
                  text = if (isSubscriptionActive) "ACTIVE" else "12 DAYS TRIAL",
                  style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSubscriptionActive) ForestGreenPrimary else WarningAmberText
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
                .background(NeutralSurfaceLight, RoundedCornerShape(8.dp))
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
                  .background(NeutralSurfaceMedium, CircleShape),
                contentAlignment = Alignment.Center
              ) {
                Icon(Icons.Default.Group, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(20.dp))
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text("Treasurer Account", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                Text("Active Session • Primary Officer", fontSize = 12.sp, color = TextSecondary)
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
              colors = ButtonDefaults.buttonColors(containerColor = NeutralSurfaceMedium),
              shape = RoundedCornerShape(8.dp)
            ) {
              Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign Out", tint = DangerRed, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Sign Out of Session", color = DangerRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
            if (filteredAuditLogs.isEmpty()) {
              Text(
                text = "No activity recorded yet. Reversals, PIN events, and week closures will appear here.",
                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
              )
            }
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

  // Change PIN dialog (Direct Room & Session persistence with confirmation & visibility toggles)
  if (showChangePinDialog) {
    var newPin by remember { mutableStateOf("") }
    var confirmNewPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var isPinVisible by remember { mutableStateOf(false) }

    AlertDialog(
      modifier = Modifier.imePadding(),
      onDismissRequest = {
        showChangePinDialog = false
        pinError = null
      },
      title = { Text("Change 4-Digit PIN", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
      text = {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
          Text("Enter your new 4-digit PIN for sensitive ledger operations:", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
          Spacer(modifier = Modifier.height(12.dp))
          OutlinedTextField(
            value = newPin,
            onValueChange = {
              if (it.length <= 4 && it.all { ch -> ch.isDigit() }) {
                newPin = it
                pinError = null
              }
            },
            label = { Text("New 4-Digit PIN") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
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
            },
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = ForestGreenPrimary,
              unfocusedBorderColor = InputBorderUnfocused
            )
          )
          Spacer(modifier = Modifier.height(10.dp))
          OutlinedTextField(
            value = confirmNewPin,
            onValueChange = {
              if (it.length <= 4 && it.all { ch -> ch.isDigit() }) {
                confirmNewPin = it
                pinError = null
              }
            },
            label = { Text("Confirm New PIN") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (isPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = ForestGreenPrimary,
              unfocusedBorderColor = InputBorderUnfocused
            )
          )
          if (pinError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(pinError ?: "", color = ErrorRed, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (newPin.length != 4) {
              pinError = "PIN must be exactly 4 digits."
              return@Button
            }
            if (newPin != confirmNewPin) {
              pinError = "PINs do not match. Please verify."
              return@Button
            }
            onChangePin(newPin) { success, message ->
              if (success) {
                showChangePinDialog = false
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
              } else {
                pinError = message
              }
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary, contentColor = PureWhite)
        ) {
          Text("Save PIN", color = PureWhite, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = {
          showChangePinDialog = false
          pinError = null
        }) {
          Text("Cancel", color = TextSecondary)
        }
      }
    )
  }

  // View All Audit Logs Bottom Sheet
  if (showAllAuditLogsModal) {
    val allAuditSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
      onDismissRequest = { showAllAuditLogsModal = false },
      sheetState = allAuditSheetState,
      containerColor = PureWhite
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp)
          .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Text(
          text = "Complete Activity Log (${filteredAuditLogs.size})",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        filteredAuditLogs.forEach { entry ->
          Column {
            Text(entry.description, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = TextPrimary))
            Text("${entry.actorName} • ${entry.timestamp}", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 10.sp))
          }
        }
        TextButton(
          onClick = { showAllAuditLogsModal = false },
          modifier = Modifier.align(Alignment.End)
        ) {
          Text("Close", color = ForestGreenPrimary)
        }
      }
    }
  }

  // Paystack MoMo Subscription Dialog
  if (showPaystackModal) {
    var selectedNetwork by remember { mutableStateOf("MTN") }
    var billingPhone by remember { mutableStateOf("") }
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
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Sandbox preview — no real charges are made.",
            fontSize = 11.sp,
            color = TextSecondary
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
              Toast.makeText(context, "Sandbox: subscription activated (no real charge).", Toast.LENGTH_LONG).show()
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
    var editName by remember(showRenameGroupDialog) { mutableStateOf(currentGroupName) }
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
          Icon(Icons.Default.Warning, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(24.dp))
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
            color = WarningAmberBg,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, WarningAmberBorder)
          ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text("• Funds Protection: All past records remain sealed and verified.", fontSize = 11.sp, color = WarningAmberText)
              Text("• Automated Notice: A WhatsApp broadcast is sent immediately to all members explaining the pause.", fontSize = 11.sp, color = WarningAmberText)
              Text("• Easy Resume: Collections can be resumed anytime with a single tap.", fontSize = 11.sp, color = WarningAmberText)
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
          colors = ButtonDefaults.buttonColors(containerColor = WarningAmber)
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
                color = if (isSelected) ForestGreenLightFill else NeutralSurfaceLight,
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
            color = NeutralSurfaceMedium,
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
        Text("Delete Account & Purge Data?", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = ErrorRed))
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
          colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
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
