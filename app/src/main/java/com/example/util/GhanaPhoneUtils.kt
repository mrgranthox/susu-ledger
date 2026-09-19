package com.example.util

/**
 * Utility for Ghanaian phone number formatting and validation.
 * In Ghana, standard national numbers are 10 digits (e.g., 024 123 4567).
 * When prefixed with country code +233, the leading '0' is dropped,
 * leaving exactly 9 digits (e.g., +233 24 123 4567).
 */
object GhanaPhoneUtils {

  private val MTN_PREFIXES = listOf("24", "25", "53", "54", "55", "59")
  private val TELECEL_PREFIXES = listOf("20", "50")
  private val AT_PREFIXES = listOf("27", "57", "26")
  private val GLO_PREFIXES = listOf("23")

  /**
   * Cleans input to extract up to 9 digits intended to follow "+233".
   * Automatically strips leading zero '0' and country code '233' or '+233'.
   */
  fun sanitizeTo9Digits(input: String): String {
    // Strip non-digits
    var digits = input.filter { it.isDigit() }

    // Strip leading country code if present (233)
    if (digits.startsWith("233") && digits.length >= 11) {
      digits = digits.removePrefix("233")
    }

    // Strip leading 0 if user entered national format (e.g., 024...)
    while (digits.startsWith("0")) {
      digits = digits.drop(1)
    }

    // Limit to 9 digits maximum
    return digits.take(9)
  }

  /**
   * Formats 9 digits into standard spaced presentation: "24 123 4567"
   */
  fun format9Digits(digits9: String): String {
    val clean = sanitizeTo9Digits(digits9)
    return when {
      clean.length <= 2 -> clean
      clean.length <= 5 -> "${clean.substring(0, 2)} ${clean.substring(2)}"
      else -> "${clean.substring(0, 2)} ${clean.substring(2, 5)} ${clean.substring(5)}"
    }
  }

  /**
   * Formats into complete international string: "+233 24 123 4567"
   */
  fun formatFullInternational(digits9: String): String {
    val formatted = format9Digits(digits9)
    return if (formatted.isNotBlank()) "+233 $formatted" else ""
  }

  /**
   * Returns true if the 9-digit string is a complete and valid Ghanaian mobile number.
   */
  fun isValidGhanaPhone(digits9: String): Boolean {
    val clean = sanitizeTo9Digits(digits9)
    if (clean.length != 9) return false
    val prefix2 = clean.take(2)
    return prefix2 in MTN_PREFIXES ||
        prefix2 in TELECEL_PREFIXES ||
        prefix2 in AT_PREFIXES ||
        prefix2 in GLO_PREFIXES ||
        (clean.startsWith("2") || clean.startsWith("5"))
  }

  /**
   * Identifies Ghana network provider by prefix
   */
  fun getNetworkProvider(digits9: String): String? {
    val clean = sanitizeTo9Digits(digits9)
    if (clean.length < 2) return null
    val prefix2 = clean.take(2)
    return when {
      prefix2 in MTN_PREFIXES -> "MTN"
      prefix2 in TELECEL_PREFIXES -> "Telecel"
      prefix2 in AT_PREFIXES -> "AT"
      prefix2 in GLO_PREFIXES -> "Glo"
      else -> null
    }
  }

  /**
   * Returns helpful UI status feedback string
   */
  fun getValidationStatus(digits9: String): Pair<Boolean, String> {
    val clean = sanitizeTo9Digits(digits9)
    return when {
      clean.isEmpty() -> Pair(false, "Enter 9 digits without leading 0 (e.g. 24 123 4567)")
      clean.length < 9 -> Pair(false, "${clean.length}/9 digits (${9 - clean.length} more required)")
      isValidGhanaPhone(clean) -> {
        val net = getNetworkProvider(clean)
        val providerText = if (net != null) " ($net MoMo)" else ""
        Pair(true, "Valid Ghana number$providerText")
      }
      else -> Pair(false, "Invalid Ghana mobile prefix (expected 24, 54, 20, 27, etc.)")
    }
  }
}
