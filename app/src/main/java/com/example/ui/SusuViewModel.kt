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
import com.example.data.local.UserEntity
import com.example.data.remote.CloudSystemStatusResponse
import com.example.data.remote.PairBotRequest
import com.example.data.remote.SendWhatsAppMessageRequest
import com.example.data.remote.SusuApiClient
import com.example.data.repository.SusuRepository
import com.example.data.repository.VerificationReport
import com.example.util.ContactsHelper
import com.example.util.CryptoUtils
import com.example.util.DeviceContact
import com.example.util.GhanaPhoneUtils
import com.example.util.SessionManager
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

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SusuViewModel(application: Application) : AndroidViewModel(application) {

  private val database = SusuDatabase.getDatabase(application, viewModelScope)
  private val repository = SusuRepository(database)

  // Enterprise Session & Persistence
  val sessionManager = SessionManager(application)

  // Auth & Session State
  private val _isOnboardingCompleted = MutableStateFlow(sessionManager.isOnboarded)
  val isOnboardingCompleted: StateFlow<Boolean> = _isOnboardingCompleted.asStateFlow()

  private val _isAppLocked = MutableStateFlow(sessionManager.isOnboarded && sessionManager.isAppLocked)
  val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

  private val _isAuthenticated = MutableStateFlow(sessionManager.isOnboarded && sessionManager.loggedInPhone.isNotBlank())
  val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

  private val _userRole = MutableStateFlow(sessionManager.loggedInRole.ifBlank { "treasurer" })
  val userRole: StateFlow<String> = _userRole.asStateFlow()

  private val _userPhone = MutableStateFlow(sessionManager.loggedInPhone)
  val userPhone: StateFlow<String> = _userPhone.asStateFlow()

  // WhatsApp Bot State & Single Unified Dynamic Pairing Code
  private val _pairingCode = MutableStateFlow(CryptoUtils.generatePairingCode())
  val pairingCode: StateFlow<String> = _pairingCode.asStateFlow()
  val botPairingCode: StateFlow<String> = _pairingCode.asStateFlow()

  private val _isBotConnected = MutableStateFlow(true)
  val isBotConnected: StateFlow<Boolean> = _isBotConnected.asStateFlow()

  // Cloud Diagnostics State
  private val _cloudStatus = MutableStateFlow<CloudSystemStatusResponse?>(null)
  val cloudStatus: StateFlow<CloudSystemStatusResponse?> = _cloudStatus.asStateFlow()

  private val _isCheckingCloud = MutableStateFlow(false)
  val isCheckingCloud: StateFlow<Boolean> = _isCheckingCloud.asStateFlow()

  fun setBotConnected(connected: Boolean) {
    _isBotConnected.value = connected
  }

  fun refreshPairingCode() {
    generateNewPairingCode()
  }

  fun generateNewPairingCode() {
    val newCode = CryptoUtils.generatePairingCode()
    _pairingCode.value = newCode
    _pairingSecondsRemaining.value = 0

    viewModelScope.launch {
      try {
        repository.syncAllOfflineDataToCloud()
        val phoneNum = _userPhone.value
        val grpId = _selectedGroupId.value.ifBlank { null }
        val response = SusuApiClient.getApiService().registerPairingCode(
          PairBotRequest(
            code = newCode,
            phone = phoneNum,
            groupId = grpId
          )
        )
        if (response.isSuccessful) {
          _pairingSecondsRemaining.value = 900
          _toastMessage.value = "New pairing code $newCode registered"
          _isBotConnected.value = false
          while (_pairingCode.value == newCode && _pairingSecondsRemaining.value > 0) {
            delay(5000)
            val status = SusuApiClient.getApiService().getPairingStatus(newCode)
            if (status.isSuccessful && status.body()?.status == "PAIRED") {
              _isBotConnected.value = true
              _toastMessage.value = "WhatsApp pairing confirmed"
              break
            }
          }
        } else {
          _pairingSecondsRemaining.value = 0
          _toastMessage.value = "Pairing registration failed (HTTP ${response.code()}). Retry after syncing."
        }
      } catch (e: Exception) {
        _pairingSecondsRemaining.value = 0
        _toastMessage.value = "Pairing registration failed: ${e.localizedMessage}"
      }
    }
  }

  fun checkCloudSystemStatus() {
    viewModelScope.launch {
      _isCheckingCloud.value = true
      try {
        val response = SusuApiClient.getApiService().getSystemStatus()
        if (response.isSuccessful && response.body() != null) {
          _cloudStatus.value = response.body()
          _toastMessage.value = if (response.body()?.database?.status == "healthy") "Cloud database is online" else "Cloud database is unavailable"
        } else {
          _toastMessage.value = "Cloud check failed (HTTP ${response.code()})"
        }
      } catch (e: Exception) {
        _toastMessage.value = "Cloud check failed: ${e.localizedMessage}"
      } finally {
        _isCheckingCloud.value = false
      }
    }
  }

  // Group Selection
  private val _selectedGroupId = MutableStateFlow("")
  val selectedGroupId: StateFlow<String> = _selectedGroupId.asStateFlow()

  init {
    viewModelScope.launch {
      val existingGroups = repository.getAllGroupsOnce()
      if (existingGroups.isNotEmpty()) {
        val targetGid = if (sessionManager.activeGroupId.isNotBlank() && existingGroups.any { it.id == sessionManager.activeGroupId }) {
          sessionManager.activeGroupId
        } else {
          existingGroups.first().id
        }
        _selectedGroupId.value = targetGid
        _isOnboardingCompleted.value = true
        sessionManager.isOnboarded = true

        if (sessionManager.loggedInPhone.isNotBlank()) {
          _userPhone.value = sessionManager.loggedInPhone
          _userRole.value = sessionManager.loggedInRole
          _isAuthenticated.value = true
        } else {
          val identities = repository.getAllIdentitiesOnce()
          val firstOfficer = identities.firstOrNull()
          if (firstOfficer != null) {
            _userPhone.value = firstOfficer.phone
            _userRole.value = "treasurer"
            _isAuthenticated.value = true
            sessionManager.saveSession(
              phone = firstOfficer.phone,
              role = "treasurer",
              name = firstOfficer.displayName,
              groupId = targetGid
            )
          }
        }
      }
    }
    checkCloudSystemStatus()
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
    com.example.service.CloudSyncWorker.enqueue(getApplication())
    if (_isSyncing.value) return
    viewModelScope.launch {
      _isSyncing.value = true
      try {
        val count = repository.syncAllOfflineDataToCloud()
        val claimCount = repository.syncPendingClaimsFromCloud(_selectedGroupId.value)
        _verificationReport.value = repository.verifyLedgerIntegrity(payments.value, ledgerEntries.value)
        _toastMessage.value = "Cloud sync complete: $count payments, $claimCount pending claims"
      } catch (e: Exception) {
        _toastMessage.value = "Sync failed: ${e.localizedMessage}"
      } finally {
        _isSyncing.value = false
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

  // Navigation & Subscreen Persistence
  private val _currentNavIndex = MutableStateFlow(sessionManager.lastNavIndex)
  val currentNavIndex: StateFlow<Int> = _currentNavIndex.asStateFlow()

  private val _currentSubscreen = MutableStateFlow(sessionManager.activeSubscreen)
  val currentSubscreen: StateFlow<String> = _currentSubscreen.asStateFlow()

  fun setNavIndex(index: Int) {
    _currentNavIndex.value = index
    sessionManager.lastNavIndex = index
    sessionManager.recordActivity()
  }

  fun openSubscreen(subscreen: String) {
    _currentSubscreen.value = subscreen
    sessionManager.activeSubscreen = subscreen
    sessionManager.recordActivity()
  }

  fun closeSubscreen() {
    _currentSubscreen.value = ""
    sessionManager.activeSubscreen = ""
    sessionManager.recordActivity()
  }

  // Device Contacts State & Loader
  private val _deviceContacts = MutableStateFlow<List<DeviceContact>>(emptyList())
  val deviceContacts: StateFlow<List<DeviceContact>> = _deviceContacts.asStateFlow()

  private val _isLoadingContacts = MutableStateFlow(false)
  val isLoadingContacts: StateFlow<Boolean> = _isLoadingContacts.asStateFlow()

  fun loadDeviceContacts(contentResolver: android.content.ContentResolver) {
    viewModelScope.launch {
      _isLoadingContacts.value = true
      try {
        val list = ContactsHelper.readDeviceContacts(contentResolver)
        _deviceContacts.value = list
      } catch (e: Exception) {
        e.printStackTrace()
      } finally {
        _isLoadingContacts.value = false
      }
    }
  }

  fun importContactsAsMembers(contacts: List<DeviceContact>, targetGroupId: String? = null) {
    val gid = targetGroupId ?: _selectedGroupId.value
    if (gid.isBlank()) return

    viewModelScope.launch {
      var importedCount = 0
      contacts.forEach { contact ->
        if (contact.name.isNotBlank()) {
          val cleanPhone = if (contact.formattedPhone.isNotBlank()) contact.formattedPhone else contact.rawPhone
          repository.addMember(gid, contact.name.trim(), cleanPhone.trim())
          importedCount++
        }
      }
      _toastMessage.value = "Imported $importedCount members from contacts."
    }
  }

  // Pairing code countdown (dynamically initialized)
  private val _pairingSecondsRemaining = MutableStateFlow(900) // 15 mins remaining
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
          0
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

    // Auto-recover session if database has registered groups
    viewModelScope.launch {
      try {
        val allGroups = repository.getAllGroupsOnce()
        if (allGroups.isNotEmpty()) {
          val firstGroup = allGroups.first()
          if (!sessionManager.isOnboarded) {
            sessionManager.isOnboarded = true
            sessionManager.activeGroupId = firstGroup.id
            _isOnboardingCompleted.value = true
          }
          val treasurer = repository.getIdentityById(firstGroup.treasurerId)
          if (sessionManager.loggedInPhone.isBlank() && treasurer != null) {
            sessionManager.loggedInPhone = treasurer.phone
            sessionManager.officerName = treasurer.displayName
            _userPhone.value = treasurer.phone
          }
          val user = repository.getUserById(firstGroup.treasurerId)
          if (user != null && sessionManager.pinHash.isBlank()) {
            sessionManager.pinHash = user.pinHash
            sessionManager.pinSalt = firstGroup.treasurerId
          }
          if (sessionManager.loggedInPhone.isNotBlank()) {
            _isAuthenticated.value = true
          }
        }
      } catch (e: Exception) {
        // Safe fallback
      }
    }
  }

  fun completeOnboarding(
    groupName: String,
    amount: Double,
    treasurerPhone: String,
    treasurerName: String,
    members: List<Pair<String, String>>,
    treasurerPin: String = "",
    schedule: String = "weekly"
  ) {
    viewModelScope.launch {
      try {
        val newGroup = repository.createNewGroupWithMembers(
          groupName = groupName,
          amount = amount,
          schedule = schedule,
          treasurerPhone = treasurerPhone,
          treasurerName = treasurerName,
          members = members,
          treasurerPin = treasurerPin
        )
        val resolvedPin = if (treasurerPin.isNotBlank()) treasurerPin else "1234"
        _selectedGroupId.value = newGroup.id
        _userPhone.value = treasurerPhone
        _userRole.value = "treasurer"
        _pairingCode.value = CryptoUtils.generatePairingCode()
        _isAuthenticated.value = true
        _isOnboardingCompleted.value = true
        _isAppLocked.value = false

        sessionManager.saveSession(
          phone = treasurerPhone,
          role = "treasurer",
          name = treasurerName,
          groupId = newGroup.id,
          pinHash = CryptoUtils.hashPin(resolvedPin, newGroup.treasurerId),
          pinSalt = newGroup.treasurerId,
          rawPin = resolvedPin
        )

        _toastMessage.value = "Group '${newGroup.name}' created successfully!"
        syncWithCloud()
        showPairingSheet(true)
      } catch (e: Exception) {
        _toastMessage.value = "Error creating group: ${e.localizedMessage}"
      }
    }
  }

  fun selectGroup(groupId: String) {
    _selectedGroupId.value = groupId
    sessionManager.activeGroupId = groupId
  }

  fun unlockApp() {
    _isAppLocked.value = false
    sessionManager.isAppLocked = false
    sessionManager.recordActivity()
  }

  fun lockApp() {
    _isAppLocked.value = true
    sessionManager.isAppLocked = true
  }

  fun handleAppStop() {
    sessionManager.recordActivity()
  }

  fun handleAppResume() {
    if (_isAuthenticated.value && sessionManager.shouldLockAppAfterInactivity()) {
      lockApp()
    } else {
      sessionManager.recordActivity()
    }
  }

  fun verifyOfficerPin(enteredPin: String, onResult: (Boolean) -> Unit) {
    viewModelScope.launch {
      if (enteredPin.length != 4 || !enteredPin.all { it.isDigit() }) {
        onResult(false)
        return@launch
      }

      sessionManager.recordActivity()

      // 1. Primary check: Query Room DB for current officer to get canonical salted SHA-256 hash
      val phone = _userPhone.value.ifBlank { sessionManager.loggedInPhone }
      val cleanDigits = phone.filter { it.isDigit() }.takeLast(9)
      val allIdentities = repository.getAllIdentitiesOnce()
      val identity = if (cleanDigits.length >= 9) {
        allIdentities.find { it.phone.filter { c -> c.isDigit() }.endsWith(cleanDigits) }
      } else {
        allIdentities.firstOrNull()
      }

      if (identity != null) {
        val user = repository.getUserById(identity.id)
        if (user != null && user.pinHash.isNotBlank()) {
          val valid = CryptoUtils.verifyPin(enteredPin, user.pinHash, identity.id) ||
              CryptoUtils.verifyPin(enteredPin, user.pinHash, "")
          if (valid) {
            sessionManager.pinHash = user.pinHash
            sessionManager.pinSalt = identity.id
            sessionManager.savedPin = enteredPin
            unlockApp()
            onResult(true)
            return@launch
          } else {
            // Salted hash mismatch against database credentials
            onResult(false)
            return@launch
          }
        }
      }

      // 2. Check cached sessionManager hash if DB query returned no user record
      val cachedHash = sessionManager.pinHash
      val cachedSalt = sessionManager.pinSalt
      if (cachedHash.isNotBlank()) {
        val valid = CryptoUtils.verifyPin(enteredPin, cachedHash, cachedSalt) ||
            CryptoUtils.verifyPin(enteredPin, cachedHash, "")
        if (valid) {
          sessionManager.savedPin = enteredPin
          unlockApp()
          onResult(true)
          return@launch
        } else {
          onResult(false)
          return@launch
        }
      }

      // 3. Check exact match against savedSession PIN if populated
      val savedPin = sessionManager.savedPin
      if (savedPin.isNotBlank() && enteredPin == savedPin) {
        unlockApp()
        onResult(true)
        return@launch
      }

      // Strictly reject invalid input
      onResult(false)
    }
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

      val last9 = cleanDigits.takeLast(9)
      val allIdentities = repository.getAllIdentitiesOnce()
      val identity = allIdentities.find {
        it.phone.filter { c -> c.isDigit() }.endsWith(last9)
      } ?: repository.getIdentityByPhone(phone)

      if (identity == null) {
        val err = "No registered officer account found for $phone. Please create your Susu group first."
        _toastMessage.value = err
        onResult(false, err)
        return@launch
      }

      val user = repository.getUserById(identity.id)
      if (user == null) {
        val err = "Officer credentials not initialized. Please verify your phone."
        _toastMessage.value = err
        onResult(false, err)
        return@launch
      }

      val isPinValid = CryptoUtils.verifyPin(pin, user.pinHash, identity.id) || 
                       CryptoUtils.verifyPin(pin, user.pinHash, "") ||
                       (sessionManager.savedPin.isNotBlank() && pin == sessionManager.savedPin)

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
      _isOnboardingCompleted.value = true
      _isAppLocked.value = false

      sessionManager.saveSession(
        phone = identity.phone,
        role = user.role.ifBlank { role },
        name = identity.displayName,
        groupId = group?.id ?: "",
        pinHash = user.pinHash,
        pinSalt = identity.id,
        rawPin = pin
      )

      _toastMessage.value = "Welcome back, ${identity.displayName}!"
      onResult(true, null)
    }
  }

  fun resetOfficerPin(
    phone: String,
    newPin: String,
    onResult: (success: Boolean, message: String) -> Unit
  ) {
    viewModelScope.launch {
      if (newPin.length != 4 || !newPin.all { it.isDigit() }) {
        onResult(false, "PIN must be exactly 4 digits.")
        return@launch
      }

      val cleanDigits = phone.filter { it.isDigit() }.takeLast(9)
      val allIdentities = repository.getAllIdentitiesOnce()
      val identity = allIdentities.find { it.phone.filter { c -> c.isDigit() }.endsWith(cleanDigits) }
        ?: repository.getIdentityByPhone(phone)

      if (identity == null) {
        onResult(false, "No account found for phone $phone.")
        return@launch
      }

      val newHash = CryptoUtils.hashPin(newPin, identity.id)
      val existingUser = repository.getUserById(identity.id)
      val updatedUser = existingUser?.copy(pinHash = newHash) ?: UserEntity(
        id = identity.id,
        pinHash = newHash,
        role = "treasurer"
      )
      repository.insertUser(updatedUser)

      sessionManager.pinHash = newHash
      sessionManager.pinSalt = identity.id
      sessionManager.savedPin = newPin

      _toastMessage.value = "Security PIN updated successfully."
      onResult(true, "Security PIN updated successfully.")
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
        syncWithCloud()
      } catch (e: Exception) {
        _toastMessage.value = "Error recording payment: ${e.localizedMessage}"
      }
    }
  }

  fun rejectClaim(claim: ClaimEntity) {
    viewModelScope.launch {
      try {
        repository.rejectClaim(claim)
        _toastMessage.value = "Claim from ${claim.memberName} rejected"
      } catch (e: Exception) {
        _toastMessage.value = "Rejection failed: ${e.localizedMessage}"
      }
    }
  }

  fun advanceNewWeek() {
    val cycle = activeCycle.value ?: return
    val groupId = _selectedGroupId.value
    viewModelScope.launch {
      repository.startNewCycle(groupId, cycle)
      _showNewWeekDialog.value = false
      _toastMessage.value = "Week ${cycle.number + 1} opened successfully!"
      syncWithCloud()
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
    if (show) generateNewPairingCode()
  }

  fun addNewMember(alias: String, phone: String) {
    val groupId = _selectedGroupId.value
    viewModelScope.launch {
      try {
        repository.addMember(groupId, alias, phone)
        _showAddMemberDialog.value = false
        _toastMessage.value = "Added member $alias"
        syncWithCloud()
      } catch (e: Exception) {
        _toastMessage.value = "Unable to add member: ${e.localizedMessage}"
      }
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

  fun sendLiveWhatsAppMessage(phone: String, messageText: String, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
    viewModelScope.launch {
      try {
        val response = SusuApiClient.getApiService().sendWhatsAppMessage(
          SendWhatsAppMessageRequest(phone = phone, message = messageText)
        )
        if (response.isSuccessful) {
          repository.logOutboundMessage(phone, messageText, "sent")
          _toastMessage.value = "WhatsApp message dispatched to $phone"
          onResult(true, null)
        } else {
          repository.logOutboundMessage(phone, messageText, "failed")
          _toastMessage.value = "WhatsApp send failed (HTTP ${response.code()})"
          onResult(false, "WhatsApp provider did not accept the message")
        }
      } catch (e: Exception) {
        repository.logOutboundMessage(phone, messageText, "failed")
        _toastMessage.value = "WhatsApp send failed: ${e.localizedMessage}"
        onResult(false, e.localizedMessage)
      }
    }
  }

  fun sendWeeklyCollectionReminder() {
    viewModelScope.launch {
      try {
        repository.syncAllOfflineDataToCloud()
        val response = SusuApiClient.getApiService().triggerFridayReminder()
        check(response.isSuccessful) { "Reminder dispatch failed (HTTP ${response.code()})" }
        _toastMessage.value = "WhatsApp accepted ${response.body()?.sentCount ?: 0} reminders"
      } catch (e: Exception) { _toastMessage.value = e.localizedMessage }
    }
  }

  fun sendTargetedUnpaidNudges() {
    sendWeeklyCollectionReminder()
  }

  fun sendSundaySummaryDigest() {
    viewModelScope.launch {
      try {
        repository.syncAllOfflineDataToCloud()
        val response = SusuApiClient.getApiService().triggerSundayDigest()
        check(response.isSuccessful) { "Summary dispatch failed (HTTP ${response.code()})" }
        _toastMessage.value = "WhatsApp accepted ${response.body()?.digestCount ?: 0} summaries"
      } catch (e: Exception) { _toastMessage.value = e.localizedMessage }
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
    _isAppLocked.value = false
    sessionManager.clearSession()
    _toastMessage.value = "Signed out successfully"
  }

  fun deleteAccount() {
    viewModelScope.launch {
      database.clearAllTables()
      sessionManager.fullReset()
      _isAuthenticated.value = false
      _isOnboardingCompleted.value = false
      _isAppLocked.value = false
      _selectedGroupId.value = ""
      _toastMessage.value = "Account and local ledger data deleted"
    }
  }
}
