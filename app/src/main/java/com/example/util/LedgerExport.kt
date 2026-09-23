package com.example.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.data.local.PaymentEntity

/**
 * Shared CSV ledger export used by the History screen and the Reports sheet.
 */
object LedgerExport {

  fun shareCycleCsv(
    context: Context,
    payments: List<PaymentEntity>,
    subjectLabel: String
  ) {
    val csvHeader = "ID,Cycle,Member,Amount,Method,Status,ConfirmedAt,PrevHash,CurrentHash,MoMoRef\n"
    val csvRows = payments.joinToString("\n") { p ->
      "${p.id},${p.cycleId},\"${p.memberName}\",${p.amountPaid},${p.method},${p.status},${p.confirmedAt},${p.prevHash},${p.currentHash},${p.momoReference ?: ""}"
    }
    val sendIntent = Intent().apply {
      action = Intent.ACTION_SEND
      putExtra(Intent.EXTRA_TEXT, csvHeader + csvRows)
      putExtra(Intent.EXTRA_SUBJECT, subjectLabel)
      type = "text/plain"
    }
    try {
      context.startActivity(Intent.createChooser(sendIntent, "Share SusuLedger CSV Export"))
    } catch (e: Exception) {
      Toast.makeText(context, "CSV prepared with ${payments.size} records!", Toast.LENGTH_SHORT).show()
    }
  }
}