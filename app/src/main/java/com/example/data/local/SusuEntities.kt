package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.util.UUID

/**
 * 1. IDENTITIES & USERS
 * Room Entity mapping to PostgreSQL 'identities' table.
 */
@Entity(
  tableName = "identities",
  indices = [
    Index(value = ["phone"], unique = true)
  ]
)
data class IdentityEntity(
  @PrimaryKey
  @ColumnInfo(name = "id")
  val id: String = UUID.randomUUID().toString(),

  @ColumnInfo(name = "phone")
  val phone: String,

  @ColumnInfo(name = "display_name")
  val displayName: String,

  @ColumnInfo(name = "created_at")
  val createdAt: Long = System.currentTimeMillis()
)

/**
 * Room Entity mapping to PostgreSQL 'users' table.
 * References identities(id) on delete cascade.
 */
@Entity(
  tableName = "users"
)
data class UserEntity(
  @PrimaryKey
  @ColumnInfo(name = "id")
  val id: String, // References identities(id)

  @ColumnInfo(name = "pin_hash")
  val pinHash: String,

  @ColumnInfo(name = "role")
  val role: String = "treasurer", // 'treasurer', 'second_officer'

  @ColumnInfo(name = "created_at")
  val createdAt: Long = System.currentTimeMillis()
)

/**
 * 2. GROUPS & MEMBERS
 * Room Entity mapping to PostgreSQL 'groups' table.
 */
@Entity(
  tableName = "groups",
  indices = [
    Index(value = ["treasurer_id"]),
    Index(value = ["officer_id"])
  ],
  foreignKeys = [
    ForeignKey(
      entity = IdentityEntity::class,
      parentColumns = ["id"],
      childColumns = ["treasurer_id"],
      onDelete = ForeignKey.RESTRICT
    )
  ]
)
data class GroupEntity(
  @PrimaryKey
  @ColumnInfo(name = "id")
  val id: String = UUID.randomUUID().toString(),

  @ColumnInfo(name = "name")
  val name: String,

  @ColumnInfo(name = "amount")
  val amount: Double,

  @ColumnInfo(name = "currency")
  val currency: String = "GHS",

  @ColumnInfo(name = "schedule")
  val schedule: String = "weekly", // 'weekly', 'monthly'

  @ColumnInfo(name = "treasurer_id")
  val treasurerId: String, // References identities(id)

  @ColumnInfo(name = "officer_id")
  val officerId: String? = null, // References identities(id)

  @ColumnInfo(name = "state")
  val state: String = "active", // 'active', 'paused', 'closed'

  @ColumnInfo(name = "created_at")
  val createdAt: Long = System.currentTimeMillis()
)

/**
 * Room Entity mapping to PostgreSQL 'members' table.
 * Enforces UNIQUE(group_id, identity_id).
 */
@Entity(
  tableName = "members",
  indices = [
    Index(value = ["group_id", "identity_id"], unique = true),
    Index(value = ["group_id"]),
    Index(value = ["identity_id"])
  ],
  foreignKeys = [
    ForeignKey(
      entity = GroupEntity::class,
      parentColumns = ["id"],
      childColumns = ["group_id"],
      onDelete = ForeignKey.CASCADE
    ),
    ForeignKey(
      entity = IdentityEntity::class,
      parentColumns = ["id"],
      childColumns = ["identity_id"],
      onDelete = ForeignKey.RESTRICT
    )
  ]
)
data class MemberEntity(
  @PrimaryKey
  @ColumnInfo(name = "id")
  val id: String = UUID.randomUUID().toString(),

  @ColumnInfo(name = "group_id")
  val groupId: String, // References groups(id)

  @ColumnInfo(name = "identity_id")
  val identityId: String = UUID.randomUUID().toString(), // References identities(id)

  @ColumnInfo(name = "alias")
  val alias: String, // e.g. "Kofi Mensah"

  @ColumnInfo(name = "state")
  val state: String = "active", // 'active', 'paused', 'exited'

  @ColumnInfo(name = "joined_cycle")
  val joinedCycle: Int = 1,

  @ColumnInfo(name = "exited_cycle")
  val exitedCycle: Int? = null,

  // Offline-first convenience denormalized fields for quick UI access without joins:
  @ColumnInfo(name = "initials")
  val initials: String = "",

  @ColumnInfo(name = "phone")
  val phone: String = "",

  @ColumnInfo(name = "total_contributed")
  val totalContributed: Double = 0.0
)

/**
 * Joined relationship helper for offline querying member with full identity
 */
data class MemberWithIdentity(
  @Embedded val member: MemberEntity,
  @Relation(
    parentColumn = "identity_id",
    entityColumn = "id"
  )
  val identity: IdentityEntity?
)

/**
 * 3. CYCLES
 * Room Entity mapping to PostgreSQL 'cycles' table.
 * Enforces UNIQUE(group_id, number).
 */
@Entity(
  tableName = "cycles",
  indices = [
    Index(value = ["group_id", "number"], unique = true),
    Index(value = ["group_id"]),
    Index(value = ["closed_by"])
  ],
  foreignKeys = [
    ForeignKey(
      entity = GroupEntity::class,
      parentColumns = ["id"],
      childColumns = ["group_id"],
      onDelete = ForeignKey.CASCADE
    )
  ]
)
data class CycleEntity(
  @PrimaryKey
  @ColumnInfo(name = "id")
  val id: String = UUID.randomUUID().toString(),

  @ColumnInfo(name = "group_id")
  val groupId: String, // References groups(id)

  @ColumnInfo(name = "number")
  val number: Int,

  @ColumnInfo(name = "amount_due")
  val amountDue: Double,

  @ColumnInfo(name = "due_date")
  val dueDate: String, // e.g. "Friday"

  @ColumnInfo(name = "state")
  val state: String = "open", // 'open', 'closed', 'paused'

  @ColumnInfo(name = "closed_at")
  val closedAt: Long? = null,

  @ColumnInfo(name = "closed_by")
  val closedBy: String? = null // References identities(id)
)

/**
 * 4. DOUBLE-ENTRY CHART OF ACCOUNTS
 * Room Entity mapping to PostgreSQL 'accounts' table.
 */
@Entity(
  tableName = "accounts",
  indices = [
    Index(value = ["group_id"]),
    Index(value = ["member_id"])
  ],
  foreignKeys = [
    ForeignKey(
      entity = GroupEntity::class,
      parentColumns = ["id"],
      childColumns = ["group_id"],
      onDelete = ForeignKey.CASCADE
    )
  ]
)
data class AccountEntity(
  @PrimaryKey
  @ColumnInfo(name = "id")
  val id: String = UUID.randomUUID().toString(),

  @ColumnInfo(name = "group_id")
  val groupId: String, // References groups(id)

  @ColumnInfo(name = "name")
  val name: String, // e.g. 'Cash/MoMo Asset', 'Member Equity: Kofi'

  @ColumnInfo(name = "type")
  val type: String, // 'asset', 'liability', 'equity'

  @ColumnInfo(name = "member_id")
  val memberId: String? = null // References members(id)
)

/**
 * 5. APPEND-ONLY PAYMENTS & CRYPTOGRAPHIC LEDGER
 * Room Entity mapping to PostgreSQL 'payments' table.
 * Includes prev_hash and current_hash for SHA-256 cryptographic chain integrity.
 */
@Entity(
  tableName = "payments",
  indices = [
    Index(value = ["cycle_id"]),
    Index(value = ["member_id"]),
    Index(value = ["confirmed_by"]),
    Index(value = ["idempotency_key"], unique = true)
  ],
  foreignKeys = [
    ForeignKey(
      entity = CycleEntity::class,
      parentColumns = ["id"],
      childColumns = ["cycle_id"],
      onDelete = ForeignKey.RESTRICT
    ),
    ForeignKey(
      entity = MemberEntity::class,
      parentColumns = ["id"],
      childColumns = ["member_id"],
      onDelete = ForeignKey.RESTRICT
    ),
    ForeignKey(
      entity = IdentityEntity::class,
      parentColumns = ["id"],
      childColumns = ["confirmed_by"],
      onDelete = ForeignKey.RESTRICT
    )
  ]
)
data class PaymentEntity(
  @PrimaryKey
  @ColumnInfo(name = "id")
  val id: String = UUID.randomUUID().toString(),

  @ColumnInfo(name = "cycle_id")
  val cycleId: String, // References cycles(id)

  @ColumnInfo(name = "member_id")
  val memberId: String, // References members(id)

  @ColumnInfo(name = "amount_paid")
  val amountPaid: Double,

  @ColumnInfo(name = "method")
  val method: String, // 'CASH', 'MOMO', 'AGENT'

  @ColumnInfo(name = "status")
  val status: String = "confirmed", // 'confirmed', 'reversed'

  @ColumnInfo(name = "confirmed_by")
  val confirmedBy: String, // References identities(id)

  @ColumnInfo(name = "confirmed_at")
  val confirmedAt: Long = System.currentTimeMillis(),

  @ColumnInfo(name = "idempotency_key")
  val idempotencyKey: String = UUID.randomUUID().toString(),

  @ColumnInfo(name = "source")
  val source: String = "whatsapp", // 'whatsapp', 'app'

  @ColumnInfo(name = "prev_hash")
  val prevHash: String, // VARCHAR(64) NOT NULL

  @ColumnInfo(name = "current_hash")
  val currentHash: String, // VARCHAR(64) NOT NULL

  // Offline-first convenience cached fields:
  @ColumnInfo(name = "member_name")
  val memberName: String = "",

  @ColumnInfo(name = "momo_reference")
  val momoReference: String? = null,

  @ColumnInfo(name = "is_synced")
  val isSynced: Boolean = false
)

/**
 * Room Entity mapping to PostgreSQL 'ledger_entries' table.
 * Double-entry book-keeping with debits and credits.
 */
@Entity(
  tableName = "ledger_entries",
  indices = [
    Index(value = ["payment_id"]),
    Index(value = ["account_id"])
  ],
  foreignKeys = [
    ForeignKey(
      entity = PaymentEntity::class,
      parentColumns = ["id"],
      childColumns = ["payment_id"],
      onDelete = ForeignKey.CASCADE
    ),
    ForeignKey(
      entity = AccountEntity::class,
      parentColumns = ["id"],
      childColumns = ["account_id"],
      onDelete = ForeignKey.CASCADE
    )
  ]
)
data class LedgerEntryEntity(
  @PrimaryKey
  @ColumnInfo(name = "id")
  val id: String = UUID.randomUUID().toString(),

  @ColumnInfo(name = "payment_id")
  val paymentId: String, // References payments(id)

  @ColumnInfo(name = "account_id")
  val accountId: String, // References accounts(id)

  @ColumnInfo(name = "account_name")
  val accountName: String,

  @ColumnInfo(name = "entry_type")
  val entryType: String, // 'debit', 'credit'

  @ColumnInfo(name = "amount")
  val amount: Double,

  @ColumnInfo(name = "created_at")
  val createdAt: Long = System.currentTimeMillis()
)

/**
 * 6. CLAIMS, DISPUTES & CORRECTIONS
 * Room Entity mapping to PostgreSQL 'claims' table.
 */
@Entity(
  tableName = "claims",
  indices = [
    Index(value = ["cycle_id"]),
    Index(value = ["member_id"]),
    Index(value = ["resolved_by"])
  ],
  foreignKeys = [
    ForeignKey(
      entity = CycleEntity::class,
      parentColumns = ["id"],
      childColumns = ["cycle_id"],
      onDelete = ForeignKey.CASCADE
    ),
    ForeignKey(
      entity = MemberEntity::class,
      parentColumns = ["id"],
      childColumns = ["member_id"],
      onDelete = ForeignKey.CASCADE
    )
  ]
)
data class ClaimEntity(
  @PrimaryKey
  @ColumnInfo(name = "id")
  val id: String = UUID.randomUUID().toString(),

  @ColumnInfo(name = "cycle_id")
  val cycleId: String, // References cycles(id)

  @ColumnInfo(name = "member_id")
  val memberId: String, // References members(id)

  @ColumnInfo(name = "member_name")
  val memberName: String,

  @ColumnInfo(name = "member_phone")
  val memberPhone: String,

  @ColumnInfo(name = "claimed_amount")
  val claimedAmount: Double,

  @ColumnInfo(name = "evidence_momo_id")
  val evidenceMoMoId: String? = null,

  @ColumnInfo(name = "state")
  val state: String = "pending", // 'pending', 'confirmed', 'rejected', 'disputed'

  @ColumnInfo(name = "created_at")
  val createdAt: Long = System.currentTimeMillis(),

  @ColumnInfo(name = "resolved_by")
  val resolvedBy: String? = null, // References identities(id)

  @ColumnInfo(name = "resolved_at")
  val resolvedAt: Long? = null
)

/**
 * Room Entity mapping to PostgreSQL 'ledger_corrections' table.
 * Supports dual sign-off for financial adjustments.
 */
@Entity(
  tableName = "ledger_corrections",
  indices = [
    Index(value = ["payment_id"]),
    Index(value = ["entered_by"]),
    Index(value = ["approved_by"])
  ],
  foreignKeys = [
    ForeignKey(
      entity = PaymentEntity::class,
      parentColumns = ["id"],
      childColumns = ["payment_id"],
      onDelete = ForeignKey.RESTRICT
    ),
    ForeignKey(
      entity = IdentityEntity::class,
      parentColumns = ["id"],
      childColumns = ["entered_by"],
      onDelete = ForeignKey.RESTRICT
    )
  ]
)
data class LedgerCorrectionEntity(
  @PrimaryKey
  @ColumnInfo(name = "id")
  val id: String = UUID.randomUUID().toString(),

  @ColumnInfo(name = "payment_id")
  val paymentId: String, // References payments(id)

  @ColumnInfo(name = "action")
  val action: String,

  @ColumnInfo(name = "reason")
  val reason: String,

  @ColumnInfo(name = "entered_by")
  val enteredBy: String, // References identities(id)

  @ColumnInfo(name = "approved_by")
  val approvedBy: String? = null, // References identities(id) for dual sign-off

  @ColumnInfo(name = "created_at")
  val createdAt: Long = System.currentTimeMillis()
)

/**
 * 7. AUDIT LOG & MESSAGES
 * Room Entity mapping to PostgreSQL 'audit_log' table.
 */
@Entity(
  tableName = "audit_log",
  indices = [
    Index(value = ["actor_id"]),
    Index(value = ["group_id"])
  ]
)
data class AuditLogEntity(
  @PrimaryKey
  @ColumnInfo(name = "id")
  val id: String = UUID.randomUUID().toString(),

  @ColumnInfo(name = "actor_id")
  val actorId: String? = null, // References identities(id)

  @ColumnInfo(name = "group_id")
  val groupId: String? = null, // References groups(id)

  @ColumnInfo(name = "action")
  val action: String,

  @ColumnInfo(name = "payload")
  val payload: String? = null,

  @ColumnInfo(name = "created_at")
  val createdAt: Long = System.currentTimeMillis()
)

/**
 * Room Entity mapping to PostgreSQL 'message_log' table.
 */
@Entity(
  tableName = "message_log",
  indices = [
    Index(value = ["identity_id"]),
    Index(value = ["phone"])
  ]
)
data class MessageLogEntity(
  @PrimaryKey
  @ColumnInfo(name = "id")
  val id: String = UUID.randomUUID().toString(),

  @ColumnInfo(name = "identity_id")
  val identityId: String? = null, // References identities(id)

  @ColumnInfo(name = "phone")
  val phone: String,

  @ColumnInfo(name = "sender_name")
  val senderName: String,

  @ColumnInfo(name = "direction")
  val direction: String, // 'IN', 'OUT'

  @ColumnInfo(name = "body")
  val body: String,

  @ColumnInfo(name = "meta_status")
  val metaStatus: String = "delivered", // 'sent', 'delivered', 'read'

  @ColumnInfo(name = "timestamp")
  val timestamp: Long = System.currentTimeMillis()
)
