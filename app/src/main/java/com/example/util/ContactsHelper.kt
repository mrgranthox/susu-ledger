package com.example.util

import android.Manifest
import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.ui.theme.BorderGrey
import com.example.ui.theme.ForestGreenLightFill
import com.example.ui.theme.ForestGreenPrimary
import com.example.ui.theme.LineIconBlack
import com.example.ui.theme.LineIconGrey
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ContactsHelper {

  /**
   * Safely reads phone contacts from the Android Contacts Provider.
   * Performs deduplication and formats Ghanaian phone numbers automatically.
   */
  suspend fun readDeviceContacts(contentResolver: ContentResolver): List<DeviceContact> =
    withContext(Dispatchers.IO) {
      val contactsList = mutableListOf<DeviceContact>()
      val seenPhones = mutableSetOf<String>()

      val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER
      )

      try {
        val cursor = contentResolver.query(
          ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
          projection,
          null,
          null,
          "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )

        cursor?.use { c ->
          val idIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
          val nameIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
          val numberIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

          while (c.moveToNext()) {
            val contactId = if (idIndex != -1) c.getString(idIndex) ?: "" else ""
            val rawName = if (nameIndex != -1) c.getString(nameIndex) ?: "" else ""
            val rawNum = if (numberIndex != -1) c.getString(numberIndex) ?: "" else ""

            val cleanName = rawName.trim().ifBlank { "Contact" }
            val cleanDigits = rawNum.filter { it.isDigit() }

            if (cleanDigits.length >= 8) {
              val formatted = if (cleanDigits.startsWith("233") || cleanDigits.length == 9 || cleanDigits.length == 10) {
                GhanaPhoneUtils.formatFullInternational(cleanDigits)
              } else {
                rawNum.trim()
              }

              val dedupeKey = cleanDigits.takeLast(9)
              if (!seenPhones.contains(dedupeKey)) {
                seenPhones.add(dedupeKey)

                val initials = cleanName.split(" ")
                  .filter { it.isNotBlank() }
                  .take(2)
                  .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                  .joinToString("")
                  .ifBlank { "M" }

                contactsList.add(
                  DeviceContact(
                    id = contactId.ifBlank { dedupeKey },
                    name = cleanName,
                    rawPhone = rawNum.trim(),
                    formattedPhone = formatted.ifBlank { rawNum.trim() },
                    initials = initials
                  )
                )
              }
            }
          }
        }
      } catch (e: Exception) {
        // Log safely without crashing
        e.printStackTrace()
      }

      contactsList.sortedBy { it.name.lowercase() }
    }

  /**
   * Helper to check if Contacts permission is granted.
   */
  fun hasContactsPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.READ_CONTACTS
    ) == PackageManager.PERMISSION_GRANTED
  }

  /**
   * Opens Android Application Details Settings screen so the user can grant permission if permanently denied.
   */
  fun openAppSettings(context: Context) {
    try {
      val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
      context.startActivity(intent)
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }
}

/**
 * Production-ready Permission Rationale Dialog for SusuLedger.
 * Explains clearly why contact access is needed for group administration.
 */
@Composable
fun ContactsPermissionRationaleDialog(
  onDismiss: () -> Unit,
  onRequestPermission: () -> Unit,
  isPermanentlyDenied: Boolean = false,
  onOpenSettings: () -> Unit = {}
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    icon = {
      Box(
        modifier = Modifier
          .size(48.dp)
          .clip(CircleShape)
          .background(ForestGreenLightFill),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.Contacts,
          contentDescription = null,
          tint = ForestGreenPrimary,
          modifier = Modifier.size(24.dp)
        )
      }
    },
    title = {
      Text(
        text = if (isPermanentlyDenied) "Contacts Permission Needed" else "Import Susu Members from Contacts",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary),
        textAlign = TextAlign.Center
      )
    },
    text = {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = if (isPermanentlyDenied) {
            "SusuLedger requires Contacts permission to import member names and WhatsApp phone numbers without manual typing.\n\nPlease enable Contacts in App Settings."
          } else {
            "Allow SusuLedger to read your contacts to quickly add trusted group members with their Mobile Money telephone numbers for automated receipts and week ledger tracking."
          },
          style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
          textAlign = TextAlign.Center
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (isPermanentlyDenied) {
            onOpenSettings()
          } else {
            onRequestPermission()
          }
        },
        colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.testTag("permission_grant_btn")
      ) {
        Text(if (isPermanentlyDenied) "Open Settings" else "Allow Access", color = PureWhite, fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      OutlinedButton(
        onClick = onDismiss,
        shape = RoundedCornerShape(8.dp)
      ) {
        Text("Cancel", color = LineIconBlack)
      }
    }
  )
}

/**
 * Bottom sheet modal for picking one or multiple contacts from the device address book.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsPickerBottomSheet(
  sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
  onDismiss: () -> Unit,
  onContactsSelected: (List<DeviceContact>) -> Unit,
  multiSelect: Boolean = true
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var contacts by remember { mutableStateOf<List<DeviceContact>>(emptyList()) }
  var isLoading by remember { mutableStateOf(true) }
  var searchQuery by remember { mutableStateOf("") }
  val selectedContacts = remember { mutableStateListOf<DeviceContact>() }

  var showRationaleDialog by remember { mutableStateOf(false) }
  var isPermanentlyDenied by remember { mutableStateOf(false) }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    if (isGranted) {
      scope.launch {
        isLoading = true
        contacts = ContactsHelper.readDeviceContacts(context.contentResolver)
        isLoading = false
      }
    } else {
      val activity = context as? Activity
      if (activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_CONTACTS)) {
        isPermanentlyDenied = true
      }
      showRationaleDialog = true
      isLoading = false
    }
  }

  LaunchedEffect(Unit) {
    if (ContactsHelper.hasContactsPermission(context)) {
      isLoading = true
      contacts = ContactsHelper.readDeviceContacts(context.contentResolver)
      isLoading = false
    } else {
      permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
    }
  }

  if (showRationaleDialog) {
    ContactsPermissionRationaleDialog(
      onDismiss = {
        showRationaleDialog = false
        onDismiss()
      },
      onRequestPermission = {
        showRationaleDialog = false
        permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
      },
      isPermanentlyDenied = isPermanentlyDenied,
      onOpenSettings = {
        showRationaleDialog = false
        ContactsHelper.openAppSettings(context)
      }
    )
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = PureWhite,
    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
  ) {
    val filteredContacts = remember(contacts, searchQuery) {
      if (searchQuery.isBlank()) contacts
      else contacts.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
            it.rawPhone.contains(searchQuery) ||
            it.formattedPhone.contains(searchQuery)
      }
    }

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 12.dp)
        .fillMaxHeight(0.85f)
    ) {
      // Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "Select from Address Book",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
          )
          Text(
            text = if (multiSelect) "${selectedContacts.size} contacts selected" else "Choose a member to add",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
          )
        }
        IconButton(onClick = onDismiss) {
          Icon(Icons.Default.Close, contentDescription = "Close", tint = LineIconGrey)
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Search field
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        placeholder = { Text("Search name or phone number...", fontSize = 13.sp) },
        leadingIcon = {
          Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
        },
        trailingIcon = {
          if (searchQuery.isNotBlank()) {
            IconButton(onClick = { searchQuery = "" }) {
              Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary, modifier = Modifier.size(16.dp))
            }
          }
        },
        modifier = Modifier.fillMaxWidth().testTag("contacts_search_field"),
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = ForestGreenPrimary,
          unfocusedBorderColor = BorderGrey
        )
      )

      Spacer(modifier = Modifier.height(12.dp))

      if (isLoading) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = ForestGreenPrimary)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Loading device contacts...", fontSize = 12.sp, color = TextSecondary)
          }
        }
      } else if (contacts.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
          contentAlignment = Alignment.Center
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
          ) {
            Icon(Icons.Default.Contacts, contentDescription = null, tint = LineIconGrey, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(
              "No contacts found or permission not granted",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary),
              textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
              onClick = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
              colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary),
              shape = RoundedCornerShape(8.dp)
            ) {
              Text("Request Contacts Permission", color = PureWhite)
            }
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
        ) {
          items(filteredContacts, key = { it.id + it.formattedPhone }) { contact ->
            val isSelected = selectedContacts.any { it.id == contact.id && it.formattedPhone == contact.formattedPhone }

            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  if (multiSelect) {
                    if (isSelected) {
                      selectedContacts.removeAll { it.id == contact.id && it.formattedPhone == contact.formattedPhone }
                    } else {
                      selectedContacts.add(contact)
                    }
                  } else {
                    onContactsSelected(listOf(contact))
                    onDismiss()
                  }
                }
                .padding(vertical = 10.dp, horizontal = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
              ) {
                // Avatar
                Box(
                  modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) ForestGreenPrimary else ForestGreenLightFill),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = contact.initials,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) PureWhite else ForestGreenPrimary
                  )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = contact.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  Text(
                    text = contact.formattedPhone,
                    fontSize = 12.sp,
                    color = TextSecondary
                  )
                }
              }

              if (multiSelect) {
                Checkbox(
                  checked = isSelected,
                  onCheckedChange = { checked ->
                    if (checked) {
                      selectedContacts.add(contact)
                    } else {
                      selectedContacts.removeAll { it.id == contact.id && it.formattedPhone == contact.formattedPhone }
                    }
                  },
                  colors = CheckboxDefaults.colors(
                    checkedColor = ForestGreenPrimary,
                    uncheckedColor = BorderGrey
                  )
                )
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Bottom Actions
      if (multiSelect) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          OutlinedButton(
            onClick = onDismiss,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.weight(1f)
          ) {
            Text("Cancel", color = LineIconBlack)
          }

          Button(
            onClick = {
              onContactsSelected(selectedContacts.toList())
              onDismiss()
            },
            enabled = selectedContacts.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
              .weight(1f)
              .testTag("confirm_import_contacts_btn")
          ) {
            Text("Import (${selectedContacts.size})", color = PureWhite, fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}
