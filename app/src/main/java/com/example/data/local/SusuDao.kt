package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SusuDao {

  // 1. Identities & Users
  @Query("SELECT * FROM identities WHERE id = :id LIMIT 1")
  suspend fun getIdentityById(id: String): IdentityEntity?

  @Query("SELECT * FROM identities WHERE phone = :phone LIMIT 1")
  suspend fun getIdentityByPhone(phone: String): IdentityEntity?

  @Query("SELECT * FROM identities ORDER BY display_name ASC")
  fun getAllIdentities(): Flow<List<IdentityEntity>>

  @Query("SELECT * FROM identities ORDER BY display_name ASC")
  suspend fun getAllIdentitiesOnce(): List<IdentityEntity>

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertIdentity(identity: IdentityEntity)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertIdentities(identities: List<IdentityEntity>)

  @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
  suspend fun getUserById(id: String): UserEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertUser(user: UserEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertUsers(users: List<UserEntity>)

  // 2. Groups
  @Query("SELECT * FROM groups ORDER BY created_at ASC")
  fun getAllGroups(): Flow<List<GroupEntity>>

  @Query("SELECT * FROM groups ORDER BY created_at ASC")
  suspend fun getAllGroupsOnce(): List<GroupEntity>

  @Query("SELECT * FROM groups WHERE id = :groupId LIMIT 1")
  suspend fun getGroupById(groupId: String): GroupEntity?

  @Query("SELECT * FROM groups WHERE treasurer_id = :treasurerId LIMIT 1")
  suspend fun getGroupByTreasurer(treasurerId: String): GroupEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertGroup(group: GroupEntity)

  @Update
  suspend fun updateGroup(group: GroupEntity)

  @Query("SELECT * FROM cycles WHERE group_id = :groupId ORDER BY number DESC LIMIT 1")
  suspend fun getLatestCycleForGroup(groupId: String): CycleEntity?

  @Query("SELECT * FROM payments WHERE cycle_id = :cycleId AND member_id = :memberId AND status = 'confirmed'")
  suspend fun getPaymentsForMemberInCycle(cycleId: String, memberId: String): List<PaymentEntity>

  // 3. Members
  @Query("SELECT * FROM members WHERE group_id = :groupId ORDER BY alias ASC")
  fun getMembersForGroup(groupId: String): Flow<List<MemberEntity>>

  @Transaction
  @Query("SELECT * FROM members WHERE group_id = :groupId ORDER BY alias ASC")
  fun getMembersWithIdentity(groupId: String): Flow<List<MemberWithIdentity>>

  @Query("SELECT * FROM members WHERE id = :memberId LIMIT 1")
  suspend fun getMemberById(memberId: String): MemberEntity?

  @Query("SELECT * FROM members WHERE phone = :phone LIMIT 1")
  suspend fun getMemberByPhone(phone: String): MemberEntity?

  @Query("SELECT * FROM members WHERE phone = :phone")
  suspend fun getAllMembersByPhone(phone: String): List<MemberEntity>

  @Query("SELECT * FROM members WHERE identity_id = :identityId")
  suspend fun getMembersByIdentityId(identityId: String): List<MemberEntity>

  @Query("SELECT * FROM members WHERE group_id = :groupId AND state = 'active'")
  suspend fun getActiveMembersForGroup(groupId: String): List<MemberEntity>

  @Query("SELECT * FROM members WHERE group_id = :groupId")
  suspend fun getMembersForGroupOnce(groupId: String): List<MemberEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMember(member: MemberEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMembers(members: List<MemberEntity>)

  @Update
  suspend fun updateMember(member: MemberEntity)

  // 4. Cycles
  @Query("SELECT * FROM cycles WHERE group_id = :groupId AND state = 'open' ORDER BY number DESC LIMIT 1")
  fun getActiveCycle(groupId: String): Flow<CycleEntity?>

  @Query("SELECT * FROM cycles WHERE group_id = :groupId AND state = 'open' ORDER BY number DESC LIMIT 1")
  suspend fun getActiveCycleOnce(groupId: String): CycleEntity?

  @Query("SELECT * FROM cycles WHERE id = :cycleId LIMIT 1")
  suspend fun getCycleById(cycleId: String): CycleEntity?

  @Query("SELECT * FROM cycles WHERE group_id = :groupId ORDER BY number DESC")
  fun getAllCycles(groupId: String): Flow<List<CycleEntity>>

  @Query("SELECT * FROM cycles WHERE group_id = :groupId ORDER BY number ASC")
  suspend fun getAllCyclesOnce(groupId: String): List<CycleEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertCycle(cycle: CycleEntity)

  @Update
  suspend fun updateCycle(cycle: CycleEntity)

  // 5. Accounts
  @Query("SELECT * FROM accounts WHERE group_id = :groupId")
  suspend fun getAccountsForGroup(groupId: String): List<AccountEntity>

  @Query("SELECT * FROM accounts WHERE group_id = :groupId AND type = 'asset' LIMIT 1")
  suspend fun getCashAssetAccount(groupId: String): AccountEntity?

  @Query("SELECT * FROM accounts WHERE group_id = :groupId AND member_id = :memberId LIMIT 1")
  suspend fun getMemberEquityAccount(groupId: String, memberId: String): AccountEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAccount(account: AccountEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAccounts(accounts: List<AccountEntity>)

  // 6. Payments & Cryptographic SHA-256 Hash Chain
  @Query("SELECT * FROM payments WHERE cycle_id = :cycleId ORDER BY confirmed_at DESC")
  fun getPaymentsForCycle(cycleId: String): Flow<List<PaymentEntity>>

  @Query("SELECT p.* FROM payments p INNER JOIN cycles c ON p.cycle_id = c.id WHERE c.group_id = :groupId ORDER BY p.confirmed_at DESC")
  fun getPaymentsForGroup(groupId: String): Flow<List<PaymentEntity>>

  @Query("SELECT p.* FROM payments p INNER JOIN cycles c ON p.cycle_id = c.id WHERE c.group_id = :groupId ORDER BY p.confirmed_at ASC")
  suspend fun getPaymentsForGroupOnce(groupId: String): List<PaymentEntity>

  @Query("SELECT * FROM payments WHERE cycle_id = :cycleId AND status = 'confirmed'")
  suspend fun getConfirmedPaymentsForCycle(cycleId: String): List<PaymentEntity>

  @Query("SELECT * FROM payments ORDER BY confirmed_at ASC")
  fun getAllPayments(): Flow<List<PaymentEntity>>

  @Query("SELECT * FROM payments ORDER BY confirmed_at ASC")
  suspend fun getAllPaymentsOnce(): List<PaymentEntity>

  @Query("SELECT * FROM payments ORDER BY confirmed_at DESC LIMIT 1")
  suspend fun getLastPayment(): PaymentEntity?

  @Query("SELECT p.* FROM payments p INNER JOIN cycles c ON p.cycle_id = c.id WHERE c.group_id = :groupId ORDER BY p.confirmed_at DESC LIMIT 1")
  suspend fun getLastPaymentForGroup(groupId: String): PaymentEntity?

  @Query("SELECT * FROM payments WHERE id = :id LIMIT 1")
  suspend fun getPaymentById(id: String): PaymentEntity?

  @Query("SELECT COUNT(*) FROM payments WHERE is_synced = 0")
  fun getUnsyncedPaymentsCount(): Flow<Int>

  @Query("SELECT COUNT(*) FROM payments WHERE is_synced = 0")
  suspend fun getUnsyncedPaymentsCountOnce(): Int

  @Query("SELECT * FROM payments WHERE is_synced = 0 ORDER BY confirmed_at ASC")
  suspend fun getUnsyncedPaymentsOnce(): List<PaymentEntity>

  @Query("UPDATE payments SET is_synced = 1 WHERE id IN (:ids)")
  suspend fun markPaymentsSynced(ids: List<String>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPayment(payment: PaymentEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPayments(payments: List<PaymentEntity>)

  // 7. Ledger Entries (Balanced Double-Entry Journal)
  @Query("SELECT * FROM ledger_entries ORDER BY created_at DESC")
  fun getAllLedgerEntries(): Flow<List<LedgerEntryEntity>>

  @Query("SELECT * FROM ledger_entries ORDER BY created_at ASC")
  suspend fun getAllLedgerEntriesOnce(): List<LedgerEntryEntity>

  @Query("SELECT le.* FROM ledger_entries le INNER JOIN accounts a ON le.account_id = a.id WHERE a.group_id = :groupId ORDER BY le.created_at DESC")
  fun getLedgerEntriesForGroup(groupId: String): Flow<List<LedgerEntryEntity>>

  @Query("SELECT le.* FROM ledger_entries le INNER JOIN accounts a ON le.account_id = a.id WHERE a.group_id = :groupId ORDER BY le.created_at ASC")
  suspend fun getLedgerEntriesForGroupOnce(groupId: String): List<LedgerEntryEntity>

  @Query("SELECT * FROM ledger_entries WHERE payment_id = :paymentId")
  suspend fun getEntriesForPayment(paymentId: String): List<LedgerEntryEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertLedgerEntries(entries: List<LedgerEntryEntity>)

  // 8. Claims & Disputes
  @Query("SELECT * FROM claims WHERE cycle_id = :cycleId AND state = 'pending' ORDER BY created_at DESC")
  fun getPendingClaimsForCycle(cycleId: String): Flow<List<ClaimEntity>>

  @Query("SELECT claims.* FROM claims JOIN cycles ON claims.cycle_id = cycles.id WHERE cycles.group_id = :groupId AND claims.state = 'pending' ORDER BY claims.created_at DESC")
  fun getPendingClaimsForGroup(groupId: String): Flow<List<ClaimEntity>>

  @Query("SELECT * FROM claims ORDER BY created_at DESC")
  fun getAllClaims(): Flow<List<ClaimEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertClaim(claim: ClaimEntity)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertCloudClaimIfAbsent(claim: ClaimEntity): Long

  @Query("SELECT * FROM claims WHERE id = :id")
  suspend fun getClaimById(id: String): ClaimEntity?

  @Update
  suspend fun updateClaim(claim: ClaimEntity)

  // 9. Ledger Corrections (Dual Sign-off)
  @Query("SELECT * FROM ledger_corrections WHERE payment_id = :paymentId ORDER BY created_at DESC")
  suspend fun getCorrectionsForPayment(paymentId: String): List<LedgerCorrectionEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertLedgerCorrection(correction: LedgerCorrectionEntity)

  // 10. Audit Logs
  @Query("SELECT * FROM audit_log WHERE :groupId = '' OR group_id = :groupId OR group_id IS NULL ORDER BY created_at DESC")
  fun getAuditLogsForGroup(groupId: String): Flow<List<AuditLogEntity>>

  @Query("SELECT * FROM audit_log ORDER BY created_at DESC")
  fun getAllAuditLogs(): Flow<List<AuditLogEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAuditLog(audit: AuditLogEntity)

  // 11. WhatsApp Message Logs
  @Query("SELECT * FROM message_log ORDER BY timestamp ASC")
  fun getAllMessages(): Flow<List<MessageLogEntity>>

  @Query("SELECT * FROM message_log WHERE phone IN (SELECT phone FROM members WHERE group_id = :groupId) OR phone = 'Broadcast: All Members' ORDER BY timestamp ASC")
  fun getMessagesForGroup(groupId: String): Flow<List<MessageLogEntity>>

  @Query("SELECT * FROM message_log WHERE meta_status = 'queued' AND sender_name = 'SusuBot Receipt'")
  suspend fun getQueuedReceipts(): List<MessageLogEntity>

  @Query("UPDATE message_log SET meta_status = :status WHERE id = :id")
  suspend fun updateMessageStatus(id: String, status: String)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMessage(message: MessageLogEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMessages(messages: List<MessageLogEntity>)

  // 12. Account Deletion & Data Purge (Ghana DPC Act 843 Right to Erasure)
  @Query("DELETE FROM payments")
  suspend fun deleteAllPayments()

  @Query("DELETE FROM ledger_entries")
  suspend fun deleteAllLedgerEntries()

  @Query("DELETE FROM ledger_corrections")
  suspend fun deleteAllLedgerCorrections()

  @Query("DELETE FROM claims")
  suspend fun deleteAllClaims()

  @Query("DELETE FROM cycles")
  suspend fun deleteAllCycles()

  @Query("DELETE FROM members")
  suspend fun deleteAllMembers()

  @Query("DELETE FROM groups")
  suspend fun deleteAllGroups()

  @Query("DELETE FROM accounts")
  suspend fun deleteAllAccounts()

  @Query("DELETE FROM users")
  suspend fun deleteAllUsers()

  @Query("DELETE FROM identities")
  suspend fun deleteAllIdentities()

  @Query("DELETE FROM audit_log")
  suspend fun deleteAllAuditLogs()

  @Query("DELETE FROM message_log")
  suspend fun deleteAllMessages()

  @Transaction
  suspend fun purgeAllAccountData() {
    deleteAllLedgerEntries()
    deleteAllLedgerCorrections()
    deleteAllClaims()
    deleteAllPayments()
    deleteAllCycles()
    deleteAllAccounts()
    deleteAllMembers()
    deleteAllGroups()
    deleteAllUsers()
    deleteAllIdentities()
    deleteAllAuditLogs()
    deleteAllMessages()
  }
}
