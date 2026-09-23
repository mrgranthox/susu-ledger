package com.example.data.remote

import com.example.data.local.*

data class AccountBackup(
  val version: Int,
  val identities: List<IdentityEntity>,
  val groups: List<GroupEntity>,
  val members: List<MemberEntity>,
  val cycles: List<CycleEntity>,
  val payments: List<PaymentEntity>,
  val accounts: List<AccountEntity>,
  val entries: List<LedgerEntryEntity>,
  val claims: List<ClaimEntity>,
  val corrections: List<LedgerCorrectionEntity>,
  val audits: List<AuditLogEntity>
)
