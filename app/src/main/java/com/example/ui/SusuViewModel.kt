package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AuditLogEntity
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
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

data class SavedGroupItem(
  val id: String,
  val name: String,
  val amount: Double,
  val schedule: String,
  val treasurerPhone: String,
  val treasurerName: String
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

  private val _isBiometricEnabled = MutableStateFlow(sessionManager.isBiometricEnabled)
  val isBiometricEnabled: StateFlow<Boolean> = _isBiometricEnabled.asStateFlow()

  private val _isAuthenticated = MutableStateFlow(sessionManager.isOnboarded && sessionManager.loggedInPhone.isNotBlank())
  val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

  private val _userRole = MutableStateFlow(sessionManager.loggedInRole.ifBlank { "treasurer" })
  val userRole: StateFlow<String> = _userRole.asStateFlow()

  private val _userPhone = MutableStateFlow(sessionManager.loggedInPhone)
  val userPhone: StateFlow<String> = _userPhone.asStateFlow()

  // WhatsApp Bot State & Single Unified Dynamic Pairing Code
  private val _pairingCode = MutableStateFlow("")
  val pairingCode: StateFlow<String> = _pairingCode.asStateFlow()
  val botPairingCode: StateFlow<String> = _pairingCode.asStateFlow()

  private val _isBotConnected = MutableStateFlow(sessionManager.isBotConnected)
  val isBotConnected: StateFlow<Boolean> = _isBotConnected.asStateFlow()

  private val _botConnectionError = MutableStateFlow<String?>(null)
  val botConnectionError: StateFlow<String?> = _botConnectionError.asStateFlow()

  // Cloud Diagnostics State
  private val _cloudStatus = MutableStateFlow<CloudSystemStatusResponse?>(null)
  val cloudStatus: StateFlow<CloudSystemStatusResponse?> = _cloudStatus.asStateFlow()

  private val _isCheckingCloud = MutableStateFlow(false)
  val isCheckingCloud: StateFlow<Boolean> = _isCheckingCloud.asStateFlow()

  fun setBotConnected(connected: Boolean) {
    _isBotConnected.value = connected
    sessionManager.isBotConnected = connected
  }

  fun refreshPairingCode() {
    generateNewPairingCode()
  }

  private var pairingJob: Job? = null
  private var pairingOwner = ""
  private var pairingDeadline = 0L
  private var pairingCompleted = false

  fun generateNewPairingCode() = startPairing(forceNew = true)

  private fun startPairing(forceNew: Boolean = false) {
    val phoneNum = _userPhone.value
    val groupId = _selectedGroupId.value
    if (phoneNum.isBlank() || groupId.isBlank()) return
    val owner = "$phoneNum:$groupId"
    // Reopening the sheet or repeated taps must not race an existing registration.
    if (pairingJob?.isActive == true && pairingOwner == owner && (!forceNew || pairingDeadline == 0L)) return
    pairingJob?.cancel()
    pairingOwner = owner
    pairingCompleted = false
    val session = sessionManager.pairingForConnection(phoneNum, groupId, forceNew)
    val newCode = session.code
    _pairingCode.value = newCode
    pairingDeadline = session.expiresAt
    _pairingSecondsRemaining.value = session.secondsRemaining()

    pairingJob = viewModelScope.launch {
      try {
        val api = SusuApiClient.getApiService()
        // Recover a registration whose response was lost, without replacing its code.
        val existing = api.getPairingStatus(newCode)
        if (existing.isSuccessful && existing.body()?.status == "PAIRED") {
          pairingCompleted = true
          _isBotConnected.value = true
          _showPairingSheet.value = false
          return@launch
        }
        if (!existing.isSuccessful && existing.code() != 404) {
          error("Unable to check pairing (HTTP ${existing.code()}). Retry with the same code.")
        }
        if (existing.code() == 404) {
          repository.syncAllOfflineDataToCloud(groupId)
          val response = api.registerPairingCode(
            PairBotRequest(
              code = newCode,
              phone = phoneNum,
              groupId = groupId
            )
          )
          if (!response.isSuccessful) error("Pairing registration failed (HTTP ${response.code()})")
        } else if (existing.body()?.status != "PENDING_WHATSAPP_CONFIRMATION") {
          error("This pairing code is no longer active. Generate a new code.")
        }
        _botConnectionError.value = null
        while (_pairingCode.value == newCode && _pairingSecondsRemaining.value > 0) {
          delay(5000)
          val status = api.getPairingStatus(newCode)
          if (status.isSuccessful && status.body()?.status == "PAIRED") {
            pairingCompleted = true
            _isBotConnected.value = true
            _botConnectionError.value = null
            _toastMessage.value = "WhatsApp pairing confirmed"
            _showPairingSheet.value = false
            break
          }
          if (!status.isSuccessful) error("Unable to check pairing (HTTP ${status.code()})")
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _botConnectionError.value = "Pairing connection interrupted: ${e.localizedMessage}"
        _toastMessage.value = _botConnectionError.value
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

  val savedGroups: StateFlow<List<SavedGroupItem>> = combine(groups, repository.getAllIdentities()) { grps, idents ->
    grps.map { g ->
      val ident = idents.find { it.id == g.treasurerId }
      SavedGroupItem(
        id = g.id,
        name = g.name,
        amount = g.amount,
        schedule = g.schedule,
        treasurerPhone = ident?.phone.orEmpty(),
        treasurerName = ident?.displayName.orEmpty()
      )
    }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

  val payments: StateFlow<List<PaymentEntity>> = _selectedGroupId.flatMapLatest { gid ->
    repository.getPaymentsForGroup(gid)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val ledgerEntries: StateFlow<List<LedgerEntryEntity>> = _selectedGroupId.flatMapLatest { gid ->
    repository.getLedgerEntriesForGroup(gid)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val pendingClaims: StateFlow<List<ClaimEntity>> = _selectedGroupId.flatMapLatest { groupId ->
    repository.getPendingGroupClaims(groupId)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val messages: StateFlow<List<MessageLogEntity>> = _selectedGroupId.flatMapLatest { gid ->
    repository.getMessagesForGroup(gid)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val auditLogs: StateFlow<List<AuditLogEntity>> = _selectedGroupId.flatMapLatest { gid ->
    repository.getAuditLogs(gid)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  // Verification & Sync
  val unsyncedPaymentsCount: StateFlow<Int> = repository.unsyncedPaymentsCount
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

  private val _verificationReport = MutableStateFlow<VerificationReport?>(null)
  val verificationReport: StateFlow<VerificationReport?> = _verificationReport.asStateFlow()

  private val _isVerifying = MutableStateFlow(false)
  val isVerifying: StateFlow<Boolean> = _isVerifying.asStateFlow()

  private val _isSyncing = MutableStateFlow(false)
  val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

  suspend fun refreshClaimsWhileVisible() {
    val groupId = _selectedGroupId.value
    if (groupId.isBlank() || com.google.firebase.auth.FirebaseAuth.getInstance().currentUser == null) return
    try {
      repository.syncPendingClaimsFromCloud(groupId)
      val id = runCatching { java.util.UUID.fromString(groupId).toString() }
        .getOrElse { java.util.UUID.nameUUIDFromBytes(groupId.toByteArray(Charsets.UTF_8)).toString() }
      val connection = SusuApiClient.getApiService().getBotConnection(id)
      if (connection.isSuccessful) {
        val connected = connection.body()?.status == "CONNECTED"
        _isBotConnected.value = connected
        sessionManager.isBotConnected = connected
        _botConnectionError.value = if (connected) null else "WhatsApp is disconnected. Reconnect with your verified number."
      }
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
      throw cancelled
    } catch (error: Exception) {
      _botConnectionError.value = "Unable to refresh: ${error.localizedMessage}"
      // Keep local records while offline; retry on the next foreground refresh.
    }
  }

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
    if (subscreen == "whatsapp_bot") {
      val phone = _userPhone.value
      val group = _selectedGroupId.value
      val saved = sessionManager.loadPairing(phone, group)
      val shouldForce = pairingCompleted || (saved != null && saved.secondsRemaining() <= 0)
      startPairing(forceNew = shouldForce)
    }
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
  private val _pairingSecondsRemaining = MutableStateFlow(0)
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
    val paidCount = cyclePays.groupBy { it.memberId }.count { (_, records) -> records.sumOf { it.amountPaid } >= (cycle?.amountDue ?: 50.0) }
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
      combine(_userPhone, _selectedGroupId) { phone, group -> phone to group }.collect { (phone, group) ->
        pairingJob?.cancel()
        pairingJob = null
        pairingDeadline = 0
        pairingCompleted = false
        _pairingSecondsRemaining.value = 0
        _pairingCode.value = ""
        val saved = sessionManager.loadPairing(phone, group)
        if (phone.isNotBlank() && group.isNotBlank()) {
          if (saved != null && saved.secondsRemaining() > 0) {
            _pairingCode.value = saved.code
            startPairing()
          } else {
            startPairing(forceNew = true)
          }
        }
      }
    }
    viewModelScope.launch {
      while (true) {
        delay(1000)
        _pairingSecondsRemaining.value = ((pairingDeadline - System.currentTimeMillis()).coerceAtLeast(0) / 1000).toInt()
      }
    }

    // Automatically select the active group or first group if none is selected
    viewModelScope.launch {
      groups.collect { groupList ->
        if (_selectedGroupId.value.isBlank() && groupList.isNotEmpty()) {
          val active = sessionManager.activeGroupId
          val found = groupList.find { it.id == active } ?: groupList.first()
          _selectedGroupId.value = found.id
          sessionManager.activeGroupId = found.id
          _isBotConnected.value = sessionManager.isBotConnectedForGroup(found.id)
        }
      }
    }
    viewModelScope.launch {
      _selectedGroupId.collect { gid ->
        if (gid.isNotBlank()) {
          _isBotConnected.value = sessionManager.isBotConnectedForGroup(gid)
        }
      }
    }

    // Auto-recover session if database has registered groups
    viewModelScope.launch {
      try {
        val allGroups = repository.getAllGroupsOnce()
        if (allGroups.isNotEmpty()) {
          val active = sessionManager.activeGroupId
          val firstGroup = allGroups.find { it.id == active } ?: allGroups.first()
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

  fun prepareOnboardingGroup(
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
        val cleanTreasurerPhone = com.example.util.GhanaPhoneUtils.toE164(treasurerPhone)
        val newGroup = repository.createNewGroupWithMembers(
          groupName = groupName,
          amount = amount,
          schedule = schedule,
          treasurerPhone = cleanTreasurerPhone,
          treasurerName = treasurerName,
          members = members,
          treasurerPin = treasurerPin
        )
        _selectedGroupId.value = newGroup.id
        _userPhone.value = cleanTreasurerPhone
        _userRole.value = "treasurer"
        sessionManager.saveSession(
          phone = cleanTreasurerPhone,
          role = "treasurer",
          name = treasurerName,
          groupId = newGroup.id,
          pinHash = CryptoUtils.hashPin(treasurerPin, newGroup.treasurerId),
          pinSalt = newGroup.treasurerId,
          rawPin = treasurerPin
        )
        repository.syncAllOfflineDataToCloud(newGroup.id)
        startPairing(forceNew = true)
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
        var group = currentGroup.value ?: groups.value.find { it.id == _selectedGroupId.value }
        if (group == null) {
          group = repository.createNewGroupWithMembers(
            groupName = groupName,
            amount = amount,
            schedule = schedule,
            treasurerPhone = treasurerPhone,
            treasurerName = treasurerName,
            members = members,
            treasurerPin = treasurerPin
          )
          _selectedGroupId.value = group.id
        }
        val cleanTreasurerPhone = com.example.util.GhanaPhoneUtils.toE164(treasurerPhone)
        _userPhone.value = cleanTreasurerPhone
        _userRole.value = "treasurer"
        _isAuthenticated.value = true
        _isOnboardingCompleted.value = true
        _isAppLocked.value = false

        sessionManager.saveSession(
          phone = cleanTreasurerPhone,
          role = "treasurer",
          name = treasurerName,
          groupId = group.id,
          pinHash = CryptoUtils.hashPin(treasurerPin, group.treasurerId),
          pinSalt = group.treasurerId,
          rawPin = treasurerPin
        )

        _toastMessage.value = "Group '${group.name}' setup completed!"
        syncWithCloud()
      } catch (e: Exception) {
        _toastMessage.value = "Error creating group: ${e.localizedMessage}"
      }
    }
  }

  fun selectGroup(groupId: String) {
    _selectedGroupId.value = groupId
    sessionManager.activeGroupId = groupId
    _isBotConnected.value = sessionManager.isBotConnectedForGroup(groupId)
    viewModelScope.launch {
      val grp = groups.value.find { it.id == groupId } ?: repository.getAllGroupsOnce().find { it.id == groupId }
      if (grp != null) {
        val treasurer = repository.getIdentityById(grp.treasurerId)
        if (treasurer != null) {
          _userPhone.value = treasurer.phone
          sessionManager.loggedInPhone = treasurer.phone
          sessionManager.officerName = treasurer.displayName
        }
      }
    }
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
    preferredGroupId: String? = null,
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
        val err = "This account is not on this phone. Use Restore account with SMS to recover your cloud ledger."
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
        repository.logAuditEvent(
          groupId = currentGroup.value?.id ?: _selectedGroupId.value.ifBlank { null },
          actorId = identity.id,
          action = "PIN_FAILED",
          payload = """{"event":"Failed PIN entry attempt for officer ${identity.displayName}"}"""
        )
        onResult(false, err)
        return@launch
      }

      repository.logAuditEvent(
        groupId = currentGroup.value?.id ?: _selectedGroupId.value.ifBlank { null },
        actorId = identity.id,
        action = "PIN_AUTHENTICATED",
        payload = """{"event":"Officer ${identity.displayName} authenticated successfully"}"""
      )

      _userPhone.value = identity.phone
      _userRole.value = user.role.ifBlank { role }
      val group = (if (!preferredGroupId.isNullOrBlank()) groups.value.find { it.id == preferredGroupId } ?: repository.getAllGroupsOnce().find { it.id == preferredGroupId } else null)
        ?: repository.getGroupByTreasurer(identity.id)
        ?: repository.getAllGroupsOnce().firstOrNull()
      if (group != null) {
        _selectedGroupId.value = group.id
        sessionManager.activeGroupId = group.id
        _isBotConnected.value = sessionManager.isBotConnectedForGroup(group.id)
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

  fun restoreOfficerAccount(newPin: String, onResult: (Boolean, String?) -> Unit) {
    viewModelScope.launch {
      try {
        val verifiedPhone = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.phoneNumber
          ?: error("Verify your phone by SMS before restoring.")
        val owner = repository.restoreAccountFromCloud(verifiedPhone, newPin)
        val botConnected = repository.lastRestoredBotConnected
        _isBotConnected.value = botConnected
        sessionManager.isBotConnected = botConnected
        authenticateOfficer(owner.phone, newPin, "treasurer") { success, err ->
          if (success) {
            viewModelScope.launch {
              refreshClaimsWhileVisible()
            }
          }
          onResult(success, err)
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        onResult(false, e.localizedMessage ?: "Cloud recovery failed. Please retry.")
      }
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
      val identity = allIdentities.find { cleanDigits.isNotBlank() && it.phone.filter { c -> c.isDigit() }.endsWith(cleanDigits) }
        ?: repository.getIdentityByPhone(phone)
        ?: currentGroup.value?.treasurerId?.let { repository.getIdentityById(it) }
        ?: allIdentities.firstOrNull()

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

      sessionManager.saveSession(
        phone = identity.phone,
        role = updatedUser.role,
        name = identity.displayName,
        groupId = currentGroup.value?.id.orEmpty(),
        pinHash = newHash,
        pinSalt = identity.id,
        rawPin = newPin
      )

      repository.logAuditEvent(
        groupId = currentGroup.value?.id ?: _selectedGroupId.value.ifBlank { null },
        actorId = identity.id,
        action = "PIN_UPDATED",
        payload = """{"event":"Officer updated 4-digit security PIN"}"""
      )

      _toastMessage.value = "Security PIN updated successfully."
      onResult(true, "Security PIN updated successfully.")
    }
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
    sendWhatsAppReceipt: Boolean,
    associatedClaim: ClaimEntity? = _selectedClaimForConfirmation.value,
    targetCycleId: String? = null
  ) {
    val cycle = activeCycle.value ?: return
    val groupId = _selectedGroupId.value

    viewModelScope.launch {
      try {
        val group = database.susuDao().getGroupById(groupId)
        val officerId = group?.treasurerId ?: "treasurer"
        val payment = repository.recordPayment(
          groupId = groupId,
          cycleId = associatedClaim?.cycleId ?: targetCycleId ?: cycle.id,
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

  fun advanceNewWeek(amount: Double? = null, dueDate: String? = null) {
    val cycle = activeCycle.value ?: return
    val groupId = _selectedGroupId.value
    viewModelScope.launch {
      try {
        repository.startNewCycle(groupId, cycle, amount ?: cycle.amountDue, dueDate)
        _showNewWeekDialog.value = false
        _toastMessage.value = "Week ${cycle.number + 1} opened successfully!"
        syncWithCloud()
      } catch (error: Exception) {
        _toastMessage.value = "Unable to open week: ${error.localizedMessage}"
      }
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
    if (show) {
      val phone = _userPhone.value
      val group = _selectedGroupId.value
      val saved = sessionManager.loadPairing(phone, group)
      val shouldForce = pairingCompleted || (saved != null && saved.secondsRemaining() <= 0)
      startPairing(forceNew = shouldForce)
    }
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
    viewModelScope.launch {
      val group = currentGroup.value
        ?: groups.value.find { it.id == _selectedGroupId.value }
        ?: groups.value.firstOrNull()
        ?: database.susuDao().getGroupById(_selectedGroupId.value)
        ?: database.susuDao().getAllGroupsOnce().firstOrNull()
        ?: return@launch

      val newState = if (group.state.equals("paused", ignoreCase = true)) "active" else "paused"
      val success = repository.pauseOrResumeGroup(
        groupId = group.id,
        newState = newState,
        reason = reason,
        officerId = group.treasurerId
      )
      if (success) {
        _toastMessage.value = if (newState == "paused") "Group paused & members notified" else "Group resumed & members notified"
        syncWithCloud()
      }
    }
  }

  fun updateContributionAmount(newAmount: Double, applyToCurrentCycle: Boolean = true, reason: String = "Officer dues adjustment") {
    viewModelScope.launch {
      val group = currentGroup.value
        ?: groups.value.find { it.id == _selectedGroupId.value }
        ?: groups.value.firstOrNull()
        ?: database.susuDao().getGroupById(_selectedGroupId.value)
        ?: database.susuDao().getAllGroupsOnce().firstOrNull()
        ?: return@launch

      val success = repository.updateGroupContributionAmount(
        groupId = group.id,
        newAmount = newAmount,
        applyToCurrentCycle = applyToCurrentCycle,
        reason = reason,
        officerId = group.treasurerId
      )
      if (success) {
        _toastMessage.value = "Contribution dues updated to GHS ${String.format(java.util.Locale.US, "%.2f", newAmount)}"
        syncWithCloud()
      }
    }
  }

  fun renameGroup(newName: String) {
    viewModelScope.launch {
      val group = currentGroup.value
        ?: groups.value.find { it.id == _selectedGroupId.value }
        ?: groups.value.firstOrNull()
        ?: database.susuDao().getGroupById(_selectedGroupId.value)
        ?: database.susuDao().getAllGroupsOnce().firstOrNull()
        ?: return@launch

      val updated = group.copy(name = newName)
      database.susuDao().updateGroup(updated)
      repository.logAuditEvent(
        groupId = group.id,
        actorId = group.treasurerId,
        action = "GROUP_RENAMED",
        payload = """{"oldName":"${group.name}","newName":"$newName"}"""
      )
      _toastMessage.value = "Group renamed to '$newName'"
      syncWithCloud()
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

  fun setBiometricEnabled(enabled: Boolean) {
    sessionManager.isBiometricEnabled = enabled
    _isBiometricEnabled.value = enabled
    _toastMessage.value = if (enabled) "Biometric unlock enabled" else "Biometric unlock disabled"
    viewModelScope.launch {
      val group = currentGroup.value
        ?: groups.value.find { it.id == _selectedGroupId.value }
        ?: groups.value.firstOrNull()
      repository.logAuditEvent(
        groupId = group?.id,
        actorId = group?.treasurerId,
        action = "BIOMETRIC_TOGGLED",
        payload = """{"enabled":$enabled}"""
      )
    }
  }

  fun updateOfficerPin(newPin: String, onResult: (Boolean, String) -> Unit) {
    val phone = sessionManager.loggedInPhone.ifBlank {
      com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.phoneNumber.orEmpty()
    }
    if (phone.isBlank()) {
      onResult(false, "No active session found. Please sign in first.")
      return
    }
    resetOfficerPin(phone, newPin, onResult)
  }

  fun beginRegistration() {
    _isOnboardingCompleted.value = false
  }

  fun authenticateBiometricOfficer(preferredGroupId: String? = null, onResult: (Boolean, String?) -> Unit) {
    viewModelScope.launch {
      val preferredGroup = if (!preferredGroupId.isNullOrBlank()) {
        groups.value.find { it.id == preferredGroupId } ?: repository.getAllGroupsOnce().find { it.id == preferredGroupId }
      } else null

      val preferredTreasurer = preferredGroup?.let { repository.getIdentityById(it.treasurerId) }

      val phone = preferredTreasurer?.phone?.ifBlank { null }
        ?: sessionManager.biometricOfficerPhone.ifBlank {
          com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.phoneNumber.orEmpty()
        }
      val identity = repository.getIdentityByPhone(phone)
      val user = identity?.let { repository.getUserById(it.id) }
      if (!sessionManager.isBiometricEnabled || identity == null || user == null) {
        onResult(false, "Sign in with your phone and PIN first to enable this account.")
        return@launch
      }
      val group = preferredGroup ?: repository.getGroupByTreasurer(identity.id) ?: repository.getAllGroupsOnce().firstOrNull()
      _userPhone.value = identity.phone
      _userRole.value = user.role
      _selectedGroupId.value = group?.id.orEmpty()
      if (group != null) {
        sessionManager.activeGroupId = group.id
        _isBotConnected.value = sessionManager.isBotConnectedForGroup(group.id)
      }
      sessionManager.saveSession(identity.phone, user.role, identity.displayName, group?.id.orEmpty(), user.pinHash, identity.id)
      _isAuthenticated.value = true
      _isOnboardingCompleted.value = true
      _isAppLocked.value = false
      onResult(true, null)
    }
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
