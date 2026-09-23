package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.StandardNavTopBar
import com.example.ui.theme.BorderGrey
import com.example.ui.theme.ForestGreenPrimary
import com.example.ui.theme.HeroAmber
import com.example.ui.theme.HeroAmberBg
import com.example.ui.theme.HeroAmberBorder
import com.example.ui.theme.HeroAmberText
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun NewCycleScreen(
  currentOpenWeek: Int? = 12,
  defaultNextWeekNumber: Int = 13,
  defaultAmount: Double = 50.0,
  memberCount: Int = 16,
  onBack: () -> Unit,
  onOpenWeek: (weekNumber: Int, amount: Double, dueDate: String) -> Unit
) {
  var weekNumberText by remember { mutableStateOf("Week $defaultNextWeekNumber") }
  var amountText by remember { mutableStateOf("GHS ${defaultAmount.toInt()}") }
  var dueDateText by remember { mutableStateOf(nextFridayLabel()) }

  val isPreviousWeekOpen = currentOpenWeek != null

  Scaffold(
    containerColor = PureWhite,
    topBar = {
      StandardNavTopBar(
        title = "New Week",
        onBackClick = onBack
      )
    }
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(PureWhite)
        .padding(paddingValues)
        .padding(horizontal = 24.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      Column(modifier = Modifier.fillMaxWidth()) {
        // Prefilled fields: "Week 13" (editable), Amount "GHS 50" (editable), Due date picker defaulting to next Friday
        Text("Collection week", style = MaterialTheme.typography.labelMedium.copy(color = TextPrimary))
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
          value = weekNumberText,
          onValueChange = { weekNumberText = it },
          readOnly = true,
          modifier = Modifier.fillMaxWidth().testTag("new_cycle_week_input"),
          singleLine = true,
          shape = RoundedCornerShape(8.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ForestGreenPrimary,
            unfocusedBorderColor = BorderGrey,
            focusedContainerColor = PureWhite,
            unfocusedContainerColor = PureWhite
          )
        )

        Spacer(modifier = Modifier.height(18.dp))

        Text("Amount due per member", style = MaterialTheme.typography.labelMedium.copy(color = TextPrimary))
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
          value = amountText,
          onValueChange = { amountText = it },
          modifier = Modifier.fillMaxWidth().testTag("new_cycle_amount_input"),
          singleLine = true,
          shape = RoundedCornerShape(8.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ForestGreenPrimary,
            unfocusedBorderColor = BorderGrey,
            focusedContainerColor = PureWhite,
            unfocusedContainerColor = PureWhite
          )
        )

        Spacer(modifier = Modifier.height(18.dp))

        Text("Due date", style = MaterialTheme.typography.labelMedium.copy(color = TextPrimary))
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
          value = dueDateText,
          onValueChange = { dueDateText = it },
          modifier = Modifier.fillMaxWidth().testTag("new_cycle_due_date_input"),
          singleLine = true,
          shape = RoundedCornerShape(8.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ForestGreenPrimary,
            unfocusedBorderColor = BorderGrey,
            focusedContainerColor = PureWhite,
            unfocusedContainerColor = PureWhite
          )
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Info box with thin amber border, plain text: "All 16 active members will be reminded on WhatsApp."
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(8.dp),
          colors = CardDefaults.cardColors(containerColor = HeroAmberBg),
          border = BorderStroke(1.dp, HeroAmberBorder)
        ) {
          Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Default.Info, contentDescription = null, tint = HeroAmber, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = "All $memberCount active members will be reminded on WhatsApp.",
              style = MaterialTheme.typography.bodyMedium.copy(
                color = HeroAmberText,
                fontWeight = FontWeight.Medium
              )
            )
          }
        }

        // If a week is already open: warning text "Close Week 12 first."
        if (isPreviousWeekOpen) {
          Spacer(modifier = Modifier.height(16.dp))
          Text(
            text = "Note: Close Week $currentOpenWeek first before opening a new collection week.",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
          )
        }
      }

      Column(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        Button(
          onClick = {
            val num = weekNumberText.replace("Week", "").trim().toIntOrNull() ?: defaultNextWeekNumber
            val amt = amountText.replace("GHS", "").trim().toDoubleOrNull() ?: defaultAmount
            onOpenWeek(num, amt, dueDateText)
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("open_week_btn"),
          shape = RoundedCornerShape(8.dp),
          colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary)
        ) {
          Text(
            text = "Open Week & Notify Everyone",
            style = MaterialTheme.typography.labelLarge.copy(color = PureWhite, fontWeight = FontWeight.SemiBold)
          )
        }
      }
    }
  }
}

/** Computes the label for the upcoming Friday (or today if it is Friday). */
private fun nextFridayLabel(): String {
  val calendar = java.util.Calendar.getInstance()
  val dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK)
  var daysUntilFriday = java.util.Calendar.FRIDAY - dayOfWeek
  if (daysUntilFriday <= 0) daysUntilFriday += 7
  calendar.add(java.util.Calendar.DAY_OF_YEAR, daysUntilFriday)
  return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
}
