package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ReceiptLong
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

class MainActivity : ComponentActivity() {

  private val viewModel: SusuViewModel by viewModels()

  @OptIn(ExperimentalMaterial3Api::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    setContent {
      SusuLedgerTheme {
        val isAuthenticated by viewModel.isAuthenticated.collectAsState()
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

        val showPaymentSheet by viewModel.showPaymentSheet.collectAsState()
        val selectedMemberForPayment by viewModel.selectedMemberForPayment.collectAsState()
        val selectedClaimForConfirmation by viewModel.selectedClaimForConfirmation.collectAsState()

        val showReportsSheet by viewModel.showReportsSheet.collectAsState()
        val showPairingSheet by viewModel.showPairingSheet.collectAsState()
        val showAddMemberDialog by viewModel.showAddMemberDialog.collectAsState()

        val pairingCode by viewModel.pairingCode.collectAsState()
        val pairingSecondsRemaining by viewModel.pairingSecondsRemaining.collectAsState()
        val toastMessage by viewModel.toastMessage.collectAsState()

        val snackbarHostState = remember { SnackbarHostState() }

        // Navigation state: [Home] [Members] [History] [More]
        var currentNavIndex by remember { mutableStateOf(0) }

        // Unauthenticated Onboarding Flow Stages: Splash -> Walkthrough -> Setup / Login
        var showSplashScreen by remember { mutableStateOf(true) }
        var showWalkthroughScreen by remember { mutableStateOf(false) }
        var showLoginScreen by remember { mutableStateOf(false) }

        // Subscreen overlays
        var showCycleDetailScreen by remember { mutableStateOf(false) }
        var showNewCycleScreen by remember { mutableStateOf(false) }
        var showWhatsAppSimulator by remember { mutableStateOf(false) }

        // Biometric / PIN App Lock state
        var isAppLocked by remember { mutableStateOf(false) }
        val lifecycleOwner = LocalLifecycleOwner.current

        DisposableEffect(lifecycleOwner) {
          val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
              if (isAuthenticated) {
                isAppLocked = true
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

        // Global Back Navigation Handling
        BackHandler(
          enabled = showWhatsAppSimulator || showCycleDetailScreen || showNewCycleScreen || currentNavIndex != 0 || (!isAuthenticated && (showLoginScreen || showWalkthroughScreen || !showSplashScreen))
        ) {
          when {
            showWhatsAppSimulator -> showWhatsAppSimulator = false
            showCycleDetailScreen -> showCycleDetailScreen = false
            showNewCycleScreen -> showNewCycleScreen = false
            currentNavIndex != 0 -> currentNavIndex = 0
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
                        isAppLocked = false
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
                    isAppLocked = false
                  },
                  onSwitchToLogin = {
                    showLoginScreen = true
                  }
                )
              }
            }
          } else if (isAppLocked) {
            // App Lock Screen: Requires Biometric or 4-digit PIN
            AppLockScreen(
              onUnlockSuccess = {
                isAppLocked = false
              },
              onSignOut = {
                viewModel.logout()
                isAppLocked = false
                showSplashScreen = true
                showWalkthroughScreen = false
              }
            )
          } else {
            val currentGroup = groups.find { it.id == selectedGroupId } ?: groups.firstOrNull()
            val currentGroupName = currentGroup?.name ?: "Susu Group"

            when {
              showCycleDetailScreen -> {
                CycleDetailScreen(
                  cycleNumber = activeCycle?.number ?: 1,
                  dueDate = "Friday",
                  members = members,
                  paidMemberIds = payments.map { it.memberId }.toSet(),
                  onBack = { showCycleDetailScreen = false },
                  onMarkPaid = { member ->
                    viewModel.openPaymentSheetForMember(member)
                  },
                  onCloseWeek = {
                    showCycleDetailScreen = false
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

              showNewCycleScreen -> {
                NewCycleScreen(
                  currentOpenWeek = activeCycle?.number ?: 1,
                  defaultNextWeekNumber = (activeCycle?.number ?: 1) + 1,
                  defaultAmount = activeCycle?.amountDue ?: (currentGroup?.amount ?: 50.0),
                  memberCount = members.size,
                  onBack = { showNewCycleScreen = false },
                  onOpenWeek = { weekNum, amt, dueDate ->
                    showNewCycleScreen = false
                    pendingSensitiveAction = Triple(
                      "Authorize Week Rollout",
                      "Open Week $weekNum at GHS ${amt.toInt()} due $dueDate and notify all members via WhatsApp."
                    ) {
                      viewModel.advanceNewWeek()
                    }
                  }
                )
              }

              showWhatsAppSimulator -> {
                WhatsAppBotScreen(
                  groupName = currentGroupName,
                  messages = messages,
                  members = members,
                  onBack = { showWhatsAppSimulator = false },
                  onSendMessage = { phone, msg -> viewModel.simulateMemberWhatsAppMessage(phone, msg) },
                  onOpenPairing = { viewModel.showPairingSheet(true) },
                  onSendWeeklyReminder = { viewModel.sendWeeklyCollectionReminder() },
                  onSendUnpaidNudges = { viewModel.sendTargetedUnpaidNudges() },
                  onSendSundayDigest = { viewModel.sendSundaySummaryDigest() }
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
                            onClick = { currentNavIndex = 0 },
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
                            onClick = { currentNavIndex = 1 },
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
                            onClick = { currentNavIndex = 2 },
                            icon = {
                              Icon(
                                imageVector = Icons.Default.ReceiptLong,
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
                            onClick = { currentNavIndex = 3 },
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
                        botPairingCode = botPairingCode,
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
                        onNewWeekClick = { showNewCycleScreen = true },
                        onDirectPaymentClick = { viewModel.openPaymentSheetDirect() },
                        onViewAllPendingClick = { currentNavIndex = 1 },
                        onOpenCycleDetail = { showCycleDetailScreen = true },
                        onOpenWhatsAppSimulator = { showWhatsAppSimulator = true },
                        onLockApp = { isAppLocked = true },
                        onSignOut = {
                          viewModel.logout()
                          isAppLocked = false
                          showSplashScreen = true
                          showWalkthroughScreen = false
                        }
                      )

                      1 -> MembersScreen(
                        members = members,
                        cyclePayments = payments.filter { it.cycleId == (activeCycle?.id ?: "") },
                        cycleNumber = activeCycle?.number ?: 1,
                        onRecordPaymentForMember = { member -> viewModel.openPaymentSheetForMember(member) },
                        onAddMemberClick = { viewModel.showAddMemberDialog(true) },
                        onBackClick = { currentNavIndex = 0 }
                      )

                      2 -> HistoryScreen(
                        groupName = currentGroupName,
                        cycles = allCycles,
                        members = members,
                        payments = payments,
                        onOpenReportExport = { viewModel.showReportsSheet(true) },
                        onBackClick = { currentNavIndex = 0 }
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
                        onOpenWhatsAppSimulator = { showWhatsAppSimulator = true },
                        onSignOut = {
                          viewModel.logout()
                          isAppLocked = false
                          showSplashScreen = true
                          showWalkthroughScreen = false
                        },
                        onDeleteAccount = {
                          viewModel.deleteAccount()
                          isAppLocked = false
                          showSplashScreen = true
                          showWalkthroughScreen = false
                        },
                        onLockApp = {
                          isAppLocked = true
                        },
                        onBackClick = { currentNavIndex = 0 }
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
                onDismiss = { viewModel.showPairingSheet(false) }
              )
            }

            // Add Member Dialog
            if (showAddMemberDialog) {
              AddMemberDialog(
                onDismiss = { viewModel.showAddMemberDialog(false) },
                onConfirm = { alias, phone ->
                  viewModel.addNewMember(alias, phone)
                }
              )
            }

            // Dual Authorization Biometric / PIN Modal
            pendingSensitiveAction?.let { (actionTitle, actionDesc, onApproved) ->
              BiometricAuthDialog(
                title = actionTitle,
                subtitle = actionDesc,
                onDismiss = { pendingSensitiveAction = null },
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
