package com.luxmap.feature.survey.capture

import com.luxmap.feature.survey.data.dao.SurveySessionDao
import com.luxmap.feature.survey.data.entity.LocalSurveySessionEntity
import kotlinx.coroutines.CancellationException
import java.io.File
import java.time.Instant
import javax.inject.Inject

// Called exactly once, from LuxMapApp.onCreate() (Step 4 below) - not from the capture feature's
// own entry point, so recovery runs on every process start regardless of which screen opens first.
// A session stuck in "recording" means the app was killed mid-session without going through the
// normal "Dung quay" path - its last segment is unfinalized and MediaMuxer likely never closed it
// cleanly, so both its DB row and its file are dropped rather than trusted.
class SurveySessionRecoveryUseCase
    @Inject
    constructor(
        private val dao: SurveySessionDao,
        private val packager: PackageSurveySessionUseCase,
    ) {
        suspend fun recoverAny() {
            dao.sessionsInRecordingState().forEach { session ->
                // One stuck session must not stop recovery of the rest, and must not crash the
                // whole app: this runs from LuxMapApp.onCreate() in a scope with no exception
                // handler, so letting a throw escape here would kill the process on every future
                // startup too, since recovery is exactly the code path meant to run after a crash.
                try {
                    recoverOne(session)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    markPackageFailed(session)
                }
            }
        }

        private suspend fun recoverOne(session: LocalSurveySessionEntity) {
            // A session can be stuck with no segment at all if the app died before the first
            // one ever opened - nothing to drop in that case, just move the session forward.
            dao.unfinalizedSegmentFor(session.sessionId)?.let { unfinalized ->
                File(unfinalized.filePath).delete()
                dao.deleteSegment(unfinalized.segmentId)
            }

            dao.updateSession(session.copy(recordingState = "stopped", updatedAt = Instant.now()))

            when (packager.invoke(session.sessionId)) {
                is PackageResult.Success -> Unit // PackageSurveySessionUseCase already set recordingState = "packaged"
                is PackageResult.Failure -> markPackageFailed(session)
            }
        }

        // Best-effort: this is already the fallback path for a session that failed to recover
        // cleanly, so a second failure here (e.g. the DAO itself is broken) is swallowed rather
        // than allowed to take down recovery of any sessions still left in the list.
        private suspend fun markPackageFailed(session: LocalSurveySessionEntity) {
            try {
                dao.updateSession(session.copy(recordingState = "package_failed", updatedAt = Instant.now()))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                // Nothing more we can do - the session stays in whatever state it was last
                // successfully written to, and will be picked up again on the next app start.
            }
        }
    }
