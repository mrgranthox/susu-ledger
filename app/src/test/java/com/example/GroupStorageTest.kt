package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.SusuDatabase
import com.example.data.repository.SusuRepository
import com.example.util.GhanaPhoneUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class GroupStorageTest {
  @Test
  fun `group creation and payment produce durable balanced records`() = runBlocking {
    val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SusuDatabase::class.java).allowMainThreadQueries().build()
    try {
      val repository = SusuRepository(db)
      val group = repository.createNewGroupWithMembers("Test Group", 50.0, treasurerPhone = "240000001", treasurerName = "Officer", members = listOf("Member" to "0250000001"), treasurerPin = "7391")
      val dao = db.susuDao()
      assertEquals("+233240000001", dao.getIdentityById(group.treasurerId)?.phone)
      val cycle = requireNotNull(dao.getActiveCycleOnce(group.id))
      assertTrue(cycle.dueDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
      val member = dao.getMembersForGroupOnce(group.id).single()
      val payment = repository.recordPayment(group.id,cycle.id,member.id,25.0,"CASH",confirmedBy = group.treasurerId,sendWhatsAppReceipt = false)
      assertFalse(payment.isSynced)
      assertEquals(2,dao.getEntriesForPayment(payment.id).size)
      assertTrue(repository.verifyLedgerIntegrity(dao.getAllPaymentsOnce(),dao.getAllLedgerEntriesOnce()).isDoubleEntryBalanced)
      assertTrue(repository.verifyLedgerIntegrity(dao.getAllPaymentsOnce(),dao.getAllLedgerEntriesOnce()).isChainValid)
    } finally { db.close() }
  }

  @Test
  fun `invalid member rolls back the entire group creation`() = runBlocking {
    val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SusuDatabase::class.java).allowMainThreadQueries().build()
    try {
      val repository = SusuRepository(db)
      val result = runCatching { repository.createNewGroupWithMembers("Test Group",50.0,treasurerPhone="240000001",treasurerName="Officer",members=listOf("Invalid" to "123"),treasurerPin="7391") }
      assertTrue(result.isFailure)
      assertTrue(db.susuDao().getAllGroupsOnce().isEmpty())
      assertTrue(db.susuDao().getAllIdentitiesOnce().isEmpty())
    } finally { db.close() }
  }

  @Test
  fun `phone normalization accepts onboarding and local formats without truncation`() {
    for (input in listOf("240000001","0240000001","+233 24 000 0001")) assertEquals("+233240000001",GhanaPhoneUtils.toE164(input))
    assertTrue(runCatching { GhanaPhoneUtils.toE164("0240000001999") }.isFailure)
  }
}
