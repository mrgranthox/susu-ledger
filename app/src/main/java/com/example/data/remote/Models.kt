package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CloudSystemStatusResponse(
  @Json(name = "status") val status: String? = null,
  @Json(name = "service") val service: String? = null,
  @Json(name = "timestamp") val timestamp: String? = null,
  @Json(name = "database") val database: DatabaseStatusInfo? = null,
  @Json(name = "cache") val cache: CacheStatusInfo? = null,
  @Json(name = "whatsappBot") val whatsappBot: WhatsAppBotStatusInfo? = null,
  @Json(name = "cryptoEngine") val cryptoEngine: CryptoEngineStatusInfo? = null
)

@JsonClass(generateAdapter = true)
data class DatabaseStatusInfo(
  @Json(name = "engine") val engine: String? = null,
  @Json(name = "status") val status: String? = null,
  @Json(name = "latencyMs") val latencyMs: Long? = null
)

@JsonClass(generateAdapter = true)
data class CacheStatusInfo(
  @Json(name = "engine") val engine: String? = null,
  @Json(name = "ping") val ping: String? = null
)

@JsonClass(generateAdapter = true)
data class WhatsAppBotStatusInfo(
  @Json(name = "provider") val provider: String? = null,
  @Json(name = "phoneIdConfigured") val phoneIdConfigured: Boolean? = null,
  @Json(name = "tokenConfigured") val tokenConfigured: Boolean? = null,
  @Json(name = "webhookPath") val webhookPath: String? = null,
  @Json(name = "autoPairingSupported") val autoPairingSupported: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class CryptoEngineStatusInfo(
  @Json(name = "algorithm") val algorithm: String? = null,
  @Json(name = "doubleEntryBalanced") val doubleEntryBalanced: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class PairBotRequest(
  @Json(name = "code") val code: String,
  @Json(name = "phone") val phone: String,
  @Json(name = "groupId") val groupId: String? = null
)

@JsonClass(generateAdapter = true)
data class PairBotResponse(
  @Json(name = "success") val success: Boolean = false,
  @Json(name = "pairingCode") val pairingCode: String? = null,
  @Json(name = "expiresInSeconds") val expiresInSeconds: Int? = null,
  @Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class SendWhatsAppMessageRequest(
  @Json(name = "phone") val phone: String,
  @Json(name = "message") val message: String,
  @Json(name = "identityId") val identityId: String? = null
)

@JsonClass(generateAdapter = true)
data class SendWhatsAppMessageResponse(
  @Json(name = "success") val success: Boolean = false,
  @Json(name = "phone") val phone: String? = null,
  @Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class AddMemberApiRequest(
  @Json(name = "name") val name: String,
  @Json(name = "phone") val phone: String
)

@JsonClass(generateAdapter = true)
data class ConfirmPaymentApiRequest(
  @Json(name = "cycleId") val cycleId: String,
  @Json(name = "memberId") val memberId: String,
  @Json(name = "amountPaid") val amountPaid: Double,
  @Json(name = "method") val method: String,
  @Json(name = "confirmedBy") val confirmedBy: String,
  @Json(name = "idempotencyKey") val idempotencyKey: String,
  @Json(name = "source") val source: String = "app"
)

@JsonClass(generateAdapter = true)
data class CronDispatchResponse(
  @Json(name = "status") val status: String? = null,
  @Json(name = "sentCount") val sentCount: Int? = null,
  @Json(name = "digestCount") val digestCount: Int? = null
)
