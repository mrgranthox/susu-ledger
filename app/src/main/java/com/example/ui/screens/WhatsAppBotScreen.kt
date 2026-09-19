package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.MemberEntity
import com.example.data.local.MessageLogEntity
import com.example.ui.theme.BorderGrey
import com.example.ui.theme.ForestGreenLightFill
import com.example.ui.theme.ForestGreenPrimary
import com.example.ui.theme.LineIconBlack
import com.example.ui.theme.LineIconGrey
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.components.StandardNavTopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val WhatsAppGreen = Color(0xFF25D366)
val WhatsAppChatBg = Color(0xFFEFEAE2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsAppBotScreen(
  groupName: String = "Susu Group",
  messages: List<MessageLogEntity>,
  members: List<MemberEntity>,
  onBack: () -> Unit,
  onSendMessage: (phone: String, messageText: String) -> Unit,
  onOpenPairing: () -> Unit,
  onSendWeeklyReminder: () -> Unit = {},
  onSendUnpaidNudges: () -> Unit = {},
  onSendSundayDigest: () -> Unit = {}
) {
  val context = LocalContext.current
  var selectedMember by remember(members) { mutableStateOf(members.firstOrNull()) }
  var inputMessage by remember { mutableStateOf("") }
  val listState = rememberLazyListState()
  val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.US) }

  // Auto scroll to bottom when new messages arrive
  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.size - 1)
    }
  }

  Scaffold(
    topBar = {
      StandardNavTopBar(
        title = "WhatsApp Bot Channel",
        subtitle = "$groupName • Cloud API Integration",
        onBackClick = onBack,
        actions = {
          Button(
            onClick = onOpenPairing,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9)),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            modifier = Modifier.height(34.dp).testTag("whatsapp_pair_btn")
          ) {
            Icon(imageVector = Icons.Default.Smartphone, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Bot Pairing", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ForestGreenPrimary)
          }
        }
      )
    }
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(WhatsAppChatBg)
        .padding(paddingValues)
    ) {
      // Top Outbound Automation Hub
      Surface(
        color = PureWhite,
        border = BorderStroke(1.dp, BorderGrey),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
          Text(
            text = "BROADCAST AUTOMATION",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = TextSecondary
          )

          Spacer(modifier = Modifier.height(6.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            OutboundTriggerChip(
              title = "Friday Reminder",
              icon = Icons.Default.NotificationsActive,
              onClick = onSendWeeklyReminder,
              modifier = Modifier.weight(1f)
            )
            OutboundTriggerChip(
              title = "Nudge Unpaid",
              icon = Icons.Default.Campaign,
              onClick = onSendUnpaidNudges,
              modifier = Modifier.weight(1f)
            )
            OutboundTriggerChip(
              title = "Sunday Digest",
              icon = Icons.Default.Receipt,
              onClick = onSendSundayDigest,
              modifier = Modifier.weight(1f)
            )
          }

          if (members.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
              text = "MEMBER CONVERSATION FOCUS",
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              letterSpacing = 0.8.sp,
              color = TextSecondary
            )
            Spacer(modifier = Modifier.height(6.dp))

            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              items(members, key = { it.id }) { member ->
                val isSelected = (selectedMember?.id == member.id)
                Surface(
                  shape = RoundedCornerShape(20.dp),
                  color = if (isSelected) ForestGreenPrimary else Color(0xFFF1F5F9),
                  border = BorderStroke(1.dp, if (isSelected) ForestGreenPrimary else BorderGrey),
                  modifier = Modifier.clickable { selectedMember = member }
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Icon(
                      imageVector = Icons.Default.AccountCircle,
                      contentDescription = null,
                      tint = if (isSelected) PureWhite else LineIconGrey,
                      modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                      text = member.alias ?: member.phone,
                      fontSize = 11.sp,
                      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                      color = if (isSelected) PureWhite else TextPrimary
                    )
                  }
                }
              }
            }
          }
        }
      }

      // Chat Messages List
      if (messages.isEmpty()) {
        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(24.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
              modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(PureWhite),
              contentAlignment = Alignment.Center
            ) {
              Icon(Icons.Default.Chat, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(26.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
              text = "WhatsApp Live Ledger Stream",
              fontWeight = FontWeight.Bold,
              fontSize = 14.sp,
              color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Messages from $groupName members and automated notifications will stream here in real-time.",
              fontSize = 12.sp,
              color = TextSecondary,
              textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
          }
        }
      } else {
        LazyColumn(
          state = listState,
          modifier = Modifier
            .weight(1f)
            .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
          items(messages, key = { it.id }) { message ->
            val isOutgoingFromBot = message.direction == "OUT"
            ChatBubble(
              message = message,
              isFromBot = isOutgoingFromBot,
              timeFormat = timeFormat
            )
            Spacer(modifier = Modifier.height(8.dp))
          }
        }
      }

      // Interactive Quick Actions for Active Group Context
      val currentPhone = selectedMember?.phone ?: "+233 24 123 4567"
      Surface(
        color = PureWhite,
        border = BorderStroke(1.dp, BorderGrey),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "GROUP BOT COMMANDS (${selectedMember?.alias ?: "Active Member"})",
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold,
              letterSpacing = 0.8.sp,
              color = TextSecondary
            )

            // Direct WhatsApp App launcher
            TextButton(
              onClick = {
                val cleanDigits = currentPhone.replace("+", "").replace(" ", "")
                val uri = Uri.parse("https://wa.me/$cleanDigits?text=Hello%20from%20$groupName")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                context.startActivity(intent)
              },
              modifier = Modifier.height(26.dp)
            ) {
              Icon(Icons.Default.OpenInNew, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(12.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Open WhatsApp", fontSize = 10.sp, color = WhatsAppGreen, fontWeight = FontWeight.Bold)
            }
          }

          Spacer(modifier = Modifier.height(6.dp))

          // Quick Action Chips
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            QuickActionButton(title = "PAID Claim", modifier = Modifier.weight(1f)) {
              onSendMessage(currentPhone, "PAID")
            }
            QuickActionButton(title = "My Balance", modifier = Modifier.weight(1f)) {
              onSendMessage(currentPhone, "BALANCE")
            }
            QuickActionButton(title = "Group Progress", modifier = Modifier.weight(1.1f)) {
              onSendMessage(currentPhone, "PROGRESS")
            }
            QuickActionButton(title = "Help Menu", modifier = Modifier.weight(0.9f)) {
              onSendMessage(currentPhone, "HELP")
            }
          }
        }
      }

      // Text Input Bar
      Surface(
        color = PureWhite,
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          OutlinedTextField(
            value = inputMessage,
            onValueChange = { inputMessage = it },
            placeholder = { Text("Send group WhatsApp message...", fontSize = 13.sp) },
            modifier = Modifier
              .weight(1f)
              .testTag("whatsapp_chat_input"),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = WhatsAppGreen,
              unfocusedBorderColor = BorderGrey
            ),
            shape = RoundedCornerShape(20.dp),
            singleLine = true
          )

          Spacer(modifier = Modifier.width(8.dp))

          IconButton(
            onClick = {
              if (inputMessage.isNotBlank()) {
                onSendMessage(currentPhone, inputMessage)
                inputMessage = ""
              }
            },
            modifier = Modifier
              .size(44.dp)
              .clip(CircleShape)
              .background(WhatsAppGreen)
              .testTag("whatsapp_send_btn")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.Send,
              contentDescription = "Send",
              tint = PureWhite,
              modifier = Modifier.size(18.dp)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun OutboundTriggerChip(
  title: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = ForestGreenLightFill,
    border = BorderStroke(1.dp, ForestGreenPrimary.copy(alpha = 0.2f)),
    modifier = modifier.clickable(onClick = onClick)
  ) {
    Row(
      modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center
    ) {
      Icon(icon, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(13.dp))
      Spacer(modifier = Modifier.width(4.dp))
      Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = ForestGreenPrimary
      )
    }
  }
}

@Composable
private fun QuickActionButton(
  title: String,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  Surface(
    shape = RoundedCornerShape(6.dp),
    color = PureWhite,
    border = BorderStroke(1.dp, BorderGrey),
    modifier = modifier.clickable(onClick = onClick)
  ) {
    Box(
      modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = ForestGreenPrimary
      )
    }
  }
}

@Composable
private fun ChatBubble(
  message: MessageLogEntity,
  isFromBot: Boolean,
  timeFormat: SimpleDateFormat
) {
  Box(
    modifier = Modifier.fillMaxWidth(),
    contentAlignment = if (isFromBot) Alignment.CenterStart else Alignment.CenterEnd
  ) {
    Card(
      shape = RoundedCornerShape(
        topStart = 12.dp,
        topEnd = 12.dp,
        bottomStart = if (isFromBot) 2.dp else 12.dp,
        bottomEnd = if (isFromBot) 12.dp else 2.dp
      ),
      colors = CardDefaults.cardColors(
        containerColor = if (isFromBot) PureWhite else Color(0xFFD9FDD3)
      ),
      elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
      modifier = Modifier.fillMaxWidth(0.85f)
    ) {
      Column(modifier = Modifier.padding(10.dp)) {
        Text(
          text = message.senderName,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = if (isFromBot) ForestGreenPrimary else Color(0xFF0F766E)
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
          text = message.body,
          fontSize = 13.sp,
          color = TextPrimary
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = timeFormat.format(Date(message.timestamp)),
            fontSize = 10.sp,
            color = TextSecondary
          )
          Spacer(modifier = Modifier.width(4.dp))
          Icon(
            imageVector = Icons.Default.DoneAll,
            contentDescription = null,
            tint = Color(0xFF0284C7),
            modifier = Modifier.size(14.dp)
          )
        }
      }
    }
  }
}
