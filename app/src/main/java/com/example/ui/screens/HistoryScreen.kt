package com.example.ui.screens

import android.content.Context
import android.content.Intent
import com.example.ui.components.StandardNavTopBar
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CycleEntity
import com.example.data.local.MemberEntity
import com.example.data.local.PaymentEntity
import com.example.ui.theme.BorderGrey
import com.example.ui.theme.ForestGreenLightFill
import com.example.ui.theme.ForestGreenPrimary
import com.example.ui.theme.LineIconBlack
import com.example.ui.theme.LineIconGreen
import com.example.ui.theme.LineIconGrey
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.LedgerExport
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PastCycleItem(
  val cycleId: String,
  val week: String,
  val paidRatio: String,
  val amountCollected: String,
  val dateRange: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
  groupName: String = "Susu Group",
  cycles: List<CycleEntity> = emptyList(),
  members: List<MemberEntity> = emptyList(),
  payments: List<PaymentEntity> = emptyList(),
  onOpenReportExport: () -> Unit = {},
  onBackClick: (() -> Unit)? = null
) {
  val context = LocalContext.current
  var searchQuery by remember { mutableStateOf("") }
  var showAllCyclesModal by remember { mutableStateOf(false) }
  var selectedPastCycleForDetail by remember { mutableStateOf<PastCycleItem?>(null) }
  var showSummaryTab by remember { mutableStateOf(false) }

  val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.US) }

  val closedCycles = remember(cycles) {
    cycles.filter { it.state.equals("closed", ignoreCase = true) }
  }

  val pastCycles = remember(closedCycles, payments, members) {
    closedCycles.map { cycle ->
      val cyclePayments = payments.filter { it.cycleId == cycle.id && it.status.equals("confirmed", ignoreCase = true) }
      val totalAmount = cyclePayments.sumOf { it.amountPaid }
      val paidCount = cyclePayments.map { it.memberId }.distinct().size
      val totalMembersCount = members.size.coerceAtLeast(1)
      val closedDate = if (cycle.closedAt != null) dateFormat.format(Date(cycle.closedAt)) else cycle.dueDate
      PastCycleItem(
        cycleId = cycle.id,
        week = "Week ${cycle.number}",
        paidRatio = "$paidCount/$totalMembersCount Paid",
        amountCollected = "GHS ${String.format(Locale.US, "%.0f", totalAmount)}",
        dateRange = "Closed $closedDate"
      )
    }
  }

  val filteredPastCycles = remember(pastCycles, searchQuery) {
    if (searchQuery.isBlank()) pastCycles
    else pastCycles.filter { it.week.contains(searchQuery, ignoreCase = true) || it.dateRange.contains(searchQuery, ignoreCase = true) }
  }

  val displayCycles = filteredPastCycles.take(3)
  val scrollState = rememberScrollState()

  // Dynamic Metrics for Summary Tab
  val confirmedPayments = remember(payments) { payments.filter { it.status.equals("confirmed", ignoreCase = true) } }
  val totalCollectedAmount = remember(confirmedPayments) { confirmedPayments.sumOf { it.amountPaid } }
  val activeMembersCount = remember(members) { members.count { it.state.equals("active", ignoreCase = true) } }
  val totalClosedCyclesCount = closedCycles.size

  val momoAmount = remember(confirmedPayments) { confirmedPayments.filter { it.method.equals("MOMO", ignoreCase = true) }.sumOf { it.amountPaid } }
  val cashAmount = remember(confirmedPayments) { confirmedPayments.filter { it.method.equals("CASH", ignoreCase = true) }.sumOf { it.amountPaid } }
  val momoRatio = if (totalCollectedAmount > 0) (momoAmount / totalCollectedAmount).toFloat() else 0f
  val cashRatio = if (totalCollectedAmount > 0) (cashAmount / totalCollectedAmount).toFloat() else 0f

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(PureWhite)
  ) {
    // Top Bar
    StandardNavTopBar(
      title = "Past Weeks",
      subtitle = "$groupName collection archives",
      onBackClick = onBackClick
    )
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {

        // Search bar
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          placeholder = { Text("Search past week...", color = LineIconGrey, fontSize = 14.sp) },
          leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null, tint = LineIconGrey, modifier = Modifier.size(18.dp))
          },
          modifier = Modifier.fillMaxWidth(),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ForestGreenPrimary,
            unfocusedBorderColor = BorderGrey,
            focusedContainerColor = PureWhite,
            unfocusedContainerColor = PureWhite
          ),
          shape = RoundedCornerShape(8.dp),
          singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Switch between Past Cycles List and Summary Report
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
              .background(if (!showSummaryTab) ForestGreenLightFill else PureWhite)
              .clickable { showSummaryTab = false }
              .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
          ) {
            Text("Weeks (${pastCycles.size})", style = MaterialTheme.typography.labelSmall.copy(color = if (!showSummaryTab) ForestGreenPrimary else TextSecondary, fontWeight = FontWeight.SemiBold))
          }
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(4.dp))
              .background(if (showSummaryTab) ForestGreenLightFill else PureWhite)
              .clickable { showSummaryTab = true }
              .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
          ) {
            Text("Summary Report", style = MaterialTheme.typography.labelSmall.copy(color = if (showSummaryTab) ForestGreenPrimary else TextSecondary, fontWeight = FontWeight.SemiBold))
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
      if (!showSummaryTab) {
        Spacer(modifier = Modifier.height(8.dp))

        if (displayCycles.isEmpty()) {
          Card(
            modifier = Modifier.fillMaxWidth(),
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
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = LineIconGrey,
                modifier = Modifier.size(36.dp)
              )
              Spacer(modifier = Modifier.height(10.dp))
              Text(
                text = "No closed weeks yet",
                style = MaterialTheme.typography.titleSmall.copy(
                  fontWeight = FontWeight.Bold,
                  color = TextPrimary
                )
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "When current weeks close, their permanent records and summary audits will appear here.",
                style = MaterialTheme.typography.bodySmall.copy(
                  color = TextSecondary,
                  textAlign = TextAlign.Center
                )
              )
            }
          }
        } else {
          displayCycles.forEach { cycle ->
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .clickable { selectedPastCycleForDetail = cycle }
                .padding(bottom = 12.dp)
                .testTag("past_cycle_${cycle.week}"),
              colors = CardDefaults.cardColors(containerColor = PureWhite),
              border = BorderStroke(1.dp, BorderGrey),
              shape = RoundedCornerShape(8.dp)
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = LineIconGreen,
                    modifier = Modifier.size(20.dp)
                  )

                  Spacer(modifier = Modifier.width(12.dp))

                  Column {
                    Text(
                      text = cycle.week,
                      style = MaterialTheme.typography.titleMedium.copy(
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                      )
                    )
                    Text(
                      text = cycle.dateRange,
                      style = MaterialTheme.typography.bodySmall.copy(
                        color = TextSecondary,
                        fontSize = 11.sp
                      )
                    )
                  }
                }

                Column(horizontalAlignment = Alignment.End) {
                  Text(
                    text = cycle.amountCollected,
                    style = MaterialTheme.typography.titleMedium.copy(
                      color = TextPrimary,
                      fontWeight = FontWeight.Bold
                    )
                  )
                  Text(
                    text = cycle.paidRatio,
                    style = MaterialTheme.typography.bodySmall.copy(
                      color = ForestGreenPrimary,
                      fontWeight = FontWeight.SemiBold,
                      fontSize = 11.sp
                    )
                  )
                }
              }
            }
          }

          if (pastCycles.size > 3) {
            Text(
              text = "View all (${pastCycles.size} past weeks)",
              style = MaterialTheme.typography.labelMedium.copy(
                color = ForestGreenPrimary,
                fontWeight = FontWeight.SemiBold
              ),
              modifier = Modifier
                .clickable { showAllCyclesModal = true }
                .padding(vertical = 8.dp)
            )
          }
        }
      } else {
        // SUMMARY REPORT TAB (Calculated live from Room database)
        Spacer(modifier = Modifier.height(8.dp))

        Text(
          text = "$groupName — Performance Summary",
          style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            color = TextPrimary
          )
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Stats Grid (2x2)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          ReportMetricCard(label = "Total collected", value = "GHS ${String.format(Locale.US, "%.0f", totalCollectedAmount)}", modifier = Modifier.weight(1f))
          ReportMetricCard(label = "Weeks closed", value = "$totalClosedCyclesCount", modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          ReportMetricCard(label = "Active members", value = "$activeMembersCount", modifier = Modifier.weight(1f))
          ReportMetricCard(label = "Total records", value = "${confirmedPayments.size}", modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Payment method breakdown (calculated from Room)
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = PureWhite),
          border = BorderStroke(1.dp, BorderGrey),
          shape = RoundedCornerShape(8.dp)
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Text("Payment Method Breakdown", style = MaterialTheme.typography.labelMedium.copy(color = TextPrimary, fontWeight = FontWeight.SemiBold))
            Spacer(modifier = Modifier.height(10.dp))

            // MoMo row
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text("MoMo", style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary))
              Text("${(momoRatio * 100).toInt()}% (GHS ${String.format(Locale.US, "%.0f", momoAmount)})", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontWeight = FontWeight.Medium))
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
              progress = { momoRatio },
              modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
              color = ForestGreenPrimary,
              trackColor = BorderGrey
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Cash row
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text("Cash", style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary))
              Text("${(cashRatio * 100).toInt()}% (GHS ${String.format(Locale.US, "%.0f", cashAmount)})", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontWeight = FontWeight.Medium))
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
              progress = { cashRatio },
              modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
              color = LineIconGrey,
              trackColor = BorderGrey
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Live Room Ledger Table Thumbnail
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = PureWhite),
          border = BorderStroke(1.dp, BorderGrey),
          shape = RoundedCornerShape(8.dp)
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text("Member", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
              Text("Method", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
              Text("Amount", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            }
            Spacer(modifier = Modifier.height(6.dp))
            if (confirmedPayments.isEmpty()) {
              Text("No payment records in ledger yet", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(vertical = 6.dp))
            } else {
              confirmedPayments.take(5).forEach { payment ->
                Row(
                  modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text(payment.memberName, fontSize = 11.sp, color = TextPrimary)
                  Text(payment.method, fontSize = 11.sp, color = TextSecondary)
                  Text("GHS ${String.format(Locale.US, "%.0f", payment.amountPaid)}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Export Actions
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          OutlinedButton(
            onClick = {
              val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, "$groupName — Total Ledger Balance: GHS ${String.format(Locale.US, "%.0f", totalCollectedAmount)} across $totalClosedCyclesCount weeks.")
                type = "text/plain"
              }
              context.startActivity(Intent.createChooser(sendIntent, "Share Summary"))
            },
            modifier = Modifier.weight(1f).height(48.dp),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, BorderGrey),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
          ) {
            Icon(Icons.Default.Share, contentDescription = null, tint = LineIconBlack, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Share Audit", fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
          }
        }
        Spacer(modifier = Modifier.height(48.dp))
      }
    }
  }

  // Cycle Detail Modal
  if (selectedPastCycleForDetail != null) {
    val item = selectedPastCycleForDetail!!
    val cyclePayments = payments.filter { it.cycleId == item.cycleId && it.status.equals("confirmed", ignoreCase = true) }
    AlertDialog(
      onDismissRequest = { selectedPastCycleForDetail = null },
      title = {
        Text("${item.week} Detail — ${item.amountCollected}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
      },
      text = {
        Column(modifier = Modifier.fillMaxWidth()) {
          Text(item.dateRange, style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
          Spacer(modifier = Modifier.height(12.dp))
          Text("Payment Records (${cyclePayments.size})", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
          Spacer(modifier = Modifier.height(6.dp))
          if (cyclePayments.isEmpty()) {
            Text("No payments logged for this week.", fontSize = 12.sp, color = TextSecondary)
          } else {
            cyclePayments.forEach { p ->
              val refText = if (!p.idempotencyKey.isNull_or_empty()) " (${p.method})" else ""
              Text("• ${p.memberName} — GHS ${String.format(Locale.US, "%.0f", p.amountPaid)}$refText", fontSize = 12.sp, color = TextPrimary, modifier = Modifier.padding(vertical = 2.dp))
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            selectedPastCycleForDetail = null
            LedgerExport.shareCycleCsv(
              context = context,
              payments = cyclePayments,
              subjectLabel = "SusuLedger_${item.week.replace(" ", "")}_Export.csv"
            )
          },
          colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary)
        ) {
          Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp), tint = PureWhite)
          Spacer(modifier = Modifier.width(6.dp))
          Text("Share CSV Ledger", color = PureWhite)
        }
      },
      dismissButton = {
        TextButton(onClick = { selectedPastCycleForDetail = null }) {
          Text("Close", color = TextSecondary)
        }
      }
    )
  }

  // View All Past Weeks Bottom Sheet
  if (showAllCyclesModal) {
    val allCyclesSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
      onDismissRequest = { showAllCyclesModal = false },
      sheetState = allCyclesSheetState,
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
          text = "All Past Weeks (${pastCycles.size})",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        pastCycles.forEach { cycle ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                showAllCyclesModal = false
                selectedPastCycleForDetail = cycle
              }
              .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("${cycle.week} (${cycle.paidRatio})", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(cycle.amountCollected, fontWeight = FontWeight.Bold, color = ForestGreenPrimary, fontSize = 13.sp)
          }
        }
        TextButton(
          onClick = { showAllCyclesModal = false },
          modifier = Modifier.align(Alignment.End)
        ) {
          Text("Close", color = ForestGreenPrimary)
        }
      }
    }
  }
}

private fun String?.isNull_or_empty(): Boolean = this == null || this.isEmpty()

@Composable
private fun ReportMetricCard(label: String, value: String, modifier: Modifier = Modifier) {
  Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(containerColor = PureWhite),
    border = BorderStroke(1.dp, BorderGrey),
    shape = RoundedCornerShape(8.dp)
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Text(label, style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
      Spacer(modifier = Modifier.height(4.dp))
      Text(value, style = MaterialTheme.typography.titleLarge.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
    }
  }
}
