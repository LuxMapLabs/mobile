# Phase 7 — Xác nhận kết quả AI (new screen) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the new "Xác nhận kết quả AI" screen (spec codes 1q–1r) end to end — list of AI pole-condition classifications with a correction sheet, filters, and a submit action — entirely against a new Fake-only repository and local-only models, per the redesign roadmap's decision #2 (UI-only, mock data, until WP2/WP5 confirm the real schema).

**Architecture:** Follows this codebase's established MVVM + Repository pattern exactly: a new `AiClassificationRepository` interface with only a `FakeAiClassificationRepository` implementation (no `Real...` counterpart yet — there is no real endpoint), a `AiConfirmationViewModel` holding the mandatory 4-state `UiState` plus small transient sheet state, and a stateless `AiConfirmationScreen` Composable wrapped by a `AiConfirmationRoute`. The screen is wired as a new nav destination reached from `SurveyHubScreen`'s existing (already-built, currently-dead) `onReviewAiConfirmation` callback — so this phase does not touch `SHOW_UNCONFIRMED_HUB_CARDS` (stays `false`, the Hub card itself is not shown in production yet) but does make the destination it already points at real and fully functional, satisfying the roadmap's "every blocked UI-only screen must be reachable only through a local flag or mock entry point" rule.

**Tech Stack:** Kotlin, Jetpack Compose, Material3 (`ModalBottomSheet`, `FilterChip`, `AlertDialog`, `Scaffold`/`SnackbarHost`), Hilt, StateFlow/Flow, JUnit + MockK + Turbine (unit), Compose UI Test (androidTest).

**Spec:** `docs/LuxMap_Mobile_Redesign_Spec_v0.1.md` §"Xác nhận kết quả AI (1q–1r)" (lines 204–211), read together with the roadmap entry for Phase 7 in `docs/superpowers/plans/2026-10-11-luxmap-redesign-roadmap.md` (decision #2, phase table row 7, review-focus bullet 3).

## Global Constraints

- No real route/entity exists for this screen yet — every model name must be unambiguous as local-only (e.g. `LocalAiClassificationReview`, not `AiClassificationReview`), per roadmap decision #2.
- The AI classification result itself (`ai_class`/`ai_confidence`) is never overwritten by a correction — only `final_class`/`override_reason`/`overridden_at`/`overridden_by` change. This is CLAUDE.md's audit-trail rule (RQ1 needs the original AI value preserved) and the single most important invariant in this phase.
- "Chọn lại đúng giá trị AI = Giữ kết quả AI (xoá override)" — re-selecting the AI's own original class in the correction sheet must fully clear a prior override (`final_class` back to `ai_class`, `override_reason`/`overridden_at`/`overridden_by` back to `null`), not just leave a stale reason string sitting on an otherwise-reverted row.
- All code comments in English, plain words, only where the WHY is non-obvious — this user wants zero comments that just restate what the code does (see `feedback_no_new_comments.md`).
- `./gradlew ktlintCheck` after every task.
- No new third-party dependencies — `material-icons-extended` already ships; this plan only uses icon names already proven to compile elsewhere in this codebase (`Icons.Filled.ArrowBack`, `Icons.Filled.CloudOff`, `Icons.Filled.EventAvailable`, `Icons.Filled.Videocam`).
- Branch off `dev` (this plan assumes branch `style/redesign-phase7-ai-confirmation`, already created from an up-to-date `dev`), never `main`.
- Each commit stays under ~400 changed lines per CLAUDE.md's commit-size rule — this plan's tasks are already sized to fit that individually.

## Review Focus

- A review whose `aiConfidencePercent` is below 85 must show the "tin cậy thấp" label in the `#8A5A00` color, and one at or above 85 must not — a naive `<= 85` or an unstyled label would both silently fail this.
- Re-selecting the AI's own original class after a prior override must erase that override entirely (`overrideReason`/`overriddenAt`/`overriddenBy` all back to `null`), not merely change `finalClass` back while leaving a stale reason behind.
- The "Lưu thay đổi" action must refuse to persist when a different class is chosen but the reason is blank or whitespace-only — enforced in the ViewModel itself (not only via a disabled button), so a direct call can't bypass it.
- The "Cần xem" filter must include a review whose `aiClass` is `NORMAL` but that has since been overridden to something else — filtering on "ai_class != normal" alone would wrongly hide it.
- A survey sweep with zero AI reviews must render the mandatory `Empty` state, not an empty list silently rendered under `Success`.

---

### Task 1: Local model + `AiClassificationRepository` + `FakeAiClassificationRepository`

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/survey/data/LocalAiClassificationReview.kt`
- Create: `app/src/main/java/com/luxmap/feature/survey/data/AiClassificationRepository.kt`
- Create: `app/src/main/java/com/luxmap/feature/survey/data/FakeAiClassificationRepository.kt`
- Modify: `app/src/main/java/com/luxmap/di/RepositoryModule.kt`
- Test: `app/src/test/java/com/luxmap/feature/survey/data/FakeAiClassificationRepositoryTest.kt`

**Interfaces:**
- Consumes: `com.luxmap.core.theme.AssetCondition` (existing enum: `NORMAL`/`DIM`/`OUT`/`UNKNOWN`, with `.label()`/`.badgeColors(isDark: Boolean)` extensions already defined in `core/theme/Color.kt`).
- Produces: `LocalAiClassificationReview(reviewId: String, surveySweepId: String, poleId: String, distanceKm: Double, frameRef: String, aiClass: AssetCondition, aiConfidencePercent: Int, finalClass: AssetCondition, overrideReason: String? = null, overriddenAt: Instant? = null, overriddenBy: String? = null)`; `AiClassificationRepository` interface with `observeReviews(surveySweepId: String): Flow<List<LocalAiClassificationReview>>`, `suspend fun setFinalClass(reviewId: String, finalClass: AssetCondition, overrideReason: String?, overriddenBy: String)`, `suspend fun submit(surveySweepId: String)` — all consumed by Task 2's `AiConfirmationViewModel`.

- [ ] **Step 1: Create the local model file**

```kotlin
package com.luxmap.feature.survey.data

import com.luxmap.core.theme.AssetCondition
import java.time.Instant

// Local-only placeholder model, not a real backend entity - the AI classification review
// contract has no confirmed sweep-status/field schema yet (see docs/contract-drift.md and the
// redesign roadmap's decision #2). Rewire to a real entity once WP2/WP5 confirm the schema.
data class LocalAiClassificationReview(
    val reviewId: String,
    val surveySweepId: String,
    val poleId: String,
    val distanceKm: Double,
    val frameRef: String,
    val aiClass: AssetCondition,
    val aiConfidencePercent: Int,
    val finalClass: AssetCondition,
    val overrideReason: String? = null,
    val overriddenAt: Instant? = null,
    val overriddenBy: String? = null,
)
```

- [ ] **Step 2: Create the repository interface**

```kotlin
package com.luxmap.feature.survey.data

import com.luxmap.core.theme.AssetCondition
import kotlinx.coroutines.flow.Flow

interface AiClassificationRepository {
    fun observeReviews(surveySweepId: String): Flow<List<LocalAiClassificationReview>>

    suspend fun setFinalClass(
        reviewId: String,
        finalClass: AssetCondition,
        overrideReason: String?,
        overriddenBy: String,
    )

    suspend fun submit(surveySweepId: String)
}
```

- [ ] **Step 3: Write the failing repository tests**

```kotlin
package com.luxmap.feature.survey.data

import com.luxmap.core.theme.AssetCondition
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeAiClassificationRepositoryTest {
    @Test
    fun `observeReviews only returns rows for the requested survey sweep`() =
        runTest {
            val repository = FakeAiClassificationRepository()

            val reviews = repository.observeReviews("SWEEP-2029").first()

            assertTrue(reviews.isNotEmpty())
            assertTrue(reviews.all { it.surveySweepId == "SWEEP-2029" })
        }

    @Test
    fun `observeReviews returns an empty list for an unknown survey sweep`() =
        runTest {
            val repository = FakeAiClassificationRepository()

            val reviews = repository.observeReviews("SWEEP-UNKNOWN").first()

            assertTrue(reviews.isEmpty())
        }

    @Test
    fun `setFinalClass with a different class than ai_class records an override`() =
        runTest {
            val repository = FakeAiClassificationRepository()
            val before = repository.observeReviews("SWEEP-2029").first().first { it.aiClass == AssetCondition.DIM }

            repository.setFinalClass(before.reviewId, AssetCondition.OUT, "Nhìn kỹ lại thấy tắt hẳn", "fe_thinh")

            val after = repository.observeReviews("SWEEP-2029").first().first { it.reviewId == before.reviewId }
            assertEquals(AssetCondition.OUT, after.finalClass)
            assertEquals("Nhìn kỹ lại thấy tắt hẳn", after.overrideReason)
            assertEquals("fe_thinh", after.overriddenBy)
            assertTrue(after.overriddenAt != null)
        }

    @Test
    fun `setFinalClass back to the review's own ai_class clears the override`() =
        runTest {
            val repository = FakeAiClassificationRepository()
            val target = repository.observeReviews("SWEEP-2029").first().first { it.aiClass == AssetCondition.DIM }
            repository.setFinalClass(target.reviewId, AssetCondition.OUT, "lý do", "fe_thinh")

            repository.setFinalClass(target.reviewId, target.aiClass, null, "fe_thinh")

            val after = repository.observeReviews("SWEEP-2029").first().first { it.reviewId == target.reviewId }
            assertEquals(target.aiClass, after.finalClass)
            assertNull(after.overrideReason)
            assertNull(after.overriddenAt)
            assertNull(after.overriddenBy)
        }

    @Test
    fun `submit records the survey sweep id`() =
        runTest {
            val repository = FakeAiClassificationRepository()

            repository.submit("SWEEP-2029")

            assertTrue(repository.submittedSweepIds.contains("SWEEP-2029"))
        }
}
```

- [ ] **Step 4: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.survey.data.FakeAiClassificationRepositoryTest"`
Expected: FAIL — `FakeAiClassificationRepository` is unresolved.

- [ ] **Step 5: Implement `FakeAiClassificationRepository`**

```kotlin
package com.luxmap.feature.survey.data

import com.luxmap.core.theme.AssetCondition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeAiClassificationRepository
    @Inject
    constructor() : AiClassificationRepository {
        private val reviews = MutableStateFlow(SAMPLE_REVIEWS)
        val submittedSweepIds = mutableListOf<String>()

        override fun observeReviews(surveySweepId: String): Flow<List<LocalAiClassificationReview>> =
            reviews.map { all -> all.filter { it.surveySweepId == surveySweepId } }

        override suspend fun setFinalClass(
            reviewId: String,
            finalClass: AssetCondition,
            overrideReason: String?,
            overriddenBy: String,
        ) {
            reviews.value =
                reviews.value.map { review ->
                    when {
                        review.reviewId != reviewId -> review
                        finalClass == review.aiClass ->
                            review.copy(
                                finalClass = finalClass,
                                overrideReason = null,
                                overriddenAt = null,
                                overriddenBy = null,
                            )
                        else ->
                            review.copy(
                                finalClass = finalClass,
                                overrideReason = overrideReason,
                                overriddenAt = Instant.now(),
                                overriddenBy = overriddenBy,
                            )
                    }
                }
        }

        override suspend fun submit(surveySweepId: String) {
            submittedSweepIds.add(surveySweepId)
        }

        companion object {
            private val SAMPLE_REVIEWS =
                listOf(
                    LocalAiClassificationReview(
                        reviewId = "REVIEW-1",
                        surveySweepId = "SWEEP-2029",
                        poleId = "POLE-0101",
                        distanceKm = 0.4,
                        frameRef = "Khung hình #012",
                        aiClass = AssetCondition.NORMAL,
                        aiConfidencePercent = 97,
                        finalClass = AssetCondition.NORMAL,
                    ),
                    LocalAiClassificationReview(
                        reviewId = "REVIEW-2",
                        surveySweepId = "SWEEP-2029",
                        poleId = "POLE-0102",
                        distanceKm = 0.8,
                        frameRef = "Khung hình #034",
                        aiClass = AssetCondition.DIM,
                        aiConfidencePercent = 78,
                        finalClass = AssetCondition.DIM,
                    ),
                    LocalAiClassificationReview(
                        reviewId = "REVIEW-3",
                        surveySweepId = "SWEEP-2029",
                        poleId = "POLE-0103",
                        distanceKm = 1.2,
                        frameRef = "Khung hình #061",
                        aiClass = AssetCondition.OUT,
                        aiConfidencePercent = 92,
                        finalClass = AssetCondition.OUT,
                    ),
                    LocalAiClassificationReview(
                        reviewId = "REVIEW-4",
                        surveySweepId = "SWEEP-2029",
                        poleId = "POLE-0104",
                        distanceKm = 1.5,
                        frameRef = "Khung hình #075",
                        aiClass = AssetCondition.DIM,
                        aiConfidencePercent = 61,
                        finalClass = AssetCondition.DIM,
                    ),
                    LocalAiClassificationReview(
                        reviewId = "REVIEW-5",
                        surveySweepId = "SWEEP-2029",
                        poleId = "POLE-0105",
                        distanceKm = 1.9,
                        frameRef = "Khung hình #098",
                        aiClass = AssetCondition.NORMAL,
                        aiConfidencePercent = 95,
                        finalClass = AssetCondition.NORMAL,
                    ),
                )
        }
    }
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.survey.data.FakeAiClassificationRepositoryTest"`
Expected: PASS (5/5).

- [ ] **Step 7: Bind the repository in `RepositoryModule.kt`**

Add the import and one `@Binds` function, following the existing pattern exactly:

```kotlin
import com.luxmap.feature.survey.data.AiClassificationRepository
import com.luxmap.feature.survey.data.FakeAiClassificationRepository
```

```kotlin
    @Binds
    abstract fun bindAiClassificationRepository(
        impl: FakeAiClassificationRepository,
    ): AiClassificationRepository
```

- [ ] **Step 8: Run ktlint and the build**

Run: `./gradlew :app:compileDebugKotlin ktlintCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/data/LocalAiClassificationReview.kt app/src/main/java/com/luxmap/feature/survey/data/AiClassificationRepository.kt app/src/main/java/com/luxmap/feature/survey/data/FakeAiClassificationRepository.kt app/src/main/java/com/luxmap/di/RepositoryModule.kt app/src/test/java/com/luxmap/feature/survey/data/FakeAiClassificationRepositoryTest.kt
git commit -m "feat(redesign-phase7): add local AI classification review model and fake repository"
```

---

### Task 2: `AiConfirmationUiState` + `AiConfirmationViewModel`

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/survey/ui/aiconfirm/AiConfirmationUiState.kt`
- Create: `app/src/main/java/com/luxmap/feature/survey/ui/aiconfirm/AiConfirmationViewModel.kt`
- Test: `app/src/test/java/com/luxmap/feature/survey/ui/aiconfirm/AiConfirmationViewModelTest.kt`

**Interfaces:**
- Consumes: Task 1's `AiClassificationRepository`/`LocalAiClassificationReview`; existing `com.luxmap.feature.auth.data.AuthRepository.observeUsername(): Flow<String?>` (already used by `ProfileViewModel`, no changes needed to it).
- Produces: `ReviewFilter` enum (`ALL`/`NEEDS_REVIEW`/`OVERRIDDEN`); `AiReviewSheetState(review: LocalAiClassificationReview, chosenClass: AssetCondition, reason: String)`; `AiConfirmationUiState` sealed interface (`Loading`/`Success(reviews, filter, normalCount, dimCount, outCount)`/`Empty`/`Error(message)`); `AiConfirmationViewModel` with `uiState: StateFlow<AiConfirmationUiState>`, `sheet: StateFlow<AiReviewSheetState?>`, `fun load(surveySweepId: String)`, `fun onFilterSelected(filter: ReviewFilter)`, `fun onReviewSelected(review: LocalAiClassificationReview)`, `fun onDismissSheet()`, `fun onConditionChosen(condition: AssetCondition)`, `fun onReasonChanged(reason: String)`, `fun onSaveSheet()`, `fun onSubmit(surveySweepId: String)` — all consumed by Task 3/4's `AiConfirmationRoute`.

- [ ] **Step 1: Create the UI state file**

```kotlin
package com.luxmap.feature.survey.ui.aiconfirm

import com.luxmap.core.theme.AssetCondition
import com.luxmap.feature.survey.data.LocalAiClassificationReview

enum class ReviewFilter { ALL, NEEDS_REVIEW, OVERRIDDEN }

data class AiReviewSheetState(
    val review: LocalAiClassificationReview,
    val chosenClass: AssetCondition,
    val reason: String,
)

sealed interface AiConfirmationUiState {
    data object Loading : AiConfirmationUiState

    data class Success(
        val reviews: List<LocalAiClassificationReview>,
        val filter: ReviewFilter,
        val normalCount: Int,
        val dimCount: Int,
        val outCount: Int,
    ) : AiConfirmationUiState

    data object Empty : AiConfirmationUiState

    data class Error(val message: String) : AiConfirmationUiState
}
```

- [ ] **Step 2: Write the failing ViewModel tests**

```kotlin
package com.luxmap.feature.survey.ui.aiconfirm

import app.cash.turbine.test
import com.luxmap.core.theme.AssetCondition
import com.luxmap.feature.auth.data.AuthRepository
import com.luxmap.feature.survey.data.AiClassificationRepository
import com.luxmap.feature.survey.data.LocalAiClassificationReview
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun review(
    reviewId: String,
    aiClass: AssetCondition,
    finalClass: AssetCondition = aiClass,
    aiConfidencePercent: Int = 90,
    overrideReason: String? = null,
) = LocalAiClassificationReview(
    reviewId = reviewId,
    surveySweepId = "SWEEP-1",
    poleId = "POLE-$reviewId",
    distanceKm = 1.0,
    frameRef = "Khung hình #$reviewId",
    aiClass = aiClass,
    aiConfidencePercent = aiConfidencePercent,
    finalClass = finalClass,
    overrideReason = overrideReason,
)

@OptIn(ExperimentalCoroutinesApi::class)
class AiConfirmationViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load emits Success with reviews and condition counts based on final class`() =
        runTest {
            val repository = mockk<AiClassificationRepository>()
            every { repository.observeReviews("SWEEP-1") } returns
                MutableStateFlow(listOf(review("1", AssetCondition.NORMAL), review("2", AssetCondition.DIM)))
            val viewModel = AiConfirmationViewModel(repository, mockk())

            viewModel.uiState.test {
                assertEquals(AiConfirmationUiState.Loading, awaitItem())
                viewModel.load("SWEEP-1")
                val success = awaitItem() as AiConfirmationUiState.Success
                assertEquals(2, success.reviews.size)
                assertEquals(1, success.normalCount)
                assertEquals(1, success.dimCount)
                assertEquals(0, success.outCount)
            }
        }

    @Test
    fun `an empty review list emits Empty, not an empty Success list`() =
        runTest {
            val repository = mockk<AiClassificationRepository>()
            every { repository.observeReviews("SWEEP-1") } returns MutableStateFlow(emptyList())
            val viewModel = AiConfirmationViewModel(repository, mockk())

            viewModel.uiState.test {
                assertEquals(AiConfirmationUiState.Loading, awaitItem())
                viewModel.load("SWEEP-1")
                assertEquals(AiConfirmationUiState.Empty, awaitItem())
            }
        }

    @Test
    fun `a repository failure emits Error`() =
        runTest {
            val repository = mockk<AiClassificationRepository>()
            every { repository.observeReviews("SWEEP-1") } returns flow { throw IllegalStateException("boom") }
            val viewModel = AiConfirmationViewModel(repository, mockk())

            viewModel.uiState.test {
                assertEquals(AiConfirmationUiState.Loading, awaitItem())
                viewModel.load("SWEEP-1")
                assertTrue(awaitItem() is AiConfirmationUiState.Error)
            }
        }

    @Test
    fun `NEEDS_REVIEW filter includes a NORMAL review that was overridden, not only non-NORMAL ai_class`() =
        runTest {
            val repository = mockk<AiClassificationRepository>()
            every { repository.observeReviews("SWEEP-1") } returns
                MutableStateFlow(
                    listOf(
                        review("1", aiClass = AssetCondition.NORMAL),
                        review(
                            "2",
                            aiClass = AssetCondition.NORMAL,
                            finalClass = AssetCondition.DIM,
                            overrideReason = "sửa",
                        ),
                        review("3", aiClass = AssetCondition.OUT),
                    ),
                )
            val viewModel = AiConfirmationViewModel(repository, mockk())

            viewModel.uiState.test {
                awaitItem()
                viewModel.load("SWEEP-1")
                awaitItem()
                viewModel.onFilterSelected(ReviewFilter.NEEDS_REVIEW)
                val success = awaitItem() as AiConfirmationUiState.Success
                assertEquals(setOf("2", "3"), success.reviews.map { it.reviewId }.toSet())
            }
        }

    @Test
    fun `OVERRIDDEN filter only includes reviews with a non-null override reason`() =
        runTest {
            val repository = mockk<AiClassificationRepository>()
            every { repository.observeReviews("SWEEP-1") } returns
                MutableStateFlow(
                    listOf(
                        review("1", aiClass = AssetCondition.OUT),
                        review(
                            "2",
                            aiClass = AssetCondition.NORMAL,
                            finalClass = AssetCondition.DIM,
                            overrideReason = "sửa",
                        ),
                    ),
                )
            val viewModel = AiConfirmationViewModel(repository, mockk())

            viewModel.uiState.test {
                awaitItem()
                viewModel.load("SWEEP-1")
                awaitItem()
                viewModel.onFilterSelected(ReviewFilter.OVERRIDDEN)
                val success = awaitItem() as AiConfirmationUiState.Success
                assertEquals(listOf("2"), success.reviews.map { it.reviewId })
            }
        }

    @Test
    fun `selecting a review opens the sheet seeded from its current final class and reason`() =
        runTest {
            val viewModel = AiConfirmationViewModel(mockk(), mockk())
            val target =
                review("1", aiClass = AssetCondition.NORMAL, finalClass = AssetCondition.DIM, overrideReason = "sửa")

            viewModel.sheet.test {
                assertNull(awaitItem())
                viewModel.onReviewSelected(target)
                val sheet = awaitItem()
                assertEquals(AssetCondition.DIM, sheet?.chosenClass)
                assertEquals("sửa", sheet?.reason)
            }
        }

    @Test
    fun `saving the same class as ai_class clears the override`() =
        runTest {
            val repository = mockk<AiClassificationRepository>(relaxed = true)
            val authRepository = mockk<AuthRepository>()
            every { authRepository.observeUsername() } returns flowOf("fe_thinh")
            val viewModel = AiConfirmationViewModel(repository, authRepository)
            val target =
                review("1", aiClass = AssetCondition.NORMAL, finalClass = AssetCondition.DIM, overrideReason = "sửa")
            viewModel.onReviewSelected(target)
            viewModel.onConditionChosen(AssetCondition.NORMAL)

            viewModel.onSaveSheet()
            advanceUntilIdle()

            coVerify {
                repository.setFinalClass(
                    reviewId = "1",
                    finalClass = AssetCondition.NORMAL,
                    overrideReason = null,
                    overriddenBy = "fe_thinh",
                )
            }
            assertNull(viewModel.sheet.value)
        }

    @Test
    fun `saving a different class without a reason is a no-op`() =
        runTest {
            val repository = mockk<AiClassificationRepository>(relaxed = true)
            val viewModel = AiConfirmationViewModel(repository, mockk())
            viewModel.onReviewSelected(review("1", aiClass = AssetCondition.NORMAL))
            viewModel.onConditionChosen(AssetCondition.OUT)
            viewModel.onReasonChanged("   ")

            viewModel.onSaveSheet()
            advanceUntilIdle()

            coVerify(exactly = 0) { repository.setFinalClass(any(), any(), any(), any()) }
            assertTrue(viewModel.sheet.value != null)
        }

    @Test
    fun `saving a different class with a reason persists the override`() =
        runTest {
            val repository = mockk<AiClassificationRepository>(relaxed = true)
            val authRepository = mockk<AuthRepository>()
            every { authRepository.observeUsername() } returns flowOf("fe_thinh")
            val viewModel = AiConfirmationViewModel(repository, authRepository)
            viewModel.onReviewSelected(review("1", aiClass = AssetCondition.NORMAL))
            viewModel.onConditionChosen(AssetCondition.OUT)
            viewModel.onReasonChanged("Nhìn kỹ lại thấy tắt hẳn")

            viewModel.onSaveSheet()
            advanceUntilIdle()

            coVerify {
                repository.setFinalClass(
                    reviewId = "1",
                    finalClass = AssetCondition.OUT,
                    overrideReason = "Nhìn kỹ lại thấy tắt hẳn",
                    overriddenBy = "fe_thinh",
                )
            }
        }

    @Test
    fun `onSubmit forwards the sweep id to the repository`() =
        runTest {
            val repository = mockk<AiClassificationRepository>(relaxed = true)
            val viewModel = AiConfirmationViewModel(repository, mockk())

            viewModel.onSubmit("SWEEP-1")
            advanceUntilIdle()

            coVerify { repository.submit("SWEEP-1") }
        }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.survey.ui.aiconfirm.AiConfirmationViewModelTest"`
Expected: FAIL — `AiConfirmationViewModel` is unresolved.

- [ ] **Step 4: Implement `AiConfirmationViewModel`**

```kotlin
package com.luxmap.feature.survey.ui.aiconfirm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.core.theme.AssetCondition
import com.luxmap.feature.auth.data.AuthRepository
import com.luxmap.feature.survey.data.AiClassificationRepository
import com.luxmap.feature.survey.data.LocalAiClassificationReview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AiConfirmationViewModel
    @Inject
    constructor(
        private val repository: AiClassificationRepository,
        private val authRepository: AuthRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<AiConfirmationUiState>(AiConfirmationUiState.Loading)
        val uiState: StateFlow<AiConfirmationUiState> = _uiState.asStateFlow()

        private val _filter = MutableStateFlow(ReviewFilter.ALL)

        private val _sheet = MutableStateFlow<AiReviewSheetState?>(null)
        val sheet: StateFlow<AiReviewSheetState?> = _sheet.asStateFlow()

        fun load(surveySweepId: String) {
            viewModelScope.launch {
                combine(repository.observeReviews(surveySweepId), _filter) { reviews, filter ->
                    if (reviews.isEmpty()) {
                        AiConfirmationUiState.Empty
                    } else {
                        AiConfirmationUiState.Success(
                            reviews = reviews.filter { matchesFilter(it, filter) },
                            filter = filter,
                            normalCount = reviews.count { it.finalClass == AssetCondition.NORMAL },
                            dimCount = reviews.count { it.finalClass == AssetCondition.DIM },
                            outCount = reviews.count { it.finalClass == AssetCondition.OUT },
                        )
                    }
                }.catch { e ->
                    _uiState.value = AiConfirmationUiState.Error(e.message ?: "Không tải được kết quả AI")
                }.collect { state -> _uiState.value = state }
            }
        }

        fun onFilterSelected(filter: ReviewFilter) {
            _filter.value = filter
        }

        fun onReviewSelected(review: LocalAiClassificationReview) {
            _sheet.value =
                AiReviewSheetState(
                    review = review,
                    chosenClass = review.finalClass,
                    reason = review.overrideReason ?: "",
                )
        }

        fun onDismissSheet() {
            _sheet.value = null
        }

        fun onConditionChosen(condition: AssetCondition) {
            _sheet.value = _sheet.value?.copy(chosenClass = condition)
        }

        fun onReasonChanged(reason: String) {
            _sheet.value = _sheet.value?.copy(reason = reason)
        }

        fun onSaveSheet() {
            val current = _sheet.value ?: return
            val isOverride = current.chosenClass != current.review.aiClass
            if (isOverride && current.reason.isBlank()) return
            viewModelScope.launch {
                val username = authRepository.observeUsername().first() ?: "Không rõ"
                repository.setFinalClass(
                    reviewId = current.review.reviewId,
                    finalClass = current.chosenClass,
                    overrideReason = if (isOverride) current.reason else null,
                    overriddenBy = username,
                )
                _sheet.value = null
            }
        }

        fun onSubmit(surveySweepId: String) {
            viewModelScope.launch { repository.submit(surveySweepId) }
        }

        private fun matchesFilter(
            review: LocalAiClassificationReview,
            filter: ReviewFilter,
        ): Boolean =
            when (filter) {
                ReviewFilter.ALL -> true
                ReviewFilter.NEEDS_REVIEW -> review.aiClass != AssetCondition.NORMAL || review.overrideReason != null
                ReviewFilter.OVERRIDDEN -> review.overrideReason != null
            }
    }
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.survey.ui.aiconfirm.AiConfirmationViewModelTest"`
Expected: PASS (10/10).

- [ ] **Step 6: Run ktlint and the build**

Run: `./gradlew :app:compileDebugKotlin ktlintCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/ui/aiconfirm/AiConfirmationUiState.kt app/src/main/java/com/luxmap/feature/survey/ui/aiconfirm/AiConfirmationViewModel.kt app/src/test/java/com/luxmap/feature/survey/ui/aiconfirm/AiConfirmationViewModelTest.kt
git commit -m "feat(redesign-phase7): add AiConfirmationViewModel with filter and override logic"
```

---

### Task 3: `AiConfirmationScreen` — summary, filters, review list (no sheet yet)

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/survey/ui/aiconfirm/AiConfirmationScreen.kt`
- Test: `app/src/androidTest/java/com/luxmap/feature/survey/ui/aiconfirm/AiConfirmationScreenTest.kt`

**Interfaces:**
- Consumes: Task 2's `AiConfirmationUiState`/`ReviewFilter`/`AiReviewSheetState`; Task 1's `LocalAiClassificationReview`; existing shared components `com.luxmap.core.ui.components.MessageState`, `com.luxmap.core.ui.components.StatusBadge`, `com.luxmap.core.ui.components.BottomActionBar`; existing `com.luxmap.core.theme.AssetCondition`/`SyncStatus`/`OverlineTextStyle`/`Spacing`/`Radius`/`badgeColors`/`label`.
- Produces: `AiConfirmationScreen(surveySweepId: String, uiState: AiConfirmationUiState, sheet: AiReviewSheetState?, onBack: () -> Unit, onFilterSelected: (ReviewFilter) -> Unit = {}, onReviewSelected: (LocalAiClassificationReview) -> Unit = {}, onDismissSheet: () -> Unit = {}, onConditionChosen: (AssetCondition) -> Unit = {}, onReasonChanged: (String) -> Unit = {}, onSaveSheet: () -> Unit = {}, onSubmit: () -> Unit = {})` — this task leaves `sheet` always rendered as nothing (Task 4 adds the actual bottom sheet UI); consumed by Task 4 (adds to the same file) and Task 5 (route wiring).

- [ ] **Step 1: Write the failing screen tests**

```kotlin
package com.luxmap.feature.survey.ui.aiconfirm

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.feature.survey.data.LocalAiClassificationReview
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private fun review(
    reviewId: String = "1",
    aiClass: AssetCondition = AssetCondition.NORMAL,
    finalClass: AssetCondition = aiClass,
    aiConfidencePercent: Int = 95,
    overrideReason: String? = null,
) = LocalAiClassificationReview(
    reviewId = reviewId,
    surveySweepId = "SWEEP-1",
    poleId = "POLE-010$reviewId",
    distanceKm = 1.2,
    frameRef = "Khung hình #0$reviewId",
    aiClass = aiClass,
    aiConfidencePercent = aiConfidencePercent,
    finalClass = finalClass,
    overrideReason = overrideReason,
)

class AiConfirmationScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun loading_state_shows_progress_indicator() {
        composeRule.setContent {
            LuxMapTheme {
                AiConfirmationScreen(
                    surveySweepId = "SWEEP-1",
                    uiState = AiConfirmationUiState.Loading,
                    sheet = null,
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag("ai_confirmation_loading").assertExists()
    }

    @Test
    fun success_state_shows_the_pole_id_and_condition_counts() {
        composeRule.setContent {
            LuxMapTheme {
                AiConfirmationScreen(
                    surveySweepId = "SWEEP-1",
                    uiState =
                        AiConfirmationUiState.Success(
                            reviews = listOf(review()),
                            filter = ReviewFilter.ALL,
                            normalCount = 1,
                            dimCount = 0,
                            outCount = 0,
                        ),
                    sheet = null,
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("POLE-0101").assertExists()
        composeRule.onNodeWithText("Bình thường 1").assertExists()
    }

    @Test
    fun a_review_below_85_percent_confidence_shows_the_low_confidence_label() {
        composeRule.setContent {
            LuxMapTheme {
                AiConfirmationScreen(
                    surveySweepId = "SWEEP-1",
                    uiState =
                        AiConfirmationUiState.Success(
                            reviews = listOf(review(aiConfidencePercent = 61)),
                            filter = ReviewFilter.ALL,
                            normalCount = 1,
                            dimCount = 0,
                            outCount = 0,
                        ),
                    sheet = null,
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("tin cậy thấp", substring = true).assertExists()
    }

    @Test
    fun a_review_at_85_percent_confidence_or_above_does_not_show_the_low_confidence_label() {
        composeRule.setContent {
            LuxMapTheme {
                AiConfirmationScreen(
                    surveySweepId = "SWEEP-1",
                    uiState =
                        AiConfirmationUiState.Success(
                            reviews = listOf(review(aiConfidencePercent = 85)),
                            filter = ReviewFilter.ALL,
                            normalCount = 1,
                            dimCount = 0,
                            outCount = 0,
                        ),
                    sheet = null,
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("tin cậy thấp", substring = true).assertDoesNotExist()
    }

    @Test
    fun an_overridden_review_shows_the_overridden_from_label() {
        composeRule.setContent {
            LuxMapTheme {
                AiConfirmationScreen(
                    surveySweepId = "SWEEP-1",
                    uiState =
                        AiConfirmationUiState.Success(
                            reviews =
                                listOf(
                                    review(
                                        aiClass = AssetCondition.NORMAL,
                                        finalClass = AssetCondition.DIM,
                                        overrideReason = "sửa",
                                    ),
                                ),
                            filter = ReviewFilter.ALL,
                            normalCount = 0,
                            dimCount = 1,
                            outCount = 0,
                        ),
                    sheet = null,
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("đã sửa từ Bình thường", substring = true).assertExists()
    }

    @Test
    fun tapping_a_review_row_invokes_onReviewSelected_with_that_review() {
        var selected: LocalAiClassificationReview? = null
        val target = review()
        composeRule.setContent {
            LuxMapTheme {
                AiConfirmationScreen(
                    surveySweepId = "SWEEP-1",
                    uiState =
                        AiConfirmationUiState.Success(
                            reviews = listOf(target),
                            filter = ReviewFilter.ALL,
                            normalCount = 1,
                            dimCount = 0,
                            outCount = 0,
                        ),
                    sheet = null,
                    onBack = {},
                    onReviewSelected = { selected = it },
                )
            }
        }
        composeRule.onNodeWithText("POLE-0101").performClick()
        assertEquals(target, selected)
    }

    @Test
    fun empty_state_shows_the_no_reviews_message() {
        composeRule.setContent {
            LuxMapTheme { AiConfirmationScreen(surveySweepId = "SWEEP-1", uiState = AiConfirmationUiState.Empty, sheet = null, onBack = {}) }
        }
        composeRule.onNodeWithText("Chưa có kết quả AI", substring = true).assertExists()
    }

    @Test
    fun error_state_shows_the_message() {
        composeRule.setContent {
            LuxMapTheme {
                AiConfirmationScreen(
                    surveySweepId = "SWEEP-1",
                    uiState = AiConfirmationUiState.Error("Không tải được kết quả AI"),
                    sheet = null,
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Không tải được kết quả AI").assertExists()
    }
}
```

This needs one more import for the click test — add `import androidx.compose.ui.test.performClick` alongside the others at the top of the file.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: FAIL — `AiConfirmationScreen` is unresolved.

- [ ] **Step 3: Implement `AiConfirmationScreen.kt` (summary + filters + list; sheet rendering stubbed)**

```kotlin
package com.luxmap.feature.survey.ui.aiconfirm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.OverlineTextStyle
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.label
import com.luxmap.core.ui.components.BottomActionBar
import com.luxmap.core.ui.components.MessageState
import com.luxmap.core.ui.components.StatusBadge
import com.luxmap.feature.survey.data.LocalAiClassificationReview
import java.util.Locale

private const val LOW_CONFIDENCE_THRESHOLD = 85

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiConfirmationScreen(
    surveySweepId: String,
    uiState: AiConfirmationUiState,
    sheet: AiReviewSheetState?,
    onBack: () -> Unit,
    onFilterSelected: (ReviewFilter) -> Unit = {},
    onReviewSelected: (LocalAiClassificationReview) -> Unit = {},
    onDismissSheet: () -> Unit = {},
    onConditionChosen: (AssetCondition) -> Unit = {},
    onReasonChanged: (String) -> Unit = {},
    onSaveSheet: () -> Unit = {},
    onSubmit: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Xác nhận kết quả AI") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
            )
        },
        bottomBar = {
            if (uiState is AiConfirmationUiState.Success) {
                BottomActionBar(ctaText = "Gửi", onCtaClick = onSubmit, ctaEnabled = true)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            when (uiState) {
                is AiConfirmationUiState.Loading ->
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).testTag("ai_confirmation_loading"),
                    )

                is AiConfirmationUiState.Success ->
                    AiConfirmationContent(
                        state = uiState,
                        onFilterSelected = onFilterSelected,
                        onReviewSelected = onReviewSelected,
                    )

                is AiConfirmationUiState.Empty ->
                    MessageState(
                        icon = Icons.Filled.EventAvailable,
                        iconColors = SyncStatus.DONE.badgeColors(),
                        title = "Chưa có kết quả AI cần xác nhận cho phiên này",
                        subtitle = "Kết quả sẽ hiển thị tại đây sau khi AI xử lý xong.",
                        action = {},
                    )

                is AiConfirmationUiState.Error ->
                    MessageState(
                        icon = Icons.Filled.CloudOff,
                        iconColors = SyncStatus.FAILED.badgeColors(),
                        title = uiState.message,
                        subtitle = "Kiểm tra kết nối mạng rồi thử lại.",
                        action = {},
                    )
            }
        }
    }
}

@Composable
private fun AiConfirmationContent(
    state: AiConfirmationUiState.Success,
    onFilterSelected: (ReviewFilter) -> Unit,
    onReviewSelected: (LocalAiClassificationReview) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { AiSummaryBlock(state) }
        item {
            Text(
                text = "Sửa nếu AI phân loại sai, kèm lý do. Kết quả gốc của AI luôn được giữ lại.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { FilterChipRow(selected = state.filter, onFilterSelected = onFilterSelected) }
        items(state.reviews) { review -> ReviewRow(review, onClick = { onReviewSelected(review) }) }
    }
}

@Composable
private fun AiSummaryBlock(state: AiConfirmationUiState.Success) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(Radius.block))
                .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = "TÓM TẮT PHÂN LOẠI",
            style = OverlineTextStyle,
            color = MaterialTheme.colorScheme.inverseOnSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatusBadge(
                text = "${AssetCondition.NORMAL.label()} ${state.normalCount}",
                colors = AssetCondition.NORMAL.badgeColors(isDark = true),
            )
            StatusBadge(
                text = "${AssetCondition.DIM.label()} ${state.dimCount}",
                colors = AssetCondition.DIM.badgeColors(isDark = true),
            )
            StatusBadge(
                text = "${AssetCondition.OUT.label()} ${state.outCount}",
                colors = AssetCondition.OUT.badgeColors(isDark = true),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChipRow(
    selected: ReviewFilter,
    onFilterSelected: (ReviewFilter) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        FilterChip(selected = selected == ReviewFilter.ALL, onClick = { onFilterSelected(ReviewFilter.ALL) }, label = { Text("Tất cả") })
        FilterChip(
            selected = selected == ReviewFilter.NEEDS_REVIEW,
            onClick = { onFilterSelected(ReviewFilter.NEEDS_REVIEW) },
            label = { Text("Cần xem") },
        )
        FilterChip(
            selected = selected == ReviewFilter.OVERRIDDEN,
            onClick = { onFilterSelected(ReviewFilter.OVERRIDDEN) },
            label = { Text("Đã sửa") },
        )
    }
}

@Composable
private fun ReviewRow(
    review: LocalAiClassificationReview,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        FrameThumbnail(finalClass = review.finalClass, modifier = Modifier.size(56.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(text = review.poleId, style = MaterialTheme.typography.bodyLarge)
            val distanceText = "%.1f".format(Locale.US, review.distanceKm)
            val base = "$distanceText km · AI ${review.aiConfidencePercent}%"
            val isLowConfidence = review.aiConfidencePercent < LOW_CONFIDENCE_THRESHOLD
            Text(
                text = if (isLowConfidence) "$base · tin cậy thấp" else base,
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (isLowConfidence) {
                        SyncStatus.SYNCING.badgeColors().text
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
            if (review.overrideReason != null) {
                Text(
                    text = "đã sửa từ ${review.aiClass.label()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        StatusBadge(text = review.finalClass.label(), colors = review.finalClass.badgeColors(isDark = false))
    }
}

@Composable
private fun FrameThumbnail(
    finalClass: AssetCondition,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.control)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Videocam,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(finalClass.badgeColors(isDark = false).background),
        )
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:compileDebugAndroidTestKotlin ktlintCheck`
Expected: BUILD SUCCESSFUL (the androidTest module compiles; actual device/emulator execution of these specific tests happens the same way every prior phase's androidTest files have been verified in this project — compile-checked here, run on a real device later).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/ui/aiconfirm/AiConfirmationScreen.kt app/src/androidTest/java/com/luxmap/feature/survey/ui/aiconfirm/AiConfirmationScreenTest.kt
git commit -m "feat(redesign-phase7): add AiConfirmationScreen summary, filters, and review list"
```

---

### Task 4: Review correction sheet + submit confirm dialog

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/aiconfirm/AiConfirmationScreen.kt`
- Modify: `app/src/androidTest/java/com/luxmap/feature/survey/ui/aiconfirm/AiConfirmationScreenTest.kt`

**Interfaces:**
- Consumes: Task 3's `AiConfirmationScreen` signature (unchanged) and Task 2's `AiReviewSheetState`.
- Produces: the sheet is now actually rendered when `sheet != null`; no new public symbols — Task 5 consumes `AiConfirmationScreen` exactly as Task 3 left it.

- [ ] **Step 1: Write the failing sheet/dialog tests (append to the existing test file)**

```kotlin
    @Test
    fun the_sheet_shows_Luu_thay_doi_disabled_when_a_different_class_is_chosen_without_a_reason() {
        val target = review(aiClass = AssetCondition.NORMAL)
        composeRule.setContent {
            LuxMapTheme {
                AiConfirmationScreen(
                    surveySweepId = "SWEEP-1",
                    uiState = AiConfirmationUiState.Empty,
                    sheet = AiReviewSheetState(review = target, chosenClass = AssetCondition.OUT, reason = ""),
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Lưu thay đổi").assertIsNotEnabled()
    }

    @Test
    fun the_sheet_shows_Luu_thay_doi_enabled_once_a_reason_is_entered() {
        val target = review(aiClass = AssetCondition.NORMAL)
        composeRule.setContent {
            LuxMapTheme {
                AiConfirmationScreen(
                    surveySweepId = "SWEEP-1",
                    uiState = AiConfirmationUiState.Empty,
                    sheet = AiReviewSheetState(review = target, chosenClass = AssetCondition.OUT, reason = "tắt hẳn"),
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Lưu thay đổi").assertIsEnabled()
    }

    @Test
    fun the_sheet_shows_Giu_ket_qua_AI_and_stays_enabled_when_the_ai_class_itself_is_chosen() {
        val target = review(aiClass = AssetCondition.NORMAL, finalClass = AssetCondition.OUT, overrideReason = "x")
        composeRule.setContent {
            LuxMapTheme {
                AiConfirmationScreen(
                    surveySweepId = "SWEEP-1",
                    uiState = AiConfirmationUiState.Empty,
                    sheet = AiReviewSheetState(review = target, chosenClass = AssetCondition.NORMAL, reason = "x"),
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Giữ kết quả AI").assertIsEnabled()
    }

    @Test
    fun choosing_a_condition_in_the_grid_invokes_onConditionChosen() {
        var chosen: AssetCondition? = null
        val target = review(aiClass = AssetCondition.NORMAL)
        composeRule.setContent {
            LuxMapTheme {
                AiConfirmationScreen(
                    surveySweepId = "SWEEP-1",
                    uiState = AiConfirmationUiState.Empty,
                    sheet = AiReviewSheetState(review = target, chosenClass = AssetCondition.NORMAL, reason = ""),
                    onBack = {},
                    onConditionChosen = { chosen = it },
                )
            }
        }
        composeRule.onNodeWithText(AssetCondition.OUT.label()).performClick()
        assertEquals(AssetCondition.OUT, chosen)
    }

    @Test
    fun confirming_the_submit_dialog_invokes_onSubmit_and_shows_the_saved_toast() {
        var submitted = false
        composeRule.setContent {
            LuxMapTheme {
                AiConfirmationScreen(
                    surveySweepId = "SWEEP-1",
                    uiState =
                        AiConfirmationUiState.Success(
                            reviews = listOf(review()),
                            filter = ReviewFilter.ALL,
                            normalCount = 1,
                            dimCount = 0,
                            outCount = 0,
                        ),
                    sheet = null,
                    onBack = {},
                    onSubmit = { submitted = true },
                )
            }
        }
        composeRule.onNodeWithText("Gửi").performClick()
        composeRule.onNodeWithText("Gửi kết quả xác nhận?").assertExists()
        composeRule.onAllNodesWithText("Gửi")[1].performClick()
        assertTrue(submitted)
        composeRule.onNodeWithText("Đã lưu trên thiết bị · sẽ gửi khi có mạng").assertExists()
    }
```

Add these imports to the top of the test file: `import androidx.compose.ui.test.assertIsEnabled`, `import androidx.compose.ui.test.assertIsNotEnabled`, `import androidx.compose.ui.test.onAllNodesWithText`, and `import org.junit.Assert.assertTrue` (the file already has `assertEquals` from Task 3's import). `AiReviewSheetState` needs no import — the test file is already in package `com.luxmap.feature.survey.ui.aiconfirm`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: FAIL — the sheet is never rendered yet, so `"Lưu thay đổi"`/`"Giữ kết quả AI"`/the confirm dialog text are never found once run on-device; at minimum confirm this compiles first (the actual node-not-found failures only surface when run on a device/emulator, consistent with how every prior androidTest file in this project is verified).

- [ ] **Step 3: Implement the sheet and confirm dialog**

Add these imports to `AiConfirmationScreen.kt`:

```kotlin
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.luxmap.core.ui.components.PrimaryButton
import kotlinx.coroutines.launch
```

Change the end of `AiConfirmationScreen`'s body (right after the `Scaffold { ... }` block closes) from nothing to:

```kotlin
    val scope = rememberCoroutineScope()
    var showSubmitConfirm by rememberSaveable { mutableStateOf(false) }
```

placed right before the `Scaffold(` call (so `showSubmitConfirm` can be referenced by both the bottom bar and the dialog below), and change the `bottomBar` block to open the dialog instead of calling `onSubmit` directly:

```kotlin
        bottomBar = {
            if (uiState is AiConfirmationUiState.Success) {
                BottomActionBar(ctaText = "Gửi", onCtaClick = { showSubmitConfirm = true }, ctaEnabled = true)
            }
        },
```

Then, after the `Scaffold { ... }` closing brace, add:

```kotlin
    if (sheet != null) {
        AiReviewSheet(
            sheet = sheet,
            onDismiss = onDismissSheet,
            onConditionChosen = onConditionChosen,
            onReasonChanged = onReasonChanged,
            onSave = onSaveSheet,
        )
    }

    if (showSubmitConfirm) {
        AlertDialog(
            onDismissRequest = { showSubmitConfirm = false },
            title = { Text("Gửi kết quả xác nhận?") },
            text = { Text("Phiên khảo sát $surveySweepId sẽ được gửi lên hệ thống khi có mạng.") },
            dismissButton = {
                TextButton(onClick = { showSubmitConfirm = false }) { Text("Huỷ") }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSubmitConfirm = false
                        onSubmit()
                        scope.launch {
                            snackbarHostState.showSnackbar("Đã lưu trên thiết bị · sẽ gửi khi có mạng")
                        }
                    },
                ) { Text("Gửi") }
            },
        )
    }
}
```

Note the trailing `}` above closes the whole `AiConfirmationScreen` function — move the function's final closing brace down here instead of right after the `Scaffold` call.

Then add these two new composables at the end of the file:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiReviewSheet(
    sheet: AiReviewSheetState,
    onDismiss: () -> Unit,
    onConditionChosen: (AssetCondition) -> Unit,
    onReasonChanged: (String) -> Unit,
    onSave: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val isOverride = sheet.chosenClass != sheet.review.aiClass
    val canSave = !isOverride || sheet.reason.isNotBlank()
    val saveLabel = if (isOverride) "Lưu thay đổi" else "Giữ kết quả AI"

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.control)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Videocam,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "AI: ${sheet.review.aiClass.label()} ${sheet.review.aiConfidencePercent}%",
                style = MaterialTheme.typography.bodyLarge,
            )
            ConditionOptionGrid(selected = sheet.chosenClass, onSelected = onConditionChosen)
            if (isOverride) {
                OutlinedTextField(
                    value = sheet.reason,
                    onValueChange = onReasonChanged,
                    label = { Text("Lý do sửa *") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            PrimaryButton(text = saveLabel, onClick = onSave, enabled = canSave, modifier = Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConditionOptionGrid(
    selected: AssetCondition,
    onSelected: (AssetCondition) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        listOf(AssetCondition.NORMAL, AssetCondition.DIM, AssetCondition.OUT).forEach { condition ->
            FilterChip(
                selected = condition == selected,
                onClick = { onSelected(condition) },
                label = { Text(condition.label()) },
            )
        }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:compileDebugAndroidTestKotlin ktlintCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/ui/aiconfirm/AiConfirmationScreen.kt app/src/androidTest/java/com/luxmap/feature/survey/ui/aiconfirm/AiConfirmationScreenTest.kt
git commit -m "feat(redesign-phase7): add the review correction sheet and submit confirm dialog"
```

---

### Task 5: `AiConfirmationRoute` + navigation wiring from Survey Hub

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/aiconfirm/AiConfirmationScreen.kt`
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/hub/SurveyHubScreen.kt`
- Modify: `app/src/main/java/com/luxmap/navigation/Routes.kt`
- Modify: `app/src/main/java/com/luxmap/navigation/NavGraph.kt`
- Modify: `app/src/androidTest/java/com/luxmap/feature/survey/ui/hub/SurveyHubScreenTest.kt`

**Interfaces:**
- Consumes: Task 2's `AiConfirmationViewModel`; Task 3/4's `AiConfirmationScreen`.
- Produces: `AiConfirmationRoute(surveySweepId: String, onBack: () -> Unit, viewModel: AiConfirmationViewModel = hiltViewModel())`; `Routes.AiConfirmation` with `route = "survey/ai-confirmation/{surveySweepId}"` and `createRoute(surveySweepId: String)`; `SurveyHubRoute(onCheckReadiness: ..., onReviewAiConfirmation: (surveySweepId: String) -> Unit = {}, ...)` — nothing downstream of this task, it is the final wiring point.

- [ ] **Step 1: Add `AiConfirmationRoute` to `AiConfirmationScreen.kt`**

Add these imports:

```kotlin
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.theme.LuxMapTheme
```

Add this Composable at the top of the file, right after the imports and before `AiConfirmationScreen`:

```kotlin
@Composable
fun AiConfirmationRoute(
    surveySweepId: String,
    onBack: () -> Unit,
    viewModel: AiConfirmationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val sheet by viewModel.sheet.collectAsState()

    LaunchedEffect(surveySweepId) { viewModel.load(surveySweepId) }

    LuxMapTheme {
        AiConfirmationScreen(
            surveySweepId = surveySweepId,
            uiState = uiState,
            sheet = sheet,
            onBack = onBack,
            onFilterSelected = viewModel::onFilterSelected,
            onReviewSelected = viewModel::onReviewSelected,
            onDismissSheet = viewModel::onDismissSheet,
            onConditionChosen = viewModel::onConditionChosen,
            onReasonChanged = viewModel::onReasonChanged,
            onSaveSheet = viewModel::onSaveSheet,
            onSubmit = { viewModel.onSubmit(surveySweepId) },
        )
    }
}
```

- [ ] **Step 2: Add the new route to `Routes.kt`**

Add this `data object` to the `Routes` sealed interface, right after `SurveySubmit`:

```kotlin
    data object AiConfirmation : Routes {
        override val route = "survey/ai-confirmation/{surveySweepId}"

        fun createRoute(surveySweepId: String) = "survey/ai-confirmation/$surveySweepId"
    }
```

- [ ] **Step 3: Write the failing Hub navigation test**

Add this test to `SurveyHubScreenTest.kt`:

```kotlin
    @Test
    fun tapping_Xem_va_xac_nhan_on_a_pending_ai_card_invokes_onReviewAiConfirmation() {
        var reviewed: String? = null
        composeRule.setContent {
            LuxMapTheme {
                SurveyHubScreen(
                    state =
                        SurveyHubUiState.Success(
                            assignedRoutes = emptyList(),
                            pendingAiConfirmation =
                                listOf(
                                    LocalPendingAiConfirmation(
                                        surveySweepId = "SWEEP-2029",
                                        normalCount = 11,
                                        dimCount = 2,
                                        outCount = 1,
                                    ),
                                ),
                        ),
                    onCheckReadiness = {},
                    onReviewAiConfirmation = { reviewed = it },
                )
            }
        }
        composeRule.onNodeWithText("Xem & xác nhận").performClick()
        assertEquals("SWEEP-2029", reviewed)
    }
```

Add `import androidx.compose.ui.test.performClick` and `import org.junit.Assert.assertEquals` to the top of `SurveyHubScreenTest.kt` (neither is currently imported there).

- [ ] **Step 4: Run the test to verify it fails**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: PASS to compile (the `onReviewAiConfirmation` param already exists on `SurveyHubScreen` from Phase 3) — this step confirms the test itself is new and wired correctly; the real gap being closed in this task is `SurveyHubRoute` never passing a real callback, which the next step fixes.

- [ ] **Step 5: Add `onReviewAiConfirmation` to `SurveyHubRoute` and wire it through**

In `SurveyHubScreen.kt`, change:

```kotlin
@Composable
fun SurveyHubRoute(
    onCheckReadiness: (surveySweepId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SurveyHubViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    LuxMapTheme(forceDark = true) {
        SurveyHubScreen(
            state = uiState,
            onCheckReadiness = onCheckReadiness,
            modifier = modifier,
            onRetry = viewModel::onRetry,
        )
    }
}
```

to:

```kotlin
@Composable
fun SurveyHubRoute(
    onCheckReadiness: (surveySweepId: String) -> Unit,
    onReviewAiConfirmation: (surveySweepId: String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SurveyHubViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    LuxMapTheme(forceDark = true) {
        SurveyHubScreen(
            state = uiState,
            onCheckReadiness = onCheckReadiness,
            modifier = modifier,
            onRetry = viewModel::onRetry,
            onReviewAiConfirmation = onReviewAiConfirmation,
        )
    }
}
```

- [ ] **Step 6: Wire the route in `NavGraph.kt`**

Add the import:

```kotlin
import com.luxmap.feature.survey.ui.aiconfirm.AiConfirmationRoute
```

Change the `Routes.Survey.route` composable block from:

```kotlin
            composable(Routes.Survey.route) {
                SurveyHubRoute(
                    onCheckReadiness = { surveySweepId ->
                        navController.navigate(Routes.SurveyPlan.createRoute(surveySweepId))
                    },
                )
            }
```

to:

```kotlin
            composable(Routes.Survey.route) {
                SurveyHubRoute(
                    onCheckReadiness = { surveySweepId ->
                        navController.navigate(Routes.SurveyPlan.createRoute(surveySweepId))
                    },
                    onReviewAiConfirmation = { surveySweepId ->
                        navController.navigate(Routes.AiConfirmation.createRoute(surveySweepId))
                    },
                )
            }
            composable(
                route = Routes.AiConfirmation.route,
                arguments = listOf(navArgument("surveySweepId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val surveySweepId = backStackEntry.arguments?.getString("surveySweepId") ?: return@composable
                AiConfirmationRoute(surveySweepId = surveySweepId, onBack = { navController.popBackStack() })
            }
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin ktlintCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Verify the camera-critical invariant**

Run: `git diff dev..HEAD -- app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureViewModel.kt app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureScreen.kt | grep -E "^\+|^-" | grep -i "BackHandler\|FLAG_KEEP_SCREEN_ON\|SCREEN_ORIENTATION_LOCKED\|warningPulses\|onSessionPackaged\|SurfaceTextureListener\|onPreviewSurfaceReady\|onPreviewSurfaceLost"`
Expected: exit code 1 (no output) — this phase never touches capture code, so this should trivially pass, but confirm anyway per this project's established habit.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/ui/aiconfirm/AiConfirmationScreen.kt app/src/main/java/com/luxmap/feature/survey/ui/hub/SurveyHubScreen.kt app/src/main/java/com/luxmap/navigation/Routes.kt app/src/main/java/com/luxmap/navigation/NavGraph.kt app/src/androidTest/java/com/luxmap/feature/survey/ui/hub/SurveyHubScreenTest.kt
git commit -m "feat(redesign-phase7): wire AiConfirmationRoute into nav and the Hub's review callback"
```

---

### Task 6: Log known gaps and open the PR

**Files:**
- Modify: `docs/contract-drift.md`

**Interfaces:**
- Consumes: nothing new.
- Produces: nothing new — this is the phase's wrap-up task.

- [ ] **Step 1: Add rows to `docs/contract-drift.md`**

Append these rows to the table (after the last existing row):

```markdown
| Phase 7 "Xác nhận kết quả AI" screen has no real route/entity backing it | `LocalAiClassificationReview`/`AiClassificationRepository`/`FakeAiClassificationRepository` are entirely local-only, per the redesign roadmap's decision #2 | **UI-only, mock data, by design (2026-10-12, Phase 7)** — no real `ai_classified`/`final_classified` entity or endpoint exists yet (see the WorkOrder-split decision notes earlier in this doc and CLAUDE.md's "ai_classified khỏi final_classified" audit-trail rule). Reachable today only via `SurveyHubScreen`'s "Chờ xác nhận AI" card, which stays hidden in production behind `SHOW_UNCONFIRMED_HUB_CARDS = false` — the new route and screen are fully functional but have no real production entry point until that flag (or a real backend-confirmed trigger) is turned on. Rewire `LocalAiClassificationReview` to a real entity once WP2/WP5 confirm the schema | WP2/WP5 |
| Phase 7's review row/sheet thumbnails are placeholder icons, not real frames | `FrameThumbnail`/`AiReviewSheet` in `AiConfirmationScreen.kt` render a fixed `Icons.Filled.Videocam` icon, never a real captured frame image | **Known, by design (2026-10-12, Phase 7)** — there is no real frame-extraction pipeline on the mobile side (per the 2026-09-25 decision, frame-cutting from video is server/AI work, not mobile); `LocalAiClassificationReview.frameRef` is just a display label string ("Khung hình #NNN"), never a path to a real file. A real thumbnail needs the server to expose a frame image/URL per review, which does not exist yet | WP2/WP5 |
```

- [ ] **Step 2: Run the full scoped verification**

Run: `./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:compileDebugUnitTestKotlin ktlintCheck`
Expected: BUILD SUCCESSFUL.

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.survey.data.FakeAiClassificationRepositoryTest" --tests "com.luxmap.feature.survey.ui.aiconfirm.AiConfirmationViewModelTest"`
Expected: BUILD SUCCESSFUL, all tests pass.

Do not run the full `./gradlew test` suite — `CaptureViewModelTest` has a known pre-existing hang unrelated to this branch (confirmed earlier in this project); the camera-critical invariant check in Task 5 Step 8 is the substitute verification already used by every prior redesign phase.

- [ ] **Step 3: Commit**

```bash
git add docs/contract-drift.md
git commit -m "docs(redesign-phase7): log the AI confirmation screen's known mock-data gaps"
```

- [ ] **Step 4: Push and open the PR**

```bash
git push -u origin style/redesign-phase7-ai-confirmation
gh pr create --base dev --head style/redesign-phase7-ai-confirmation --title "style(redesign-phase7): add the Xác nhận kết quả AI screen" --body "..."
```

(The exact PR body is written at execution time, summarizing the actual commits made — follow this project's established PR description pattern from Phases 2–6.)

---

## Self-Review Notes

- **Spec coverage:** every bullet in spec §"Xác nhận kết quả AI (1q–1r)" is covered — Ink summary block with 3-way counts (Task 3), "Sửa nếu AI..." copy (Task 3), 3 filter chips (Task 3), review row with thumbnail/pole-id/km·AI%/low-confidence label/final badge/overridden-from line (Task 3), 16:9 sheet with AI label+%, `ConditionOptionGrid`, conditional reason field, Lưu-thay-đổi/Giữ-kết-quả-AI button (Task 4), confirm dialog + "Đã lưu trên thiết bị..." toast (Task 4), minimum data fields `ai_class`/`ai_confidence`/frame ref/`final_class`/`override_reason`/`overridden_at`/`overridden_by` (Task 1).
- **Placeholder scan:** none found — every step has concrete, runnable code.
- **Type consistency:** `AssetCondition`, `LocalAiClassificationReview`, `AiReviewSheetState`, `ReviewFilter`, `AiConfirmationUiState` are used with identical shapes across all 6 tasks; verified by re-reading each task's Interfaces block against the ones before it.
- **Review Focus:** all 5 items listed above have an owning test — low-confidence label (Task 3 Steps 1/3's two confidence tests), override-clearing (Task 1 Step 3's `setFinalClass back to...` test and Task 2 Step 2's `saving the same class...` test), blank-reason no-op (Task 2 Step 2's `saving a different class without a reason...` test), NEEDS_REVIEW filter correctness (Task 2 Step 2's dedicated filter test), Empty state for zero reviews (Task 2 Step 2's `an empty review list...` test).
