package com.luxmap

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.luxmap.core.map.initMapLibre
import com.luxmap.core.sync.SyncScheduler
import com.luxmap.feature.survey.capture.SurveySessionRecoveryUseCase
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class LuxMapApp : Application(), Configuration.Provider, ImageLoaderFactory {
    @Inject lateinit var surveySessionRecoveryUseCase: SurveySessionRecoveryUseCase

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var syncScheduler: SyncScheduler

    // Same OkHttpClient every API call already uses (NetworkModule) - attaches the Bearer token
    // and refreshes it on expiry. Without this, Coil falls back to its own default client with
    // no Authorization header, so any AsyncImage pointed at a backend URL that needs auth
    // (ReadWorkOrders policy etc.) silently 401s.
    @Inject lateinit var okHttpClient: OkHttpClient

    override fun onCreate() {
        super.onCreate()
        initMapLibre(this)
        CoroutineScope(Dispatchers.Default).launch { surveySessionRecoveryUseCase.recoverAny() }
        CoroutineScope(Dispatchers.Default).launch { syncScheduler.schedulePeriodicSync() }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this).okHttpClient(okHttpClient).build()
}
