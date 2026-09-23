package com.example.ui.screens

import com.example.ui.theme.ErrorBgLight
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.NeutralSurfaceMuted
import com.example.ui.theme.NeutralTrack
import com.example.ui.theme.OffWhiteSurface
import com.example.ui.theme.SoftGreenTint
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberBg
import com.example.ui.theme.WarningAmberBorder
import com.example.ui.theme.WarningAmberText
import com.example.ui.theme.WarningTextDeep


import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ClaimEntity
import com.example.data.local.CycleEntity
import com.example.data.local.GroupEntity
import com.example.data.local.PaymentEntity
import com.example.ui.DashboardStats
import com.example.ui.theme.BorderGrey
import com.example.ui.theme.ForestGreenLightFill
import com.example.ui.theme.ForestGreenPrimary
import com.example.ui.theme.HeroAmber
import com.example.ui.theme.HeroAmberBg
import com.example.ui.theme.HeroAmberBorder
import com.example.ui.theme.HeroAmberText
import com.example.ui.theme.LineIconBlack
import com.example.ui.theme.LineIconGreen
import com.example.ui.theme.LineIconGrey
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.CryptoUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
  groups: List<GroupEntity>,
  selectedGroupId: String,
  activeCycle: CycleEntity?,
  stats: DashboardStats,
  pendingClaims: List<ClaimEntity>,
  recentPayments: List<PaymentEntity> = emptyList(),
  unsyncedCount: Int = 0,
  isSyncing: Boolean = false,
  botPairingCode: String = "",
  isBotConnected: Boolean = false,
  onSyncClick: () -> Unit = {},
  onSelectGroup: (String) -> Unit,
  onConfirmClaim: (ClaimEntity) -> Unit,
  onRejectClaim: (ClaimEntity) -> Unit,
  onNewWeekClick: () -> Unit,
  onDirectPaymentClick: () -> Unit,
  onViewAllPendingClick: () -> Unit,
  onOpenCycleDetail: () -> Unit = {},
  onOpenLedger: () -> Unit = {},
  onOpenWhatsAppSimulator: () -> Unit = {},
  onLockApp: () -> Unit = {},
  onSignOut: () -> Unit = {}
) {
  var groupDropdownOpen by remember { mutableStateOf(false) }
  var topMenuOpen by remember { mutableStateOf(false) }
  var showAllClaimsModal by remember { mutableStateOf(false) }
  var selectedPaymentForDetail by remember { mutableStateOf<PaymentEntity?>(null) }
  var transactionFilter by remember { mutableStateOf("ALL") } // "ALL", "MOMO", "CASH", "AGENT"

  val currentGroup = groups.find { it.id == selectedGroupId } ?: groups.firstOrNull()
  val displayClaims = pendingClaims.take(3)

  // Total Savings Balance across all confirmed payments from Room Database
  val totalSavingsBalance = remember(recentPayments) {
    recentPayments.filter { it.status == "confirmed" }.sumOf { it.amountPaid }
  }

  // Method breakdown
  val momoTotal = remember(recentPayments) {
    recentPayments.filter { it.status == "confirmed" && it.method.equals("MOMO", ignoreCase = true) }.sumOf { it.amountPaid }
  }
  val cashTotal = remember(recentPayments) {
    recentPayments.filter { it.status == "confirmed" && it.method.equals("CASH", ignoreCase = true) }.sumOf { it.amountPaid }
  }
  val agentTotal = remember(recentPayments) {
    recentPayments.filter { it.status == "confirmed" && it.method.equals("AGENT", ignoreCase = true) }.sumOf { it.amountPaid }
  }

  // Filtered recent transactions list
  val filteredTransactions = remember(recentPayments, transactionFilter) {
    val sorted = recentPayments.sortedByDescending { it.confirmedAt }
    when (transactionFilter) {
      "MOMO" -> sorted.filter { it.method.equals("MOMO", ignoreCase = true) }
      "CASH" -> sorted.filter { it.method.equals("CASH", ignoreCase = true) }
      "AGENT" -> sorted.filter { it.method.equals("AGENT", ignoreCase = true) }
      else -> sorted
    }
  }

  Scaffold(
    containerColor = PureWhite,
    topBar = {
      // Top Bar: Group Name with dropdown arrow + notification bell
      Surface(
        color = PureWhite,
        modifier = Modifier
          .fillMaxWidth()
          .statusBarsPadding()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Group Switcher
          Box {
            Row(
              modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { groupDropdownOpen = true }
                .padding(vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = currentGroup?.name ?: "Susu Group",
                style = MaterialTheme.typography.titleLarge.copy(
                  fontWeight = FontWeight.Bold,
                  color = TextPrimary
                )
              )
              Spacer(modifier = Modifier.width(4.dp))
              Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Switch Group",
                tint = LineIconBlack,
                modifier = Modifier.size(22.dp)
              )
            }

            DropdownMenu(
              expanded = groupDropdownOpen,
              onDismissRequest = { groupDropdownOpen = false },
              modifier = Modifier.background(PureWhite)
            ) {
              groups.forEach { g ->
                DropdownMenuItem(
                  text = {
                    Column {
                      Text(text = g.name, fontWeight = FontWeight.Bold, color = TextPrimary)
                      Text(
                        text = "GHS ${String.format(Locale.US, "%.2f", g.amount)} • ${g.schedule}",
                        fontSize = 12.sp,
                        color = TextSecondary
                      )
                    }
                  },
                  onClick = {
                    onSelectGroup(g.id)
                    groupDropdownOpen = false
                  }
                )
              }
            }
          }

          // Actions: Lock App, WhatsApp Bot, Pairing Notifications, and Quick Menu
          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onSyncClick, modifier = Modifier.testTag("dashboard_refresh_btn")) {
              Icon(Icons.Default.Refresh, contentDescription = "Refresh ledger", tint = ForestGreenPrimary)
            }
            IconButton(
              onClick = onLockApp,
              modifier = Modifier.testTag("dashboard_lock_app_btn")
            ) {
              Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Lock App (PIN / Biometrics)",
                tint = ForestGreenPrimary,
                modifier = Modifier.size(20.dp)
              )
            }

            IconButton(
              onClick = onOpenWhatsAppSimulator,
              modifier = Modifier.testTag("dashboard_bot_btn")
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.Chat,
                contentDescription = "WhatsApp Bot",
                tint = ForestGreenPrimary,
                modifier = Modifier.size(20.dp)
              )
            }

            Box {
              IconButton(
                onClick = { topMenuOpen = true },
                modifier = Modifier.testTag("dashboard_top_menu_btn")
              ) {
                Icon(
                  imageVector = Icons.Default.MoreVert,
                  contentDescription = "More Options",
                  tint = LineIconBlack,
                  modifier = Modifier.size(20.dp)
                )
              }

              DropdownMenu(
                expanded = topMenuOpen,
                onDismissRequest = { topMenuOpen = false },
                modifier = Modifier.background(PureWhite)
              ) {
                DropdownMenuItem(
                  leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(18.dp)) },
                  text = { Text("Sync Cloud Ledger", color = TextPrimary) },
                  onClick = {
                    topMenuOpen = false
                    onSyncClick()
                  }
                )
                DropdownMenuItem(
                  leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(18.dp)) },
                  text = { Text("Sign Out", color = ErrorRed, fontWeight = FontWeight.Bold) },
                  onClick = {
                    topMenuOpen = false
                    onSignOut()
                  }
                )
              }
            }
          }
        }
      }
    },
    floatingActionButton = {
      // Floating green button "＋ New Week"
      FloatingActionButton(
        onClick = onNewWeekClick,
        containerColor = ForestGreenPrimary,
        contentColor = PureWhite,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.testTag("new_week_fab")
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = PureWhite)
          Spacer(modifier = Modifier.width(6.dp))
          Text(text = "New Week", style = MaterialTheme.typography.labelLarge.copy(color = PureWhite))
        }
      }
    }
  ) { paddingValues ->
    val dashboardScrollState = rememberScrollState()
    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(PureWhite)
        .padding(paddingValues)
        .padding(horizontal = 20.dp)
        .verticalScroll(dashboardScrollState),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // -----------------------------------------------------------------------
      // -----------------------------------------------------------------------
      // TOTAL SAVINGS BALANCE CARD (PROMINENT METRIC)
      // Displays total savings balance from double-entry ledger with breakdown
      // -----------------------------------------------------------------------
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("total_savings_balance_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = BorderStroke(1.5.dp, ForestGreenPrimary.copy(alpha = 0.35f))
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(34.dp)
                  .background(ForestGreenLightFill, CircleShape),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.AccountBalanceWallet,
                  contentDescription = "Total Savings",
                  tint = ForestGreenPrimary,
                  modifier = Modifier.size(18.dp)
                )
              }
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(
                  text = "TOTAL SAVINGS BALANCE",
                  style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 0.8.sp
                  )
                )
                Text(
                  text = "Cumulative Group Vault",
                  style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    color = TextSecondary
                  )
                )
              }
            }

            Surface(
              color = ForestGreenLightFill,
              shape = RoundedCornerShape(8.dp)
            ) {
              Text(
                text = "GHS",
                style = MaterialTheme.typography.labelSmall.copy(
                  fontSize = 11.sp,
                  color = ForestGreenPrimary,
                  fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
              )
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          Text(
            text = "GHS ${String.format(Locale.US, "%,.2f", totalSavingsBalance)}",
            style = MaterialTheme.typography.headlineLarge.copy(
              fontWeight = FontWeight.Bold,
              color = TextPrimary,
              fontSize = 32.sp
            ),
            modifier = Modifier.testTag("total_savings_amount_text")
          )

          Spacer(modifier = Modifier.height(14.dp))

          // Breakdown pills for payment channels
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            BalanceBreakdownChip(
              label = "MoMo",
              amount = momoTotal,
              modifier = Modifier.weight(1f)
            )
            BalanceBreakdownChip(
              label = "Cash",
              amount = cashTotal,
              modifier = Modifier.weight(1f)
            )
            BalanceBreakdownChip(
              label = "Agent",
              amount = agentTotal,
              modifier = Modifier.weight(1f)
            )
          }

          Spacer(modifier = Modifier.height(16.dp))

          // Quick Action buttons
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Button(
              onClick = onDirectPaymentClick,
              modifier = Modifier
                .weight(1f)
                .height(42.dp)
                .testTag("record_payment_quick_btn"),
              colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary),
              shape = RoundedCornerShape(8.dp),
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
            ) {
              Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = PureWhite)
              Spacer(modifier = Modifier.width(6.dp))
              Text("Record Payment", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PureWhite)
            }

            OutlinedButton(
              onClick = onSyncClick,
              modifier = Modifier
                .weight(1f)
                .height(42.dp)
                .testTag("sync_cloud_btn"),
              colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
              border = BorderStroke(1.dp, BorderGrey),
              shape = RoundedCornerShape(8.dp),
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
            ) {
              Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp), tint = ForestGreenPrimary)
              Spacer(modifier = Modifier.width(6.dp))
              Text(if (isSyncing) "Syncing..." else "Sync Cloud", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            }
          }
        }
      }

      // -----------------------------------------------------------------------
      // PROFESSIONAL EXECUTIVE DASHBOARD HERO: Active Cycle Progress & Analytics
      // -----------------------------------------------------------------------
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .clickable(onClick = onOpenCycleDetail)
          .testTag("amber_hero_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = BorderStroke(1.5.dp, WarningAmber.copy(alpha = 0.5f))
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp)
        ) {
          // Top Row: Week Badge & Due Date Pill
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Surface(
                color = WarningAmberBg,
                shape = RoundedCornerShape(6.dp)
              ) {
                Text(
                  text = "ACTIVE CYCLE",
                  style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = WarningAmberText,
                    fontSize = 10.sp
                  ),
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
              }
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Week ${activeCycle?.number ?: 1}",
                style = MaterialTheme.typography.titleLarge.copy(
                  fontWeight = FontWeight.Bold,
                  color = TextPrimary
                )
              )
            }

            Surface(
              color = Color(0xFFFFFBEB),
              border = BorderStroke(1.dp, Color(0xFFFDE68A)),
              shape = RoundedCornerShape(16.dp)
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Box(
                  modifier = Modifier
                    .size(6.dp)
                    .background(WarningAmber, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "Due ${activeCycle?.dueDate ?: "Friday, 5 PM"}",
                  style = MaterialTheme.typography.labelSmall.copy(
                    color = WarningAmberText,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                  )
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          // Middle 3-Column Metrics Row
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .background(Color(0xFFFDF8F0), RoundedCornerShape(10.dp))
              .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text("Confirmed", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = TextSecondary))
              Text(
                text = "GHS ${String.format(Locale.US, "%.0f", stats.confirmedAmount)}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = ForestGreenPrimary)
              )
            }

            Box(
              modifier = Modifier
                .width(1.dp)
                .height(28.dp)
                .background(BorderGrey)
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text("Target Pool", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = TextSecondary))
              val targetAmount = (activeCycle?.amountDue ?: 50.0) * stats.totalMembers
              Text(
                text = "GHS ${String.format(Locale.US, "%.0f", targetAmount)}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
              )
            }

            Box(
              modifier = Modifier
                .width(1.dp)
                .height(28.dp)
                .background(BorderGrey)
            )

            Column(horizontalAlignment = Alignment.End) {
              Text("Completion", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = TextSecondary))
              Text(
                text = "${stats.progressPercent.toInt()}%",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = WarningAmber)
              )
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          // Progress Bar with dynamic fill
          Column(modifier = Modifier.fillMaxWidth()) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = "${stats.paidMembersCount} of ${stats.totalMembers} members contributed",
                style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
              )
              Text(
                text = "${stats.totalMembers - stats.paidMembersCount} pending",
                style = MaterialTheme.typography.bodySmall.copy(color = WarningAmber, fontWeight = FontWeight.Medium, fontSize = 12.sp)
              )
            }

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
              progress = { (stats.progressPercent / 100f).coerceIn(0f, 1f) },
              modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
              color = ForestGreenPrimary,
              trackColor = NeutralTrack
            )
          }

          Spacer(modifier = Modifier.height(12.dp))

          // Footer link
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .minimumInteractiveComponentSize()
              .clickable(onClick = onOpenCycleDetail)
              .testTag("view_week_roster_link"),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "View Week Roster & Actions →",
              style = MaterialTheme.typography.labelSmall.copy(
                color = ForestGreenPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
              )
            )
          }
        }
      }

      // WHATSAPP BOT PAIRING BANNER (Visible when bot is not connected)
      // -----------------------------------------------------------------------
      if (!isBotConnected && botPairingCode.isNotBlank()) {
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_bot_banner"),
          colors = CardDefaults.cardColors(containerColor = ForestGreenLightFill),
          border = BorderStroke(1.dp, ForestGreenPrimary.copy(alpha = 0.4f)),
          shape = RoundedCornerShape(8.dp)
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("WhatsApp Bot Pending Connection", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ForestGreenPrimary)
              }
              Spacer(modifier = Modifier.height(2.dp))
              Text("Pairing Code: $botPairingCode", fontSize = 12.sp, color = TextPrimary)
            }

            Button(
              onClick = onOpenWhatsAppSimulator,
              modifier = Modifier.minimumInteractiveComponentSize(),
              colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary),
              shape = RoundedCornerShape(6.dp),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
              Text("Connect Bot", fontSize = 12.sp, color = PureWhite, fontWeight = FontWeight.Bold)
            }
          }
        }
      }

      // -----------------------------------------------------------------------
      // PENDING OFFLINE BACKUP BANNER (Visible ONLY when offline data needs cloud backup)
      // -----------------------------------------------------------------------
      if (unsyncedCount > 0 || isSyncing) {
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("offline_cloud_sync_banner"),
          color = if (isSyncing) ForestGreenLightFill else WarningAmberBg,
          shape = RoundedCornerShape(8.dp),
          border = BorderStroke(1.dp, if (isSyncing) ForestGreenPrimary.copy(alpha = 0.3f) else WarningAmberBorder)
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              modifier = Modifier.weight(1f),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(8.dp)
                  .background(if (isSyncing) ForestGreenPrimary else WarningAmber, CircleShape)
              )
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(
                  text = if (isSyncing) "Backing up to Database..." else "Offline Data Pending Backup ($unsyncedCount)",
                  style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isSyncing) ForestGreenPrimary else WarningAmberText
                  )
                )
                Text(
                  text = if (isSyncing) "Syncing offline records with database..." else "$unsyncedCount payment(s) saved locally. Tap to backup to database.",
                  style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 10.sp,
                    color = if (isSyncing) TextSecondary else WarningTextDeep
                  )
                )
              }
            }

            Button(
              onClick = onSyncClick,
              modifier = Modifier.minimumInteractiveComponentSize(),
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
              shape = RoundedCornerShape(6.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = if (isSyncing) ForestGreenPrimary else WarningAmber
              )
            ) {
              Icon(
                imageVector = if (isSyncing) Icons.Default.Refresh else Icons.Default.CloudUpload,
                contentDescription = "Backup",
                tint = PureWhite,
                modifier = Modifier.size(13.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = if (isSyncing) "SYNCING..." else "BACKUP NOW",
                style = MaterialTheme.typography.labelSmall.copy(
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = PureWhite
                )
              )
            }
          }
        }
      }

      // -----------------------------------------------------------------------
      // TWO STAT CARDS SIDE BY SIDE WITH THIN GREY BORDERS
      // -----------------------------------------------------------------------
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        // Confirmed Stat Card
        Card(
          modifier = Modifier
            .weight(1f)
            .height(96.dp),
          colors = CardDefaults.cardColors(containerColor = PureWhite),
          border = BorderStroke(1.dp, BorderGrey),
          shape = RoundedCornerShape(8.dp)
        ) {
          Column(
            modifier = Modifier
              .fillMaxSize()
              .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = LineIconGreen,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("Confirmed", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
            }
            Text(
              text = "GHS ${String.format(Locale.US, "%.0f", stats.confirmedAmount)}",
              style = MaterialTheme.typography.headlineMedium.copy(
                color = TextPrimary,
                fontWeight = FontWeight.Bold
              )
            )
          }
        }

        // Pending Stat Card
        Card(
          modifier = Modifier
            .weight(1f)
            .height(96.dp)
            .clickable(onClick = onViewAllPendingClick),
          colors = CardDefaults.cardColors(containerColor = PureWhite),
          border = BorderStroke(1.dp, BorderGrey),
          shape = RoundedCornerShape(8.dp)
        ) {
          Column(
            modifier = Modifier
              .fillMaxSize()
              .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.HourglassEmpty,
                contentDescription = null,
                tint = HeroAmber,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("Pending", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
            }
            Text(
              text = "${stats.pendingMembersCount} members",
              style = MaterialTheme.typography.headlineMedium.copy(
                color = TextPrimary,
                fontWeight = FontWeight.Bold
              )
            )
          }
        }
      }

      // -----------------------------------------------------------------------
      // "NEEDS YOUR ATTENTION" SECTION
      // -----------------------------------------------------------------------
      Column(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "NEEDS YOUR ATTENTION",
            style = MaterialTheme.typography.labelMedium.copy(
              fontWeight = FontWeight.SemiBold,
              letterSpacing = 0.5.sp,
              color = TextSecondary
            )
          )

          TextButton(
            onClick = onDirectPaymentClick
          ) {
            Text(
              text = "+ Direct Payment",
              style = MaterialTheme.typography.labelSmall.copy(
                color = ForestGreenPrimary,
                fontWeight = FontWeight.SemiBold
              )
            )
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (pendingClaims.isEmpty()) {
          Surface(
            modifier = Modifier
              .fillMaxWidth()
              .testTag("empty_attention_card"),
            color = PureWhite,
            border = BorderStroke(1.dp, ForestGreenPrimary.copy(alpha = 0.25f)),
            shape = RoundedCornerShape(10.dp),
            shadowElevation = 1.dp
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .background(ForestGreenLightFill, CircleShape),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.Check,
                  contentDescription = "Reconciled",
                  tint = ForestGreenPrimary,
                  modifier = Modifier.size(20.dp)
                )
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "All Claims Reconciled",
                  style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                  )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = "Zero pending WhatsApp member claims requiring verification.",
                  style = MaterialTheme.typography.bodySmall.copy(
                    color = TextSecondary,
                    fontSize = 11.sp
                  )
                )
              }
            }
          }
        } else {
          Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = PureWhite),
            border = BorderStroke(1.dp, BorderGrey),
            shape = RoundedCornerShape(8.dp)
          ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
              displayClaims.forEachIndexed { index, claim ->
                AttentionClaimRow(
                  claim = claim,
                  onConfirm = { onConfirmClaim(claim) },
                  onReject = { onRejectClaim(claim) }
                )
                if (index < displayClaims.lastIndex) {
                  Box(
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(1.dp)
                      .background(BorderGrey)
                  )
                }
              }

              if (pendingClaims.size > 3) {
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(BorderGrey)
                )
                Text(
                  text = "View all (${pendingClaims.size})",
                  style = MaterialTheme.typography.labelMedium.copy(
                    color = ForestGreenPrimary,
                    fontWeight = FontWeight.SemiBold
                  ),
                  modifier = Modifier
                    .clickable { showAllClaimsModal = true }
                    .padding(vertical = 10.dp)
                )
              }
            }
          }
        }
      }

      // -----------------------------------------------------------------------
      // RECENT TRANSACTIONS SECTION (FROM ROOM DATABASE)
      // Displays immutable ledger payments with method badges and cryptographic SHA-256 tags
      // -----------------------------------------------------------------------
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("recent_transactions_section")
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = "RECENT TRANSACTIONS",
              style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
                color = TextSecondary
              )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
              color = SoftGreenTint,
              shape = RoundedCornerShape(10.dp)
            ) {
              Text(
                text = "${recentPayments.size}",
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall.copy(
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = ForestGreenPrimary
                )
              )
            }
          }

          TextButton(
            onClick = onOpenLedger
          ) {
            Text(
              text = "View Ledger →",
              style = MaterialTheme.typography.labelSmall.copy(
                color = ForestGreenPrimary,
                fontWeight = FontWeight.SemiBold
              )
            )
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (filteredTransactions.isEmpty()) {
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .testTag("empty_transactions_card"),
            colors = CardDefaults.cardColors(containerColor = PureWhite),
            border = BorderStroke(1.dp, BorderGrey),
            shape = RoundedCornerShape(8.dp)
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Icon(
                imageVector = Icons.Default.Receipt,
                contentDescription = null,
                tint = LineIconGrey,
                modifier = Modifier.size(36.dp)
              )
              Spacer(modifier = Modifier.height(10.dp))
              Text(
                text = if (transactionFilter == "ALL") "No transactions recorded yet" else "No $transactionFilter transactions found",
                style = MaterialTheme.typography.titleSmall.copy(
                  fontWeight = FontWeight.Bold,
                  color = TextPrimary
                )
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "When payments are confirmed via WhatsApp bot or recorded directly, cryptographic records will appear here.",
                style = MaterialTheme.typography.bodySmall.copy(
                  color = TextSecondary,
                  textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
              )
            }
          }
        } else {
          Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = PureWhite),
            border = BorderStroke(1.dp, BorderGrey),
            shape = RoundedCornerShape(8.dp)
          ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
              filteredTransactions.take(8).forEachIndexed { index, payment ->
                DashboardTransactionItemRow(
                  payment = payment,
                  onClick = { selectedPaymentForDetail = payment }
                )
                if (index < filteredTransactions.take(8).lastIndex) {
                  Box(
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(1.dp)
                      .background(BorderGrey.copy(alpha = 0.6f))
                  )
                }
              }
            }
          }
        }
      }

      // Small Trust reminder note (Design spec requirement)
      Text(
        text = "Your money never passes through us — we only keep the record.",
        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
      )
    }
  }

  // View All Claims Bottom Sheet
  if (showAllClaimsModal) {
    val allClaimsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
      onDismissRequest = { showAllClaimsModal = false },
      sheetState = allClaimsSheetState,
      containerColor = PureWhite
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp)
          .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Text(
          text = "Pending Member Claims (${pendingClaims.size})",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        pendingClaims.forEach { claim ->
          AttentionClaimRow(
            claim = claim,
            onConfirm = {
              showAllClaimsModal = false
              onConfirmClaim(claim)
            },
            onReject = {
              showAllClaimsModal = false
              onRejectClaim(claim)
            }
          )
        }
        TextButton(
          onClick = { showAllClaimsModal = false },
          modifier = Modifier.align(Alignment.End)
        ) {
          Text("Close", color = ForestGreenPrimary)
        }
      }
    }
  }

  // Transaction Receipt & Cryptographic Verification Dialog
  selectedPaymentForDetail?.let { payment ->
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val formattedDate = remember(payment.confirmedAt) {
      SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(Date(payment.confirmedAt))
    }

    AlertDialog(
      onDismissRequest = { selectedPaymentForDetail = null },
      title = {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Transaction Receipt", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
          Surface(
            color = if (payment.status == "confirmed") ForestGreenLightFill else ErrorBgLight,
            shape = RoundedCornerShape(6.dp)
          ) {
            Text(
              text = payment.status.uppercase(),
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = if (payment.status == "confirmed") ForestGreenPrimary else ErrorRed
              )
            )
          }
        }
      },
      text = {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          // Amount & Member Name Box
          Surface(
            color = OffWhiteSurface,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, BorderGrey),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier.padding(14.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Text(
                text = "GHS ${String.format(Locale.US, "%.2f", payment.amountPaid)}",
                style = MaterialTheme.typography.headlineMedium.copy(
                  fontWeight = FontWeight.Bold,
                  color = ForestGreenPrimary
                )
              )
              Text(
                text = payment.memberName.ifBlank { "Member Deposit" },
                style = MaterialTheme.typography.bodyMedium.copy(
                  fontWeight = FontWeight.SemiBold,
                  color = TextPrimary
                )
              )
              Text(
                text = formattedDate,
                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
              )
            }
          }

          ReceiptDetailRow(label = "Payment Method", value = payment.method)
          ReceiptDetailRow(label = "Source", value = payment.source.uppercase())
          payment.momoReference?.let { ref ->
            ReceiptDetailRow(label = "MoMo Reference", value = ref)
          }
          ReceiptDetailRow(label = "Idempotency Key", value = payment.idempotencyKey.take(16) + "...")

          // Cryptographic Hash Section
          Surface(
            color = NeutralSurfaceMuted,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(10.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text("TRANSACTION AUDIT HASH", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextSecondary))
                Icon(
                  imageVector = Icons.Default.CheckCircle,
                  contentDescription = "Verified",
                  tint = ForestGreenPrimary,
                  modifier = Modifier.size(14.dp)
                )
              }
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = payment.currentHash,
                style = MaterialTheme.typography.bodySmall.copy(
                  fontFamily = FontFamily.Monospace,
                  fontSize = 10.sp,
                  color = TextPrimary
                )
              )

              Spacer(modifier = Modifier.height(8.dp))
              Text("PREVIOUS HASH", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextSecondary))
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = payment.prevHash,
                style = MaterialTheme.typography.bodySmall.copy(
                  fontFamily = FontFamily.Monospace,
                  fontSize = 10.sp,
                  color = TextSecondary
                )
              )
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            clipboardManager.setText(AnnotatedString(payment.currentHash))
            Toast.makeText(context, "Audit Hash copied to clipboard", Toast.LENGTH_SHORT).show()
          },
          colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary),
          shape = RoundedCornerShape(6.dp)
        ) {
          Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = PureWhite)
          Spacer(modifier = Modifier.width(6.dp))
          Text("Copy Hash", fontSize = 12.sp, color = PureWhite)
        }
      },
      dismissButton = {
        TextButton(onClick = { selectedPaymentForDetail = null }) {
          Text("Close", color = TextSecondary)
        }
      }
    )
  }
}

@Composable
private fun BalanceBreakdownChip(
  label: String,
  amount: Double,
  modifier: Modifier = Modifier
) {
  Surface(
    modifier = modifier,
    color = Color(0xFFF7FAF8),
    border = BorderStroke(1.dp, BorderGrey.copy(alpha = 0.7f)),
    shape = RoundedCornerShape(8.dp)
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
      horizontalAlignment = Alignment.Start
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(
          color = TextSecondary,
          fontSize = 10.sp,
          fontWeight = FontWeight.Medium
        )
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = "GHS ${String.format(Locale.US, "%.0f", amount)}",
        style = MaterialTheme.typography.labelMedium.copy(
          color = TextPrimary,
          fontWeight = FontWeight.Bold,
          fontSize = 12.sp
        )
      )
    }
  }
}

@Composable
private fun DashboardTransactionItemRow(
  payment: PaymentEntity,
  onClick: () -> Unit
) {
  val formattedDate = remember(payment.confirmedAt) {
    SimpleDateFormat("MMM dd • hh:mm a", Locale.getDefault()).format(Date(payment.confirmedAt))
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(vertical = 10.dp)
      .testTag("tx_row_${payment.id}"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Row(
      modifier = Modifier.weight(1f),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Channel Icon Avatar
      val (iconVector, iconBg, iconTint) = when (payment.method.uppercase()) {
        "MOMO" -> Triple(Icons.Default.PhoneAndroid, Color(0xFFFFF8E1), HeroAmber)
        "AGENT" -> Triple(Icons.Default.PointOfSale, Color(0xFFEDE7F6), Color(0xFF5E35B1))
        else -> Triple(Icons.Default.AccountBalanceWallet, ForestGreenLightFill, ForestGreenPrimary)
      }

      Box(
        modifier = Modifier
          .size(36.dp)
          .clip(CircleShape)
          .background(iconBg),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = iconVector,
          contentDescription = payment.method,
          tint = iconTint,
          modifier = Modifier.size(18.dp)
        )
      }

      Spacer(modifier = Modifier.width(10.dp))

      Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = payment.memberName.ifBlank { "Member Deposit" },
            style = MaterialTheme.typography.bodyMedium.copy(
              color = TextPrimary,
              fontWeight = FontWeight.SemiBold
            )
          )
          Spacer(modifier = Modifier.width(6.dp))
          // Method chip
          Surface(
            color = iconBg,
            shape = RoundedCornerShape(4.dp)
          ) {
            Text(
              text = payment.method.uppercase(),
              modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
              style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = iconTint
              )
            )
          }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = formattedDate,
            style = MaterialTheme.typography.bodySmall.copy(
              color = TextSecondary,
              fontSize = 11.sp
            )
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "•",
            style = MaterialTheme.typography.bodySmall.copy(color = BorderGrey, fontSize = 10.sp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = CryptoUtils.formatShortHash(payment.currentHash),
            style = MaterialTheme.typography.bodySmall.copy(
              fontFamily = FontFamily.Monospace,
              color = LineIconGrey,
              fontSize = 10.sp
            )
          )
        }
      }
    }

    // Amount
    Column(horizontalAlignment = Alignment.End) {
      Text(
        text = "+ GHS ${String.format(Locale.US, "%.2f", payment.amountPaid)}",
        style = MaterialTheme.typography.titleSmall.copy(
          color = if (payment.status == "confirmed") ForestGreenPrimary else ErrorRed,
          fontWeight = FontWeight.Bold
        )
      )
      Text(
        text = if (payment.source == "whatsapp") "via WhatsApp" else "via App",
        style = MaterialTheme.typography.bodySmall.copy(
          color = TextSecondary,
          fontSize = 10.sp
        )
      )
    }
  }
}

@Composable
private fun AttentionClaimRow(
  claim: ClaimEntity,
  onConfirm: () -> Unit,
  onReject: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.weight(1f)
    ) {
      val initial = claim.memberName.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").uppercase()
      Box(
        modifier = Modifier
          .size(36.dp)
          .clip(CircleShape)
          .background(ForestGreenPrimary),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = if (initial.isNotEmpty()) initial else "M",
          color = PureWhite,
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold
        )
      }

      Spacer(modifier = Modifier.width(10.dp))

      Column {
        Text(
          text = claim.memberName,
          style = MaterialTheme.typography.bodyMedium.copy(
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold
          )
        )
        Text(
          text = "claims paid GHS ${String.format(Locale.US, "%.0f", claim.claimedAmount)}",
          style = MaterialTheme.typography.bodySmall.copy(
            color = TextSecondary,
            fontSize = 12.sp
          )
        )
      }
    }

    // Green Confirm / Grey Reject buttons
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Button(
        onClick = onConfirm,
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary),
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("confirm_claim_${claim.id}")
      ) {
        Text("Confirm", fontSize = 12.sp, color = PureWhite, fontWeight = FontWeight.SemiBold)
      }

      OutlinedButton(
        onClick = onReject,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, BorderGrey),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = LineIconGrey),
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("reject_claim_${claim.id}")
      ) {
        Text("Reject", fontSize = 12.sp, color = LineIconGrey, fontWeight = FontWeight.SemiBold)
      }
    }
  }
}

@Composable
private fun ReceiptDetailRow(
  label: String,
  value: String
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(text = label, style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
    Text(text = value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = TextPrimary))
  }
}
