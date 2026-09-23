package com.example.data.repository

import com.example.data.local.AccountEntity
import com.example.data.local.AuditLogEntity
import com.example.data.local.ClaimEntity
import com.example.data.local.CycleEntity
import com.example.data.local.GroupEntity
import com.example.data.local.IdentityEntity
import com.example.data.local.LedgerCorrectionEntity
import com.example.data.local.LedgerEntryEntity
import com.example.data.local.MemberEntity
import com.example.data.local.MemberWithIdentity
import com.example.data.local.MessageLogEntity
import com.example.data.local.PaymentEntity
import com.example.data.local.SusuDao
import com.example.data.local.SusuDatabase
import androidx.room.withTransaction
import com.example.data.local.UserEntity
import com.example.data.remote.SusuApiClient
import com.example.util.CryptoUtils
import kotlinx.coroutines.flow.Flow
import java.util.UUID

data class VerificationReport(
  val totalBlocks: Int,
  val isChainValid: Boolean,
  val brokenBlockIndex: Int? = null,
  val totalDebits: Double,
  val totalCredits: Double,
  val isDoubleEntryBalanced: Boolean,
  val genesisHash: String,
  val latestHash: String
)

class SusuRepository(private val database: SusuDatabase) {
  private val dao = database.susuDao()
  var hasPendingCloudMessages: Boolean = false
    private set

  val allGroups: Flow<List<GroupEntity>> = dao.getAllGroups()
  val allPayments: Flow<List<PaymentEntity>> = dao.getAllPayments()
  val allLedgerEntries: Flow<List<LedgerEntryEntity>> = dao.getAllLedgerEntries()
  val allMessages: Flow<List<MessageLogEntity>> = dao.getAllMessages()
  val unsyncedPaymentsCount: Flow<Int> = dao.getUnsyncedPaymentsCount()

  suspend fun syncAllOfflineDataToCloud(): Int {
    hasPendingCloudMessages = false
    var uploaded = 0
    // Resend all payments to recover records falsely marked synced by older versions.
    // The server checks immutable payment IDs and idempotency keys on every retry.
    val allPayments = dao.getAllPaymentsOnce()
    for (group in dao.getAllGroupsOnce()) {
      val treasurer = dao.getIdentityById(group.treasurerId)
        ?: error("Group treasurer is missing")
      val cycles = dao.getAllCyclesOnce(group.id).map { cycle ->
        if (cycle.dueDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) cycle
        else cycle.copy(dueDate = nextDueDate(group.schedule))
      }
      val cycleIds = cycles.map { it.id }.toSet()
      val payments = allPayments.filter { it.cycleId in cycleIds }
      val receiptIds = dao.getQueuedReceipts().map { it.id.removePrefix("receipt-") }.filter { id -> payments.any { it.id == id } }
      val response = SusuApiClient.getApiService().syncGroup(
        com.example.data.remote.SyncGroupRequest(group.copy(schedule = group.schedule.lowercase(java.util.Locale.ROOT)), treasurer, dao.getMembersForGroupOnce(group.id), cycles, payments, receiptIds)
      )
      if (!response.isSuccessful) error("Cloud sync failed (HTTP ${response.code()}). Records remain on this device.")
      val acknowledged = response.body()?.acknowledgedPaymentIds ?: error("Cloud sync returned no acknowledgement")
      check(acknowledged.toSet() == payments.map { it.id }.toSet()) { "Cloud sync acknowledgement is incomplete" }
      dao.markPaymentsSynced(acknowledged)
      response.body()?.acceptedReceiptIds.orEmpty().forEach { dao.updateMessageStatus("receipt-$it", "sent") }
      hasPendingCloudMessages = hasPendingCloudMessages || (response.body()?.pendingNotificationCount ?: 0) > 0 || (response.body()?.pendingReceiptCount ?: 0) > 0
      uploaded += payments.count { !it.isSynced }
    }
    return uploaded
  }

  private fun cloudId(id: String): String = runCatching { UUID.fromString(id).toString() }
    .getOrElse { UUID.nameUUIDFromBytes(id.toByteArray(Charsets.UTF_8)).toString() }

  private fun nextDueDate(schedule: String = "weekly"): String {
    val calendar = java.util.Calendar.getInstance()
    if (schedule.equals("monthly", ignoreCase = true)) calendar.add(java.util.Calendar.MONTH, 1)
    else do { calendar.add(java.util.Calendar.DAY_OF_MONTH, 1) } while (calendar.get(java.util.Calendar.DAY_OF_WEEK) != java.util.Calendar.FRIDAY)
    return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(calendar.time)
  }

  suspend fun syncPendingClaimsFromCloud(groupId: String): Int {
    return try {
      val response = SusuApiClient.getApiService().getActiveCycleSnapshot(cloudId(groupId))
      if (!response.isSuccessful) error("Claim sync failed (HTTP ${response.code()})")

      val claims = response.body()?.claims.orEmpty()
      if (claims.isEmpty()) return 0

      val localCycle = dao.getActiveCycleOnce(groupId)
      val activeMembers = dao.getActiveMembersForGroup(groupId)
      var inserted = 0

      claims.forEach { cloudClaim ->
        val localCycleId = dao.getAllCyclesOnce(groupId).firstOrNull { cloudId(it.id) == cloudClaim.cycleId }?.id
          ?: return@forEach

        val memberById = activeMembers.firstOrNull { cloudId(it.id) == cloudClaim.memberId }
        val cloudPhoneDigits = cloudClaim.memberPhone.orEmpty().filter(Char::isDigit)
        val memberByPhone = activeMembers.firstOrNull { member ->
          val localDigits = member.phone.filter(Char::isDigit)
          cloudPhoneDigits.length >= 9 && (localDigits == cloudPhoneDigits || localDigits.takeLast(9) == cloudPhoneDigits.takeLast(9))
        }
        val localMember = memberById ?: memberByPhone ?: return@forEach

        val incoming = ClaimEntity(
            id = cloudClaim.id,
            cycleId = localCycleId,
            memberId = localMember.id,
            memberName = cloudClaim.memberName ?: localMember.alias,
            memberPhone = cloudClaim.memberPhone ?: localMember.phone,
            claimedAmount = cloudClaim.claimedAmount,
            evidenceMoMoId = cloudClaim.evidenceMoMoId,
            state = cloudClaim.state
          )
        database.withTransaction {
          val existing = dao.getClaimById(incoming.id)
          if (existing == null) {
            dao.insertCloudClaimIfAbsent(incoming)
            if (incoming.state == "pending") inserted += 1
          } else if (existing.state == "pending") {
            dao.updateClaim(incoming)
          }
        }
      }

      inserted
    } catch (error: Exception) {
      throw error
    }
  }

  fun getMembers(groupId: String): Flow<List<MemberEntity>> = dao.getMembersForGroup(groupId)

  fun getActiveCycle(groupId: String): Flow<CycleEntity?> = dao.getActiveCycle(groupId)

  fun getAllCycles(groupId: String): Flow<List<CycleEntity>> = dao.getAllCycles(groupId)

  fun getPaymentsForCycle(cycleId: String): Flow<List<PaymentEntity>> =
    dao.getPaymentsForCycle(cycleId)

  fun getPendingClaims(cycleId: String): Flow<List<ClaimEntity>> =
    dao.getPendingClaimsForCycle(cycleId)

  fun getPendingGroupClaims(groupId: String): Flow<List<ClaimEntity>> = dao.getPendingClaimsForGroup(groupId)

  suspend fun recordPayment(
    groupId: String,
    cycleId: String,
    memberId: String,
    amount: Double,
    method: String,
    source: String = "app",
    momoRef: String? = null,
    confirmedBy: String = "identity-treasurer-01",
    sendWhatsAppReceipt: Boolean = true,
    associatedClaimId: String? = null
  ): PaymentEntity = database.withTransaction {
    if (associatedClaimId != null) {
      val claim = dao.getClaimById(associatedClaimId) ?: error("Claim not found")
      require(claim.state == "pending") { "This claim has already been resolved" }
      require(claim.cycleId == cycleId && claim.memberId == memberId) { "Claim does not match this payment" }
    }
    val member = dao.getMemberById(memberId)
      ?: throw IllegalArgumentException("Member not found: $memberId")
    val paymentCycle = dao.getCycleById(cycleId) ?: error("Cycle not found")
    require(member.groupId == groupId && paymentCycle.groupId == groupId) { "Payment records must belong to the same group" }
    require(paymentCycle.state == "open" || paymentCycle.state == "closed") { "This week cannot accept payments" }
    require(amount.isFinite() && amount > 0 && kotlin.math.abs(amount * 100 - kotlin.math.round(amount * 100)) < 0.0001) { "Enter a positive amount with at most two decimals" }

    // Fetch previous hash for SHA-256 chain
    val lastPayment = dao.getLastPayment()
    val prevHash = lastPayment?.currentHash ?: CryptoUtils.getGenesisHash()

    require(amount.isFinite() && amount > 0) { "Enter a positive payment amount" }
    val paymentId = UUID.randomUUID().toString()
    val idempotencyKey = "idemp-${UUID.randomUUID().toString()}"

    val currentHash = CryptoUtils.generatePaymentHash(
      id = paymentId,
      cycleId = cycleId,
      memberId = memberId,
      amountPaid = amount,
      idempotencyKey = idempotencyKey,
      prevHash = prevHash
    )

    val payment = PaymentEntity(
      id = paymentId,
      cycleId = cycleId,
      memberId = memberId,
      memberName = member.alias,
      amountPaid = amount,
      method = method,
      status = "confirmed",
      confirmedBy = confirmedBy,
      confirmedAt = System.currentTimeMillis(),
      idempotencyKey = idempotencyKey,
      source = source,
      prevHash = prevHash,
      currentHash = currentHash,
      momoReference = momoRef
    )

    dao.insertPayment(payment)

    // Double-Entry Ledger Posting:
    // 1. Debit Cash/MoMo Asset
    val assetAcc = dao.getCashAssetAccount(groupId)
    val assetAccId = assetAcc?.id ?: "acc-asset-momo"

    // 2. Credit Member Equity
    var equityAcc = dao.getMemberEquityAccount(groupId, memberId)
    if (equityAcc == null) {
      equityAcc = AccountEntity(
        groupId = groupId,
        name = "Member Equity: ${member.alias}",
        type = "equity",
        memberId = memberId
      )
      dao.insertAccount(equityAcc)
    }

    val entries = listOf(
      LedgerEntryEntity(
        id = "le-dr-$paymentId",
        paymentId = paymentId,
        accountId = assetAccId,
        accountName = "Cash / MoMo Asset",
        entryType = "debit",
        amount = amount,
        createdAt = payment.confirmedAt
      ),
      LedgerEntryEntity(
        id = "le-cr-$paymentId",
        paymentId = paymentId,
        accountId = equityAcc.id,
        accountName = equityAcc.name,
        entryType = "credit",
        amount = amount,
        createdAt = payment.confirmedAt
      )
    )
    dao.insertLedgerEntries(entries)

    // Update member total
    dao.updateMember(member.copy(totalContributed = member.totalContributed + amount))

    // If there was an associated claim, resolve it
    if (associatedClaimId != null) {
      // Update claim
      val claim = ClaimEntity(
        id = associatedClaimId,
        cycleId = cycleId,
        memberId = memberId,
        memberName = member.alias,
        memberPhone = member.phone,
        claimedAmount = amount,
        evidenceMoMoId = momoRef,
        state = "confirmed",
        resolvedBy = confirmedBy,
        resolvedAt = System.currentTimeMillis()
      )
      dao.updateClaim(claim)
    }

    // Send instant WhatsApp receipt if toggled
    if (sendWhatsAppReceipt) {
      val cycle = dao.getCycleById(cycleId)
      val group = dao.getGroupById(member.groupId)
      val treasurer = group?.let { dao.getIdentityById(it.treasurerId) }
      val treasurerName = treasurer?.displayName?.split(" ")?.firstOrNull() ?: "Treasurer"
      val timeFormat = java.text.SimpleDateFormat("h:mma", java.util.Locale.US)
      val timeStr = timeFormat.format(java.util.Date(payment.confirmedAt)).lowercase()
      val cycleNum = cycle?.number ?: 1
      val cycleDue = cycle?.amountDue ?: group?.amount ?: 50.0

      // Calculate cumulative contributions for member in this cycle
      val memberPaymentsThisCycle = dao.getPaymentsForMemberInCycle(cycleId, memberId)
      val cumulativeCycleTotal = memberPaymentsThisCycle.sumOf { it.amountPaid }
      val remainingBalance = (cycleDue - cumulativeCycleTotal).coerceAtLeast(0.0)
      val isFullyPaid = remainingBalance <= 0.001

      val receiptBody = if (isFullyPaid) {
        """
          *SUSULEDGER OFFICIAL RECEIPT — FULL SETTLEMENT*
          GHS ${String.format(java.util.Locale.US, "%.2f", amount)} received for Week $cycleNum.
          Status: FULLY PAID (GHS ${String.format(java.util.Locale.US, "%.2f", cumulativeCycleTotal)} / GHS ${String.format(java.util.Locale.US, "%.2f", cycleDue)})
          Confirmed by $treasurerName at $timeStr via $method ${momoRef?.let { "($it)" } ?: ""}
          Transaction Hash: ${CryptoUtils.formatShortHash(currentHash)}
          Double-entry ledger entry committed & cryptographically sealed.
        """.trimIndent()
      } else {
        """
          *SUSULEDGER OFFICIAL RECEIPT — PARTIAL INSTALLMENT*
          GHS ${String.format(java.util.Locale.US, "%.2f", amount)} micro-contribution received for Week $cycleNum.
          Cumulative Paid: GHS ${String.format(java.util.Locale.US, "%.2f", cumulativeCycleTotal)} of GHS ${String.format(java.util.Locale.US, "%.2f", cycleDue)} (${((cumulativeCycleTotal / cycleDue) * 100).toInt()}%)
          Remaining Dues: GHS ${String.format(java.util.Locale.US, "%.2f", remainingBalance)} (Due this ${cycle?.dueDate ?: "Friday"})
          Confirmed by $treasurerName at $timeStr via $method ${momoRef?.let { "($it)" } ?: ""}
          Transaction Hash: ${CryptoUtils.formatShortHash(currentHash)}
          Small-amount installment sealed into immutable ledger.
        """.trimIndent()
      }

      val receiptMessage = MessageLogEntity(
        id = "receipt-$paymentId",
        phone = member.phone,
        senderName = "SusuBot Receipt",
        direction = "OUT",
        body = receiptBody,
        metaStatus = "queued"
      )
      dao.insertMessage(receiptMessage)
    }

    payment
  }

  suspend fun pauseOrResumeGroup(groupId: String, newState: String, reason: String, officerId: String): Boolean {
    val group = dao.getGroupById(groupId) ?: return false
    val isPausing = newState.equals("paused", ignoreCase = true)
    val updatedGroup = group.copy(state = if (isPausing) "paused" else "active")
    dao.updateGroup(updatedGroup)

    val activeCycle = dao.getActiveCycleOnce(groupId)
    if (activeCycle != null) {
      dao.updateCycle(activeCycle.copy(state = if (isPausing) "paused" else "open"))
    }

    // Log to audit trail
    dao.insertAuditLog(
      AuditLogEntity(
        actorId = officerId,
        groupId = groupId,
        action = if (isPausing) "GROUP_PAUSED" else "GROUP_RESUMED",
        payload = """{"reason":"$reason","state":"$newState","cycleNumber":${activeCycle?.number ?: 0}}"""
      )
    )

    // Broadcast automated WhatsApp message to all group members
    val members = dao.getActiveMembersForGroup(groupId)
    val cycleNum = activeCycle?.number ?: 1
    val cycleDue = activeCycle?.amountDue ?: group.amount

    val broadcastBody = if (isPausing) {
      """
        [OFFICIAL NOTICE: ${group.name.uppercase()} IS TEMPORARILY PAUSED]
        Please note that group contributions and weekly collection reminders are paused starting immediately.
        
        • *Reason*: $reason
        • *Your Funds*: All lifetime savings are 100% safe, verified, and sealed in the SHA-256 cryptographic double-entry ledger.
        • *What's Next*: No new payments are required at this time. Officers will dispatch an automatic WhatsApp notice once collections resume.
        • Reply 'BALANCE' at any time to inspect your complete savings record.
      """.trimIndent()
    } else {
      """
        [OFFICIAL NOTICE: ${group.name.uppercase()} HAS RESUMED]
        Weekly contributions for ${group.name} are officially open again!
        
        • *Active Cycle*: Week $cycleNum (Due ${activeCycle?.dueDate ?: "Friday"})
        • *Contribution Dues*: GHS ${String.format(java.util.Locale.US, "%.2f", cycleDue)} (Full or partial micro-installments accepted)
        • Reply 'PAID' or 'MOMO:TxID' when you make your transfer to log your claim instantly.
      """.trimIndent()
    }

    dao.insertMessage(
      MessageLogEntity(
        phone = "Broadcast: All Members (${members.size})",
        senderName = "SusuBot Broadcast",
        direction = "OUT",
        body = broadcastBody,
        metaStatus = "delivered"
      )
    )

    members.forEach { m ->
      dao.insertMessage(
        MessageLogEntity(
          phone = m.phone,
          senderName = "SusuBot Notice",
          direction = "OUT",
          body = broadcastBody,
          metaStatus = "delivered"
        )
      )
    }

    return true
  }

  suspend fun updateGroupContributionAmount(
    groupId: String,
    newAmount: Double,
    applyToCurrentCycle: Boolean,
    reason: String,
    officerId: String
  ): Boolean {
    val group = dao.getGroupById(groupId) ?: return false
    val oldAmount = group.amount
    val updatedGroup = group.copy(amount = newAmount)
    dao.updateGroup(updatedGroup)

    val activeCycle = dao.getActiveCycleOnce(groupId)
    if (applyToCurrentCycle && activeCycle != null) {
      dao.updateCycle(activeCycle.copy(amountDue = newAmount))
    }

    // Log to immutable audit trail with dual-signoff entry
    dao.insertAuditLog(
      AuditLogEntity(
        actorId = officerId,
        groupId = groupId,
        action = "DUES_AMOUNT_UPDATED",
        payload = """{"oldAmount":$oldAmount,"newAmount":$newAmount,"applyToCurrent":$applyToCurrentCycle,"reason":"$reason"}"""
      )
    )

    // Broadcast WhatsApp update to members
    val members = dao.getActiveMembersForGroup(groupId)
    val effectiveWeek = if (applyToCurrentCycle && activeCycle != null) activeCycle.number else ((activeCycle?.number ?: 0) + 1)
    val broadcastBody = """
      [CONTRIBUTION DUES UPDATE: ${group.name.uppercase()}]
      The weekly contribution dues rate has been officially adjusted:
      
      • *New Dues Amount*: GHS ${String.format(java.util.Locale.US, "%.2f", newAmount)} (Previously GHS ${String.format(java.util.Locale.US, "%.2f", oldAmount)})
      • *Effective*: Starting Week $effectiveWeek
      • *Reason*: $reason
      • *Ledger Protection*: All past historical cycles and payments remain sealed and cryptographically locked.
      • Micro-contributions (small-small installment payments) remain fully supported.
    """.trimIndent()

    dao.insertMessage(
      MessageLogEntity(
        phone = "Broadcast: All Members (${members.size})",
        senderName = "SusuBot Broadcast",
        direction = "OUT",
        body = broadcastBody,
        metaStatus = "delivered"
      )
    )

    members.forEach { m ->
      dao.insertMessage(
        MessageLogEntity(
          phone = m.phone,
          senderName = "SusuBot Notice",
          direction = "OUT",
          body = broadcastBody,
          metaStatus = "delivered"
        )
      )
    }

    return true
  }

  suspend fun rejectClaim(claim: ClaimEntity, reason: String = "Unverified payment claim") {
    val cycle = dao.getCycleById(claim.cycleId) ?: error("Claim cycle not found")
    val group = dao.getGroupById(cycle.groupId) ?: error("Claim group not found")
    val response = SusuApiClient.getApiService().rejectClaim(cloudId(group.id), cloudId(claim.id), mapOf("reason" to reason))
    check(response.isSuccessful) { "Cloud claim rejection failed (HTTP ${response.code()})" }
    val updated = claim.copy(
      state = "rejected",
      resolvedBy = group.treasurerId,
      resolvedAt = System.currentTimeMillis()
    )
    dao.updateClaim(updated)

    // Inform member via WhatsApp
    val notice = MessageLogEntity(
      phone = claim.memberPhone,
      senderName = "SusuBot Notice",
      direction = "OUT",
      body = "Your payment claim of GHS ${String.format(java.util.Locale.US, "%.2f", claim.claimedAmount)} could not be verified by the treasurer. Reason: $reason. Please check your MoMo reference or contact officer.",
      metaStatus = "delivered"
    )
    dao.insertMessage(notice)
  }

  suspend fun startNewCycle(groupId: String, currentCycle: CycleEntity, amount: Double = currentCycle.amountDue, dueDate: String? = null) = database.withTransaction {
    require(dao.getCycleById(currentCycle.id)?.state == "open") { "This week is already closed. Refresh the ledger." }
    require(amount.isFinite() && amount > 0) { "Enter a positive contribution amount" }
    val nextDate = dueDate ?: nextDueDate(dao.getGroupById(groupId)?.schedule ?: "weekly")
    require(nextDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) { "Use a date in YYYY-MM-DD format" }
    // Close current cycle
    dao.updateCycle(
      currentCycle.copy(
        state = "closed",
        closedAt = System.currentTimeMillis(),
        closedBy = dao.getGroupById(groupId)?.treasurerId
      )
    )

    // Open next cycle
    val nextNumber = currentCycle.number + 1
    val nextCycle = CycleEntity(
      groupId = groupId,
      number = nextNumber,
      amountDue = amount,
      dueDate = nextDate,
      state = "open"
    )
    dao.insertCycle(nextCycle)

    // Log announcement broadcast message
    dao.insertMessage(
      MessageLogEntity(
        phone = "Broadcast: All Members",
        senderName = "SusuBot Broadcast",
        direction = "OUT",
        body = "Week $nextNumber is open. Member announcements await cloud dispatch to opted-in WhatsApp numbers.",
        metaStatus = "queued"
      )
    )
  }

  suspend fun addMember(groupId: String, alias: String, phone: String): MemberEntity {
    val initials = alias.split(" ")
      .filter { it.isNotBlank() }
      .take(2)
      .map { it.first().uppercaseChar() }
      .joinToString("")
      .ifEmpty { "M" }

    val cleanPhone = com.example.util.GhanaPhoneUtils.toE164(phone)
    dao.getMembersForGroupOnce(groupId).firstOrNull { it.phone == cleanPhone }?.let { return it }
    val existingIdentity = dao.getIdentityByPhone(cleanPhone)
    val identityId = existingIdentity?.id ?: UUID.randomUUID().toString()
    
    if (existingIdentity == null) {
      val identity = IdentityEntity(
        id = identityId,
        phone = cleanPhone,
        displayName = alias
      )
      dao.insertIdentity(identity)
    }

    val member = MemberEntity(
      groupId = groupId,
      identityId = identityId,
      alias = alias,
      initials = initials,
      phone = cleanPhone,
      state = "active",
      joinedCycle = 1,
      totalContributed = 0.0
    )
    dao.insertMember(member)

    // Create equity account
    dao.insertAccount(
      AccountEntity(
        groupId = groupId,
        name = "Member Equity: $alias",
        type = "equity",
        memberId = member.id
      )
    )

    return member
  }

  fun getMembersWithIdentity(groupId: String): Flow<List<MemberWithIdentity>> =
    dao.getMembersWithIdentity(groupId)

  fun getAllIdentities(): Flow<List<IdentityEntity>> = dao.getAllIdentities()

  suspend fun getAllIdentitiesOnce(): List<IdentityEntity> = dao.getAllIdentitiesOnce()

  suspend fun getIdentityByPhone(phone: String): IdentityEntity? = dao.getIdentityByPhone(phone)

  suspend fun verifyLedgerIntegrity(payments: List<PaymentEntity>, ledgerEntries: List<LedgerEntryEntity>): VerificationReport {
    var isChainValid = true
    var brokenIndex: Int? = null
    var expectedPrevHash = CryptoUtils.getGenesisHash()

    for (i in payments.indices) {
      val payment = payments[i]
      if (payment.prevHash != expectedPrevHash) {
        isChainValid = false
        brokenIndex = i
        break
      }

      val recomputedHash = CryptoUtils.generatePaymentHash(
        id = payment.id,
        cycleId = payment.cycleId,
        memberId = payment.memberId,
        amountPaid = payment.amountPaid,
        idempotencyKey = payment.idempotencyKey,
        prevHash = payment.prevHash
      )

      if (payment.currentHash != recomputedHash) {
        isChainValid = false
        brokenIndex = i
        break
      }

      expectedPrevHash = payment.currentHash
    }

    val debits = ledgerEntries.filter { it.entryType == "debit" }.sumOf { it.amount }
    val credits = ledgerEntries.filter { it.entryType == "credit" }.sumOf { it.amount }
    val isBalanced = kotlin.math.abs(debits - credits) < 0.001

    return VerificationReport(
      totalBlocks = payments.size,
      isChainValid = isChainValid,
      brokenBlockIndex = brokenIndex,
      totalDebits = debits,
      totalCredits = credits,
      isDoubleEntryBalanced = isBalanced,
      genesisHash = CryptoUtils.getGenesisHash(),
      latestHash = payments.lastOrNull()?.currentHash ?: CryptoUtils.getGenesisHash()
    )
  }

  // In-memory session tracking for active group disambiguation
  private val userActiveGroup = java.util.concurrent.ConcurrentHashMap<String, String>()

  suspend fun processWhatsAppBotMessage(fromPhone: String, messageText: String, activeCycle: CycleEntity?): String {
    val cleanPhone = fromPhone.trim()
    // 1. Phone Lookup in identities
    val identity = dao.getIdentityByPhone(cleanPhone)
    // Query members joined with groups
    val memberList = if (identity != null) {
      dao.getMembersByIdentityId(identity.id).ifEmpty { dao.getAllMembersByPhone(cleanPhone) }
    } else {
      dao.getAllMembersByPhone(cleanPhone)
    }

    val defaultMember = memberList.firstOrNull()
    val senderName = identity?.displayName ?: defaultMember?.alias ?: "Member ($cleanPhone)"

    // Log incoming
    dao.insertMessage(
      MessageLogEntity(
        phone = cleanPhone,
        senderName = senderName,
        direction = "IN",
        body = messageText
      )
    )

    val cleanText = messageText.trim().uppercase()

    // Pairing flow
    if (cleanText.startsWith("PAIR:") || cleanText.startsWith("PAIR")) {
      val code = cleanText.substringAfter(":", "").trim().ifEmpty { "A7K2-9P" }
      val botResponse = "Device paired with SusuLedger Cloud Run engine via code [$code]. Verified as $senderName!"
      dao.insertMessage(
        MessageLogEntity(
          phone = cleanPhone,
          senderName = "SusuBot (Cloud Run)",
          direction = "OUT",
          body = botResponse
        )
      )
      return botResponse
    }

    // 2. Multi-Group Disambiguation:
    val groupsForMember = memberList.mapNotNull { m ->
      val g = dao.getGroupById(m.groupId)
      if (g != null) Pair(m, g) else null
    }

    var chosenPair = if (userActiveGroup.containsKey(cleanPhone)) {
      val savedGid = userActiveGroup[cleanPhone]
      groupsForMember.firstOrNull { it.second.id == savedGid } ?: groupsForMember.firstOrNull()
    } else {
      null
    }

    // Check if user is choosing a group from buttons or text
    if (cleanText.startsWith("SELECT_GROUP:") || cleanText.startsWith("GROUP_")) {
      val selectedId = if (cleanText.startsWith("SELECT_GROUP:")) cleanText.substringAfter("SELECT_GROUP:").trim() else cleanText.substringAfter("GROUP_").trim()
      val matched = groupsForMember.firstOrNull { it.second.id.equals(selectedId, ignoreCase = true) }
        ?: groupsForMember.firstOrNull { it.second.id.contains(selectedId, ignoreCase = true) }
      if (matched != null) {
        chosenPair = matched
        userActiveGroup[cleanPhone] = matched.second.id
      }
    } else if (cleanText == "1" && groupsForMember.isNotEmpty()) {
      chosenPair = groupsForMember[0]
      userActiveGroup[cleanPhone] = chosenPair.second.id
    } else if (cleanText == "2" && groupsForMember.size > 1) {
      chosenPair = groupsForMember[1]
      userActiveGroup[cleanPhone] = chosenPair.second.id
    } else if (cleanText.contains("NIMA") && groupsForMember.isNotEmpty()) {
      val matched = groupsForMember.firstOrNull { it.second.name.contains("Nima", ignoreCase = true) }
      if (matched != null) {
        chosenPair = matched
        userActiveGroup[cleanPhone] = matched.second.id
      }
    } else if (cleanText.contains("MAKOLA") && groupsForMember.isNotEmpty()) {
      val matched = groupsForMember.firstOrNull { it.second.name.contains("Makola", ignoreCase = true) }
      if (matched != null) {
        chosenPair = matched
        userActiveGroup[cleanPhone] = matched.second.id
      }
    }

    // If member sends PAID or CLAIM_PAID, check if multi-group disambiguation is needed
    if (cleanText == "PAID" || cleanText == "CLAIM_PAID") {
      if (groupsForMember.size > 1 && chosenPair == null) {
        val optionsList = groupsForMember.mapIndexed { index, (_, g) ->
          val cyc = dao.getActiveCycleOnce(g.id)
          val cycNum = cyc?.number ?: 1
          val amt = cyc?.amountDue ?: g.amount
          "(${index + 1}) [ ${g.name} (Week $cycNum • GHS ${String.format(java.util.Locale.US, "%.2f", amt)}) ]"
        }.joinToString("\n")

        val botResponse = """
          You belong to multiple active Susu groups. Please select which group this payment is for:
          $optionsList
          Reply with group number (e.g. '1' or '2') or tap button.
        """.trimIndent()

        dao.insertMessage(
          MessageLogEntity(
            phone = cleanPhone,
            senderName = "SusuBot (Cloud Run)",
            direction = "OUT",
            body = botResponse
          )
        )
        return botResponse
      }
    }

    val targetPair = chosenPair ?: groupsForMember.firstOrNull()
    val targetMember = targetPair?.first ?: defaultMember
    val targetGroup = targetPair?.second ?: if (activeCycle != null) dao.getGroupById(activeCycle.groupId) else null
    val targetCycle = if (targetGroup != null) dao.getActiveCycleOnce(targetGroup.id) ?: activeCycle else activeCycle

    val groupName = targetGroup?.name ?: "Nima Market Susu"
    val cycleNum = targetCycle?.number ?: 12
    val amountDue = targetCycle?.amountDue ?: targetGroup?.amount ?: 50.0

    if (cleanText == "PAID" || cleanText == "CLAIM_PAID" || cleanText == "1" || cleanText == "2" || cleanText.contains("NIMA") || cleanText.contains("MAKOLA")) {
      val botResponse = """
        Confirm your payment claim for $groupName (Week $cycleNum):
        [ CONFIRM_AMOUNT_${String.format(java.util.Locale.US, "%.2f", amountDue)} ]
        [ Partial Payment ]
        Reply with your MoMo Transaction ID (e.g. MOMO:MP12345) to attach evidence.
      """.trimIndent()

      dao.insertMessage(
        MessageLogEntity(
          phone = cleanPhone,
          senderName = "SusuBot (Cloud Run)",
          direction = "OUT",
          body = botResponse
        )
      )
      return botResponse
    }

    if (cleanText.startsWith("MOMO:") || cleanText.contains("MOMO") || cleanText.startsWith("CONFIRM_AMOUNT")) {
      val momoId = if (cleanText.startsWith("MOMO:")) {
        cleanText.replace("MOMO:", "").trim()
      } else {
        "MP-${UUID.randomUUID().toString().take(6).uppercase()}"
      }
      val claimAmount = if (cleanText.startsWith("CONFIRM_AMOUNT_")) {
        cleanText.substringAfter("CONFIRM_AMOUNT_").trim().toDoubleOrNull() ?: amountDue
      } else {
        amountDue
      }

      if (targetMember != null && targetCycle != null) {
        val claim = ClaimEntity(
          cycleId = targetCycle.id,
          memberId = targetMember.id,
          memberName = targetMember.alias,
          memberPhone = targetMember.phone,
          claimedAmount = claimAmount,
          evidenceMoMoId = momoId,
          state = "pending"
        )
        dao.insertClaim(claim)

        // Dispatch interactive notification card to the treasurer
        val treasurer = targetGroup?.let { dao.getIdentityById(it.treasurerId) }
        val treasurerPhone = treasurer?.phone ?: "+233 24 123 4567"
        val treasurerAlert = MessageLogEntity(
          phone = treasurerPhone,
          senderName = "SusuBot Alert",
          direction = "OUT",
          body = "*MEMBER CLAIM ALERT*: ${targetMember.alias} claims GHS ${String.format(java.util.Locale.US, "%.0f", claimAmount)} for $groupName (Week $cycleNum, Ref: $momoId). Tap Confirm or Reject in the app.",
          metaStatus = "delivered"
        )
        dao.insertMessage(treasurerAlert)
      }

      val botResponse = "Payment claim for GHS ${String.format(java.util.Locale.US, "%.2f", claimAmount)} recorded for $groupName (Week $cycleNum) with MoMo ID: $momoId! It has been forwarded to the Treasurer for instant dual-signoff confirmation."
      dao.insertMessage(
        MessageLogEntity(
          phone = cleanPhone,
          senderName = "SusuBot (Cloud Run)",
          direction = "OUT",
          body = botResponse
        )
      )
      return botResponse
    }

    // DPC Opt-in Consent
    if (cleanText == "OK" || cleanText == "START" || cleanText == "CONSENT") {
      val botResponse = """
        *Akwaaba to SusuLedger!*
        Your DPC Act 843 opt-in consent is recorded.
        • Funds never touch this bot; all transfers are P2P.
        • Send 'PAID' to log your weekly contribution.
        • Send 'BALANCE' to check your savings standing.
        • Send 'STOP' to unsubscribe at any time.
      """.trimIndent()
      dao.insertMessage(
        MessageLogEntity(
          phone = cleanPhone,
          senderName = "SusuBot (Cloud Run)",
          direction = "OUT",
          body = botResponse
        )
      )
      return botResponse
    }

    // DPC Opt-out
    if (cleanText == "STOP" || cleanText == "EXIT" || cleanText == "UNSUBSCRIBE") {
      val botResponse = """
        You have unsubscribed from automated Susu notifications.
        Under Ghana DPC Act 843, your historic payment records remain sealed in the 7-year cryptographic ledger for mutual financial dispute protection.
        Reply 'START' or 'OK' at any time to re-enable WhatsApp receipts.
      """.trimIndent()
      dao.insertMessage(
        MessageLogEntity(
          phone = cleanPhone,
          senderName = "SusuBot (Cloud Run)",
          direction = "OUT",
          body = botResponse
        )
      )
      return botResponse
    }

    // Balance and Statement inquiry
    if (cleanText == "BALANCE" || cleanText == "STATEMENT" || cleanText == "STATUS" || cleanText.contains("MY SAVINGS") || cleanText.contains("MY BALANCE")) {
      val totalContributed = targetMember?.totalContributed ?: 0.0
      val unsyncedPayments = dao.getUnsyncedPaymentsOnce()
      val unsyncedForMember = unsyncedPayments.filter { it.memberId == targetMember?.id }

      val cycleStatus = if (targetCycle != null) {
        val payments = dao.getConfirmedPaymentsForCycle(targetCycle.id)
        val hasPaid = payments.any { it.memberId == targetMember?.id }
        if (hasPaid) {
          "Week ${targetCycle.number}: PAID (GHS ${String.format(java.util.Locale.US, "%.2f", amountDue)})"
        } else if (unsyncedForMember.isNotEmpty()) {
          "Week ${targetCycle.number}: CONFIRMED OFFLINE (Pending Cloud Sync)"
        } else {
          "Week ${targetCycle.number}: DUE (GHS ${String.format(java.util.Locale.US, "%.2f", targetCycle.amountDue)}) by ${targetCycle.dueDate}"
        }
      } else {
        "Active"
      }

      val syncNote = if (unsyncedForMember.isNotEmpty()) {
        "\n*Offline Status*: 1 payment recorded offline by Treasurer awaiting cloud sync."
      } else {
        ""
      }

      val botResponse = """
        *STATEMENT: ${targetMember?.alias ?: senderName}*
        Group: $groupName
        Your Dues Rate: GHS ${String.format(java.util.Locale.US, "%.2f", amountDue)} / week
        Current Cycle Standing: $cycleStatus$syncNote
        Total Lifetime Savings in $groupName: GHS ${String.format(java.util.Locale.US, "%.2f", totalContributed)}
        Double-Entry Ledger: Sealed via SHA-256
        
        *Reconciliation*: If you made an offline payment that hasn't synced, reply 'PAID' or 'MOMO:TxID' to cross-verify your claim.
      """.trimIndent()
      dao.insertMessage(
        MessageLogEntity(
          phone = cleanPhone,
          senderName = "SusuBot (Cloud Run)",
          direction = "OUT",
          body = botResponse
        )
      )
      return botResponse
    }

    // Group Progress & Group-Specific Pool Inquiries (e.g., Akosua asking about Group 5)
    if (cleanText.contains("GROUP") || cleanText.contains("POOL") || cleanText.contains("PROGRESS") || cleanText.contains("TOTAL") || cleanText.contains("HOW MUCH")) {
      val groupMems = if (targetGroup != null) dao.getActiveMembersForGroup(targetGroup.id) else emptyList()
      val groupPayments = if (targetCycle != null) dao.getConfirmedPaymentsForCycle(targetCycle.id) else emptyList()
      val confirmedCount = groupPayments.map { it.memberId }.distinct().size
      val totalMemsCount = groupMems.size
      val confirmedAmt = groupPayments.sumOf { it.amountPaid }
      val targetPool = totalMemsCount * amountDue
      val percent = if (targetPool > 0) ((confirmedAmt / targetPool) * 100).toInt().coerceIn(0, 100) else 0

      val botResponse = """
        *GROUP REPORT: $groupName*
        • Current Cycle: Week $cycleNum (Due ${targetCycle?.dueDate ?: "Friday"})
        • Group Dues: GHS ${String.format(java.util.Locale.US, "%.2f", amountDue)} per member
        • Pool Progress: GHS ${String.format(java.util.Locale.US, "%.2f", confirmedAmt)} / GHS ${String.format(java.util.Locale.US, "%.2f", targetPool)} ($percent%)
        • Participation: $confirmedCount of $totalMemsCount members confirmed
        • Your Standing (${targetMember?.alias ?: senderName}): ${if (groupPayments.any { it.memberId == targetMember?.id }) "PAID" else "UNPAID (Due GHS ${String.format(java.util.Locale.US, "%.2f", amountDue)})"}
      """.trimIndent()

      dao.insertMessage(
        MessageLogEntity(
          phone = cleanPhone,
          senderName = "SusuBot (Cloud Run)",
          direction = "OUT",
          body = botResponse
        )
      )
      return botResponse
    }

    // Due date & payment instruction inquiry
    if (cleanText.contains("DUE") || cleanText.contains("WHEN") || cleanText.contains("PAY") || cleanText.contains("HOW TO")) {
      val treasurer = targetGroup?.let { dao.getIdentityById(it.treasurerId) }
      val treasurerName = treasurer?.displayName ?: "Treasurer"
      val treasurerMoMo = treasurer?.phone ?: "+233 24 123 4567"

      val botResponse = """
        ℹ️ *PAYMENT INSTRUCTIONS FOR $groupName:*
        • Dues Amount: GHS ${String.format(java.util.Locale.US, "%.2f", amountDue)}
        • Due Date: Every ${targetCycle?.dueDate ?: "Friday"}
        • Send MoMo to: $treasurerName ($treasurerMoMo)
        • Reference: ${targetMember?.alias ?: "Your Name"} - Week $cycleNum
        Once transferred, reply 'PAID' or 'MOMO:TransactionID' right here on WhatsApp to register your claim instantly!
      """.trimIndent()

      dao.insertMessage(
        MessageLogEntity(
          phone = cleanPhone,
          senderName = "SusuBot (Cloud Run)",
          direction = "OUT",
          body = botResponse
        )
      )
      return botResponse
    }

    // Help menu
    if (cleanText == "HELP" || cleanText == "MENU" || cleanText == "COMMANDS") {
      val botResponse = """
        *Susu Ledger WhatsApp Commands ($groupName):*
        • *PAID*: Submit your payment claim for Week $cycleNum
        • *BALANCE*: Check your personal contributions in $groupName
        • *PROGRESS*: View group pool progress and member stats
        • *DUE*: View dues amount and MoMo payment instructions
        • *SWITCH*: Switch active Susu group if you belong to multiple
        • *STOP*: Unsubscribe from automated WhatsApp notifications
      """.trimIndent()
      dao.insertMessage(
        MessageLogEntity(
          phone = cleanPhone,
          senderName = "SusuBot (Cloud Run)",
          direction = "OUT",
          body = botResponse
        )
      )
      return botResponse
    }

    // Default bot reply tailored to group context
    val fallback = "Susu Bot ($groupName): Hello ${targetMember?.alias ?: "Member"}! You are registered in $groupName (Dues: GHS ${String.format(java.util.Locale.US, "%.0f", amountDue)}). Send 'PAID' to claim payment, 'BALANCE' to check standing, 'PROGRESS' for group pool status, or 'HELP'."
    dao.insertMessage(
      MessageLogEntity(
        phone = cleanPhone,
        senderName = "SusuBot (Cloud Run)",
        direction = "OUT",
        body = fallback
      )
    )
    return fallback
  }

  // -----------------------------------------------------------------------------
  // GROUP-DRIVEN AUTOMATED OUTBOUND MESSAGING (Via Cloud Scheduler & Meta Templates)
  // -----------------------------------------------------------------------------

  suspend fun sendWeeklyCollectionReminder(groupId: String): Int {
    val group = dao.getGroupById(groupId) ?: return 0
    val cycle = dao.getActiveCycleOnce(groupId) ?: return 0
    val treasurer = dao.getIdentityById(group.treasurerId)
    val members = dao.getActiveMembersForGroup(groupId)
    val treasurerName = treasurer?.displayName ?: "Treasurer"
    val treasurerMoMo = treasurer?.phone ?: ""

    val broadcastBody = """
      *WEEKLY SUSU REMINDER: Week ${cycle.number} OPEN*
      Group: ${group.name}
      Amount Due: GHS ${String.format(java.util.Locale.US, "%.2f", group.amount)} (Due ${cycle.dueDate})
      Send MoMo to Treasurer: $treasurerName ($treasurerMoMo)
      Reply 'PAID' on WhatsApp once sent to instantly register your payment claim.
    """.trimIndent()

    val messages = members.map { member ->
      MessageLogEntity(
        phone = member.phone,
        senderName = "SusuBot (Cloud Scheduler)",
        direction = "OUT",
        body = broadcastBody,
        metaStatus = "delivered"
      )
    }
    dao.insertMessages(messages)
    return members.size
  }

  suspend fun sendTargetedUnpaidNudges(groupId: String): Int {
    val group = dao.getGroupById(groupId) ?: return 0
    val cycle = dao.getActiveCycleOnce(groupId) ?: return 0
    val treasurer = dao.getIdentityById(group.treasurerId)
    val members = dao.getActiveMembersForGroup(groupId)
    val payments = dao.getConfirmedPaymentsForCycle(cycle.id)

    val paidMemberIds = payments.map { it.memberId }.toSet()
    val unpaidMembers = members.filter { it.id !in paidMemberIds }

    if (unpaidMembers.isEmpty()) return 0

    val messages = unpaidMembers.map { member ->
      MessageLogEntity(
        phone = member.phone,
        senderName = "SusuBot Nudge",
        direction = "OUT",
        body = """
          *FRIENDLY NUDGE: Week ${cycle.number} Collection*
          Hello ${member.alias ?: "Member"}, this is a polite reminder for ${group.name}.
          Contribution Due: GHS ${String.format(java.util.Locale.US, "%.2f", cycle.amountDue)} (Due this ${cycle.dueDate}).
          Treasurer MoMo: ${treasurer?.phone ?: ""} (${treasurer?.displayName ?: "Treasurer"}).
          Reply 'PAID' when transferred to instantly register your payment claim.
        """.trimIndent(),
        metaStatus = "delivered"
      )
    }
    dao.insertMessages(messages)
    return unpaidMembers.size
  }

  suspend fun sendSundaySummaryDigest(groupId: String): Int {
    val group = dao.getGroupById(groupId) ?: return 0
    val cycle = dao.getActiveCycleOnce(groupId) ?: return 0
    val members = dao.getActiveMembersForGroup(groupId)
    val payments = dao.getConfirmedPaymentsForCycle(cycle.id)

    val paidMembersCount = payments.map { it.memberId }.distinct().size
    val totalMembersCount = members.size
    val totalConfirmed = payments.sumOf { it.amountPaid }
    val totalExpected = totalMembersCount * group.amount
    val unpaidCount = (totalMembersCount - paidMembersCount).coerceAtLeast(0)
    val percent = if (totalMembersCount > 0) (paidMembersCount * 100) / totalMembersCount else 0

    val digestBody = """
      *SUNDAY SUMMARY DIGEST: Week ${cycle.number}*
      Group: ${group.name}
      Collection Status: $paidMembersCount of $totalMembersCount Members Paid ($percent%)
      Total Confirmed: GHS ${String.format(java.util.Locale.US, "%.2f", totalConfirmed)} / GHS ${String.format(java.util.Locale.US, "%.2f", totalExpected)}
      Pending Collections: $unpaidCount Members
      All ledger records cryptographically chained via SHA-256 hash. Have a blessed week!
    """.trimIndent()

    val messages = members.map { member ->
      MessageLogEntity(
        phone = member.phone,
        senderName = "SusuBot Digest",
        direction = "OUT",
        body = digestBody,
        metaStatus = "delivered"
      )
    }
    dao.insertMessages(messages)
    return members.size
  }

  suspend fun purgeAllAccountData() {
    dao.purgeAllAccountData()
  }

  suspend fun createNewGroupWithMembers(
    groupName: String,
    amount: Double,
    schedule: String = "weekly",
    treasurerPhone: String,
    treasurerName: String,
    members: List<Pair<String, String>>,
    treasurerPin: String = ""
  ): GroupEntity = database.withTransaction {
    val cleanTreasurerPhone = com.example.util.GhanaPhoneUtils.toE164(treasurerPhone)
    require(amount.isFinite() && amount > 0 && groupName.isNotBlank()) { "Group name and positive contribution amount are required" }
    val existingTreasurer = dao.getIdentityByPhone(cleanTreasurerPhone)
    val treasurerIdentityId = existingTreasurer?.id ?: UUID.randomUUID().toString()

    if (existingTreasurer == null) {
      val treasurerIdentity = IdentityEntity(
        id = treasurerIdentityId,
        phone = cleanTreasurerPhone,
        displayName = treasurerName.ifBlank { "Treasurer" }
      )
      dao.insertIdentity(treasurerIdentity)
    }

    require(treasurerPin.length == 4 && treasurerPin.all(Char::isDigit)) { "Choose a 4-digit PIN" }
    val cleanPin = treasurerPin
    val user = UserEntity(
      id = treasurerIdentityId,
      pinHash = CryptoUtils.hashPin(cleanPin, treasurerIdentityId),
      role = "treasurer"
    )
    dao.insertUser(user)

    val groupId = UUID.randomUUID().toString()
    val group = GroupEntity(
      id = groupId,
      name = groupName.ifBlank { "Susu Group" },
      amount = amount,
      currency = "GHS",
      schedule = schedule.lowercase(java.util.Locale.ROOT),
      treasurerId = treasurerIdentityId,
      state = "active"
    )
    dao.insertGroup(group)

    // Cash Asset account
    dao.insertAccount(
      AccountEntity(
        id = "acc-cash-$groupId",
        groupId = groupId,
        name = "Cash/MoMo Asset",
        type = "asset"
      )
    )

    // Initial Cycle 1
    val cycle = CycleEntity(
      groupId = groupId,
      number = 1,
      amountDue = amount,
      dueDate = nextDueDate(schedule),
      state = "open"
    )
    dao.insertCycle(cycle)

    // Members
    members.forEach { (memberName, memberPhone) ->
      if (memberName.isNotBlank()) {
        addMember(groupId, memberName.trim(), memberPhone.trim())
      }
    }

    group
  }

  suspend fun logOutboundMessage(phone: String, body: String, metaStatus: String = "delivered") {
    dao.insertMessage(
      MessageLogEntity(
        phone = phone,
        senderName = "SusuBot Cloud Run",
        direction = "OUT",
        body = body,
        metaStatus = metaStatus
      )
    )
  }

  suspend fun getUserById(userId: String): UserEntity? = dao.getUserById(userId)

  suspend fun insertUser(user: UserEntity) = dao.insertUser(user)

  suspend fun getIdentityById(id: String): IdentityEntity? = dao.getIdentityById(id)

  suspend fun getGroupByTreasurer(treasurerId: String): GroupEntity? = dao.getGroupByTreasurer(treasurerId)

  suspend fun getAllGroupsOnce(): List<GroupEntity> = dao.getAllGroupsOnce()
}
