package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.util.CryptoUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

@Database(
  entities = [
    IdentityEntity::class,
    UserEntity::class,
    GroupEntity::class,
    MemberEntity::class,
    CycleEntity::class,
    AccountEntity::class,
    PaymentEntity::class,
    LedgerEntryEntity::class,
    ClaimEntity::class,
    LedgerCorrectionEntity::class,
    AuditLogEntity::class,
    MessageLogEntity::class
  ],
  version = 4,
  exportSchema = false
)
abstract class SusuDatabase : RoomDatabase() {

  abstract fun susuDao(): SusuDao

  companion object {
    @Volatile
    private var INSTANCE: SusuDatabase? = null

    fun getDatabase(context: Context, scope: CoroutineScope): SusuDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          SusuDatabase::class.java,
          "susu_ledger.db"
        )
          .addCallback(SusuDatabaseCallback(scope))
          .fallbackToDestructiveMigration(dropAllTables = true)
          .build()
        INSTANCE = instance
        instance
      }
    }

    private class SusuDatabaseCallback(
      private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
      override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        // Clean slate initialization: no hardcoded or mock data inserted.
        // User creates group and members organically during initial onboarding.
      }
    }

    suspend fun populateInitialData(dao: SusuDao) {
      val treasurer = IdentityEntity(
        id = "id-treasurer-01",
        phone = "+233 24 000 0000",
        displayName = "Kwame Mensah"
      )
      val officer = IdentityEntity(
        id = "id-officer-01",
        phone = "+233 24 000 0001",
        displayName = "Ama Osei"
      )
      val member1 = IdentityEntity(
        id = "id-member-01",
        phone = "+233 24 555 0001",
        displayName = "Kofi Mensah"
      )
      val member2 = IdentityEntity(
        id = "id-member-02",
        phone = "+233 24 555 0002",
        displayName = "Abena Poku"
      )
      dao.insertIdentities(listOf(treasurer, officer, member1, member2))

      val treasurerUser = UserEntity(
        id = treasurer.id,
        pinHash = CryptoUtils.hashPin("1234", treasurer.id),
        role = "treasurer"
      )
      val officerUser = UserEntity(
        id = officer.id,
        pinHash = CryptoUtils.hashPin("1234", officer.id),
        role = "second_officer"
      )
      dao.insertUsers(listOf(treasurerUser, officerUser))

      val group1 = GroupEntity(
        id = "group-nima-001",
        name = "Nima Market Susu",
        amount = 50.0,
        currency = "GHS",
        schedule = "weekly",
        treasurerId = treasurer.id,
        officerId = officer.id,
        state = "active"
      )
      val group2 = GroupEntity(
        id = "group-kaneshie-002",
        name = "Kaneshie Traders Susu",
        amount = 50.0,
        currency = "GHS",
        schedule = "weekly",
        treasurerId = treasurer.id,
        officerId = officer.id,
        state = "active"
      )
      dao.insertGroup(group1)
      dao.insertGroup(group2)

      val mem1Group1 = MemberEntity(
        id = "member-001",
        groupId = group1.id,
        identityId = member1.id,
        alias = "Kofi Mensah",
        phone = "+233 24 555 0001",
        state = "active",
        joinedCycle = 1,
        initials = "KM"
      )
      val mem1Group2 = MemberEntity(
        id = "member-002",
        groupId = group2.id,
        identityId = member1.id,
        alias = "Kofi Mensah",
        phone = "+233 24 555 0001",
        state = "active",
        joinedCycle = 1,
        initials = "KM"
      )
      val mem2Group1 = MemberEntity(
        id = "member-003",
        groupId = group1.id,
        identityId = member2.id,
        alias = "Abena Poku",
        phone = "+233 24 555 0002",
        state = "active",
        joinedCycle = 1,
        initials = "AP"
      )
      dao.insertMembers(listOf(mem1Group1, mem1Group2, mem2Group1))

      val cycle1 = CycleEntity(
        id = "cycle-12",
        groupId = group1.id,
        number = 12,
        amountDue = 50.0,
        dueDate = "Friday",
        state = "open"
      )
      val cycle2 = CycleEntity(
        id = "cycle-kaneshie-01",
        groupId = group2.id,
        number = 1,
        amountDue = 50.0,
        dueDate = "Friday",
        state = "open"
      )
      dao.insertCycle(cycle1)
      dao.insertCycle(cycle2)

      val assetAccount = AccountEntity(
        id = "acc-asset-nima",
        groupId = group1.id,
        name = "Cash/MoMo Asset",
        type = "asset"
      )
      val equityAccount = AccountEntity(
        id = "acc-equity-kofi",
        groupId = group1.id,
        name = "Member Equity: Kofi Mensah",
        type = "equity",
        memberId = mem1Group1.id
      )
      dao.insertAccounts(listOf(assetAccount, equityAccount))

      val genesisHash = CryptoUtils.getGenesisHash()
      val payId = "pay-001"
      val idempKey = "idemp-001"
      val payHash = CryptoUtils.generatePaymentHash(
        id = payId,
        cycleId = cycle1.id,
        memberId = mem1Group1.id,
        amountPaid = 50.0,
        idempotencyKey = idempKey,
        prevHash = genesisHash
      )
      val payment = PaymentEntity(
        id = payId,
        cycleId = cycle1.id,
        memberId = mem1Group1.id,
        memberName = mem1Group1.alias,
        amountPaid = 50.0,
        method = "MOMO",
        status = "confirmed",
        confirmedBy = treasurer.id,
        confirmedAt = System.currentTimeMillis(),
        idempotencyKey = idempKey,
        source = "whatsapp",
        prevHash = genesisHash,
        currentHash = payHash,
        momoReference = "MOMO-TEST-001",
        isSynced = true
      )
      dao.insertPayment(payment)

      val debitEntry = LedgerEntryEntity(
        paymentId = payId,
        accountId = assetAccount.id,
        accountName = assetAccount.name,
        entryType = "debit",
        amount = 50.0
      )
      val creditEntry = LedgerEntryEntity(
        paymentId = payId,
        accountId = equityAccount.id,
        accountName = equityAccount.name,
        entryType = "credit",
        amount = 50.0
      )
      dao.insertLedgerEntries(listOf(debitEntry, creditEntry))
    }
  }
}
