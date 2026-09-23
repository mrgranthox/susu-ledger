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

  data class PairingSession(val code: String, val expiresAt: Long) {
    fun secondsRemaining(now: Long = System.currentTimeMillis()): Int =
      ((expiresAt - now).coerceAtLeast(0) / 1000).toInt()
  }

  fun loadPairing(phone: String, groupId: String): PairingSession? {
    val key = "pairing:$phone:$groupId"
    val code = prefs.getString(key, null) ?: return null
    return PairingSession(code, prefs.getLong("$key:expiry", 0))
  }

  fun savePairing(phone: String, groupId: String, session: PairingSession) {
    val key = "pairing:$phone:$groupId"
    prefs.edit().putString(key, session.code).putLong("$key:expiry", session.expiresAt).apply()
  }

  fun pairingForConnection(phone: String, groupId: String, forceNew: Boolean = false): PairingSession {
    if (!forceNew) loadPairing(phone, groupId)?.let { return it }
    return PairingSession(CryptoUtils.generatePairingCode(), System.currentTimeMillis() + 900_000).also {
      savePairing(phone, groupId, it)
    }
  }

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
    private const val KEY_SAVED_PIN = "officer_saved_pin"
    private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
    private const val KEY_LAST_NAV_INDEX = "last_nav_index"
    private const val KEY_ACTIVE_SUBSCREEN = "active_subscreen"
    private const val KEY_LAST_ACTIVE_TIME = "last_active_time"
    private const val INACTIVITY_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes grace period
  }

  var isOnboarded: Boolean
    get() = prefs.getBoolean(KEY_IS_ONBOARDED, false)
    set(value) = prefs.edit().putBoolean(KEY_IS_ONBOARDED, value).apply()

  var loggedInPhone: String
    get() = prefs.getString(KEY_LOGGED_IN_PHONE, "") ?: ""
    set(value) = prefs.edit().putString(KEY_LOGGED_IN_PHONE, value).apply()

  val biometricOfficerPhone: String
    get() = prefs.getString("biometric_officer_phone", "") ?: ""

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

  var savedPin: String
    get() = prefs.getString(KEY_SAVED_PIN, "") ?: ""
    set(value) = prefs.edit().putString(KEY_SAVED_PIN, value).apply()

  var isBiometricEnabled: Boolean
    get() = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, true)
    set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, value).apply()

  var lastNavIndex: Int
    get() = prefs.getInt(KEY_LAST_NAV_INDEX, 0)
    set(value) = prefs.edit().putInt(KEY_LAST_NAV_INDEX, value).apply()

  var activeSubscreen: String
    get() = prefs.getString(KEY_ACTIVE_SUBSCREEN, "") ?: ""
    set(value) = prefs.edit().putString(KEY_ACTIVE_SUBSCREEN, value).apply()

  var lastActiveTimestamp: Long
    get() = prefs.getLong(KEY_LAST_ACTIVE_TIME, System.currentTimeMillis())
    set(value) = prefs.edit().putLong(KEY_LAST_ACTIVE_TIME, value).apply()

  fun recordActivity() {
    lastActiveTimestamp = System.currentTimeMillis()
  }

  fun shouldLockAppAfterInactivity(): Boolean {
    val elapsed = System.currentTimeMillis() - lastActiveTimestamp
    return elapsed > INACTIVITY_TIMEOUT_MS
  }

  fun saveSession(
    phone: String,
    role: String,
    name: String,
    groupId: String,
    pinHash: String = "",
    pinSalt: String = "",
    rawPin: String = ""
  ) {
    val editor = prefs.edit()
      .putBoolean(KEY_IS_ONBOARDED, true)
      .putString(KEY_LOGGED_IN_PHONE, phone)
      .putString("biometric_officer_phone", phone)
      .putString(KEY_LOGGED_IN_ROLE, role)
      .putString(KEY_OFFICER_NAME, name)
      .putString(KEY_ACTIVE_GROUP_ID, groupId)
      .putLong(KEY_LAST_ACTIVE_TIME, System.currentTimeMillis())

    if (pinHash.isNotBlank()) {
      editor.putString(KEY_PIN_HASH, pinHash)
    }
    if (pinSalt.isNotBlank()) {
      editor.putString(KEY_PIN_SALT, pinSalt)
    }
    if (rawPin.isNotBlank()) {
      editor.putString(KEY_SAVED_PIN, rawPin)
    }
    editor.apply()
  }

  fun clearSession() {
    prefs.edit()
      .putString("biometric_officer_phone", biometricOfficerPhone.ifBlank { loggedInPhone })
      .remove(KEY_LOGGED_IN_PHONE)
      .remove(KEY_LOGGED_IN_ROLE)
      .remove(KEY_OFFICER_NAME)
      .remove(KEY_ACTIVE_GROUP_ID)
      .remove(KEY_PIN_HASH)
      .remove(KEY_PIN_SALT)
      .remove(KEY_SAVED_PIN)
      .remove(KEY_ACTIVE_SUBSCREEN)
      .putInt(KEY_LAST_NAV_INDEX, 0)
      .putBoolean(KEY_IS_LOCKED, false)
      .apply()
  }

  fun fullReset() {
    prefs.edit().clear().apply()
  }
}
