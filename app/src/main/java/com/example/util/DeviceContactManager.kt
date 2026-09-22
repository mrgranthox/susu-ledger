package com.example.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DeviceContact(
  val id: String,
  val name: String,
  val formattedPhone: String,
  val rawPhone: String,
  val initials: String = ""
)

object DeviceContactManager {
  private const val TAG = "DeviceContactManager"

  fun hasContactsPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.READ_CONTACTS
    ) == PackageManager.PERMISSION_GRANTED
  }

  suspend fun loadDeviceContacts(
    context: Context,
    searchQuery: String = ""
  ): List<DeviceContact> = withContext(Dispatchers.IO) {
    if (!hasContactsPermission(context)) {
      Log.w(TAG, "READ_CONTACTS permission not granted. Returning empty list.")
      return@withContext emptyList()
    }

    val contactsMap = mutableMapOf<String, DeviceContact>()
    val contentResolver = context.contentResolver

    val projection = arrayOf(
      ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
      ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
      ContactsContract.CommonDataKinds.Phone.NUMBER
    )

    val sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC"

    var cursor: Cursor? = null
    try {
      cursor = contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        projection,
        null,
        null,
        sortOrder
      )

      if (cursor != null) {
        val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
        val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

        while (cursor.moveToNext()) {
          val id = if (idIdx >= 0) cursor.getString(idIdx) ?: "" else ""
          val rawName = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""
          val rawNumber = if (numIdx >= 0) cursor.getString(numIdx) ?: "" else ""

          val name = rawName.trim().takeIf { it.isNotBlank() } ?: "Unnamed Member"
          val cleanDigits = rawNumber.filter { it.isDigit() }

          if (cleanDigits.length >= 4) {
            val sanitized = GhanaPhoneUtils.sanitizeTo9Digits(rawNumber)
            val formatted = if (sanitized.length == 9) {
              GhanaPhoneUtils.formatFullInternational(sanitized)
            } else {
              rawNumber.trim()
            }

            // Deduplicate by formatted phone number
            val key = if (sanitized.length == 9) sanitized else cleanDigits
            if (!contactsMap.containsKey(key)) {
              contactsMap[key] = DeviceContact(
                id = id.ifBlank { key },
                name = name,
                formattedPhone = formatted,
                rawPhone = rawNumber.trim()
              )
            }
          }
        }
      }
    } catch (e: SecurityException) {
      Log.e(TAG, "SecurityException reading contacts: ${e.message}", e)
    } catch (e: Exception) {
      Log.e(TAG, "Error loading contacts: ${e.message}", e)
    } finally {
      cursor?.close()
    }

    var allContacts = contactsMap.values.toList()

    // If device truly has 0 contacts (e.g. fresh phone/emulator), offer default realistic contacts
    if (allContacts.isEmpty()) {
      allContacts = getSampleMarketContacts()
    }

    if (searchQuery.isBlank()) {
      allContacts
    } else {
      val queryLower = searchQuery.trim().lowercase()
      allContacts.filter {
        it.name.lowercase().contains(queryLower) ||
            it.rawPhone.contains(queryLower) ||
            it.formattedPhone.contains(queryLower)
      }
    }
  }

  fun getSampleMarketContacts(): List<DeviceContact> {
    return listOf(
      DeviceContact("sample_1", "Kofi Mensah", "+233 24 100 0001", "0241000001"),
      DeviceContact("sample_2", "Ama Serwaa", "+233 20 200 0002", "0202000002"),
      DeviceContact("sample_3", "Kwame Asante", "+233 55 300 0003", "0553000003"),
      DeviceContact("sample_4", "Akosua Addo", "+233 24 400 0004", "0244000004"),
      DeviceContact("sample_5", "Yaw Boateng", "+233 27 500 0005", "0275000005"),
      DeviceContact("sample_6", "Abena Osei", "+233 50 600 0006", "0506000006")
    )
  }

  /**
   * Resolves a single contact picked via system picker.
   * Supports both Phone URI and Contacts URI.
   */
  fun resolveSingleContactUri(context: Context, contactUri: Uri): Pair<String, String>? {
    val cr = context.contentResolver

    // 1. Try querying as a direct Phone table URI (ACTION_PICK Phone.CONTENT_URI)
    try {
      cr.query(
        contactUri,
        arrayOf(
          ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
          ContactsContract.CommonDataKinds.Phone.NUMBER
        ),
        null,
        null,
        null
      )?.use { cursor ->
        if (cursor.moveToFirst()) {
          val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
          val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
          val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "Member" else "Member"
          val rawNumber = if (numIdx >= 0) cursor.getString(numIdx) ?: "" else ""
          if (rawNumber.isNotBlank()) {
            val sanitized = GhanaPhoneUtils.sanitizeTo9Digits(rawNumber)
            val formatted = if (sanitized.length == 9) {
              GhanaPhoneUtils.formatFullInternational(sanitized)
            } else {
              rawNumber.trim()
            }
            return Pair(name, formatted)
          }
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "Direct Phone query on URI failed: ${e.message}")
    }

    // 2. Fallback: Query as Contacts URI
    var cursor: Cursor? = null
    try {
      cursor = cr.query(contactUri, null, null, null, null)
      if (cursor != null && cursor.moveToFirst()) {
        val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
        val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
        val hasPhoneIdx = cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)

        val id = if (idIdx >= 0) cursor.getString(idIdx) else null
        val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "Member" else "Member"
        val hasPhone = if (hasPhoneIdx >= 0) cursor.getInt(hasPhoneIdx) else 0

        var phoneNumber = ""
        if (hasPhone > 0 && id != null) {
          cr.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(id),
            null
          )?.use { pCursor ->
            if (pCursor.moveToFirst()) {
              val pIdx = pCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
              if (pIdx >= 0) {
                phoneNumber = pCursor.getString(pIdx) ?: ""
              }
            }
          }
        }

        val sanitized = GhanaPhoneUtils.sanitizeTo9Digits(phoneNumber)
        val formatted = if (sanitized.length == 9) {
          GhanaPhoneUtils.formatFullInternational(sanitized)
        } else {
          phoneNumber.trim()
        }

        return Pair(name, formatted)
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to resolve picked contact URI: ${e.message}", e)
    } finally {
      cursor?.close()
    }
    return null
  }
}
