package com.luxmap.di

import com.luxmap.feature.survey.capture.RealSurveyCaptureController
import com.luxmap.feature.survey.capture.SurveyCaptureController
import com.luxmap.feature.survey.domain.RealSurveyReadinessInputProvider
import com.luxmap.feature.survey.domain.SurveyReadinessInputProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// Bindings for the survey capture feature that are not data Repositories (per CLAUDE.md,
// RepositoryModule.kt is Fake/Real repository bindings only) — Task 18 adds
// SurveyCaptureController's binding to this same file.
@Module
@InstallIn(SingletonComponent::class)
abstract class CaptureModule {
    @Binds
    abstract fun bindSurveyReadinessInputProvider(impl: RealSurveyReadinessInputProvider): SurveyReadinessInputProvider

    @Binds
    abstract fun bindSurveyCaptureController(impl: RealSurveyCaptureController): SurveyCaptureController
}
