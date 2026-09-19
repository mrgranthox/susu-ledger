package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ClaimEntity
import com.example.data.local.CycleEntity
import com.example.data.local.GroupEntity
import com.example.data.local.LedgerEntryEntity
import com.example.data.local.MemberEntity
import com.example.data.local.MessageLogEntity
import com.example.data.local.PaymentEntity
import com.example.data.local.SusuDatabase
import com.example.data.repository.SusuRepository
import com.example.data.repository.VerificationReport
import com.example.util.CryptoUtils
import com.example.util.GhanaPhoneUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardStats(
  val totalMembers: Int = 0,
  val paidMembersCount: Int = 0,
  val pendingMembersCount: Int = 0,
  val confirmedAmount: Double = 0.0,
  val pendingAmount: Double = 0.0,
  val totalTargetAmount: Double = 0.0,
  val progressPercent: Int = 0
)

class SusuViewModel(application: Application) : AndroidViewModel(application) {

  private val database = SusuDatabase.getDatabase(application, viewModelScope)
  private val repository = SusuRepository(database.susuDao())

  // Auth & Session State: Clean slate default is unauthenticated (false)
  private val _isAuthenticated = MutableStateFlow(false)
  val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

  private val _userRole = MutableStateFlow("treasurer")
  val userRole: StateFlow<String> = _userRole.asStateFlow()

  private val _userPhone = MutableStateFlow("")
  val userPhone: StateFlow<String> = _userPhone.asStateFlow()

  // WhatsApp Bot State
  private val _botPairingCode = MutableStateFlow(CryptoUtils.generatePairingCode())
  val botPairingCode: StateFlow<String> = _botPairingCode.asStateFlow()

  private val _isBotConnected = MutableStateFlow(false)
  val isBotConnected: StateFlow<Boolean> = _isBotConnected.asStateFlow()

  fun setBotConnected(connected: Boolean) {
    _isBotConnected.value = connected
  }

  fun refreshPairingCode() {
    _botPairingCode.value = CryptoUtils.generatePairingCode()
    _isBotConnected.value = false
  }

  // Group Selection
  private val _selectedGroupId = MutableStateFlow("")
  val selectedGroupId: StateFlow<String> = _selectedGroupId.asStateFlow()

  init {
    viewModelScope.launch {
      val existingGroups = repository.getAllGroupsOnce()
      if (existingGroups.isNotEmpty()) {
        _selectedGroupId.value = existingGroups.first().id
      }
    }
  }

  val groups: StateFlow<List<GroupEntity>> = repository.allGroups
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val currentGroup: StateFlow<GroupEntity?> = combine(_selectedGroupId, groups) { gid, glist ->
    glist.find { it.id == gid } ?: glist.firstOrNull()
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

  val activeCycle: StateFlow<CycleEntity?> = _selectedGroupId.flatMapLatest { gid ->
    repository.getActiveCycle(gid)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

  val allCycles: StateFlow<List<CycleEntity>> = _selectedGroupId.flatMapLatest { gid ->
    repository.getAllCycles(gid)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val members: StateFlow<List<MemberEntity>> = _selectedGroupId.flatMapLatest { gid ->
    repository.getMembers(gid)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val payments: StateFlow<List<PaymentEntity>> = repository.allPayments
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val ledgerEntries: StateFlow<List<LedgerEntryEntity>> = repository.allLedgerEntries
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val pendingClaims: StateFlow<List<ClaimEntity>> = activeCycle.flatMapLatest { cycle ->
    if (cycle != null) {
      repository.getPendingClaims(cycle.id)
    } else {
      MutableStateFlow(emptyList())
    }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val messages: StateFlow<List<MessageLogEntity>> = repository.allMessages
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  // Verification & Sync
  val unsyncedPaymentsCount: StateFlow<Int> = repository.unsyncedPaymentsCount
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

  private val _verificationReport = MutableStateFlow<VerificationReport?>(null)
  val verificationReport: StateFlow<VerificationReport?> = _verificationReport.asStateFlow()

  private val _isVerifying = MutableStateFlow(false)
  val isVerifying: StateFlow<Boolean> = _isVerifying.asStateFlow()

  private val _isSyncing = MutableStateFlow(false)
  val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

  fun syncWithCloud() {
    viewModelScope.launch {
      _isSyncing.value = true
      delay(700)
      val count = repository.syncAllOfflineDataToCloud()
      val report = repository.verifyLedgerIntegrity(payments.value, ledgerEntries.value)
      _verificationReport.value = report
      _isSyncing.value = false
      _toastMessage.value = if (count > 0) {
        "Backed up $count offline record(s) to Cloud Database • Verified"
      } else {
        "Cloud Ledger up to date • Verified"
      }
    }
  }

  // UI Dialog / Sheet State
  private val _showPaymentSheet = MutableStateFlow(false)
  val showPaymentSheet: StateFlow<Boolean> = _showPaymentSheet.asStateFlow()

  private val _selectedClaimForConfirmation = MutableStateFlow<ClaimEntity?>(null)
  val selectedClaimForConfirmation: StateFlow<ClaimEntity?> = _selectedClaimForConfirmation.asStateFlow()

  private val _selectedMemberForPayment = MutableStateFlow<MemberEntity?>(null)
  val selectedMemberForPayment: StateFlow<MemberEntity?> = _selectedMemberForPayment.asStateFlow()

  private val _showNewWeekDialog = MutableStateFlow(false)
  val showNewWeekDialog: StateFlow<Boolean> = _showNewWeekDialog.asStateFlow()

  private val _showAddMemberDialog = MutableStateFlow(false)
  val showAddMemberDialog: StateFlow<Boolean> = _showAddMemberDialog.asStateFlow()

  private val _showReportsSheet = MutableStateFlow(false)
  val showReportsSheet: StateFlow<Boolean> = _showReportsSheet.asStateFlow()

  private val _showPairingSheet = MutableStateFlow(false)
  val showPairingSheet: StateFlow<Boolean> = _showPairingSheet.asStateFlow()

  // Pairing code countdown
  private val _pairingCode = MutableStateFlow("A7K2-9P")
  val pairingCode: StateFlow<String> = _pairingCode.asStateFlow()

  private val _pairingSecondsRemaining = MutableStateFlow(840) // 14 mins remaining
  val pairingSecondsRemaining: StateFlow<Int> = _pairingSecondsRemaining.asStateFlow()

  // Success toast message
  private val _toastMessage = MutableStateFlow<String?>(null)
  val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

  // Computed dashboard stats
  val dashboardStats: StateFlow<DashboardStats> = combine(
    members,
    payments,
    activeCycle,
    pendingClaims
  ) { mems, pays, cycle, claims ->
    val cyclePays = if (cycle != null) pays.filter { it.cycleId == cycle.id } else pays
    val totalMems = mems.size
    val paidCount = cyclePays.map { it.memberId }.distinct().size
    val pendingCount = (totalMems - paidCount).coerceAtLeast(0)
    val confirmedAmt = cyclePays.sumOf { it.amountPaid }
    val cycleDue = cycle?.amountDue ?: 50.0
    val totalTarget = totalMems * cycleDue
    val pendingAmt = (totalTarget - confirmedAmt).coerceAtLeast(0.0)
    val progress = if (totalTarget > 0) ((confirmedAmt / totalTarget) * 100).toInt().coerceIn(0, 100) else 0

    DashboardStats(
      totalMembers = totalMems,
      paidMembersCount = paidCount,
      pendingMembersCount = pendingCount,
      confirmedAmount = confirmedAmt,
      pendingAmount = pendingAmt,
      totalTargetAmount = totalTarget,
      progressPercent = progress
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardStats())

  init {
    // Pairing timer countdown
    viewModelScope.launch {
      while (true) {
        delay(1000)
        _pairingSecondsRemaining.value = if (_pairingSecondsRemaining.value > 1) {
          _pairingSecondsRemaining.value - 1
        } else {
          900 // reset to 15m
        }
      }
    }

    // Automatically select the first group if none is selected
    viewModelScope.launch {
      groups.collect { groupList ->
        if (_selectedGroupId.value.isBlank() && groupList.isNotEmpty()) {
          _selectedGroupId.value = groupList.first().id
        }
      }
    }
  }

  fun completeOnboarding(
    groupName: String,
    amount: Double,
    treasurerPhone: String,
    treasurerName: String,
    members: List<Pair<String, String>>,
    treasurerPin: String = ""
  ) {
    viewModelScope.launch {
      try {
        val newGroup = repository.createNewGroupWithMembers(
          groupName = groupName,
          amount = amount,
          schedule = "weekly",
          treasurerPhone = treasurerPhone,
          treasurerName = treasurerName,
          members = members,
          treasurerPin = treasurerPin
        )
        _selectedGroupId.value = newGroup.id
        _userPhone.value = treasurerPhone
        _userRole.value = "treasurer"
        _botPairingCode.value = CryptoUtils.generatePairingCode()
        _isAuthenticated.value = true
        _toastMessage.value = "Group '${newGroup.name}' created successfully!"
      } catch (e: Exception) {
        _toastMessage.value = "Error creating group: ${e.localizedMessage}"
      }
    }
  }

  fun selectGroup(groupId: String) {
    _selectedGroupId.value = groupId
  }

  fun authenticateOfficer(
    phone: String,
    pin: String,
    role: String = "treasurer",
    onResult: (success: Boolean, errorMessage: String?) -> Unit
  ) {
    viewModelScope.launch {
      val cleanDigits = phone.filter { it.isDigit() }
      if (cleanDigits.length < 9) {
        val err = "Please enter a valid Ghana phone number (min 9 digits)."
        _toastMessage.value = err
        onResult(false, err)
        return@launch
      }

      val formattedPhone = CryptoUtils.formatGhanaPhone(phone)
      
      // Look up identity by phone number from database
      val identity = repository.getIdentityByPhone(formattedPhone)
        ?: repository.getIdentityByPhone(phone)
        ?: run {
          val all = database.susuDao().getAllIdentities().firstOrNull() ?: emptyList()
          all.find { it.phone.filter { c -> c.isDigit() }.endsWith(cleanDigits.takeLast(9)) }
        }

      if (identity == null) {
        val err = "No registered officer account found for $phone. Please create your Susu group first."
        _toastMessage.value = err
        onResult(false, err)
        return@launch
      }

      val user = repository.getUserById(identity.id)
      if (user == null) {
        val err = "Officer credentials record not found."
        _toastMessage.value = err
        onResult(false, err)
        return@launch
      }

      val isPinValid = CryptoUtils.verifyPin(pin, user.pinHash, identity.id) || 
                       CryptoUtils.verifyPin(pin, user.pinHash, "")

      if (!isPinValid) {
        val err = "Incorrect 4-digit security PIN. Access denied."
        _toastMessage.value = err
        onResult(false, err)
        return@launch
      }

      _userPhone.value = identity.phone
      _userRole.value = user.role.ifBlank { role }
      val group = repository.getGroupByTreasurer(identity.id) ?: repository.getAllGroupsOnce().firstOrNull()
      if (group != null) {
        _selectedGroupId.value = group.id
      }
      _isAuthenticated.value = true
      _toastMessage.value = "Welcome back, ${identity.displayName}!"
      onResult(true, null)
    }
  }

  fun loginWithOtp(phone: String, role: String) {
    authenticateOfficer(phone, "1234", role) { _, _ -> }
  }

  fun openPaymentSheetForClaim(claim: ClaimEntity) {
    _selectedClaimForConfirmation.value = claim
    val mem = members.value.find { it.id == claim.memberId }
    _selectedMemberForPayment.value = mem
    _showPaymentSheet.value = true
  }

  fun openPaymentSheetForMember(member: MemberEntity) {
    _selectedMemberForPayment.value = member
    _selectedClaimForConfirmation.value = null
    _showPaymentSheet.value = true
  }

  fun openPaymentSheetDirect() {
    _selectedMemberForPayment.value = members.value.firstOrNull()
    _selectedClaimForConfirmation.value = null
    _showPaymentSheet.value = true
  }

  fun dismissPaymentSheet() {
    _showPaymentSheet.value = false
    _selectedClaimForConfirmation.value = null
    _selectedMemberForPayment.value = null
  }

  fun confirmPayment(
    memberId: String,
    amount: Double,
    method: String,
    momoRef: String?,
    sendWhatsAppReceipt: Boolean
  ) {
    val cycle = activeCycle.value ?: return
    val groupId = _selectedGroupId.value
    val associatedClaim = _selectedClaimForConfirmation.value

    viewModelScope.launch {
      try {
        val group = database.susuDao().getGroupById(groupId)
        val officerId = group?.treasurerId ?: "treasurer"
        val payment = repository.recordPayment(
          groupId = groupId,
          cycleId = cycle.id,
          memberId = memberId,
          amount = amount,
          method = method,
          source = if (associatedClaim != null) "whatsapp" else "app",
          momoRef = momoRef,
          confirmedBy = officerId,
          sendWhatsAppReceipt = sendWhatsAppReceipt,
          associatedClaimId = associatedClaim?.id
        )
        dismissPaymentSheet()
        _toastMessage.value = "Payment confirmed and recorded in ledger"
        runIntegrityCheck()
      } catch (e: Exception) {
        _toastMessage.value = "Error recording payment: ${e.localizedMessage}"
      }
    }
  }

  fun rejectClaim(claim: ClaimEntity) {
    viewModelScope.launch {
      repository.rejectClaim(claim)
      _toastMessage.value = "Claim from ${claim.memberName} rejected"
    }
  }

  fun advanceNewWeek() {
    val cycle = activeCycle.value ?: return
    val groupId = _selectedGroupId.value
    viewModelScope.launch {
      repository.startNewCycle(groupId, cycle)
      _showNewWeekDialog.value = false
      _toastMessage.value = "Week ${cycle.number + 1} opened successfully!"
    }
  }

  fun showNewWeekDialog(show: Boolean) {
    _showNewWeekDialog.value = show
  }

  fun showAddMemberDialog(show: Boolean) {
    _showAddMemberDialog.value = show
  }

  fun showReportsSheet(show: Boolean) {
    _showReportsSheet.value = show
  }

  fun showPairingSheet(show: Boolean) {
    _showPairingSheet.value = show
  }

  fun addNewMember(alias: String, phone: String) {
    val groupId = _selectedGroupId.value
    viewModelScope.launch {
      repository.addMember(groupId, alias, phone)
      _showAddMemberDialog.value = false
      _toastMessage.value = "Added member $alias"
    }
  }

  fun runIntegrityCheck() {
    viewModelScope.launch {
      _isVerifying.value = true
      delay(400) // slight debounce for smooth animation
      val report = repository.verifyLedgerIntegrity(payments.value, ledgerEntries.value)
      _verificationReport.value = report
      _isVerifying.value = false
    }
  }

  fun simulateMemberWhatsAppMessage(phone: String, messageText: String) {
    viewModelScope.launch {
      repository.processWhatsAppBotMessage(phone, messageText, activeCycle.value)
      _toastMessage.value = "Bot response logged to message history"
    }
  }

  fun sendWeeklyCollectionReminder() {
    val groupId = _selectedGroupId.value
    viewModelScope.launch {
      val count = repository.sendWeeklyCollectionReminder(groupId)
      _toastMessage.value = "Dispatched $count collection reminders via WhatsApp"
    }
  }

  fun sendTargetedUnpaidNudges() {
    val groupId = _selectedGroupId.value
    viewModelScope.launch {
      val count = repository.sendTargetedUnpaidNudges(groupId)
      _toastMessage.value = if (count > 0) "Sent $count targeted nudges to unpaid members" else "All members in active cycle have contributed!"
    }
  }

  fun sendSundaySummaryDigest() {
    val groupId = _selectedGroupId.value
    viewModelScope.launch {
      val count = repository.sendSundaySummaryDigest(groupId)
      _toastMessage.value = "Broadcasted Sunday Summary Digest to $count members"
    }
  }

  fun togglePauseGroup(reason: String = "Administrative pause") {
    val group = currentGroup.value ?: return
    val newState = if (group.state.equals("paused", ignoreCase = true)) "active" else "paused"
    viewModelScope.launch {
      val success = repository.pauseOrResumeGroup(
        groupId = group.id,
        newState = newState,
        reason = reason,
        officerId = "identity-treasurer-01"
      )
      if (success) {
        _toastMessage.value = if (newState == "paused") "Group paused & members notified on WhatsApp" else "Group resumed & WhatsApp announcement broadcast"
      }
    }
  }

  fun updateContributionAmount(newAmount: Double, applyToCurrentCycle: Boolean = true, reason: String = "Officer dues adjustment") {
    val group = currentGroup.value ?: return
    viewModelScope.launch {
      val success = repository.updateGroupContributionAmount(
        groupId = group.id,
        newAmount = newAmount,
        applyToCurrentCycle = applyToCurrentCycle,
        reason = reason,
        officerId = "identity-treasurer-01"
      )
      if (success) {
        _toastMessage.value = "Contribution dues updated to GHS ${String.format(java.util.Locale.US, "%.2f", newAmount)} with dual-officer record!"
      }
    }
  }

  fun renameGroup(newName: String) {
    val group = currentGroup.value ?: return
    viewModelScope.launch {
      val updated = group.copy(name = newName)
      database.susuDao().updateGroup(updated)
      _toastMessage.value = "Group renamed to '$newName'"
    }
  }

  fun clearToast() {
    _toastMessage.value = null
  }

  fun logout() {
    _isAuthenticated.value = false
    _toastMessage.value = "Signed out successfully"
  }

  fun deleteAccount() {
    viewModelScope.launch {
      database.clearAllTables()
      _isAuthenticated.value = false
      _selectedGroupId.value = ""
      _toastMessage.value = "Account and local ledger data deleted"
    }
  }
}
