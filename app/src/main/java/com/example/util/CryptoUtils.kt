package com.example.util

import java.security.MessageDigest

object CryptoUtils {

  private const val GENESIS_HASH = "0000000000000000000000000000000000000000000000000000000000000000"

  fun getGenesisHash(): String = GENESIS_HASH

  /**
   * Generates SHA-256 hash chaining according to the SusuLedger specification:
   * encode(digest(CONCAT(id, cycle_id, member_id, amount_paid, idempotency_key, last_hash), 'sha256'), 'hex')
   */
  fun generatePaymentHash(
    id: String,
    cycleId: String,
    memberId: String,
    amountPaid: Double,
    idempotencyKey: String,
    prevHash: String
  ): String {
    // Format amount with 2 decimal places to match PostgreSQL numeric(12,2)
    val amountFormatted = String.format(java.util.Locale.US, "%.2f", amountPaid)
    val data = "$id$cycleId$memberId$amountFormatted$idempotencyKey$prevHash"
    return sha256(data)
  }

  fun sha256(input: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(input.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
  }

  fun formatShortHash(hash: String): String {
    if (hash.length <= 12) return hash
    return "${hash.take(6)}...${hash.takeLast(6)}"
  }

  fun hashPin(pin: String, salt: String = ""): String {
    return sha256("$salt:$pin")
  }

  /**
   * Constant-time comparison to prevent timing attack vulnerabilities during
   * PIN hash and token verification.
   */
  fun secureEquals(a: String, b: String): Boolean {
    val aBytes = a.toByteArray(Charsets.UTF_8)
    val bBytes = b.toByteArray(Charsets.UTF_8)
    return MessageDigest.isEqual(aBytes, bBytes)
  }

  /**
   * Normalizes and formats Ghana phone numbers to standard "+233 XX XXX XXXX"
   */
  fun formatGhanaPhone(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    val normalized = when {
      digits.startsWith("233") -> digits
      digits.startsWith("0") -> "233" + digits.substring(1)
      digits.length == 9 -> "233$digits"
      else -> digits
    }
    return if (normalized.length == 12 && normalized.startsWith("233")) {
      "+233 ${normalized.substring(3, 5)} ${normalized.substring(5, 8)} ${normalized.substring(8, 12)}"
    } else {
      raw.trim()
    }
  }

  /**
   * Verifies PIN against stored hash in constant time.
   */
  fun verifyPin(enteredPin: String, storedPinHash: String, salt: String = ""): Boolean {
    val computedHash = hashPin(enteredPin, salt)
    return secureEquals(computedHash, storedPinHash)
  }

  /**
   * Cryptographically generates a dynamic 6-character WhatsApp Bot pairing code
   */
  fun generatePairingCode(): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    val random = java.security.SecureRandom()
    val part1 = (1..3).map { chars[random.nextInt(chars.length)] }.joinToString("")
    val part2 = (1..3).map { chars[random.nextInt(chars.length)] }.joinToString("")
    return "$part1-$part2"
  }
}
