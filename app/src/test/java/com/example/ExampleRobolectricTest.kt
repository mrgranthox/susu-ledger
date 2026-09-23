package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PaymentEntity
import com.example.data.local.LedgerEntryEntity
import com.example.util.CryptoUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Susu Ledger", appName)
  }

  @Test
  fun `verify SHA256 hash generation is consistent`() {
    val hash1 = CryptoUtils.generatePaymentHash(
      id = "pay-001",
      cycleId = "cycle-12",
      memberId = "member-1",
      amountPaid = 50.0,
      idempotencyKey = "key-001",
      prevHash = CryptoUtils.getGenesisHash()
    )
    val hash2 = CryptoUtils.generatePaymentHash(
      id = "pay-001",
      cycleId = "cycle-12",
      memberId = "member-1",
      amountPaid = 50.0,
      idempotencyKey = "key-001",
      prevHash = CryptoUtils.getGenesisHash()
    )
    assertEquals(hash1, hash2)
    assertEquals(64, hash1.length)
  }

  @Test
  fun `verify salted PIN hashing and validation`() {
    val correctPin = "1234"
    val wrongPin = "9999"
    val hash = com.example.auth.BiometricAuthManager.hashPin(correctPin)

    org.junit.Assert.assertTrue(com.example.auth.BiometricAuthManager.verifyPin(correctPin, hash))
    org.junit.Assert.assertFalse(com.example.auth.BiometricAuthManager.verifyPin(wrongPin, hash))
  }

  @Test
  fun `verify SusuDatabase initialization and WhatsApp multi-group bot flow`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = androidx.room.Room.inMemoryDatabaseBuilder(context, com.example.data.local.SusuDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    val dao = db.susuDao()
    com.example.data.local.SusuDatabase.populateInitialData(dao)
    val repo = com.example.data.repository.SusuRepository(db)

    // Verify initial groups exist
    val groups = dao.getGroupById("group-nima-001")
    assertNotNull(groups)
    assertEquals("Nima Market Susu", groups?.name)

    // Test Multi-group disambiguation for member with multiple groups (Kofi Mensah: +233 24 555 0001)
    val botPrompt = repo.processWhatsAppBotMessage("+233 24 555 0001", "PAID", null)
    org.junit.Assert.assertTrue(botPrompt.contains("You belong to multiple active Susu groups"))

    // Test selecting group 1
    val selectResponse = repo.processWhatsAppBotMessage("+233 24 555 0001", "1", null)
    org.junit.Assert.assertTrue(selectResponse.contains("Confirm your payment claim for Nima Market Susu"))

    // Test Outbound Weekly Reminder
    val reminderCount = repo.sendWeeklyCollectionReminder("group-nima-001")
    org.junit.Assert.assertTrue(reminderCount > 0)

    // Test Outbound Targeted Unpaid Nudges
    val nudgeCount = repo.sendTargetedUnpaidNudges("group-nima-001")
    org.junit.Assert.assertTrue(nudgeCount >= 0)

    // Test Outbound Sunday Summary Digest
    val digestCount = repo.sendSundaySummaryDigest("group-nima-001")
    org.junit.Assert.assertTrue(digestCount > 0)

    // Test DPC Opt-in Consent flow
    val consentResp = repo.processWhatsAppBotMessage("+233 24 555 0001", "OK", null)
    org.junit.Assert.assertTrue(consentResp.contains("DPC Act 843"))

    // Test Member Balance Query
    val balanceResp = repo.processWhatsAppBotMessage("+233 24 555 0001", "BALANCE", null)
    org.junit.Assert.assertTrue(balanceResp.contains("STATEMENT"))

    // Test DPC Opt-out Unsubscribe
    val optOutResp = repo.processWhatsAppBotMessage("+233 24 555 0001", "STOP", null)
    org.junit.Assert.assertTrue(optOutResp.contains("unsubscribed"))

    // Test Help menu
    val helpResp = repo.processWhatsAppBotMessage("+233 24 555 0001", "HELP", null)
    org.junit.Assert.assertTrue(helpResp.contains("Commands"))
  }

  @Test
  fun `verify cryptographic ledger integrity and tampering detection`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = androidx.room.Room.inMemoryDatabaseBuilder(context, com.example.data.local.SusuDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    val dao = db.susuDao()
    com.example.data.local.SusuDatabase.populateInitialData(dao)
    val repo = com.example.data.repository.SusuRepository(db)

    val payments = dao.getAllPaymentsOnce()
    val ledgerEntries = dao.getAllLedgerEntriesOnce()

    // 1. Verify valid genesis-linked ledger
    val report = repo.verifyLedgerIntegrity(payments, ledgerEntries)
    org.junit.Assert.assertTrue(report.isChainValid)
    org.junit.Assert.assertTrue(report.isDoubleEntryBalanced)
    assertEquals(0.0, kotlin.math.abs(report.totalDebits - report.totalCredits), 0.01)

    // 2. Test tampering detection: alter an amount in a payment
    if (payments.isNotEmpty()) {
      val tamperedPayment = payments.first().copy(amountPaid = 99999.0)
      val tamperedList = listOf(tamperedPayment) + payments.drop(1)
      val tamperedReport = repo.verifyLedgerIntegrity(tamperedList, ledgerEntries)
      org.junit.Assert.assertFalse(tamperedReport.isChainValid)
    }
  }
}
