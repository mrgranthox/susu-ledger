package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.ForestGreenPrimary
import com.example.ui.theme.PureWhite

/**
 * Enterprise In-App Feedback Toast / Snackbar.
 * Solves low-contrast readability by guaranteeing crisp white text on a rich
 * deep slate/charcoal background (#0F172A) with clear status iconography.
 */
@Composable
fun SusuFeedbackSnackbar(
  data: SnackbarData,
  modifier: Modifier = Modifier
) {
  val message = data.visuals.message
  val isError = message.contains("error", ignoreCase = true) ||
                message.contains("failed", ignoreCase = true) ||
                message.contains("denied", ignoreCase = true) ||
                message.contains("unable", ignoreCase = true) ||
                message.contains("incorrect", ignoreCase = true)

  val isSuccess = message.contains("success", ignoreCase = true) ||
                  message.contains("confirmed", ignoreCase = true) ||
                  message.contains("completed", ignoreCase = true) ||
                  message.contains("welcome", ignoreCase = true) ||
                  message.contains("verified", ignoreCase = true) ||
                  message.contains("deleted", ignoreCase = true) ||
                  message.contains("added", ignoreCase = true) ||
                  message.contains("opened", ignoreCase = true)

  val (statusIcon, iconTint, badgeBg, borderStroke) = when {
    isError -> Quad(
      Icons.Default.Warning,
      Color(0xFFF87171), // Bright coral red
      Color(0xFF7F1D1D).copy(alpha = 0.4f),
      BorderStroke(1.dp, ErrorRed.copy(alpha = 0.7f))
    )
    isSuccess -> Quad(
      Icons.Default.CheckCircle,
      Color(0xFF4ADE80), // Bright emerald green
      Color(0xFF064E3B).copy(alpha = 0.4f),
      BorderStroke(1.dp, ForestGreenPrimary.copy(alpha = 0.7f))
    )
    else -> Quad(
      Icons.Default.Info,
      Color(0xFF60A5FA), // Bright sky blue
      Color(0xFF1E3A8A).copy(alpha = 0.4f),
      BorderStroke(1.dp, Color(0xFF334155))
    )
  }

  Card(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 8.dp)
      .testTag("in_app_feedback_snackbar"),
    shape = RoundedCornerShape(10.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)), // Rich deep charcoal #0F172A
    border = borderStroke,
    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(24.dp)
          .background(badgeBg, CircleShape),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = statusIcon,
          contentDescription = null,
          tint = iconTint,
          modifier = Modifier.size(16.dp)
        )
      }

      Spacer(modifier = Modifier.width(10.dp))

      Text(
        text = message,
        color = PureWhite,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 18.sp,
        modifier = Modifier.weight(1f)
      )

      if (data.visuals.actionLabel != null) {
        Spacer(modifier = Modifier.width(8.dp))
        TextButton(
          onClick = { data.performAction() },
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
        ) {
          Text(
            text = data.visuals.actionLabel!!,
            color = iconTint,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
          )
        }
      }
    }
  }
}

@Composable
fun SusuSnackbarHost(
  hostState: SnackbarHostState,
  modifier: Modifier = Modifier
) {
  SnackbarHost(
    hostState = hostState,
    modifier = modifier
  ) { data ->
    SusuFeedbackSnackbar(data = data)
  }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
