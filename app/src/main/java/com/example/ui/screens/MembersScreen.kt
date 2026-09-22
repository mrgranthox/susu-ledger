package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.MemberEntity
import com.example.data.local.PaymentEntity
import com.example.ui.components.StandardNavTopBar
import com.example.ui.theme.BorderGrey
import com.example.ui.theme.ForestGreenLightFill
import com.example.ui.theme.ForestGreenPrimary
import com.example.ui.theme.HeroAmber
import com.example.ui.theme.HeroAmberBg
import com.example.ui.theme.LineIconBlack
import com.example.ui.theme.LineIconGrey
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun MembersScreen(
  members: List<MemberEntity>,
  cyclePayments: List<PaymentEntity>,
  cycleNumber: Int,
  expectedCycleAmount: Double = 50.0,
  onRecordPaymentForMember: (MemberEntity) -> Unit,
  onAddMemberClick: () -> Unit,
  onImportContactsClick: (() -> Unit)? = null,
  onBackClick: (() -> Unit)? = null
) {
  var searchQuery by remember { mutableStateOf("") }
  var statusFilter by remember { mutableStateOf("ALL") } // ALL, PAID, PARTIAL, UNPAID
  var showAddMemberForm by remember { mutableStateOf(false) }

  // Add Member form state
  var newMemberName by remember { mutableStateOf("") }
  var newMemberPhone by remember { mutableStateOf("") }
  var newMemberCycleLiable by remember { mutableStateOf("Week ${cycleNumber + 1}") }

  // Precompute member contribution statuses
  val memberPaymentMap = remember(members, cyclePayments) {
    members.associateWith { member ->
      val paid = cyclePayments
        .filter { it.memberId == member.id && it.status != "reversed" }
        .sumOf { it.amountPaid }
      paid
    }
  }

  val paidCount = remember(memberPaymentMap, expectedCycleAmount) {
    memberPaymentMap.count { it.value >= expectedCycleAmount }
  }
  val partialCount = remember(memberPaymentMap, expectedCycleAmount) {
    memberPaymentMap.count { it.value > 0 && it.value < expectedCycleAmount }
  }
  val unpaidCount = remember(memberPaymentMap) {
    memberPaymentMap.count { it.value <= 0.0 }
  }

  val filteredMembers = remember(members, searchQuery, statusFilter, memberPaymentMap, expectedCycleAmount) {
    members.filter { member ->
      val matchesSearch = searchQuery.isBlank() ||
          member.alias.contains(searchQuery, ignoreCase = true) ||
          member.phone.contains(searchQuery)

      val paid = memberPaymentMap[member] ?: 0.0
      val matchesFilter = when (statusFilter) {
        "PAID" -> paid >= expectedCycleAmount
        "PARTIAL" -> paid > 0 && paid < expectedCycleAmount
        "UNPAID" -> paid <= 0.0
        else -> true
      }

      matchesSearch && matchesFilter
    }
  }

  Scaffold(
    containerColor = PureWhite,
    topBar = {
      Column(modifier = Modifier.fillMaxWidth()) {
        StandardNavTopBar(
          title = "${members.size} Group Members",
          subtitle = "Week $cycleNumber Contribution Status",
          onBackClick = onBackClick
        )
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            // Search bar with thin grey border
            OutlinedTextField(
              value = searchQuery,
              onValueChange = { searchQuery = it },
              placeholder = { Text("Search by name or phone...", color = LineIconGrey, fontSize = 14.sp) },
              leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = LineIconGrey, modifier = Modifier.size(18.dp))
              },
              modifier = Modifier
                .weight(1f)
                .testTag("members_search_input"),
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ForestGreenPrimary,
                unfocusedBorderColor = BorderGrey,
                focusedContainerColor = PureWhite,
                unfocusedContainerColor = PureWhite
              ),
              shape = RoundedCornerShape(8.dp),
              singleLine = true
            )

            if (onImportContactsClick != null) {
              androidx.compose.material3.OutlinedIconButton(
                onClick = onImportContactsClick,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, ForestGreenPrimary),
                modifier = Modifier
                  .size(52.dp)
                  .testTag("import_contacts_top_btn")
              ) {
                Icon(
                  imageVector = Icons.Default.Contacts,
                  contentDescription = "Import Contacts",
                  tint = ForestGreenPrimary
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          // Filter Chips Row
          LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            item {
              StatusFilterChip(
                label = "All (${members.size})",
                selected = statusFilter == "ALL",
                onClick = { statusFilter = "ALL" }
              )
            }
            item {
              StatusFilterChip(
                label = "Paid ($paidCount)",
                selected = statusFilter == "PAID",
                onClick = { statusFilter = "PAID" },
                selectedColor = ForestGreenPrimary
              )
            }
            item {
              StatusFilterChip(
                label = "Partial ($partialCount)",
                selected = statusFilter == "PARTIAL",
                onClick = { statusFilter = "PARTIAL" },
                selectedColor = HeroAmber
              )
            }
            item {
              StatusFilterChip(
                label = "Unpaid ($unpaidCount)",
                selected = statusFilter == "UNPAID",
                onClick = { statusFilter = "UNPAID" }
              )
            }
          }
        }
      }
    },
    floatingActionButton = {
      FloatingActionButton(
        onClick = { showAddMemberForm = true },
        containerColor = ForestGreenPrimary,
        contentColor = PureWhite,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.testTag("add_member_fab")
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(Icons.Default.Add, contentDescription = null, tint = PureWhite, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Add Member", style = MaterialTheme.typography.labelLarge.copy(color = PureWhite))
        }
      }
    }
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .background(PureWhite)
        .padding(paddingValues)
        .padding(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      if (filteredMembers.isEmpty()) {
        item {
          Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = PureWhite),
            border = BorderStroke(1.dp, BorderGrey),
            shape = RoundedCornerShape(8.dp)
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Text(
                text = if (searchQuery.isNotBlank()) "No members found matching '$searchQuery'" else "No members in this category.",
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
              )
            }
          }
        }
      } else {
        items(filteredMembers, key = { it.id }) { member ->
          val paidAmount = memberPaymentMap[member] ?: 0.0
          EnhancedMemberRowItem(
            member = member,
            paidAmount = paidAmount,
            targetAmount = expectedCycleAmount,
            onRecordPayment = { onRecordPaymentForMember(member) }
          )
        }
      }

      item {
        Spacer(modifier = Modifier.height(72.dp))
      }
    }
  }

  // Add Member Form Dialog
  if (showAddMemberForm) {
    AlertDialog(
      onDismissRequest = { showAddMemberForm = false },
      title = {
        Text("Add Member", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
      },
      text = {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          OutlinedTextField(
            value = newMemberName,
            onValueChange = { newMemberName = it },
            label = { Text("Name") },
            placeholder = { Text("e.g. Kofi Mensah") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = ForestGreenPrimary,
              unfocusedBorderColor = BorderGrey
            )
          )

          OutlinedTextField(
            value = newMemberPhone,
            onValueChange = { newMemberPhone = it },
            label = { Text("Phone") },
            placeholder = { Text("+233 24 000 0000") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = ForestGreenPrimary,
              unfocusedBorderColor = BorderGrey
            )
          )

          OutlinedTextField(
            value = newMemberCycleLiable,
            onValueChange = { newMemberCycleLiable = it },
            label = { Text("First cycle liable") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = ForestGreenPrimary,
              unfocusedBorderColor = BorderGrey
            )
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (newMemberName.isNotBlank()) {
              showAddMemberForm = false
              onAddMemberClick()
            }
          },
          shape = RoundedCornerShape(8.dp),
          colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary)
        ) {
          Text("Add Member", color = PureWhite)
        }
      },
      dismissButton = {
        TextButton(onClick = { showAddMemberForm = false }) {
          Text("Cancel", color = TextSecondary)
        }
      }
    )
  }
}

@Composable
private fun StatusFilterChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  selectedColor: Color = ForestGreenPrimary
) {
  FilterChip(
    selected = selected,
    onClick = onClick,
    label = {
      Text(
        text = label,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) PureWhite else TextSecondary
      )
    },
    colors = FilterChipDefaults.filterChipColors(
      selectedContainerColor = selectedColor,
      containerColor = PureWhite
    ),
    border = BorderStroke(1.dp, if (selected) selectedColor else BorderGrey),
    shape = RoundedCornerShape(16.dp)
  )
}

@Composable
private fun EnhancedMemberRowItem(
  member: MemberEntity,
  paidAmount: Double,
  targetAmount: Double,
  onRecordPayment: () -> Unit
) {
  var menuExpanded by remember { mutableStateOf(false) }
  val remainingAmount = (targetAmount - paidAmount).coerceAtLeast(0.0)
  val progress = if (targetAmount > 0) (paidAmount / targetAmount).toFloat().coerceIn(0f, 1f) else 0f

  val isPaid = paidAmount >= targetAmount
  val isPartial = paidAmount > 0 && paidAmount < targetAmount
  val isUnpaid = paidAmount <= 0.0

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onRecordPayment() }
      .testTag("member_row_${member.id}"),
    colors = CardDefaults.cardColors(containerColor = PureWhite),
    border = BorderStroke(
      1.dp,
      when {
        isPartial -> HeroAmber.copy(alpha = 0.6f)
        isPaid -> ForestGreenPrimary.copy(alpha = 0.3f)
        else -> BorderGrey
      }
    ),
    shape = RoundedCornerShape(10.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f)
        ) {
          // Initials circle
          val initial = member.alias.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").uppercase()
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(
                when {
                  isPaid -> ForestGreenPrimary
                  isPartial -> HeroAmber
                  else -> Color(0xFF718096)
                }
              ),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = if (initial.isNotEmpty()) initial else "M",
              color = PureWhite,
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold
            )
          }

          Spacer(modifier = Modifier.width(12.dp))

          Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = member.alias,
                style = MaterialTheme.typography.bodyMedium.copy(
                  color = TextPrimary,
                  fontWeight = FontWeight.Bold,
                  fontSize = 15.sp
                )
              )
              Spacer(modifier = Modifier.width(6.dp))
              // Tag
              Box(
                modifier = Modifier
                  .border(1.dp, BorderGrey, RoundedCornerShape(4.dp))
                  .background(Color(0xFFF9FAFB))
                  .padding(horizontal = 6.dp, vertical = 2.dp)
              ) {
                Text(
                  text = if (member.state == "exited") "exited" else "joined Wk ${member.joinedCycle}",
                  style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 10.sp)
                )
              }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
              text = member.phone,
              style = MaterialTheme.typography.bodySmall.copy(
                color = TextSecondary,
                fontSize = 12.sp
              )
            )
          }
        }

        // Status Badge
        when {
          isPaid -> {
            Surface(
              color = ForestGreenLightFill,
              shape = RoundedCornerShape(6.dp)
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = "Paid GHS ${String.format(Locale.US, "%.0f", paidAmount)}",
                  style = MaterialTheme.typography.labelSmall.copy(
                    color = ForestGreenPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                  )
                )
              }
            }
          }
          isPartial -> {
            Surface(
              color = HeroAmberBg,
              shape = RoundedCornerShape(6.dp),
              border = BorderStroke(1.dp, HeroAmber.copy(alpha = 0.5f))
            ) {
              Text(
                text = "Partial: GHS ${String.format(Locale.US, "%.0f", paidAmount)}/${String.format(Locale.US, "%.0f", targetAmount)}",
                style = MaterialTheme.typography.labelSmall.copy(
                  color = Color(0xFFB7791F),
                  fontWeight = FontWeight.Bold,
                  fontSize = 11.sp
                ),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
              )
            }
          }
          else -> {
            Surface(
              color = Color(0xFFF7FAFC),
              shape = RoundedCornerShape(6.dp),
              border = BorderStroke(1.dp, BorderGrey)
            ) {
              Text(
                text = "Unpaid",
                style = MaterialTheme.typography.labelSmall.copy(
                  color = TextSecondary,
                  fontWeight = FontWeight.SemiBold,
                  fontSize = 11.sp
                ),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
              )
            }
          }
        }
      }

      // Progress bar & balance info
      Spacer(modifier = Modifier.height(10.dp))

      // Linear Progress Bar (Green for paid, Amber for partial, Grey for unpaid)
      LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
          .fillMaxWidth()
          .height(6.dp)
          .clip(RoundedCornerShape(3.dp)),
        color = when {
          isPaid -> ForestGreenPrimary
          isPartial -> HeroAmber
          else -> BorderGrey
        },
        trackColor = Color(0xFFF0F0F0)
      )

      Spacer(modifier = Modifier.height(8.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (isPartial) {
          Text(
            text = "Remaining balance: GHS ${String.format(Locale.US, "%.2f", remainingAmount)}",
            style = MaterialTheme.typography.bodySmall.copy(
              color = Color(0xFFB7791F),
              fontWeight = FontWeight.SemiBold,
              fontSize = 12.sp
            )
          )
        } else if (isPaid) {
          Text(
            text = "Full cycle contribution settled",
            style = MaterialTheme.typography.bodySmall.copy(
              color = ForestGreenPrimary,
              fontSize = 11.sp
            )
          )
        } else {
          Text(
            text = "Target due: GHS ${String.format(Locale.US, "%.2f", targetAmount)}",
            style = MaterialTheme.typography.bodySmall.copy(
              color = TextSecondary,
              fontSize = 12.sp
            )
          )
        }

        // Action button
        if (!isPaid) {
          OutlinedButton(
            onClick = onRecordPayment,
            modifier = Modifier.height(30.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            border = BorderStroke(1.dp, if (isPartial) HeroAmber else ForestGreenPrimary),
            shape = RoundedCornerShape(6.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Payments,
              contentDescription = null,
              tint = if (isPartial) HeroAmber else ForestGreenPrimary,
              modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = if (isPartial) "Pay Remaining" else "Pay",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = if (isPartial) HeroAmber else ForestGreenPrimary
            )
          }
        }
      }
    }
  }
}
