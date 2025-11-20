package svaga.taho.di

import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import svaga.taho.data.local.TokenManager
import svaga.taho.data.remote.ApiService
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideApi(): ApiService {
        return Retrofit.Builder()
            .baseUrl("http://188.120.239.157:8081/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ApiProvider {
        fun apiService(): ApiService
        fun tokenManager(): TokenManager
    }
}