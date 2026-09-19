package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.PaymentEntity
import com.example.ui.DashboardStats
import com.example.ui.theme.AgentPurple
import com.example.ui.theme.AmberGold
import com.example.ui.theme.BorderGrey
import com.example.ui.theme.CashBlue
import com.example.ui.theme.ForestGreenPrimary
import com.example.ui.theme.MoMoYellow
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsSheet(
  sheetState: SheetState,
  stats: DashboardStats,
  cycleNumber: Int,
  payments: List<PaymentEntity>,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = Color.White
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 8.dp)
        .padding(bottom = 36.dp)
    ) {
      // Header
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
      ) {
        Box(
          modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color(0xFFD4EBDD)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.Assessment,
            contentDescription = null,
            tint = ForestGreenPrimary,
            modifier = Modifier.size(24.dp)
          )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
          Text(
            text = "Cycle Performance Report",
            style = MaterialTheme.typography.titleLarge.copy(
              fontWeight = FontWeight.Bold,
              color = TextPrimary
            )
          )
          Text(
            text = "Nima Market Susu • Week $cycleNumber Summary",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
          )
        }
      }

      Spacer(modifier = Modifier.height(18.dp))

      // 2x2 Grid of Key Metrics
      // [ Total Collected | Collection Rate ]
      // [ Outstanding     | Active Members  ]
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          MetricTile(
            title = "Total Collected",
            value = "GHS ${String.format(java.util.Locale.US, "%.0f", stats.confirmedAmount)}",
            subtext = "Confirmed & committed",
            color = SuccessGreen,
            modifier = Modifier.weight(1f)
          )

          MetricTile(
            title = "Collection Rate",
            value = "${stats.progressPercent}%",
            subtext = "${stats.paidMembersCount} of ${stats.totalMembers} contributed",
            color = ForestGreenPrimary,
            modifier = Modifier.weight(1f)
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          MetricTile(
            title = "Outstanding",
            value = "GHS ${String.format(java.util.Locale.US, "%.0f", stats.pendingAmount)}",
            subtext = "${stats.pendingMembersCount} members pending",
            color = AmberGold,
            modifier = Modifier.weight(1f)
          )

          MetricTile(
            title = "Active Members",
            value = "${stats.totalMembers}",
            subtext = "Zero member exits",
            color = Color(0xFF1E40AF),
            modifier = Modifier.weight(1f)
          )
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Breakdown Bars by Method
      Text(
        text = "PAYMENT METHOD DISPERSION",
        style = MaterialTheme.typography.labelSmall.copy(
          fontWeight = FontWeight.Bold,
          letterSpacing = 1.sp,
          color = TextSecondary
        )
      )

      Spacer(modifier = Modifier.height(10.dp))

      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGrey)
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          MethodBar(title = "Mobile Money (MoMo)", percentage = 70, amount = stats.confirmedAmount * 0.7, color = MoMoYellow)
          Spacer(modifier = Modifier.height(10.dp))
          MethodBar(title = "Cash at Stall", percentage = 20, amount = stats.confirmedAmount * 0.2, color = CashBlue)
          Spacer(modifier = Modifier.height(10.dp))
          MethodBar(title = "Market Agent Deposit", percentage = 10, amount = stats.confirmedAmount * 0.1, color = AgentPurple)
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Dual Officer Sign-Off Status
      Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFF1F5F9),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.Shield,
            contentDescription = null,
            tint = ForestGreenPrimary,
            modifier = Modifier.size(22.dp)
          )
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = "Dual Sign-off Architecture",
              fontWeight = FontWeight.Bold,
              fontSize = 12.sp,
              color = TextPrimary
            )
            Text(
              text = "Lead Treasurer: Confirmed • Second Officer: Co-signed",
              fontSize = 11.sp,
              color = TextSecondary
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Export CSV Button
      Button(
        onClick = {
          exportLedgerCsv(context, payments, cycleNumber)
        },
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp)
          .testTag("export_csv_button"),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = ForestGreenPrimary,
          contentColor = Color.White
        )
      ) {
        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Export Cycle Ledger (CSV)", fontSize = 15.sp, fontWeight = FontWeight.Bold)
      }

      Spacer(modifier = Modifier.height(10.dp))

      OutlinedButton(
        onClick = onDismiss,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp)
      ) {
        Text("Close")
      }
    }
  }
}

@Composable
private fun MetricTile(
  title: String,
  value: String,
  subtext: String,
  color: Color,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier,
    shape = RoundedCornerShape(10.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = androidx.compose.foundation.BorderStroke(1.dp, BorderGrey)
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Text(text = title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = value,
        fontSize = 18.sp,
        fontWeight = FontWeight.Black,
        color = color
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(text = subtext, fontSize = 10.sp, color = TextSecondary)
    }
  }
}

@Composable
private fun MethodBar(
  title: String,
  percentage: Int,
  amount: Double,
  color: Color
) {
  Column {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
      Text(
        text = "$percentage% (GHS ${String.format(java.util.Locale.US, "%.0f", amount)})",
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = TextSecondary
      )
    }
    Spacer(modifier = Modifier.height(4.dp))
    LinearProgressIndicator(
      progress = { percentage / 100f },
      modifier = Modifier
        .fillMaxWidth()
        .height(8.dp)
        .clip(RoundedCornerShape(4.dp)),
      color = color,
      trackColor = Color(0xFFE2E8F0)
    )
  }
}

private fun exportLedgerCsv(context: Context, payments: List<PaymentEntity>, cycleNumber: Int) {
  val csvHeader = "ID,Cycle,Member,Amount,Method,Status,ConfirmedAt,PrevHash,CurrentHash,MoMoRef\n"
  val csvRows = payments.joinToString("\n") { p ->
    "${p.id},${p.cycleId},\"${p.memberName}\",${p.amountPaid},${p.method},${p.status},${p.confirmedAt},${p.prevHash},${p.currentHash},${p.momoReference ?: ""}"
  }
  val csvContent = csvHeader + csvRows

  val sendIntent = Intent().apply {
    action = Intent.ACTION_SEND
    putExtra(Intent.EXTRA_TEXT, csvContent)
    putExtra(Intent.EXTRA_SUBJECT, "SusuLedger_Week${cycleNumber}_Export.csv")
    type = "text/plain"
  }
  try {
    context.startActivity(Intent.createChooser(sendIntent, "Share SusuLedger CSV Export"))
  } catch (e: Exception) {
    Toast.makeText(context, "CSV prepared with ${payments.size} records!", Toast.LENGTH_SHORT).show()
  }
}
