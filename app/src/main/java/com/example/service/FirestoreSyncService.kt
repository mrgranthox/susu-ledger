package com.example.service

import android.util.Log
import com.example.data.local.CycleEntity
import com.example.data.local.GroupEntity
import com.example.data.local.MemberEntity
import com.example.data.local.PaymentEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class FirestoreSyncService {

  companion object {
    private const val TAG = "FirestoreSyncService"
    private const val COLLECTION_GROUPS = "groups"
    private const val COLLECTION_MEMBERS = "members"
    private const val COLLECTION_CYCLES = "cycles"
    private const val COLLECTION_PAYMENTS = "payments"
  }

  private val firestore: FirebaseFirestore? by lazy {
    try {
      FirebaseFirestore.getInstance()
    } catch (e: Exception) {
      Log.w(TAG, "FirebaseFirestore instance unavailable: ${e.message}")
      null
    }
  }

  suspend fun syncGroup(group: GroupEntity): Boolean {
    val db = firestore ?: return false
    return try {
      val data = mapOf(
        "id" to group.id,
        "name" to group.name,
        "amount" to group.amount,
        "currency" to group.currency,
        "schedule" to group.schedule,
        "treasurerId" to group.treasurerId,
        "officerId" to group.officerId,
        "state" to group.state,
        "updatedAt" to System.currentTimeMillis()
      )
      db.collection(COLLECTION_GROUPS).document(group.id).set(data, SetOptions.merge()).await()
      Log.d(TAG, "Group ${group.id} synced to Firestore.")
      true
    } catch (e: Exception) {
      Log.e(TAG, "Failed syncing group ${group.id}: ${e.message}")
      false
    }
  }

  suspend fun syncMember(member: MemberEntity): Boolean {
    val db = firestore ?: return false
    return try {
      val data = mapOf(
        "id" to member.id,
        "groupId" to member.groupId,
        "phone" to member.phone,
        "alias" to member.alias,
        "state" to member.state,
        "joinedCycle" to member.joinedCycle,
        "exitedCycle" to member.exitedCycle,
        "updatedAt" to System.currentTimeMillis()
      )
      db.collection(COLLECTION_MEMBERS).document(member.id).set(data, SetOptions.merge()).await()
      Log.d(TAG, "Member ${member.id} synced to Firestore.")
      true
    } catch (e: Exception) {
      Log.e(TAG, "Failed syncing member ${member.id}: ${e.message}")
      false
    }
  }

  suspend fun syncCycle(cycle: CycleEntity): Boolean {
    val db = firestore ?: return false
    return try {
      val data = mapOf(
        "id" to cycle.id,
        "groupId" to cycle.groupId,
        "number" to cycle.number,
        "amountDue" to cycle.amountDue,
        "dueDate" to cycle.dueDate,
        "state" to cycle.state,
        "closedAt" to cycle.closedAt,
        "closedBy" to cycle.closedBy,
        "updatedAt" to System.currentTimeMillis()
      )
      db.collection(COLLECTION_CYCLES).document(cycle.id).set(data, SetOptions.merge()).await()
      Log.d(TAG, "Cycle ${cycle.id} synced to Firestore.")
      true
    } catch (e: Exception) {
      Log.e(TAG, "Failed syncing cycle ${cycle.id}: ${e.message}")
      false
    }
  }

  suspend fun syncPayment(payment: PaymentEntity): Boolean {
    val db = firestore ?: return false
    return try {
      val data = mapOf(
        "id" to payment.id,
        "cycleId" to payment.cycleId,
        "memberId" to payment.memberId,
        "amountPaid" to payment.amountPaid,
        "method" to payment.method,
        "status" to payment.status,
        "confirmedBy" to payment.confirmedBy,
        "confirmedAt" to payment.confirmedAt,
        "idempotencyKey" to payment.idempotencyKey,
        "source" to payment.source,
        "prevHash" to payment.prevHash,
        "currentHash" to payment.currentHash,
        "updatedAt" to System.currentTimeMillis()
      )
      db.collection(COLLECTION_PAYMENTS).document(payment.id).set(data, SetOptions.merge()).await()
      Log.d(TAG, "Payment ${payment.id} synced to Firestore with hash ${payment.currentHash}.")
      true
    } catch (e: Exception) {
      Log.e(TAG, "Failed syncing payment ${payment.id}: ${e.message}")
      false
    }
  }
}
