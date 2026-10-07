package com.luxmap

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.luxmap.core.map.initMapLibre
import com.luxmap.core.sync.SyncScheduler
import com.luxmap.feature.survey.capture.SurveySessionRecoveryUseCase
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class LuxMapApp : Application(), Configuration.Provider {
    @Inject lateinit var surveySessionRecoveryUseCase: SurveySessionRecoveryUseCase

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var syncScheduler: SyncScheduler

    override fun onCreate() {
        super.onCreate()
        initMapLibre(this)
        CoroutineScope(Dispatchers.Default).launch { surveySessionRecoveryUseCase.recoverAny() }
        syncScheduler.schedulePeriodicSync()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
