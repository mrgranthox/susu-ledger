package com.example.ui.screens

import com.example.ui.theme.DangerRed
import com.example.ui.theme.NeutralSurfaceLight
import com.example.ui.theme.NeutralSurfaceMedium


import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BorderGrey
import com.example.ui.theme.ForestGreenPrimary
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WhatsAppBrandGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPairingSheet(
  sheetState: SheetState,
  pairingCode: String,
  secondsRemaining: Int,
  onDismiss: () -> Unit,
  onGenerateNewCode: () -> Unit = {}
) {
  val context = LocalContext.current
  val clipboard = LocalClipboardManager.current

  val minutes = secondsRemaining / 60
  val seconds = secondsRemaining % 60
  val timeString = String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = Color.White
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp, vertical = 8.dp)
        .padding(bottom = 32.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // Header Icon
      Box(
        modifier = Modifier
          .size(54.dp)
          .clip(CircleShape)
          .background(Color(0xFFDCFCE7)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.Chat,
          contentDescription = null,
          tint = WhatsAppBrandGreen,
          modifier = Modifier.size(28.dp)
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      Text(
        text = "WhatsApp Bot Pairing",
        style = MaterialTheme.typography.titleLarge.copy(
          fontWeight = FontWeight.Bold,
          color = TextPrimary
        )
      )

      Text(
        text = "Pair your SusuLedger mobile instance with the WhatsApp bot to enable automated collection reminders and member receipts.",
        style = MaterialTheme.typography.bodyMedium.copy(
          color = TextSecondary,
          textAlign = TextAlign.Center
        ),
        modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
      )

      // 6-Character Monospace Pairing Code Box
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = NeutralSurfaceLight),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, BorderGrey)
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            text = "ONE-TIME PAIRING CODE",
            style = MaterialTheme.typography.labelSmall.copy(
              fontWeight = FontWeight.Bold,
              letterSpacing = 1.2.sp,
              color = TextSecondary
            )
          )

          Spacer(modifier = Modifier.height(10.dp))

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
          ) {
            Text(
              text = pairingCode,
              style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 4.sp,
                color = ForestGreenPrimary
              ),
              modifier = Modifier.testTag("pairing_code_text")
            )

            Spacer(modifier = Modifier.width(12.dp))

            IconButton(
              enabled = secondsRemaining > 0,
              onClick = {
                clipboard.setText(AnnotatedString("PAIR:$pairingCode"))
                Toast.makeText(context, "Pairing code copied to clipboard", Toast.LENGTH_SHORT).show()
              }
            ) {
              Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy code",
                tint = TextSecondary
              )
            }
          }

          Spacer(modifier = Modifier.height(8.dp))

          // 15-Minute Expiry Countdown
          Row(
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.Timer,
              contentDescription = null,
              tint = DangerRed,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "Expires in $timeString (15-min TTL)",
              style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = DangerRed
              )
            )
          }

          Spacer(modifier = Modifier.height(10.dp))

          OutlinedButton(
            onClick = onGenerateNewCode,
            modifier = Modifier.minimumInteractiveComponentSize(),
            shape = RoundedCornerShape(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)
          ) {
            Text("Generate new code", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = ForestGreenPrimary)
          }
        }
      }

      Spacer(modifier = Modifier.height(18.dp))

      // Secure Bot Gateway status
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = NeutralSurfaceMedium,
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            imageVector = Icons.Default.CloudDone,
            contentDescription = null,
            tint = ForestGreenPrimary,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = "Secure Bot Verification Gateway",
              fontWeight = FontWeight.SemiBold,
              fontSize = 12.sp,
              color = TextPrimary
            )
            Text(
              text = "End-to-End Encrypted WhatsApp Channel",
              fontSize = 11.sp,
              color = TextSecondary
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Action: Open WhatsApp & Send Code
      Button(
        onClick = {
          openWhatsAppChat(context, pairingCode)
        },
        enabled = secondsRemaining > 0,
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp)
          .testTag("open_whatsapp_pairing_button"),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = WhatsAppBrandGreen,
          contentColor = Color.White
        )
      ) {
        Icon(imageVector = Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Open WhatsApp & Send Code",
          fontWeight = FontWeight.Bold,
          fontSize = 15.sp
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      OutlinedButton(
        onClick = onDismiss,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp)
      ) {
        Text("Done")
      }
    }
  }
}

private fun openWhatsAppChat(context: Context, pairingCode: String) {
  val message = "PAIR:$pairingCode"
  val intent = Intent(Intent.ACTION_VIEW).apply {
    data = Uri.parse("https://wa.me/233545908371?text=${Uri.encode(message)}")
  }
  try {
    context.startActivity(intent)
  } catch (e: Exception) {
    // Fallback if WhatsApp client is not directly launchable in simulator
    Toast.makeText(context, "Unable to open WhatsApp. Send $message to +233 54 590 8371.", Toast.LENGTH_LONG).show()
  }
}
