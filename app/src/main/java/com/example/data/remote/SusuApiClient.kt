package com.example.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object SusuApiClient {

  // Default Cloud Run backend URL deployed in Google Cloud
  const val DEFAULT_BASE_URL = "https://susu-backend-965064733382.africa-south1.run.app/"

  private var customBaseUrl: String = DEFAULT_BASE_URL

  private val moshi: Moshi by lazy {
    Moshi.Builder()
      .add(KotlinJsonAdapterFactory())
      .build()
  }

  private val loggingInterceptor: HttpLoggingInterceptor by lazy {
    HttpLoggingInterceptor().apply {
      level = HttpLoggingInterceptor.Level.BASIC
    }
  }

  private val okHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .addInterceptor(loggingInterceptor)
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(15, TimeUnit.SECONDS)
      .writeTimeout(15, TimeUnit.SECONDS)
      .build()
  }

  private var apiServiceInstance: SusuApiService? = null

  fun getApiService(baseUrl: String? = null): SusuApiService {
    val targetUrl = if (!baseUrl.isNullOrBlank()) {
      if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
    } else {
      if (customBaseUrl.endsWith("/")) customBaseUrl else "$customBaseUrl/"
    }

    if (apiServiceInstance == null || targetUrl != customBaseUrl) {
      customBaseUrl = targetUrl
      val retrofit = Retrofit.Builder()
        .baseUrl(targetUrl)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
      apiServiceInstance = retrofit.create(SusuApiService::class.java)
    }

    return apiServiceInstance!!
  }

  fun updateBaseUrl(newUrl: String) {
    if (newUrl.isNotBlank()) {
      customBaseUrl = if (newUrl.endsWith("/")) newUrl else "$newUrl/"
      apiServiceInstance = null
    }
  }

  fun getCurrentBaseUrl(): String = customBaseUrl
}
