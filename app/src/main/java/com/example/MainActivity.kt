package com.example

import android.Manifest
import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.SusuViewModel
import com.example.ui.components.AddMemberDialog
import com.example.ui.components.BiometricAuthDialog
import com.example.ui.screens.AppLockScreen
import com.example.ui.screens.AppPairingSheet
import com.example.ui.screens.AuthOtpScreen
import com.example.ui.screens.ConfirmPaymentSheet
import com.example.ui.screens.CycleDetailScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.FeatureWalkthroughScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.LedgerScreen
import com.example.ui.screens.MembersScreen
import com.example.ui.screens.MoreSettingsScreen
import com.example.ui.screens.NewCycleScreen
import com.example.ui.screens.OnboardingFlowScreen
import com.example.ui.screens.ReportsSheet
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.WhatsAppBotScreen
import com.example.ui.theme.BorderGrey
import com.example.ui.theme.ForestGreenLightFill
import com.example.ui.theme.ForestGreenPrimary
import com.example.ui.theme.LineIconBlack
import com.example.ui.theme.LineIconGrey
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SusuLedgerTheme
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.ContactsHelper
import com.example.util.ContactsPermissionRationaleDialog
import com.example.util.ContactsPickerBottomSheet
import com.example.util.DeviceContact

class MainActivity : FragmentActivity() {

  private val viewModel: SusuViewModel by viewModels()

  @OptIn(ExperimentalMaterial3Api::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    setContent {
      SusuLedgerTheme {
        val context = LocalContext.current

        val isAuthenticated by viewModel.isAuthenticated.collectAsState()
        val isOnboardingCompleted by viewModel.isOnboardingCompleted.collectAsState()
        val isAppLocked by viewModel.isAppLocked.collectAsState()
        val groups by viewModel.groups.collectAsState()
        val selectedGroupId by viewModel.selectedGroupId.collectAsState()
        val activeCycle by viewModel.activeCycle.collectAsState()
        val allCycles by viewModel.allCycles.collectAsState()
        val members by viewModel.members.collectAsState()
        val payments by viewModel.payments.collectAsState()
        val pendingClaims by viewModel.pendingClaims.collectAsState()
        val messages by viewModel.messages.collectAsState()
        val stats by viewModel.dashboardStats.collectAsState()
        val isSyncing by viewModel.isSyncing.collectAsState()
        val unsyncedPaymentsCount by viewModel.unsyncedPaymentsCount.collectAsState()
        val botPairingCode by viewModel.botPairingCode.collectAsState()
        val isBotConnected by viewModel.isBotConnected.collectAsState()
        val cloudStatus by viewModel.cloudStatus.collectAsState()

        val showPaymentSheet by viewModel.showPaymentSheet.collectAsState()
        val selectedMemberForPayment by viewModel.selectedMemberForPayment.collectAsState()
        val selectedClaimForConfirmation by viewModel.selectedClaimForConfirmation.collectAsState()

        val showReportsSheet by viewModel.showReportsSheet.collectAsState()
        val showPairingSheet by viewModel.showPairingSheet.collectAsState()
        val showAddMemberDialog by viewModel.showAddMemberDialog.collectAsState()

        val pairingCode by viewModel.pairingCode.collectAsState()
        val pairingSecondsRemaining by viewModel.pairingSecondsRemaining.collectAsState()
        val toastMessage by viewModel.toastMessage.collectAsState()

        val ledgerEntries by viewModel.ledgerEntries.collectAsState()
        val verificationReport by viewModel.verificationReport.collectAsState()
        val isVerifying by viewModel.isVerifying.collectAsState()

        val snackbarHostState = remember { SnackbarHostState() }

        // Persistent Navigation & Subscreen state managed by ViewModel and DataStore/SessionManager
        val currentNavIndex by viewModel.currentNavIndex.collectAsState()
        val currentSubscreen by viewModel.currentSubscreen.collectAsState()

        // Unauthenticated Onboarding Flow Stages: Splash -> Walkthrough -> Setup / Login
        // Note: For returning authenticated officers, we never block them with the splash screen
        var showSplashScreen by remember { mutableStateOf(!isOnboardingCompleted) }
        var showWalkthroughScreen by remember { mutableStateOf(false) }
        var showLoginScreen by remember { mutableStateOf(false) }

        // Robust Contacts Permission Handling
        var showContactsPickerSheet by remember { mutableStateOf(false) }
        var showContactsRationaleDialog by remember { mutableStateOf(false) }
        var isPermanentlyDenied by remember { mutableStateOf(false) }

        val contactsPermissionLauncher = rememberLauncherForActivityResult(
          contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
          if (isGranted) {
            showContactsPickerSheet = true
          } else {
            val activity = context as? Activity
            if (activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_CONTACTS)) {
              isPermanentlyDenied = true
            }
            showContactsRationaleDialog = true
          }
        }

        val requestContactsAccess: () -> Unit = {
          if (ContactsHelper.hasContactsPermission(context)) {
            showContactsPickerSheet = true
          } else {
            val activity = context as? Activity
            if (activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_CONTACTS)) {
              isPermanentlyDenied = false
              showContactsRationaleDialog = true
            } else {
              contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
          }
        }

        val lifecycleOwner = LocalLifecycleOwner.current

        DisposableEffect(lifecycleOwner) {
          val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
              if (isAuthenticated) {
                viewModel.lockApp()
              }
            }
          }
          lifecycleOwner.lifecycle.addObserver(observer)
          onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
          }
        }

        // State for Biometric / PIN authorization on sensitive operations
        var pendingSensitiveAction by remember {
          mutableStateOf<Triple<String, String, () -> Unit>?>(null)
        }

        // Toast / Snackbar listener
        LaunchedEffect(toastMessage) {
          toastMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearToast()
          }
        }

        // Global Back Navigation Handling using persistent architecture
        BackHandler(
          enabled = currentSubscreen.isNotBlank() || currentNavIndex != 0 || (!isAuthenticated && (showLoginScreen || showWalkthroughScreen || !showSplashScreen))
        ) {
          when {
            currentSubscreen.isNotBlank() -> viewModel.closeSubscreen()
            currentNavIndex != 0 -> viewModel.setNavIndex(0)
            !isAuthenticated && showLoginScreen -> showLoginScreen = false
            !isAuthenticated && !showSplashScreen && !showWalkthroughScreen -> showWalkthroughScreen = true
            !isAuthenticated && showWalkthroughScreen -> {
              showWalkthroughScreen = false
              showSplashScreen = true
            }
          }
        }

        Surface(
          modifier = Modifier.fillMaxSize(),
          color = PureWhite
        ) {
          if (!isAuthenticated) {
            if (isOnboardingCompleted) {
              // Returning officer session: Directly present PIN / Biometric login to prevent starting from scratch
              AuthOtpScreen(
                onAuthenticate = { phone, pin, role, onResult ->
                  viewModel.authenticateOfficer(phone, pin, role) { success, err ->
                    if (success) {
                      showLoginScreen = false
                      viewModel.unlockApp()
                    }
                    onResult(success, err)
                  }
                },
                onNavigateToRegister = {
                  showLoginScreen = false
                  showSplashScreen = false
                  showWalkthroughScreen = false
                }
              )
            } else {
              when {
                showSplashScreen -> {
                  SplashScreen(
                    onProceed = {
                      showSplashScreen = false
                      showWalkthroughScreen = true
                    }
                  )
                }
                showWalkthroughScreen -> {
                  FeatureWalkthroughScreen(
                    onComplete = {
                      showWalkthroughScreen = false
                      showLoginScreen = false
                    },
                    onSkip = {
                      showWalkthroughScreen = false
                      showLoginScreen = false
                    }
                  )
                }
                showLoginScreen -> {
                  AuthOtpScreen(
                    onAuthenticate = { phone, pin, role, onResult ->
                      viewModel.authenticateOfficer(phone, pin, role) { success, err ->
                        if (success) {
                          showLoginScreen = false
                          viewModel.unlockApp()
                        }
                        onResult(success, err)
                      }
                    },
                    onNavigateToRegister = {
                      showLoginScreen = false
                    }
                  )
                }
                else -> {
                  // App-First Clean Slate Onboarding Flow
                  OnboardingFlowScreen(
                    pairingCode = pairingCode,
                    onCompleteOnboarding = { groupName, amount, treasurerPhone, treasurerName, initialMembers, treasurerPin ->
                      val memberPairs = initialMembers.map { it.name to it.phone }
                      viewModel.completeOnboarding(
                        groupName = groupName,
                        amount = amount,
                        treasurerPhone = treasurerPhone,
                        treasurerName = treasurerName,
                        members = memberPairs,
                        treasurerPin = treasurerPin
                      )
                      viewModel.unlockApp()
                    },
                    onSwitchToLogin = {
                      showLoginScreen = true
                    }
                  )
                }
              }
            }
          } else if (isAppLocked) {
            // App Lock Screen: Requires Biometric or 4-digit PIN
            AppLockScreen(
              groupName = groups.find { it.id == selectedGroupId }?.name ?: "Susu Group",
              onVerifyPin = { pin, onResult ->
                viewModel.verifyOfficerPin(pin, onResult)
              },
              onUnlockSuccess = {
                viewModel.unlockApp()
              },
              onSignOut = {
                viewModel.logout()
                showSplashScreen = true
                showWalkthroughScreen = false
              }
            )
          } else {
            val currentGroup = groups.find { it.id == selectedGroupId } ?: groups.firstOrNull()
            val currentGroupName = currentGroup?.name ?: "Susu Group"

            when (currentSubscreen) {
              "cycle_detail" -> {
                CycleDetailScreen(
                  cycleNumber = activeCycle?.number ?: 1,
                  dueDate = "Friday",
                  members = members,
                  paidMemberIds = payments.map { it.memberId }.toSet(),
                  onBack = { viewModel.closeSubscreen() },
                  onMarkPaid = { member ->
                    viewModel.openPaymentSheetForMember(member)
                  },
                  onCloseWeek = {
                    viewModel.closeSubscreen()
                    val curWeek = activeCycle?.number ?: 1
                    pendingSensitiveAction = Triple(
                      "Authorize Week Close",
                      "Close Week $curWeek and lock contributions with dual-officer cryptographic sign-off."
                    ) {
                      viewModel.advanceNewWeek()
                    }
                  }
                )
              }

              "new_cycle" -> {
                NewCycleScreen(
                  currentOpenWeek = activeCycle?.number ?: 1,
                  defaultNextWeekNumber = (activeCycle?.number ?: 1) + 1,
                  defaultAmount = activeCycle?.amountDue ?: (currentGroup?.amount ?: 50.0),
                  memberCount = members.size,
                  onBack = { viewModel.closeSubscreen() },
                  onOpenWeek = { weekNum, amt, dueDate ->
                    viewModel.closeSubscreen()
                    pendingSensitiveAction = Triple(
                      "Authorize Week Rollout",
                      "Open Week $weekNum at GHS ${amt.toInt()} due $dueDate and notify all members via WhatsApp."
                    ) {
                      viewModel.advanceNewWeek()
                    }
                  }
                )
              }

              "whatsapp_bot" -> {
                WhatsAppBotScreen(
                  groupName = currentGroupName,
                  messages = messages,
                  members = members,
                  pairingCode = pairingCode,
                  cloudStatus = cloudStatus,
                  onBack = { viewModel.closeSubscreen() },
                  onSendMessage = { phone, msg -> viewModel.sendLiveWhatsAppMessage(phone, msg) },
                  onOpenPairing = { viewModel.showPairingSheet(true) },
                  onRefreshPairingCode = { viewModel.refreshPairingCode() },
                  onCheckCloudStatus = { viewModel.checkCloudSystemStatus() },
                  onSendWeeklyReminder = { viewModel.sendWeeklyCollectionReminder() },
                  onSendUnpaidNudges = { viewModel.sendTargetedUnpaidNudges() },
                  onSendSundayDigest = { viewModel.sendSundaySummaryDigest() }
                )
              }

              "ledger" -> {
                LedgerScreen(
                  payments = payments,
                  ledgerEntries = ledgerEntries,
                  verificationReport = verificationReport,
                  isVerifying = isVerifying,
                  onVerifyIntegrityClick = { viewModel.runIntegrityCheck() },
                  onBackClick = { viewModel.closeSubscreen() }
                )
              }

              else -> {
                Scaffold(
                  containerColor = PureWhite,
                  snackbarHost = { SnackbarHost(snackbarHostState) },
                  bottomBar = {
                    Surface(
                      color = PureWhite,
                      modifier = Modifier.fillMaxWidth()
                    ) {
                      Box(
                        modifier = Modifier
                          .fillMaxWidth()
                          .border(1.dp, BorderGrey)
                      ) {
                        NavigationBar(
                          containerColor = PureWhite,
                          tonalElevation = 0.dp,
                          modifier = Modifier.testTag("bottom_nav_bar")
                        ) {
                          NavigationBarItem(
                            selected = currentNavIndex == 0,
                            onClick = { viewModel.setNavIndex(0) },
                            icon = {
                              Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Home",
                                tint = if (currentNavIndex == 0) ForestGreenPrimary else LineIconGrey
                              )
                            },
                            label = { Text("Home", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                              selectedIconColor = ForestGreenPrimary,
                              selectedTextColor = ForestGreenPrimary,
                              indicatorColor = ForestGreenLightFill,
                              unselectedIconColor = LineIconGrey,
                              unselectedTextColor = TextSecondary
                            ),
                            modifier = Modifier.testTag("nav_home")
                          )

                          NavigationBarItem(
                            selected = currentNavIndex == 1,
                            onClick = { viewModel.setNavIndex(1) },
                            icon = {
                              Icon(
                                imageVector = Icons.Default.People,
                                contentDescription = "Members",
                                tint = if (currentNavIndex == 1) ForestGreenPrimary else LineIconGrey
                              )
                            },
                            label = { Text("Members", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                              selectedIconColor = ForestGreenPrimary,
                              selectedTextColor = ForestGreenPrimary,
                              indicatorColor = ForestGreenLightFill,
                              unselectedIconColor = LineIconGrey,
                              unselectedTextColor = TextSecondary
                            ),
                            modifier = Modifier.testTag("nav_members")
                          )

                          NavigationBarItem(
                            selected = currentNavIndex == 2,
                            onClick = { viewModel.setNavIndex(2) },
                            icon = {
                              Icon(
                                imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                contentDescription = "History",
                                tint = if (currentNavIndex == 2) ForestGreenPrimary else LineIconGrey
                              )
                            },
                            label = { Text("History", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                              selectedIconColor = ForestGreenPrimary,
                              selectedTextColor = ForestGreenPrimary,
                              indicatorColor = ForestGreenLightFill,
                              unselectedIconColor = LineIconGrey,
                              unselectedTextColor = TextSecondary
                            ),
                            modifier = Modifier.testTag("nav_history")
                          )

                          NavigationBarItem(
                            selected = currentNavIndex == 3,
                            onClick = { viewModel.setNavIndex(3) },
                            icon = {
                              Icon(
                                imageVector = Icons.Default.MoreHoriz,
                                contentDescription = "More",
                                tint = if (currentNavIndex == 3) ForestGreenPrimary else LineIconGrey
                              )
                            },
                            label = { Text("More", fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                              selectedIconColor = ForestGreenPrimary,
                              selectedTextColor = ForestGreenPrimary,
                              indicatorColor = ForestGreenLightFill,
                              unselectedIconColor = LineIconGrey,
                              unselectedTextColor = TextSecondary
                            ),
                            modifier = Modifier.testTag("nav_more")
                          )
                        }
                      }
                    }
                  }
                ) { innerPadding ->
                  Box(
                    modifier = Modifier
                      .fillMaxSize()
                      .padding(innerPadding)
                  ) {
                    when (currentNavIndex) {
                      0 -> DashboardScreen(
                        groups = groups,
                        selectedGroupId = selectedGroupId,
                        activeCycle = activeCycle,
                        stats = stats,
                        pendingClaims = pendingClaims,
                        recentPayments = payments,
                        unsyncedCount = unsyncedPaymentsCount,
                        isSyncing = isSyncing,
                        botPairingCode = pairingCode,
                        isBotConnected = isBotConnected,
                        onSyncClick = { viewModel.syncWithCloud() },
                        onSelectGroup = { viewModel.selectGroup(it) },
                        onOpenPairing = { viewModel.showPairingSheet(true) },
                        onConfirmClaim = { claim -> viewModel.openPaymentSheetForClaim(claim) },
                        onRejectClaim = { claim ->
                          pendingSensitiveAction = Triple(
                            "Authorize Claim Rejection",
                            "Rejecting unverified claim of GHS ${String.format(java.util.Locale.US, "%.2f", claim.claimedAmount)} from ${claim.memberName}."
                          ) {
                            viewModel.rejectClaim(claim)
                          }
                        },
                        onNewWeekClick = { viewModel.openSubscreen("new_cycle") },
                        onDirectPaymentClick = { viewModel.openPaymentSheetDirect() },
                        onViewAllPendingClick = { viewModel.setNavIndex(1) },
                        onOpenCycleDetail = { viewModel.openSubscreen("cycle_detail") },
                        onOpenLedger = { viewModel.openSubscreen("ledger") },
                        onOpenWhatsAppSimulator = { viewModel.openSubscreen("whatsapp_bot") },
                        onLockApp = { viewModel.lockApp() },
                        onSignOut = {
                          viewModel.logout()
                          showSplashScreen = true
                          showWalkthroughScreen = false
                        }
                      )

                      1 -> MembersScreen(
                        members = members,
                        cyclePayments = payments.filter { it.cycleId == (activeCycle?.id ?: "") },
                        cycleNumber = activeCycle?.number ?: 1,
                        expectedCycleAmount = activeCycle?.amountDue ?: (currentGroup?.amount ?: 50.0),
                        onRecordPaymentForMember = { member -> viewModel.openPaymentSheetForMember(member) },
                        onAddMemberClick = { viewModel.showAddMemberDialog(true) },
                        onImportContactsClick = requestContactsAccess,
                        onBackClick = { viewModel.setNavIndex(0) }
                      )

                      2 -> HistoryScreen(
                        groupName = currentGroupName,
                        cycles = allCycles,
                        members = members,
                        payments = payments,
                        onOpenReportExport = { viewModel.showReportsSheet(true) },
                        onBackClick = { viewModel.setNavIndex(0) }
                      )

                      3 -> MoreSettingsScreen(
                        groupName = currentGroupName,
                        currentGroup = currentGroup,
                        activeCycle = activeCycle,
                        onTogglePauseGroup = { reason -> viewModel.togglePauseGroup(reason) },
                        onUpdateContributionAmount = { newAmt, applyCurrent, reason ->
                          viewModel.updateContributionAmount(newAmt, applyCurrent, reason)
                        },
                        onRenameGroup = { newName -> viewModel.renameGroup(newName) },
                        onOpenPairingSheet = { viewModel.showPairingSheet(true) },
                        onOpenWhatsAppSimulator = { viewModel.openSubscreen("whatsapp_bot") },
                        onSignOut = {
                          viewModel.logout()
                          showSplashScreen = true
                          showWalkthroughScreen = false
                        },
                        onDeleteAccount = {
                          viewModel.deleteAccount()
                          showSplashScreen = true
                          showWalkthroughScreen = false
                        },
                        onLockApp = {
                          viewModel.lockApp()
                        },
                        onBackClick = { viewModel.setNavIndex(0) }
                      )
                    }
                  }
                }
              }
            }

            // Confirm Payment Bottom Sheet
            if (showPaymentSheet) {
              val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
              ConfirmPaymentSheet(
                sheetState = sheetState,
                members = members,
                initialMember = selectedMemberForPayment,
                initialClaim = selectedClaimForConfirmation,
                cycleNumber = activeCycle?.number ?: 1,
                cycleDueAmount = activeCycle?.amountDue ?: (currentGroup?.amount ?: 50.0),
                onDismiss = { viewModel.dismissPaymentSheet() },
                onConfirm = { memberId, amount, method, momoRef, sendReceipt ->
                  val memberName = members.find { it.id == memberId }?.alias ?: "Member"
                  viewModel.dismissPaymentSheet()
                  pendingSensitiveAction = Triple(
                    "Authorize Ledger Record",
                    "Authorizing GHS ${String.format(java.util.Locale.US, "%.2f", amount)} for $memberName to be appended to official ledger history."
                  ) {
                    viewModel.confirmPayment(memberId, amount, method, momoRef, sendReceipt)
                  }
                }
              )
            }

            // Reports Bottom Sheet
            if (showReportsSheet) {
              val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
              ReportsSheet(
                sheetState = sheetState,
                stats = stats,
                cycleNumber = activeCycle?.number ?: 1,
                payments = payments,
                onDismiss = { viewModel.showReportsSheet(false) }
              )
            }

            // Pairing Bottom Sheet
            if (showPairingSheet) {
              val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
              AppPairingSheet(
                sheetState = sheetState,
                pairingCode = pairingCode,
                secondsRemaining = pairingSecondsRemaining,
                onDismiss = { viewModel.showPairingSheet(false) },
                onGenerateNewCode = { viewModel.generateNewPairingCode() }
              )
            }

            // Add Member Dialog
            if (showAddMemberDialog) {
              AddMemberDialog(
                onDismiss = { viewModel.showAddMemberDialog(false) },
                onConfirm = { alias, phone ->
                  viewModel.addNewMember(alias, phone)
                },
                onOpenContacts = requestContactsAccess
              )
            }

            // Contacts Permission Rationale Dialog
            if (showContactsRationaleDialog) {
              ContactsPermissionRationaleDialog(
                onDismiss = { showContactsRationaleDialog = false },
                onRequestPermission = {
                  showContactsRationaleDialog = false
                  contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                },
                isPermanentlyDenied = isPermanentlyDenied,
                onOpenSettings = {
                  showContactsRationaleDialog = false
                  ContactsHelper.openAppSettings(context)
                }
              )
            }

            // Contacts Picker Bottom Sheet
            if (showContactsPickerSheet) {
              ContactsPickerBottomSheet(
                onDismiss = { showContactsPickerSheet = false },
                onContactsSelected = { selectedContacts ->
                  viewModel.importContactsAsMembers(selectedContacts)
                }
              )
            }

            // Dual Authorization Biometric / PIN Modal
            pendingSensitiveAction?.let { (actionTitle, actionDesc, onApproved) ->
              BiometricAuthDialog(
                title = actionTitle,
                subtitle = actionDesc,
                onDismiss = { pendingSensitiveAction = null },
                onVerifyPin = { pin, onResult ->
                  viewModel.verifyOfficerPin(pin, onResult)
                },
                onAuthorized = {
                  pendingSensitiveAction = null
                  onApproved()
                }
              )
            }
          }
        }
      }
    }
  }
}
