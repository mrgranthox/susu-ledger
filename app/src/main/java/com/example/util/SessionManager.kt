package com.example.util

import android.content.Context
import android.content.SharedPreferences

/**
 * Enterprise Session & Persistence Manager for SusuLedger.
 * Persists officer authentication, active group ID, and app lock security state
 * across app lifecycle events and process restarts.
 */
class SessionManager(context: Context) {
  private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  companion object {
    private const val PREFS_NAME = "susu_ledger_session"
    private const val KEY_IS_ONBOARDED = "is_onboarded"
    private const val KEY_LOGGED_IN_PHONE = "logged_in_phone"
    private const val KEY_LOGGED_IN_ROLE = "logged_in_role"
    private const val KEY_OFFICER_NAME = "officer_name"
    private const val KEY_ACTIVE_GROUP_ID = "active_group_id"
    private const val KEY_IS_LOCKED = "is_app_locked"
    private const val KEY_PIN_HASH = "officer_pin_hash"
    private const val KEY_PIN_SALT = "officer_pin_salt"
    private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
  }

  var isOnboarded: Boolean
    get() = prefs.getBoolean(KEY_IS_ONBOARDED, false)
    set(value) = prefs.edit().putBoolean(KEY_IS_ONBOARDED, value).apply()

  var loggedInPhone: String
    get() = prefs.getString(KEY_LOGGED_IN_PHONE, "") ?: ""
    set(value) = prefs.edit().putString(KEY_LOGGED_IN_PHONE, value).apply()

  var loggedInRole: String
    get() = prefs.getString(KEY_LOGGED_IN_ROLE, "treasurer") ?: "treasurer"
    set(value) = prefs.edit().putString(KEY_LOGGED_IN_ROLE, value).apply()

  var officerName: String
    get() = prefs.getString(KEY_OFFICER_NAME, "") ?: ""
    set(value) = prefs.edit().putString(KEY_OFFICER_NAME, value).apply()

  var activeGroupId: String
    get() = prefs.getString(KEY_ACTIVE_GROUP_ID, "") ?: ""
    set(value) = prefs.edit().putString(KEY_ACTIVE_GROUP_ID, value).apply()

  var isAppLocked: Boolean
    get() = prefs.getBoolean(KEY_IS_LOCKED, false)
    set(value) = prefs.edit().putBoolean(KEY_IS_LOCKED, value).apply()

  var pinHash: String
    get() = prefs.getString(KEY_PIN_HASH, "") ?: ""
    set(value) = prefs.edit().putString(KEY_PIN_HASH, value).apply()

  var pinSalt: String
    get() = prefs.getString(KEY_PIN_SALT, "") ?: ""
    set(value) = prefs.edit().putString(KEY_PIN_SALT, value).apply()

  var isBiometricEnabled: Boolean
    get() = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, true)
    set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, value).apply()

  fun saveSession(
    phone: String,
    role: String,
    name: String,
    groupId: String,
    pinHash: String = "",
    pinSalt: String = ""
  ) {
    prefs.edit()
      .putBoolean(KEY_IS_ONBOARDED, true)
      .putString(KEY_LOGGED_IN_PHONE, phone)
      .putString(KEY_LOGGED_IN_ROLE, role)
      .putString(KEY_OFFICER_NAME, name)
      .putString(KEY_ACTIVE_GROUP_ID, groupId)
      .apply()

    if (pinHash.isNotBlank()) {
      prefs.edit().putString(KEY_PIN_HASH, pinHash).apply()
    }
    if (pinSalt.isNotBlank()) {
      prefs.edit().putString(KEY_PIN_SALT, pinSalt).apply()
    }
  }

  fun clearSession() {
    prefs.edit()
      .remove(KEY_LOGGED_IN_PHONE)
      .remove(KEY_LOGGED_IN_ROLE)
      .remove(KEY_OFFICER_NAME)
      .remove(KEY_ACTIVE_GROUP_ID)
      .remove(KEY_PIN_HASH)
      .remove(KEY_PIN_SALT)
      .putBoolean(KEY_IS_LOCKED, false)
      .apply()
  }

  fun fullReset() {
    prefs.edit().clear().apply()
  }
}
