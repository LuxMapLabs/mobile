package com.luxmap

import android.app.Application
import com.luxmap.core.map.initMapLibre
import com.luxmap.feature.survey.capture.SurveySessionRecoveryUseCase
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class LuxMapApp : Application() {
    // Hilt injects fields declared directly on a @HiltAndroidApp Application before onCreate() runs.
    @Inject lateinit var surveySessionRecoveryUseCase: SurveySessionRecoveryUseCase

    override fun onCreate() {
        super.onCreate()
        initMapLibre(this)
        CoroutineScope(Dispatchers.Default).launch { surveySessionRecoveryUseCase.recoverAny() }
    }
}
