package com.example

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.ui.screens.AuthOtpScreen
import com.example.ui.theme.SusuLedgerTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    composeTestRule.setContent {
      SusuLedgerTheme {
        AuthOtpScreen(
          onAuthenticate = { _, _, _, _, callback -> callback(true, null) },
          onNavigateToRegister = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }

  @Test
  fun test_delete_account_button_and_dialog_trigger() {
    var deleteAccountTriggered = false
    composeTestRule.setContent {
      SusuLedgerTheme {
        com.example.ui.screens.MoreSettingsScreen(
          onDeleteAccount = { deleteAccountTriggered = true }
        )
      }
    }

    // Scroll to and click the delete account button
    composeTestRule.onNodeWithTag("settings_delete_account_btn")
      .performScrollTo()
      .performClick()

    // Verify dialog appears and click confirmation
    composeTestRule.onNodeWithTag("confirm_delete_account_btn")
      .assertIsDisplayed()
      .performClick()

    org.junit.Assert.assertTrue(deleteAccountTriggered)
  }

  @Test
  fun test_susu_feedback_snackbar_displays_high_contrast_feedback() {
    val snackbarHostState = androidx.compose.material3.SnackbarHostState()
    composeTestRule.setContent {
      SusuLedgerTheme {
        androidx.compose.runtime.LaunchedEffect(Unit) {
          snackbarHostState.showSnackbar("All account data successfully erased.")
        }
        androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
          com.example.ui.components.SusuSnackbarHost(hostState = snackbarHostState)
        }
      }
    }

    composeTestRule.onNodeWithTag("in_app_feedback_snackbar").assertIsDisplayed()
    composeTestRule.onNodeWithText("All account data successfully erased.").assertIsDisplayed()
  }
}
