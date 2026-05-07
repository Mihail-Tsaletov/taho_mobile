package svaga.taho.di

import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import svaga.taho.TahoApplication
import svaga.taho.data.local.TokenManager
import svaga.taho.data.remote.ApiService
import svaga.taho.util.BuildConfig
import svaga.taho.util.SseClient
import svaga.taho.util.SseEventBus
import svaga.taho.util.WaitingTimerManager
import svaga.taho.utils.ActiveOrderManager
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

private const val BASE_URL = BuildConfig.BASE_URL
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideApi(okHttpClient: OkHttpClient): ApiService {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ApiProvider {
        fun apiService(): ApiService
        fun tokenManager(): TokenManager
        fun activeOrderManager(): ActiveOrderManager
        fun sseClient(): SseClient
        fun waitingTimerManager(): WaitingTimerManager
        fun sseEventBus(): SseEventBus
    }

    @Provides
    @Singleton
    fun provideActiveOrderManager(
        apiService: ApiService,
        tokenManager: TokenManager
    ): ActiveOrderManager = ActiveOrderManager(apiService, tokenManager)

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .pingInterval(10, TimeUnit.SECONDS)
            .addInterceptor(
                HttpLoggingInterceptor { Log.d("OKHTTP", it) }.apply {
                    level = HttpLoggingInterceptor.Level.HEADERS
                }
            )
            .build()
    }



    @Provides
    @Singleton
    fun provideSseClient(okHttpClient: OkHttpClient): SseClient {
        return SseClient(okHttpClient)
    }

    @Provides
    @Singleton
    fun provideSseEventBus(): SseEventBus = SseEventBus()

    @Provides
    @Singleton
    fun provideWaitingTimerManager(
        tokenManager: TokenManager
    ): WaitingTimerManager = WaitingTimerManager(tokenManager)
}