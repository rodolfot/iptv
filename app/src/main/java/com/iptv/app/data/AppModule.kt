package com.iptv.app.data

import android.content.Context
import androidx.room.Room
import com.iptv.app.data.api.XtreamApi
import com.iptv.app.data.db.ALL_MIGRATIONS
import com.iptv.app.data.db.AppDatabase
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Provides @Singleton
    fun provideOkHttp(): OkHttpClient {
        val ua = "VLC/3.0.20 LibVLC/3.0.20"
        val log = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        return OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", ua)
                    .header("Accept", "application/json, text/plain, */*")
                    .build()
                chain.proceed(req)
            }
            .addInterceptor(log)
            .build()
    }

    @Provides @Singleton
    fun provideRetrofit(client: OkHttpClient, moshi: Moshi): Retrofit = Retrofit.Builder()
        // baseUrl is required by Retrofit but we override with @Url on every call
        .baseUrl("http://localhost/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    @Provides @Singleton
    fun provideXtreamApi(retrofit: Retrofit): XtreamApi = retrofit.create(XtreamApi::class.java)

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "iptv.db")
            .addMigrations(*ALL_MIGRATIONS)
            // Cache-only fallback: if a migration is missing for a future version
            // we keep favorites/progress and just rebuild the catalog from the API.
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides fun provideFavoriteDao(db: AppDatabase) = db.favoriteDao()
    @Provides fun provideEpisodeProgressDao(db: AppDatabase) = db.episodeProgressDao()
    @Provides fun provideMovieProgressDao(db: AppDatabase) = db.movieProgressDao()
    @Provides fun provideSeriesProgressDao(db: AppDatabase) = db.seriesProgressDao()
    @Provides fun provideCacheMetaDao(db: AppDatabase) = db.cacheMetaDao()
    @Provides fun provideCategoryCacheDao(db: AppDatabase) = db.categoryCacheDao()
    @Provides fun provideLiveCacheDao(db: AppDatabase) = db.liveCacheDao()
    @Provides fun provideMovieCacheDao(db: AppDatabase) = db.movieCacheDao()
    @Provides fun provideSeriesCacheDao(db: AppDatabase) = db.seriesCacheDao()
    @Provides fun provideEpgDao(db: AppDatabase) = db.epgDao()
    @Provides fun provideDetailCacheDao(db: AppDatabase) = db.detailCacheDao()
}
