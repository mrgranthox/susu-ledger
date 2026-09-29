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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import com.example.ui.components.SusuSnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.MemberEntity
import com.example.ui.components.StandardNavTopBar
import com.example.ui.theme.BorderGrey
import com.example.ui.theme.ErrorBorderRed
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.ForestGreenLightFill
import com.example.ui.theme.ForestGreenPrimary
import com.example.ui.theme.HeroAmber
import com.example.ui.theme.LineIconBlack
import com.example.ui.theme.LineIconGreen
import com.example.ui.theme.LineIconGrey
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

enum class CycleFilterTab {
  ALL, PAID, PENDING
}

data class CycleMemberStatus(
  val member: MemberEntity,
  val isPaid: Boolean,
  val paidTime: String? = null,
  val isDisputed: Boolean = false,
  val disputeReason: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CycleDetailScreen(
  cycleNumber: Int = 12,
  dueDate: String = "Friday",
  cycleAmount: Double = 50.0,
  members: List<MemberEntity>,
  paidMemberIds: Set<String>,
  onBack: () -> Unit,
  onMarkPaid: (MemberEntity) -> Unit,
  onCloseWeek: () -> Unit,
  onSendReminder: () -> Unit = {}
) {
  var selectedTab by remember { mutableStateOf(CycleFilterTab.ALL) }
  var showAllMembersModal by remember { mutableStateOf(false) }
  var showResolveDisputeModal by remember { mutableStateOf(false) }
  var activeDisputeMember by remember { mutableStateOf<CycleMemberStatus?>(null) }
  val snackbarHostState = remember { SnackbarHostState() }
  val coroutineScope = rememberCoroutineScope()

  // Build cycle member statuses
  val cycleMemberStatuses = remember(members, paidMemberIds) {
    members.map { m ->
      val isPaid = paidMemberIds.contains(m.id)
      CycleMemberStatus(
        member = m,
        isPaid = isPaid,
        paidTime = if (isPaid) "Confirmed" else null,
        isDisputed = false,
        disputeReason = null
      )
    }
  }

  val paidCount = cycleMemberStatuses.count { it.isPaid }
  val pendingCount = cycleMemberStatuses.count { !it.isPaid && !it.isDisputed }
  val confirmedAmount = paidCount * cycleAmount

  val filteredMembers = when (selectedTab) {
    CycleFilterTab.ALL -> cycleMemberStatuses
    CycleFilterTab.PAID -> cycleMemberStatuses.filter { it.isPaid }
    CycleFilterTab.PENDING -> cycleMemberStatuses.filter { !it.isPaid && !it.isDisputed }
  }

  // Maximum 4 rows visible, then "View all"
  val displayMembers = filteredMembers.take(4)

  Scaffold(
    containerColor = PureWhite,
    snackbarHost = { SusuSnackbarHost(snackbarHostState) },
    topBar = {
      StandardNavTopBar(
        title = "Week $cycleNumber — Due $dueDate",
        onBackClick = onBack
      )
    },
    bottomBar = {
      // Sticky bottom bar: green "Send reminder to 3 pending" + subtle grey "Close Week" text link
      Surface(
        color = PureWhite,
        border = BorderStroke(1.dp, BorderGrey),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Button(
            onClick = {
              onSendReminder()
              coroutineScope.launch {
                snackbarHostState.showSnackbar("WhatsApp reminder sent to $pendingCount pending members.")
              }
            },
            modifier = Modifier
              .fillMaxWidth()
              .height(50.dp)
              .testTag("send_pending_reminder_btn"),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary)
          ) {
            Text(
              text = "Send reminder to $pendingCount pending",
              style = MaterialTheme.typography.labelLarge.copy(color = PureWhite, fontWeight = FontWeight.SemiBold)
            )
          }

          Spacer(modifier = Modifier.height(6.dp))

          TextButton(onClick = onCloseWeek, modifier = Modifier.testTag("close_week_link")) {
            Text(
              text = "Close Week",
              style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontWeight = FontWeight.Medium)
            )
          }
        }
      }
    }
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(PureWhite)
        .padding(paddingValues)
        .padding(horizontal = 20.dp)
    ) {
      Spacer(modifier = Modifier.height(12.dp))

      // Header summary chips (outlined, monochrome): "17 Paid 3 Pending GHS 850"
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        SummaryChip(text = "$paidCount Paid", modifier = Modifier.weight(1f))
        SummaryChip(text = "$pendingCount Pending", modifier = Modifier.weight(1f))
        SummaryChip(text = "GHS $confirmedAmount", modifier = Modifier.weight(1.3f))
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Filter tabs as plain text: All | Paid | Pending | Disputed
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, BorderGrey, RoundedCornerShape(8.dp))
          .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceAround
      ) {
        CycleFilterTab.values().forEach { tab ->
          val isSelected = selectedTab == tab
          val title = when (tab) {
            CycleFilterTab.ALL -> "All (${cycleMemberStatuses.size})"
            CycleFilterTab.PAID -> "Paid ($paidCount)"
            CycleFilterTab.PENDING -> "Pending ($pendingCount)"
          }

          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(6.dp))
              .background(if (isSelected) ForestGreenLightFill else PureWhite)
              .clickable { selectedTab = tab }
              .padding(horizontal = 10.dp, vertical = 8.dp)
          ) {
            Text(
              text = title,
              style = MaterialTheme.typography.labelSmall.copy(
                color = if (isSelected) ForestGreenPrimary else TextSecondary,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
              )
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Member list: maximum 4 rows visible, then "View all"
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = BorderStroke(1.dp, BorderGrey),
        shape = RoundedCornerShape(8.dp)
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          if (filteredMembers.isEmpty()) {
            Text("No members in this filter.", style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary))
          } else {
            displayMembers.forEachIndexed { index, item ->
              CycleMemberRow(
                status = item,
                onMarkPaid = { onMarkPaid(item.member) },
                onResolveDispute = {
                  activeDisputeMember = item
                  showResolveDisputeModal = true
                }
              )
              if (index < displayMembers.lastIndex) {
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(BorderGrey)
                )
              }
            }

            if (filteredMembers.size > 4) {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(1.dp)
                  .background(BorderGrey)
              )
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = "View all (${filteredMembers.size})",
                style = MaterialTheme.typography.labelMedium.copy(
                  color = ForestGreenPrimary,
                  fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier
                  .clickable { showAllMembersModal = true }
                  .padding(vertical = 6.dp)
              )
            }
          }
        }
      }
    }
  }

  // Full Member List Bottom Sheet
  if (showAllMembersModal) {
    val allMembersSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
      onDismissRequest = { showAllMembersModal = false },
      sheetState = allMembersSheetState,
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
          text = "All Members (${filteredMembers.size})",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        filteredMembers.forEach { item ->
          CycleMemberRow(
            status = item,
            onMarkPaid = {
              showAllMembersModal = false
              onMarkPaid(item.member)
            },
            onResolveDispute = {
              showAllMembersModal = false
              activeDisputeMember = item
              showResolveDisputeModal = true
            }
          )
        }
        TextButton(
          onClick = { showAllMembersModal = false },
          modifier = Modifier.align(Alignment.End)
        ) {
          Text("Close", color = ForestGreenPrimary)
        }
      }
    }
  }

  // Resolve Dispute Modal
  if (showResolveDisputeModal && activeDisputeMember != null) {
    AlertDialog(
      onDismissRequest = { showResolveDisputeModal = false },
      title = {
        Text("Resolve Dispute — ${activeDisputeMember?.member?.alias}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
      },
      text = {
        Column {
          Text(activeDisputeMember?.disputeReason ?: "Disputed claim", style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary))
          Spacer(modifier = Modifier.height(14.dp))
          Text("You can confirm manual receipt or reverse claim.", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
        }
      },
      confirmButton = {
        Button(
          onClick = {
            showResolveDisputeModal = false
            onMarkPaid(activeDisputeMember!!.member)
          },
          colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary)
        ) {
          Text("Accept & Mark Paid")
        }
      },
      dismissButton = {
        TextButton(onClick = { showResolveDisputeModal = false }) {
          Text("Dismiss", color = TextSecondary)
        }
      }
    )
  }
}

@Composable
private fun SummaryChip(text: String, modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .border(1.dp, BorderGrey, RoundedCornerShape(6.dp))
      .background(PureWhite)
      .padding(vertical = 8.dp, horizontal = 10.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelMedium.copy(
        color = TextPrimary,
        fontWeight = FontWeight.SemiBold
      )
    )
  }
}

@Composable
private fun CycleMemberRow(
  status: CycleMemberStatus,
  onMarkPaid: () -> Unit,
  onResolveDispute: () -> Unit
) {
  val isDisputed = status.isDisputed
  val rowBorder = if (isDisputed) BorderStroke(1.dp, ErrorRed) else null

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .then(if (rowBorder != null) Modifier.border(rowBorder, RoundedCornerShape(6.dp)).padding(6.dp) else Modifier.padding(vertical = 10.dp)),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.weight(1f)
    ) {
      // Green initials circle
      val initial = status.member.alias.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").uppercase()
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
          text = status.member.alias,
          style = MaterialTheme.typography.bodyMedium.copy(
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold
          )
        )
        Text(
          text = status.member.phone,
          style = MaterialTheme.typography.bodySmall.copy(
            color = TextSecondary,
            fontSize = 12.sp
          )
        )
      }
    }

    // Right side: either green outlined "PAID" badge with confirmed time, or amber "MARK PAID" button, or "Resolve" link
    when {
      isDisputed -> {
        TextButton(onClick = onResolveDispute) {
          Text("Resolve", color = ErrorRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
      }
      status.isPaid -> {
        Column(horizontalAlignment = Alignment.End) {
          Box(
            modifier = Modifier
              .border(1.dp, ForestGreenPrimary, RoundedCornerShape(4.dp))
              .background(ForestGreenLightFill)
              .padding(horizontal = 8.dp, vertical = 2.dp)
          ) {
            Text(
              text = "PAID",
              color = ForestGreenPrimary,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold
            )
          }
          if (status.paidTime != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = status.paidTime,
              style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 10.sp)
            )
          }
        }
      }
      else -> {
        // Amber "MARK PAID" button
        Button(
          onClick = onMarkPaid,
          shape = RoundedCornerShape(6.dp),
          colors = ButtonDefaults.buttonColors(containerColor = HeroAmber),
          modifier = Modifier.height(32.dp)
        ) {
          Text("MARK PAID", fontSize = 11.sp, color = PureWhite, fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}
