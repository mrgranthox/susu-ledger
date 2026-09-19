package com.example.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.MemberEntity
import com.example.data.local.PaymentEntity
import com.example.ui.theme.BorderGrey
import com.example.ui.theme.ForestGreenPrimary
import com.example.ui.theme.LineIconBlack
import com.example.ui.theme.LineIconGrey
import com.example.ui.theme.PureWhite
import com.example.ui.components.StandardNavTopBar
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun MembersScreen(
  members: List<MemberEntity>,
  cyclePayments: List<PaymentEntity>,
  cycleNumber: Int,
  onRecordPaymentForMember: (MemberEntity) -> Unit,
  onAddMemberClick: () -> Unit,
  onBackClick: (() -> Unit)? = null
) {
  var searchQuery by remember { mutableStateOf("") }
  var showAllMembersModal by remember { mutableStateOf(false) }
  var showAddMemberForm by remember { mutableStateOf(false) }

  // Add Member form state
  var newMemberName by remember { mutableStateOf("") }
  var newMemberPhone by remember { mutableStateOf("") }
  var newMemberCycleLiable by remember { mutableStateOf("Week ${cycleNumber + 1}") }

  val filteredMembers = remember(members, searchQuery) {
    if (searchQuery.isBlank()) members
    else members.filter { it.alias.contains(searchQuery, ignoreCase = true) || it.phone.contains(searchQuery) }
  }

  // List: 4 rows max + "View all"
  val displayMembers = filteredMembers.take(4)

  Scaffold(
    containerColor = PureWhite,
    topBar = {
      Column(modifier = Modifier.fillMaxWidth()) {
        StandardNavTopBar(
          title = "${members.size} Active Members",
          subtitle = "Group roster & contribution records",
          onBackClick = onBackClick
        )
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
          // Search bar with thin grey border
          OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search members...", color = LineIconGrey, fontSize = 14.sp) },
            leadingIcon = {
              Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = LineIconGrey, modifier = Modifier.size(18.dp))
            },
            modifier = Modifier
              .fillMaxWidth()
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
        }
      }
    },
    floatingActionButton = {
      // Green FAB "Add Member" opens simple form: Name, Phone, "First cycle liable: Week 13"
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
    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(PureWhite)
        .padding(paddingValues)
        .padding(horizontal = 20.dp)
    ) {
      Spacer(modifier = Modifier.height(8.dp))

      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = BorderStroke(1.dp, BorderGrey),
        shape = RoundedCornerShape(8.dp)
      ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
          if (filteredMembers.isEmpty()) {
            Text(
              text = if (searchQuery.isNotBlank()) "No members match '$searchQuery'" else "No members added yet. Tap 'Add Member' to register group members.",
              style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
              modifier = Modifier.padding(vertical = 16.dp)
            )
          } else {
            displayMembers.forEachIndexed { index, member ->
              MemberRowItem(
                member = member,
                tag = if (member.state == "exited") "exited" else "joined Week ${member.joinedCycle}"
              )
              if (index < displayMembers.lastIndex) {
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(BorderGrey)
                )
              }
            }

            if (filteredMembers.size > 4) {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(1.dp)
                  .background(BorderGrey)
              )
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = "View all (${filteredMembers.size})",
                style = MaterialTheme.typography.labelMedium.copy(
                  color = ForestGreenPrimary,
                  fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier
                  .clickable { showAllMembersModal = true }
                  .padding(vertical = 8.dp)
              )
            }
          }
        }
      }
    }
  }

  // All Members Modal
  if (showAllMembersModal) {
    AlertDialog(
      onDismissRequest = { showAllMembersModal = false },
      title = {
        Text("All Active Members (${filteredMembers.size})", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
      },
      text = {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          filteredMembers.forEach { member ->
            MemberRowItem(member = member, tag = "active")
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showAllMembersModal = false }) {
          Text("Close", color = ForestGreenPrimary)
        }
      }
    )
  }

  // Add Member Form Dialog: Name, Phone, "First cycle liable: Week 13"
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
private fun MemberRowItem(member: MemberEntity, tag: String) {
  var menuExpanded by remember { mutableStateOf(false) }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      // Green initials circle
      val initial = member.alias.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").uppercase()
      Box(
        modifier = Modifier
          .size(36.dp)
          .clip(CircleShape)
          .background(ForestGreenPrimary),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = if (initial.isNotEmpty()) initial else "M",
          color = PureWhite,
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold
        )
      }

      Spacer(modifier = Modifier.width(10.dp))

      Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = member.alias,
            style = MaterialTheme.typography.bodyMedium.copy(
              color = TextPrimary,
              fontWeight = FontWeight.SemiBold
            )
          )
          Spacer(modifier = Modifier.width(6.dp))
          // Small grey tag ("joined Week 8" / "exited")
          Box(
            modifier = Modifier
              .border(1.dp, BorderGrey, RoundedCornerShape(4.dp))
              .background(PureWhite)
              .padding(horizontal = 6.dp, vertical = 2.dp)
          ) {
            Text(
              text = tag,
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

    // Black "⋮" menu (Edit name, Change number, Mark exited)
    Box {
      IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(32.dp)) {
        Icon(Icons.Default.MoreVert, contentDescription = "Member Menu", tint = LineIconBlack, modifier = Modifier.size(18.dp))
      }

      DropdownMenu(
        expanded = menuExpanded,
        onDismissRequest = { menuExpanded = false },
        modifier = Modifier.background(PureWhite)
      ) {
        DropdownMenuItem(
          text = { Text("Edit name", color = TextPrimary) },
          onClick = { menuExpanded = false }
        )
        DropdownMenuItem(
          text = { Text("Change number", color = TextPrimary) },
          onClick = { menuExpanded = false }
        )
        DropdownMenuItem(
          text = { Text("Mark exited", color = TextSecondary) },
          onClick = { menuExpanded = false }
        )
      }
    }
  }
}
