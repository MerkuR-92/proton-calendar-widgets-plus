package me.proton.android.calendar.data.api

import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import me.proton.android.calendar.common.API_BASE_URL
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitFactory {

    val contentType = "application/json".toMediaType()

    inline fun <reified T> createService(
        httpClient: OkHttpClient,
        gsonConverterFactory: GsonConverterFactory,
        coroutineCallAdapterFactory: CoroutineCallAdapterFactory,
        baseUrl: String = API_BASE_URL
    ): T = Retrofit.Builder()
        .client(httpClient)
        .baseUrl(baseUrl)
        //.addConverterFactory(gsonConverterFactory)
        .addConverterFactory(Json {
            this.ignoreUnknownKeys = true
            this.classDiscriminator = "android_kotlin_class"
        }.asConverterFactory(contentType))
        .addCallAdapterFactory(coroutineCallAdapterFactory)
        .build()
        .create(T::class.java)

}
