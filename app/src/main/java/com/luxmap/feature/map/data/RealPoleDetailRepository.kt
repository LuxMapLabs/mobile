package com.luxmap.feature.map.data

import com.luxmap.core.network.MapApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealPoleDetailRepository
    @Inject
    constructor(
        private val mapApi: MapApi,
    ) : PoleDetailRepository {
        override fun observePoleDetail(poleId: String): Flow<PoleDetail?> =
            flow {
                try {
                    emit(mapApi.poleDetail(poleId).toPoleDetail())
                } catch (e: HttpException) {
                    if (e.code() == 404) emit(null) else throw e
                }
            }
    }
