package com.example.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface SusuApiService {

  @POST("api/app/sync")
  suspend fun syncGroup(@Body request: SyncGroupRequest): Response<SyncGroupResponse>

  @GET("api/app/status")
  suspend fun getSystemStatus(): Response<CloudSystemStatusResponse>

  @GET("api/app/groups/{id}/cycles/active")
  suspend fun getActiveCycleSnapshot(
    @Path("id") groupId: String
  ): Response<ActiveCycleCloudResponse?>

  @POST("api/app/pair-bot")
  suspend fun registerPairingCode(@Body request: PairBotRequest): Response<PairBotResponse>

  @GET("api/app/pair-bot/{code}")
  suspend fun getPairingStatus(@Path("code") code: String): Response<PairingStatusResponse>

  @POST("api/app/groups/{groupId}/claims/{claimId}/reject")
  suspend fun rejectClaim(
    @Path("groupId") groupId: String,
    @Path("claimId") claimId: String,
    @Body request: Map<String, String>
  ): Response<Map<String, String>>

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
