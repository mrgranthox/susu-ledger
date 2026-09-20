package com.example.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface SusuApiService {

  @GET("api/app/status")
  suspend fun getSystemStatus(): Response<CloudSystemStatusResponse>

  @POST("api/app/pair-bot")
  suspend fun registerPairingCode(@Body request: PairBotRequest): Response<PairBotResponse>

  @POST("api/app/whatsapp/send-message")
  suspend fun sendWhatsAppMessage(@Body request: SendWhatsAppMessageRequest): Response<SendWhatsAppMessageResponse>

  @POST("api/app/groups/{id}/members")
  suspend fun addMember(
    @Path("id") groupId: String,
    @Body request: AddMemberApiRequest
  ): Response<Map<String, Any>>

  @POST("api/app/payments/confirm")
  suspend fun confirmPayment(@Body request: ConfirmPaymentApiRequest): Response<Map<String, Any>>

  @GET("api/cron/reminders/weekly")
  suspend fun triggerFridayReminder(): Response<CronDispatchResponse>

  @GET("api/cron/summaries/weekly")
  suspend fun triggerSundayDigest(): Response<CronDispatchResponse>
}
