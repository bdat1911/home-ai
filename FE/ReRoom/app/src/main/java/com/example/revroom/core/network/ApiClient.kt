package com.example.revroom.core.network

import com.example.revroom.BuildConfig
import com.example.revroom.data.remote.AuthApi
import com.example.revroom.data.remote.ChatApi
import com.example.revroom.data.remote.DesignApi
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    val baseUrl: String
        get() = ServerEndpoint.baseUrl.value

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BASIC
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(EndpointRoutingInterceptor(BuildConfig.API_BASE_URL.toHttpUrl()) {
            baseUrl.toHttpUrl()
        })
        .addInterceptor(loggingInterceptor)
        .connectTimeout(10, TimeUnit.SECONDS)
        // Mutating requests must not be silently replayed after an ambiguous network failure.
        .retryOnConnectionFailure(false)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val retrofitInstance: Retrofit
        get() = retrofit

    val designApi: DesignApi by lazy {
        retrofit.create(DesignApi::class.java)
    }

    val authApi: AuthApi by lazy {
        retrofit.create(AuthApi::class.java)
    }

    val chatApi: ChatApi by lazy {
        retrofit.create(ChatApi::class.java)
    }
}
