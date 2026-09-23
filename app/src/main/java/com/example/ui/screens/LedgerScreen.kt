package com.example.ui.screens

import com.example.ui.theme.CreditBlue
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DebitGreen
import com.example.ui.theme.NeutralBorderStrong
import com.example.ui.theme.NeutralSurfaceLight
import com.example.ui.theme.NeutralSurfaceMedium
import com.example.ui.theme.NeutralTrack
import com.example.ui.theme.SoftGreenFill


import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.LedgerEntryEntity
import com.example.data.local.PaymentEntity
import com.example.data.repository.VerificationReport
import com.example.ui.theme.BorderGrey
import com.example.ui.theme.CashBlue
import com.example.ui.theme.CashBlueBg
import com.example.ui.theme.ForestGreenPrimary
import com.example.ui.theme.MoMoYellow
import com.example.ui.theme.MoMoYellowBg
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.CryptoUtils
import com.example.ui.components.StandardNavTopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LedgerScreen(
  payments: List<PaymentEntity>,
  ledgerEntries: List<LedgerEntryEntity>,
  verificationReport: VerificationReport?,
  isVerifying: Boolean,
  onVerifyIntegrityClick: () -> Unit,
  onBackClick: (() -> Unit)? = null
) {
  var selectedTab by remember { mutableStateOf(0) } // 0: Double-Entry Journal, 1: SHA-256 Hash Chain
  val dateFormat = remember { SimpleDateFormat("dd MMM HH:mm", Locale.US) }

  Scaffold(
    topBar = {
      Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
        StandardNavTopBar(
          title = "Audit Ledger",
          subtitle = "Balanced Double-Entry & Transaction Chain",
          onBackClick = onBackClick,
          actions = {
            Button(
              onClick = onVerifyIntegrityClick,
              shape = RoundedCornerShape(8.dp),
              colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary),
              modifier = Modifier.testTag("verify_ledger_button")
            ) {
              if (isVerifying) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
              } else {
                Icon(imageVector = Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Verify", fontSize = 12.sp, fontWeight = FontWeight.Bold)
              }
            }
          }
        )

        // Tab Row
        TabRow(
          selectedTabIndex = selectedTab,
          containerColor = Color.White,
          contentColor = ForestGreenPrimary,
          indicator = { tabPositions ->
            TabRowDefaults.SecondaryIndicator(
              modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
              color = ForestGreenPrimary
            )
          }
        ) {
            Tab(
              selected = selectedTab == 0,
              onClick = { selectedTab = 0 },
              text = {
                Text(
                  text = "Double-Entry Journal",
                  fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                )
              }
            )
            Tab(
              selected = selectedTab == 1,
              onClick = { selectedTab = 1 },
              text = {
                Text(
                  text = "Audit Chain (${payments.size})",
                  fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                )
              }
            )
          }
        }
      }
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .background(NeutralSurfaceLight)
        .padding(paddingValues)
        .padding(horizontal = 16.dp)
    ) {
      item {
        Spacer(modifier = Modifier.height(12.dp))

        // Verification Status Banner
        if (verificationReport != null) {
          IntegrityBanner(report = verificationReport)
          Spacer(modifier = Modifier.height(14.dp))
        }
      }

      if (selectedTab == 0) {
        // Tab 0: Double-Entry Journal View
        item {
          // Journal Balance Summary Card
          DoubleEntryBalanceCard(ledgerEntries = ledgerEntries)
          Spacer(modifier = Modifier.height(14.dp))
          Text(
            text = "JOURNAL TRANSACTIONS (APPEND-ONLY)",
            style = MaterialTheme.typography.labelSmall.copy(
              fontWeight = FontWeight.Bold,
              letterSpacing = 1.sp,
              color = TextSecondary
            )
          )
          Spacer(modifier = Modifier.height(8.dp))
        }

        // Group entries by payment
        val groupedEntries = ledgerEntries.groupBy { it.paymentId }
        items(payments, key = { it.id }) { payment ->
          val entries = groupedEntries[payment.id] ?: emptyList()
          JournalTransactionCard(
            payment = payment,
            entries = entries,
            dateFormat = dateFormat
          )
          Spacer(modifier = Modifier.height(10.dp))
        }
      } else {
        // Tab 1: SHA-256 Hash Chain Inspector
        item {
          GenesisBlockCard()
          Spacer(modifier = Modifier.height(10.dp))
        }

        itemsIndexed(payments, key = { _, payment -> payment.id }) { index, payment ->
          BlockChainCard(
            index = index + 1,
            payment = payment,
            dateFormat = dateFormat
          )
          if (index < payments.size - 1) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.Link,
                contentDescription = "Chained",
                tint = ForestGreenPrimary,
                modifier = Modifier.size(18.dp)
              )
            }
          }
        }
      }

      item {
        Spacer(modifier = Modifier.height(40.dp))
      }
    }
  }
}

@Composable
private fun IntegrityBanner(report: VerificationReport) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(10.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (report.isChainValid && report.isDoubleEntryBalanced) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
    ),
    border = androidx.compose.foundation.BorderStroke(
      1.dp,
      if (report.isChainValid && report.isDoubleEntryBalanced) Color(0xFF86EFAC) else Color(0xFFFCA5A5)
    )
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = if (report.isChainValid) Icons.Default.CheckCircle else Icons.Default.Security,
        contentDescription = null,
        tint = if (report.isChainValid) SuccessGreen else DangerRed,
        modifier = Modifier.size(24.dp)
      )
      Spacer(modifier = Modifier.width(10.dp))
      Column {
        Text(
          text = if (report.isChainValid) "100% Verified & Balanced" else "Ledger Verification Warning",
          fontWeight = FontWeight.Bold,
          fontSize = 14.sp,
          color = if (report.isChainValid) DebitGreen else Color(0xFF991B1B)
        )
        Text(
          text = "${report.totalBlocks} Entries Verified • Audit Chain Intact • Dual Entry Balanced (Debits = Credits = GHS ${String.format(java.util.Locale.US, "%.0f", report.totalDebits)})",
          fontSize = 11.sp,
          color = TextSecondary
        )
      }
    }
  }
}

@Composable
private fun DoubleEntryBalanceCard(ledgerEntries: List<LedgerEntryEntity>) {
  val totalDebits = ledgerEntries.filter { it.entryType == "debit" }.sumOf { it.amount }
  val totalCredits = ledgerEntries.filter { it.entryType == "credit" }.sumOf { it.amount }

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = androidx.compose.foundation.BorderStroke(1.dp, BorderGrey)
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "ACCOUNTING EQUATION",
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 1.sp,
          color = TextSecondary
        )
        Surface(
          shape = RoundedCornerShape(4.dp),
          color = Color(0xFFDCFCE7)
        ) {
          Text(
            text = "DEBITS = CREDITS (BALANCED)",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = DebitGreen,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column {
          Text(text = "Total Asset Debits", fontSize = 12.sp, color = TextSecondary)
          Text(
            text = "GHS ${String.format(java.util.Locale.US, "%.2f", totalDebits)}",
            fontWeight = FontWeight.Black,
            fontSize = 16.sp,
            color = DebitGreen
          )
        }

        Column(horizontalAlignment = Alignment.End) {
          Text(text = "Total Member Equity Credits", fontSize = 12.sp, color = TextSecondary)
          Text(
            text = "GHS ${String.format(java.util.Locale.US, "%.2f", totalCredits)}",
            fontWeight = FontWeight.Black,
            fontSize = 16.sp,
            color = CreditBlue
          )
        }
      }
    }
  }
}

@Composable
private fun JournalTransactionCard(
  payment: PaymentEntity,
  entries: List<LedgerEntryEntity>,
  dateFormat: SimpleDateFormat
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(10.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = androidx.compose.foundation.BorderStroke(1.dp, BorderGrey)
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Surface(
            shape = RoundedCornerShape(4.dp),
            color = if (payment.method == "MOMO") MoMoYellowBg else CashBlueBg
          ) {
            Text(
              text = payment.method,
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = if (payment.method == "MOMO") MoMoYellow else CashBlue,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = payment.memberName,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = TextPrimary
          )
        }

        Text(
          text = "GHS ${String.format(java.util.Locale.US, "%.2f", payment.amountPaid)}",
          fontWeight = FontWeight.Black,
          fontSize = 14.sp,
          color = ForestGreenPrimary
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Balanced Debit & Credit entries
      entries.forEach { entry ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = "${if (entry.entryType == "debit") "Dr." else "  Cr."} ${entry.accountName}",
            fontSize = 12.sp,
            fontWeight = if (entry.entryType == "debit") FontWeight.Medium else FontWeight.Normal,
            color = if (entry.entryType == "debit") DebitGreen else CreditBlue
          )
          Text(
            text = "GHS ${String.format(java.util.Locale.US, "%.2f", entry.amount)}",
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = TextSecondary
          )
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = "Time: ${dateFormat.format(Date(payment.confirmedAt))} • Hash: ${CryptoUtils.formatShortHash(payment.currentHash)}",
        fontSize = 10.sp,
        color = TextSecondary,
        fontFamily = FontFamily.Monospace
      )
    }
  }
}

@Composable
private fun GenesisBlockCard() {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(10.dp),
    colors = CardDefaults.cardColors(containerColor = NeutralSurfaceMedium),
    border = androidx.compose.foundation.BorderStroke(1.dp, NeutralBorderStrong)
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(32.dp)
          .clip(CircleShape)
          .background(NeutralTrack),
        contentAlignment = Alignment.Center
      ) {
        Text("0", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextSecondary)
      }
      Spacer(modifier = Modifier.width(10.dp))
      Column {
        Text(text = "GENESIS BLOCK", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TextSecondary)
        Text(
          text = "0000000000000000000000000000000000000000000000000000000000000000",
          fontFamily = FontFamily.Monospace,
          fontSize = 9.sp,
          color = TextSecondary
        )
      }
    }
  }
}

@Composable
private fun BlockChainCard(
  index: Int,
  payment: PaymentEntity,
  dateFormat: SimpleDateFormat
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = androidx.compose.foundation.BorderStroke(1.dp, BorderGrey)
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(28.dp)
              .clip(CircleShape)
              .background(SoftGreenFill),
            contentAlignment = Alignment.Center
          ) {
            Text("#$index", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = ForestGreenPrimary)
          }
          Spacer(modifier = Modifier.width(8.dp))
          Text(text = payment.memberName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        Text(
          text = "GHS ${String.format(java.util.Locale.US, "%.2f", payment.amountPaid)}",
          fontWeight = FontWeight.Bold,
          color = ForestGreenPrimary
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Prev Hash
      Column {
        Text(text = "PREV HASH", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
        Text(
          text = payment.prevHash,
          fontSize = 10.sp,
          fontFamily = FontFamily.Monospace,
          color = TextSecondary
        )
      }

      Spacer(modifier = Modifier.height(4.dp))

      // Current SHA-256 Hash
      Column {
        Text(text = "CURRENT AUDIT HASH", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = ForestGreenPrimary)
        Text(
          text = payment.currentHash,
          fontSize = 10.sp,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.SemiBold,
          color = ForestGreenPrimary
        )
      }

      Spacer(modifier = Modifier.height(4.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          text = "Idemp: ${payment.idempotencyKey.take(16)}...",
          fontSize = 9.sp,
          fontFamily = FontFamily.Monospace,
          color = TextSecondary
        )
        Text(
          text = dateFormat.format(Date(payment.confirmedAt)),
          fontSize = 9.sp,
          color = TextSecondary
        )
      }
    }
  }
}
