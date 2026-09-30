package com.example.ui.screens

import com.example.ui.theme.NeutralSurfaceLight
import com.example.ui.theme.NeutralSurfaceMedium
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.TealDeep
import com.example.ui.theme.WhatsAppBrandGreen
import com.example.ui.theme.WhatsAppBubbleGreen
import com.example.ui.theme.WhatsAppChatBg


import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.MemberEntity
import com.example.data.local.MessageLogEntity
import com.example.data.remote.CloudSystemStatusResponse
import com.example.ui.components.StandardNavTopBar
import com.example.ui.theme.BorderGrey
import com.example.ui.theme.ForestGreenLightFill
import com.example.ui.theme.ForestGreenPrimary
import com.example.ui.theme.LineIconGrey
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsAppBotScreen(
  groupName: String = "Susu Group",
  messages: List<MessageLogEntity>,
  members: List<MemberEntity>,
  pairingCode: String = "",
  pairingSecondsRemaining: Int = 0,
  isBotConnected: Boolean = false,
  hasActiveCycle: Boolean = true,
  connectionError: String? = null,
  cloudStatus: CloudSystemStatusResponse? = null,
  onBack: () -> Unit,
  onSendMessage: (phone: String, messageText: String) -> Unit,
  onOpenPairing: () -> Unit,
  onRefreshPairingCode: () -> Unit = {},
  onCheckCloudStatus: () -> Unit = {},
  onSendWeeklyReminder: () -> Unit = {},
  onSendUnpaidNudges: () -> Unit = {},
  onSendSundayDigest: () -> Unit = {}
) {
  val context = LocalContext.current
  val clipboard = LocalClipboardManager.current
  var selectedMember by remember(members) { mutableStateOf(members.firstOrNull()) }
  var inputMessage by remember { mutableStateOf("") }
  val listState = rememberLazyListState()
  val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.US) }
  var showBotControls by remember { mutableStateOf(false) }

  // Auto scroll to bottom when new messages arrive
  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.size - 1)
    }
  }

  Scaffold(
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = {
      StandardNavTopBar(
        title = "WhatsApp Bot Channel",
        subtitle = groupName,
        onBackClick = onBack,
        actions = {
          IconButton(
            onClick = { showBotControls = !showBotControls },
            modifier = Modifier.size(34.dp)
          ) {
            Icon(
              imageVector = if (showBotControls) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
              contentDescription = "Toggle Tools",
              tint = ForestGreenPrimary,
              modifier = Modifier.size(20.dp)
            )
          }
          Button(
            onClick = onOpenPairing,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NeutralSurfaceMedium),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            modifier = Modifier
              .height(34.dp)
              .testTag("whatsapp_pair_btn")
          ) {
            Icon(imageVector = Icons.Default.Smartphone, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(if (isBotConnected) "Reconnect" else "Connect", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ForestGreenPrimary)
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
        .imePadding()
    ) {
      if (connectionError != null) {
        Text(connectionError, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(14.dp))
      }
      // Top Live Infrastructure & Pairing Status Card (Collapsible)
      Surface(
        color = PureWhite,
        border = BorderStroke(1.dp, BorderGrey),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
          // Bot Status Row (Tappable header to expand/collapse)
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { showBotControls = !showBotControls }
              .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              Box(
                modifier = Modifier
                  .size(8.dp)
                  .clip(CircleShape)
                  .background(ForestGreenPrimary)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = if (connectionError != null) "WhatsApp: CHECK CONNECTION" else if (isBotConnected) "WhatsApp: CONNECTED" else "WhatsApp: DISCONNECTED",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = ForestGreenPrimary
              )
              if (!isBotConnected && pairingCode.isNotBlank()) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "• $pairingCode",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold,
                  fontFamily = FontFamily.Monospace,
                  color = TextSecondary
                )
              }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = if (showBotControls) "Hide Tools" else "Show Tools",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = ForestGreenPrimary
              )
              Icon(
                imageVector = if (showBotControls) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = ForestGreenPrimary,
                modifier = Modifier.size(16.dp)
              )
            }
          }

          AnimatedVisibility(visible = showBotControls) {
            Column {
              Spacer(modifier = Modifier.height(6.dp))

              // Dynamic Pairing Code Summary Box
              if (!isBotConnected && pairingCode.isNotBlank()) {
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = NeutralSurfaceLight,
                  border = BorderStroke(1.dp, BorderGrey),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Column {
                      Text(
                        text = if (pairingSecondsRemaining > 0) "ACTIVE PAIRING CODE" else "PAIRING CODE NOT READY",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        color = TextSecondary
                      )
                      Text(
                        text = pairingCode,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = ForestGreenPrimary
                      )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                      IconButton(
                        enabled = pairingCode.isNotBlank(),
                        onClick = {
                          clipboard.setText(AnnotatedString("PAIR:$pairingCode"))
                          Toast.makeText(context, "Pairing code copied!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(30.dp)
                      ) {
                        Icon(
                          imageVector = Icons.Default.ContentCopy,
                          contentDescription = "Copy code",
                          tint = TextSecondary,
                          modifier = Modifier.size(14.dp)
                        )
                      }

                      TextButton(
                        enabled = pairingCode.isNotBlank(),
                        onClick = {
                          val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("https://wa.me/233545908371?text=${Uri.encode("PAIR:$pairingCode")}")
                          }
                          try {
                            context.startActivity(intent)
                          } catch (e: Exception) {
                            clipboard.setText(AnnotatedString("PAIR:$pairingCode"))
                            Toast.makeText(context, "Copied PAIR:$pairingCode", Toast.LENGTH_SHORT).show()
                          }
                        },
                        modifier = Modifier.height(28.dp)
                      ) {
                        Icon(
                          imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                          contentDescription = null,
                          tint = WhatsAppBrandGreen,
                          modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Open WhatsApp", fontSize = 12.sp, color = WhatsAppBrandGreen, fontWeight = FontWeight.Bold)
                      }
                    }
                  }
                }
              }

              // Cloud Infrastructure Diagnostics Row
              if (cloudStatus != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = "CLOUD ENGINE STATUS",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = TextSecondary
                  )
                  TextButton(
                    onClick = onCheckCloudStatus,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    modifier = Modifier.height(22.dp)
                  ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = ForestGreenPrimary, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Refresh", fontSize = 10.sp, color = ForestGreenPrimary, fontWeight = FontWeight.Bold)
                  }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  val isDbOk = cloudStatus.database?.status == "healthy"
                  val isWaOk = cloudStatus.whatsappBot?.phoneIdConfigured == true
                  val isCacheOk = cloudStatus.cache?.ping == "PONG"

                  StatusIndicatorPill(label = "Database", isHealthy = isDbOk, modifier = Modifier.weight(1f))
                  StatusIndicatorPill(label = "WhatsApp API", isHealthy = isWaOk, modifier = Modifier.weight(1f))
                  StatusIndicatorPill(label = "Redis Cache", isHealthy = isCacheOk, modifier = Modifier.weight(1f))
                }
              }

              Spacer(modifier = Modifier.height(8.dp))

              // Outbound Broadcast Actions (LazyRow for zero horizontal clipping)
              Text(
                text = "META BROADCAST DISPATCHES",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = TextSecondary
              )

              Spacer(modifier = Modifier.height(4.dp))

              LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                item {
                  OutboundTriggerChip(
                    title = "Friday Reminder",
                    icon = Icons.Default.NotificationsActive,
                    enabled = hasActiveCycle && isBotConnected,
                    onClick = onSendWeeklyReminder
                  )
                }
                item {
                  OutboundTriggerChip(
                    title = "Nudge Unpaid",
                    icon = Icons.Default.Campaign,
                    enabled = hasActiveCycle && isBotConnected,
                    onClick = onSendUnpaidNudges
                  )
                }
                item {
                  OutboundTriggerChip(
                    title = "Sunday Digest",
                    icon = Icons.Default.Receipt,
                    enabled = hasActiveCycle && isBotConnected,
                    onClick = onSendSundayDigest
                  )
                }
              }

              if (members.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                  text = "SELECT MEMBER RECIPIENT",
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  letterSpacing = 0.8.sp,
                  color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))

                LazyRow(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  items(members, key = { it.id }) { member ->
                    val isSelected = (selectedMember?.id == member.id)
                    Surface(
                      shape = RoundedCornerShape(20.dp),
                      color = if (isSelected) ForestGreenPrimary else NeutralSurfaceMedium,
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
                Spacer(modifier = Modifier.height(4.dp))
              }
            }
          }
        }
      }

      // Live Activity Stream & Webhook Logs
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
              Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = WhatsAppBrandGreen, modifier = Modifier.size(26.dp))
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
              text = "Live WhatsApp bot messages from $groupName members and automated notifications will appear here.",
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

      // Direct Live WhatsApp Dispatch Bar
      val targetPhone = selectedMember?.phone ?: "+233 24 123 4567"
      Surface(
        color = PureWhite,
        border = BorderStroke(1.dp, BorderGrey),
        modifier = Modifier
          .fillMaxWidth()
          .navigationBarsPadding()
      ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "DIRECT WHATSAPP DISPATCH • ${selectedMember?.alias ?: "All Members"}",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              letterSpacing = 0.8.sp,
              color = TextSecondary
            )

            // Direct WhatsApp App launcher
            TextButton(
              onClick = {
                val cleanDigits = targetPhone.replace("+", "").replace(" ", "")
                val uri = Uri.parse("https://wa.me/$cleanDigits?text=Hello%20from%20$groupName")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                try {
                  context.startActivity(intent)
                } catch (e: Exception) {
                  Toast.makeText(context, "Opening chat with $targetPhone", Toast.LENGTH_SHORT).show()
                }
              },
              modifier = Modifier.minimumInteractiveComponentSize()
            ) {
              Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = WhatsAppBrandGreen, modifier = Modifier.size(12.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Open Direct Chat", fontSize = 12.sp, color = WhatsAppBrandGreen, fontWeight = FontWeight.Bold)
            }
          }

          Spacer(modifier = Modifier.height(6.dp))

          // Text Input Bar for Live Dispatch
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
          ) {
            OutlinedTextField(
              value = inputMessage,
              onValueChange = { inputMessage = it },
              placeholder = { Text("Send live WhatsApp announcement...", fontSize = 13.sp) },
              modifier = Modifier
                .weight(1f)
                .testTag("whatsapp_chat_input"),
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = WhatsAppBrandGreen,
                unfocusedBorderColor = BorderGrey
              ),
              shape = RoundedCornerShape(20.dp),
              singleLine = true
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
              onClick = {
                if (inputMessage.isNotBlank()) {
                  onSendMessage(targetPhone, inputMessage)
                  inputMessage = ""
                }
              },
              modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(WhatsAppBrandGreen)
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
}

@Composable
private fun OutboundTriggerChip(
  title: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  enabled: Boolean = true,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  val surfaceColor = if (enabled) ForestGreenLightFill else NeutralSurfaceMedium.copy(alpha = 0.6f)
  val contentColor = if (enabled) ForestGreenPrimary else TextSecondary
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = surfaceColor,
    border = BorderStroke(1.dp, contentColor.copy(alpha = if (enabled) 0.25f else 0.15f)),
    modifier = modifier.clickable(enabled = enabled, onClick = onClick)
  ) {
    Row(
      modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center
    ) {
      Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(13.dp))
      Spacer(modifier = Modifier.width(4.dp))
      Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = contentColor
      )
    }
  }
}

@Composable
private fun StatusIndicatorPill(
  label: String,
  isHealthy: Boolean,
  modifier: Modifier = Modifier
) {
  val activeColor = if (isHealthy) ForestGreenPrimary else Color(0xFFD32F2F)
  Surface(
    shape = RoundedCornerShape(6.dp),
    color = if (isHealthy) ForestGreenLightFill else Color(0xFFFFEBEE),
    border = BorderStroke(1.dp, activeColor.copy(alpha = 0.3f)),
    modifier = modifier
  ) {
    Row(
      modifier = Modifier.padding(vertical = 4.dp, horizontal = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center
    ) {
      Box(
        modifier = Modifier
          .size(6.dp)
          .clip(CircleShape)
          .background(activeColor)
      )
      Spacer(modifier = Modifier.width(4.dp))
      Text(
        text = label,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = activeColor
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
        containerColor = if (isFromBot) PureWhite else WhatsAppBubbleGreen
      ),
      elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
      modifier = Modifier.fillMaxWidth(0.85f)
    ) {
      Column(modifier = Modifier.padding(10.dp)) {
        Text(
          text = message.senderName,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = if (isFromBot) ForestGreenPrimary else TealDeep
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
            tint = SkyBlue,
            modifier = Modifier.size(14.dp)
          )
        }
      }
    }
  }
}
