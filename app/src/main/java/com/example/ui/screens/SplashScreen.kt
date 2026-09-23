package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AppLogoBadge
import com.example.ui.theme.ForestGreenLightFill
import com.example.ui.theme.ForestGreenPrimary
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SplashScreen(
  onProceed: () -> Unit
) {
  var startAnimation by remember { mutableStateOf(false) }

  val alphaAnim by animateFloatAsState(
    targetValue = if (startAnimation) 1f else 0f,
    animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
    label = "splashAlpha"
  )

  val scaleAnim by animateFloatAsState(
    targetValue = if (startAnimation) 1f else 0.85f,
    animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
    label = "splashScale"
  )

  LaunchedEffect(Unit) {
    startAnimation = true
  }

  Surface(
    modifier = Modifier
      .fillMaxSize()
      .testTag("splash_screen"),
    color = PureWhite
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 28.dp, vertical = 36.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      Spacer(modifier = Modifier.height(16.dp))

      // Center Branding Container
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .scale(scaleAnim)
          .alpha(alphaAnim),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // App Logo Icon
        AppLogoBadge(size = 96.dp)

        Spacer(modifier = Modifier.height(24.dp))

        // App Name
        Text(
          text = "Susu Ledger",
          style = MaterialTheme.typography.headlineLarge.copy(
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary,
            letterSpacing = (-0.5).sp,
            fontSize = 32.sp
          )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Badge Tagline
        Surface(
          shape = RoundedCornerShape(20.dp),
          color = ForestGreenLightFill,
          border = androidx.compose.foundation.BorderStroke(1.dp, ForestGreenPrimary.copy(alpha = 0.3f))
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.Security,
              contentDescription = null,
              tint = ForestGreenPrimary,
              modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "Double-Entry Record System",
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              color = ForestGreenPrimary
            )
          }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // High-level description
        Text(
          text = "Digital Financial Record-Keeping & Group-Specific WhatsApp Automation for Traditional Ghanaian Susu Savings Groups.",
          style = MaterialTheme.typography.bodyMedium.copy(
            color = TextSecondary,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center
          ),
          modifier = Modifier.padding(horizontal = 12.dp)
        )
      }

      // Bottom Button Action
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .alpha(alphaAnim),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Button(
          onClick = onProceed,
          modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag("splash_continue_btn"),
          shape = RoundedCornerShape(10.dp),
          colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary)
        ) {
          Text(
            text = "Explore Features & Get Started",
            style = MaterialTheme.typography.labelLarge.copy(
              fontWeight = FontWeight.Bold,
              fontSize = 15.sp,
              color = PureWhite
            )
          )
          Spacer(modifier = Modifier.width(8.dp))
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = PureWhite,
            modifier = Modifier.size(18.dp)
          )
        }

        Spacer(modifier = Modifier.height(12.dp))
      }
    }
  }
}
