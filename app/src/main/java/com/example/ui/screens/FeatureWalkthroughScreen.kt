package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BorderGrey
import com.example.ui.theme.ForestGreenLightFill
import com.example.ui.theme.ForestGreenPrimary
import com.example.ui.theme.HeroAmber
import com.example.ui.theme.HeroAmberBg
import com.example.ui.theme.HeroAmberBorder
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

data class WalkthroughSlide(
  val id: Int,
  val title: String,
  val subtitle: String,
  val description: String,
  val icon: ImageVector,
  val accentColor: Color,
  val previewBadge: String,
  val highlights: List<String>
)

@Composable
fun FeatureWalkthroughScreen(
  onComplete: () -> Unit,
  onSkip: () -> Unit
) {
  val slides = listOf(
    WalkthroughSlide(
      id = 1,
      title = "Group-Specific WhatsApp Bot",
      subtitle = "Isolated Context for Each Savings Group",
      description = "Every Susu savings group has dedicated WhatsApp intelligence. When members like Akosua ask for dues, the bot knows their exact group, weekly rate, and paid status.",
      icon = Icons.AutoMirrored.Filled.Chat,
      accentColor = Color(0xFF25D366),
      previewBadge = "WhatsApp Meta Cloud API",
      highlights = listOf(
        "Strict per-group isolation & dues matching",
        "Instant payment claims via 'PAID' command",
        "Real-time personal balance & statement lookups"
      )
    ),
    WalkthroughSlide(
      id = 2,
      title = "Balanced Double-Entry Ledger",
      subtitle = "Tamper-Evident & Transparent Recordkeeping",
      description = "Replace traditional paper exercise books. Every contribution generates balanced double-entry accounting journals tied to verifiable audit records.",
      icon = Icons.Default.Security,
      accentColor = ForestGreenPrimary,
      previewBadge = "Balanced Debit & Credit Journals",
      highlights = listOf(
        "Immutable append-only transaction sequence",
        "Dual-officer sign-off for sensitive operations",
        "Zero administrator tampering capability"
      )
    ),
    WalkthroughSlide(
      id = 3,
      title = "Ghana MoMo & Dual Sign-Off",
      subtitle = "Seamless Mobile Money Reconciliation",
      description = "Track MTN MoMo, Telecel Cash & AT Money transactions with full transaction IDs, audit trails, and mandatory dual-officer confirmations for dispute prevention.",
      icon = Icons.Default.AccountBalanceWallet,
      accentColor = HeroAmber,
      previewBadge = "MTN • Telecel • AT Money",
      highlights = listOf(
        "Transaction ID & Reference verification",
        "Instant digital receipts delivered to member chats",
        "Dual-officer authorization for week closing"
      )
    ),
    WalkthroughSlide(
      id = 4,
      title = "Automated Reminders & Digests",
      subtitle = "Zero Manual Chasing for Treasurers",
      description = "Automate Friday collection nudges, targeted unpaid reminders, and Sunday executive summaries delivered directly via WhatsApp to keep everyone on track.",
      icon = Icons.Default.NotificationsActive,
      accentColor = Color(0xFF0284C7),
      previewBadge = "Cloud Run & Cloud Scheduler",
      highlights = listOf(
        "Automated Friday payment reminders",
        "Sunday summary digests for group transparency",
        "One-touch WhatsApp receipts for members"
      )
    )
  )

  val pagerState = rememberPagerState(pageCount = { slides.size })
  val scope = rememberCoroutineScope()

  Surface(
    modifier = Modifier
      .fillMaxSize()
      .testTag("feature_walkthrough_screen"),
    color = PureWhite
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 24.dp, vertical = 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      // Top Navigation: Skip & Indicator
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          repeat(slides.size) { index ->
            val isSelected = pagerState.currentPage == index
            Box(
              modifier = Modifier
                .size(if (isSelected) 24.dp else 8.dp, 8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (isSelected) ForestGreenPrimary else Color(0xFFCBD5E1))
            )
          }
        }

        TextButton(
          onClick = onSkip,
          modifier = Modifier.testTag("walkthrough_skip_btn")
        ) {
          Text(
            text = "Skip to Setup",
            color = TextSecondary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
          )
        }
      }

      // Center Carousel Pager
      HorizontalPager(
        state = pagerState,
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
      ) { page ->
        val slide = slides[page]
        SlideCard(slide = slide)
      }

      // Bottom Action Controls
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        val isLastPage = pagerState.currentPage == slides.size - 1

        Button(
          onClick = {
            if (isLastPage) {
              onComplete()
            } else {
              scope.launch {
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
              }
            }
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("walkthrough_next_btn"),
          shape = RoundedCornerShape(10.dp),
          colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary)
        ) {
          Text(
            text = if (isLastPage) "Get Started & Create Group" else "Next Feature",
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
      }
    }
  }
}

@Composable
private fun SlideCard(slide: WalkthroughSlide) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(vertical = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    // Feature Hero Illustration Card
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .height(200.dp),
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = ForestGreenLightFill),
      border = androidx.compose.foundation.BorderStroke(1.dp, ForestGreenPrimary.copy(alpha = 0.2f))
    ) {
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
          modifier = Modifier.padding(16.dp)
        ) {
          Box(
            modifier = Modifier
              .size(72.dp)
              .clip(CircleShape)
              .background(PureWhite)
              .border(2.dp, slide.accentColor.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = slide.icon,
              contentDescription = slide.title,
              tint = slide.accentColor,
              modifier = Modifier.size(38.dp)
            )
          }

          Spacer(modifier = Modifier.height(14.dp))

          Surface(
            shape = RoundedCornerShape(12.dp),
            color = PureWhite,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderGrey)
          ) {
            Text(
              text = slide.previewBadge,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = TextPrimary,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(24.dp))

    Text(
      text = slide.title,
      style = MaterialTheme.typography.headlineSmall.copy(
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
        textAlign = TextAlign.Center
      )
    )

    Spacer(modifier = Modifier.height(4.dp))

    Text(
      text = slide.subtitle,
      style = MaterialTheme.typography.bodyMedium.copy(
        fontWeight = FontWeight.SemiBold,
        color = ForestGreenPrimary,
        textAlign = TextAlign.Center
      )
    )

    Spacer(modifier = Modifier.height(12.dp))

    Text(
      text = slide.description,
      style = MaterialTheme.typography.bodyMedium.copy(
        color = TextSecondary,
        textAlign = TextAlign.Center,
        lineHeight = 20.sp
      ),
      modifier = Modifier.padding(horizontal = 8.dp)
    )

    Spacer(modifier = Modifier.height(18.dp))

    // Scannable bullet points
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      slide.highlights.forEach { highlight ->
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth()
        ) {
          Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = SuccessGreen,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = highlight,
            fontSize = 12.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Medium
          )
        }
      }
    }
  }
}
