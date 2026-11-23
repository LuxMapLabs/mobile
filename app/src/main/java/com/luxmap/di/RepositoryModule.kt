package com.luxmap.di

import com.luxmap.feature.auth.data.AuthRepository
import com.luxmap.feature.auth.data.RealAuthRepository
import com.luxmap.feature.home.data.HomeRepository
import com.luxmap.feature.home.data.RealHomeRepository
import com.luxmap.feature.map.data.MapRepository
import com.luxmap.feature.map.data.PoleDetailRepository
import com.luxmap.feature.map.data.RealMapRepository
import com.luxmap.feature.map.data.RealPoleDetailRepository
import com.luxmap.feature.notification.data.NotificationRepository
import com.luxmap.feature.notification.data.RealNotificationRepository
import com.luxmap.feature.profile.data.ProfileRepository
import com.luxmap.feature.profile.data.RealProfileRepository
import com.luxmap.feature.survey.data.LuxReadingRepository
import com.luxmap.feature.survey.data.PoleListRepository
import com.luxmap.feature.survey.data.PolePhotoRepository
import com.luxmap.feature.survey.data.RealLuxReadingRepository
import com.luxmap.feature.survey.data.RealPoleListRepository
import com.luxmap.feature.survey.data.RealPolePhotoRepository
import com.luxmap.feature.survey.data.RealSurveyRepository
import com.luxmap.feature.survey.data.SurveyRepository
import com.luxmap.feature.workorder.data.RealWorkOrderCompletionRepository
import com.luxmap.feature.workorder.data.RealWorkOrderDetailRepository
import com.luxmap.feature.workorder.data.RealWorkOrderDraftRepository
import com.luxmap.feature.workorder.data.WorkOrderCompletionRepository
import com.luxmap.feature.workorder.data.WorkOrderDetailRepository
import com.luxmap.feature.workorder.data.WorkOrderDraftRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// Bind the Real repository of each feature here, in one place
// (see "Repository Pattern" in CLAUDE.md).
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindWorkOrderDraftRepository(impl: RealWorkOrderDraftRepository): WorkOrderDraftRepository

    @Binds
    abstract fun bindMapRepository(impl: RealMapRepository): MapRepository

    @Binds
    abstract fun bindPoleDetailRepository(impl: RealPoleDetailRepository): PoleDetailRepository

    @Binds
    abstract fun bindAuthRepository(impl: RealAuthRepository): AuthRepository

    @Binds
    abstract fun bindHomeRepository(impl: RealHomeRepository): HomeRepository

    @Binds
    abstract fun bindSurveyRepository(impl: RealSurveyRepository): SurveyRepository

    @Binds
    abstract fun bindWorkOrderDetailRepository(impl: RealWorkOrderDetailRepository): WorkOrderDetailRepository

    @Binds
    abstract fun bindWorkOrderCompletionRepository(
        impl: RealWorkOrderCompletionRepository,
    ): WorkOrderCompletionRepository

    @Binds
    abstract fun bindPolePhotoRepository(impl: RealPolePhotoRepository): PolePhotoRepository

    @Binds
    abstract fun bindPoleListRepository(impl: RealPoleListRepository): PoleListRepository

    @Binds
    abstract fun bindLuxReadingRepository(impl: RealLuxReadingRepository): LuxReadingRepository

    @Binds
    abstract fun bindNotificationRepository(impl: RealNotificationRepository): NotificationRepository

    @Binds
    abstract fun bindProfileRepository(impl: RealProfileRepository): ProfileRepository
}
