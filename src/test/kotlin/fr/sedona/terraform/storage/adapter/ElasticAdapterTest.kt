package fr.sedona.terraform.storage.adapter

import com.fasterxml.jackson.databind.ObjectMapper
import fr.sedona.terraform.exception.StateAlreadyLockedException
import fr.sedona.terraform.exception.StateLockMismatchException
import fr.sedona.terraform.exception.StateNotLockedException
import fr.sedona.terraform.http.exception.BadRequestException
import fr.sedona.terraform.http.exception.ConflictException
import fr.sedona.terraform.http.exception.LockedException
import fr.sedona.terraform.http.exception.ResourceNotFoundException
import fr.sedona.terraform.http.model.TfLockInfo
import fr.sedona.terraform.http.model.TfState
import fr.sedona.terraform.storage.elasticsearch.service.ElasticsearchStateService
import fr.sedona.terraform.storage.model.State
import fr.sedona.terraform.util.ensureIsLocked
import fr.sedona.terraform.util.ensureIsNotLocked
import fr.sedona.terraform.util.ensureLockOwnership
import fr.sedona.terraform.util.toInternal
import io.mockk.*
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*
import kotlin.NoSuchElementException
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

@ExtendWith(MockKExtension::class)
class ElasticAdapterTest {
    private lateinit var elasticAdapter: ElasticAdapter

    @RelaxedMockK
    private lateinit var elasticStateService: ElasticsearchStateService

    @RelaxedMockK
    private lateinit var objectMapper: ObjectMapper

    private val testProjectName = "test-project"
    private val testLockId = "lock-id"
    private val testTfState = TfState(
        version = 4,
        tfVersion = "0.14.0",
        serial = 1,
        lineage = "",
        outputs = null,
        resources = null
    )
    private val testTfLockInfo = TfLockInfo(
        id = testLockId,
        version = "0.14.0",
        operation = "some-operation",
        created = Date(),
        info = null,
        who = "test@test.com",
        path = "test-project"
    )
    private val defaultPageIndex = 1
    private val defaultPageSize = 25

    private lateinit var testUnlockedState: State
    private lateinit var testLockedState: State

    @BeforeTest
    fun setup() {
        // Init unlocked state
        testUnlockedState = State()
        testUnlockedState.name = testProjectName
        testUnlockedState.lastModified = Date()
        testUnlockedState.lockId = null
        testUnlockedState.lockInfo = null
        testUnlockedState.locked = false

        // Init locked state
        testLockedState = State()
        testLockedState.name = testProjectName
        testLockedState.lastModified = Date()
        testLockedState.lockId = testLockId
        testLockedState.lockInfo = "{}"
        testLockedState.locked = true

        mockkStatic(
            "fr.sedona.terraform.util.ExtensionsKt"
        )

        every { testTfState.toInternal(any(), any(), any()) } returns testUnlockedState
        every { testTfState.toInternal(any(), any(), any(), any()) } returns testUnlockedState

        every { testUnlockedState.ensureIsLocked() } throws StateNotLockedException()
        every { testUnlockedState.ensureIsNotLocked(any()) } just Runs
        every { testUnlockedState.ensureLockOwnership(any(), any()) } just Runs

        every { testLockedState.ensureIsLocked() } just Runs
        every { testLockedState.ensureIsNotLocked(any()) } throws StateAlreadyLockedException(testTfLockInfo)
        every { testLockedState.ensureLockOwnership(any(), any()) } just Runs

        every { elasticStateService.listAll() } returns listOf(testUnlockedState)
        every { elasticStateService.paginate(any(), any()) } returns listOf(testUnlockedState)
        every { elasticStateService.get(any()) } returns testUnlockedState
        every { elasticStateService.update(any() as State) } just Runs
        every { elasticStateService.delete(any()) } just Runs
        every { elasticStateService.createAndLock(any(), any()) } returns testLockedState
        every { elasticStateService.lock(any(), any(), any()) } returns testLockedState
        every { elasticStateService.unlock(any(), any()) } returns testUnlockedState

        elasticAdapter = ElasticAdapter(elasticStateService, objectMapper)
    }

    @Test
    @DisplayName("Given nominal case, when listing all states, then returns a list of states")
    fun testNominalCaseOnListAll() {
        // Given - nothing

        // When
        val result = elasticAdapter.listAll()

        // Then
        verify(exactly = 1) { elasticStateService.listAll() }
        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals(testUnlockedState, result.firstOrNull())
    }

    @Test
    @DisplayName("Given no elements in ES, when listing all states, then returns an empty list of states")
    fun testNoElementsCaseOnListAll() {
        // Given
        every { elasticStateService.listAll() } returns emptyList()

        // When
        val result = elasticAdapter.listAll()

        // Then
        verify(exactly = 1) { elasticStateService.listAll() }
        assertNotNull(result)
        assertEquals(0, result.size)
    }

    @Test
    @DisplayName("Given nominal case, when paginating states, then returns a page of states")
    fun testNominalCaseOnPaginate() {
        // Given - nothing

        // When
        val result = elasticAdapter.paginate(defaultPageIndex, defaultPageSize)

        // Then
        verify(exactly = 1) { elasticStateService.paginate(any(), any()) }
        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals(testUnlockedState, result.firstOrNull())
    }

    @Test
    @DisplayName("Given no elements in ES, when paginating states, then returns an empty list of states")
    fun testNoElementsCaseOnPaginate() {
        // Given
        every { elasticStateService.paginate(any(), any()) } returns emptyList()

        // When
        val result = elasticAdapter.paginate(defaultPageIndex, defaultPageSize)

        // Then
        verify(exactly = 1) { elasticStateService.paginate(any(), any()) }
        assertNotNull(result)
        assertEquals(0, result.size)
    }

    @Test
    @DisplayName("Given no params, when paginating states, then returns the 1st page of 25 states")
    fun testNoParamsCaseOnPaginate() {
        // Given
        val capturedPageIndex = slot<Int>()
        val capturedPageSize = slot<Int>()
        every { elasticAdapter.paginate(capture(capturedPageIndex), capture(capturedPageSize)) } returns
                listOf(testUnlockedState)

        // When
        val result = elasticAdapter.paginate()

        // Then
        verify(exactly = 1) { elasticStateService.paginate(any(), any()) }
        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals(testUnlockedState, result.firstOrNull())
        assertEquals(defaultPageIndex, capturedPageIndex.captured)
        assertEquals(defaultPageSize, capturedPageSize.captured)
    }

    @Test
    @DisplayName("Given nominal case, when finding by id, then returns a state")
    fun testNominalCaseOnFindById() {
        // Given - nothing

        // When
        val result = elasticAdapter.findById(testProjectName)

        // Then
        verify(exactly = 1) { elasticStateService.get(any()) }
        assertNotNull(result)
        assertEquals(testUnlockedState, result)
    }

    @Test
    @DisplayName("Given no element with specified id, when finding by id, then throws a resource not found exception")
    fun testNotFoundCaseOnFindById() {
        // Given
        every { elasticStateService.get(any()) } throws NoSuchElementException()

        // When / Then
        try {
            elasticAdapter.findById(testProjectName)

            fail("Should not find any element")
        } catch (e: ResourceNotFoundException) {
            assertEquals(testProjectName, e.name)
        }
    }

    @Test
    @DisplayName("Given nominal case, when updating with lock, then updates state")
    fun testNominalCaseOnUpdateWithLock() {
        // Given
        every { elasticStateService.get(any()) } returns testLockedState

        // When
        elasticAdapter.updateWithLock(testProjectName, testLockId, testTfState)

        // Then
        verify(exactly = 1) { elasticStateService.get(any()) }
        verify(exactly = 1) { testLockedState.ensureIsLocked() }
        verify(exactly = 1) { testLockedState.ensureLockOwnership(any(), any()) }
        verify(exactly = 0) { testUnlockedState.ensureIsLocked() }
        verify(exactly = 0) { testUnlockedState.ensureLockOwnership(any(), any()) }
        verify(exactly = 1) { elasticStateService.update(any() as State) }
    }

    @Test
    @DisplayName("Given no element with specified id, when updating with lock, then adds state")
    fun testNotFoundCaseOnUpdateWithLock() {
        // Given
        every { elasticStateService.get(any()) } throws NoSuchElementException()

        // When
        elasticAdapter.updateWithLock(testProjectName, testLockId, testTfState)

        // Then
        verify(exactly = 1) { elasticStateService.get(any()) }
        verify(exactly = 0) { testLockedState.ensureIsLocked() }
        verify(exactly = 0) { testLockedState.ensureLockOwnership(any(), any()) }
        verify(exactly = 0) { testUnlockedState.ensureIsLocked() }
        verify(exactly = 0) { testUnlockedState.ensureLockOwnership(any(), any()) }
        verify(exactly = 1) { elasticStateService.update(any() as State) }
    }

    @Test
    @DisplayName("Given state is not locked, when updating with lock, then throws bad request exception")
    fun testNotLockedCaseOnUpdateWithLock() {
        // Given
        every { elasticStateService.get(any()) } returns testUnlockedState

        // When / Then
        try {
            elasticAdapter.updateWithLock(testProjectName, testLockId, testTfState)

            fail("Should not just run")
        } catch (e: BadRequestException) {
            verify(exactly = 1) { elasticStateService.get(any()) }
            verify(exactly = 0) { testLockedState.ensureIsLocked() }
            verify(exactly = 0) { testLockedState.ensureLockOwnership(any(), any()) }
            verify(exactly = 1) { testUnlockedState.ensureIsLocked() }
            verify(exactly = 0) { testUnlockedState.ensureLockOwnership(any(), any()) }
            verify(exactly = 0) { elasticStateService.update(any() as State) }
        }
    }

    @Test
    @DisplayName("Given state is locked by someone else, when updating with lock, then throws locked exception")
    fun testLockMismatchedCaseOnUpdateWithLock() {
        // Given
        every { elasticStateService.get(any()) } returns testLockedState
        every { testLockedState.ensureLockOwnership(any(), any()) } throws StateLockMismatchException(testTfLockInfo)

        // When / Then
        try {
            elasticAdapter.updateWithLock(testProjectName, testLockId, testTfState)

            fail("Should not just run")
        } catch (e: LockedException) {
            verify(exactly = 1) { elasticStateService.get(any()) }
            verify(exactly = 1) { testLockedState.ensureIsLocked() }
            verify(exactly = 1) { testLockedState.ensureLockOwnership(any(), any()) }
            verify(exactly = 0) { testUnlockedState.ensureIsLocked() }
            verify(exactly = 0) { testUnlockedState.ensureLockOwnership(any(), any()) }
            verify(exactly = 0) { elasticStateService.update(any() as State) }
            assertEquals(testTfLockInfo, e.lockInfo)
        }
    }

    @Test
    @DisplayName("Given nominal case, when updating without lock, then updates state")
    fun testNominalCaseOnUpdateWithoutLock() {
        // Given - nothing

        // When
        elasticAdapter.updateWithoutLock(testProjectName, testTfState)

        // Then
        verify(exactly = 1) { elasticStateService.get(any()) }
        verify(exactly = 1) { elasticStateService.update(any() as State) }
    }

    @Test
    @DisplayName("Given no element with specified id, when updating without lock, then adds state")
    fun testNotFoundCaseOnUpdateWithoutLock() {
        // Given
        every { elasticStateService.get(any()) } throws NoSuchElementException()

        // When
        elasticAdapter.updateWithoutLock(testProjectName, testTfState)

        // Then
        verify(exactly = 1) { elasticStateService.get(any()) }
        verify(exactly = 1) { elasticStateService.update(any() as State) }
    }

    @Test
    @DisplayName("Given nominal case, when deleting, then deletes state")
    fun testNominalCaseOnDelete() {
        // Given - nothing

        // When
        elasticAdapter.delete(testProjectName)

        // Then
        verify(exactly = 1) { elasticStateService.get(any()) }
        verify(exactly = 0) { testLockedState.ensureIsNotLocked(any()) }
        verify(exactly = 1) { testUnlockedState.ensureIsNotLocked(any()) }
        verify(exactly = 1) { elasticStateService.delete(any()) }
    }

    @Test
    @DisplayName("Given no element with specified id, when deleting, then throws resource not found exception")
    fun testNotFoundCaseOnDelete() {
        // Given
        every { elasticStateService.get(any()) } throws NoSuchElementException()

        // When / Then
        try {
            elasticAdapter.delete(testProjectName)

            fail("Should not just run")
        } catch (e: ResourceNotFoundException) {
            verify(exactly = 1) { elasticStateService.get(any()) }
            verify(exactly = 0) { testLockedState.ensureIsNotLocked(any()) }
            verify(exactly = 0) { testUnlockedState.ensureIsNotLocked(any()) }
            verify(exactly = 0) { elasticStateService.delete(any()) }
            assertEquals(testProjectName, e.name)
        }
    }

    @Test
    @DisplayName("Given state is locked, when deleting, then throws locked exception")
    fun testLockedCaseOnDelete() {
        // Given
        every { elasticStateService.get(any()) } returns testLockedState

        // When / Then
        try {
            elasticAdapter.delete(testProjectName)

            fail("Should not just run")
        } catch (e: LockedException) {
            verify(exactly = 1) { elasticStateService.get(any()) }
            verify(exactly = 1) { testLockedState.ensureIsNotLocked(any()) }
            verify(exactly = 0) { testUnlockedState.ensureIsNotLocked(any()) }
            verify(exactly = 0) { elasticStateService.delete(any()) }
            assertEquals(testTfLockInfo, e.lockInfo)
        }
    }

    @Test
    @DisplayName("Given nominal case, when locking, then locks state")
    fun testNominalCaseOnLock() {
        // Given - nothing

        // When
        elasticAdapter.lock(testProjectName, testTfLockInfo)

        // Then
        verify(exactly = 1) { elasticStateService.get(any()) }
        verify(exactly = 0) { testLockedState.ensureIsNotLocked(any()) }
        verify(exactly = 1) { testUnlockedState.ensureIsNotLocked(any()) }
        verify(exactly = 1) { elasticStateService.lock(any(), any(), any()) }
        verify(exactly = 0) { elasticStateService.createAndLock(any(), any()) }
    }

    @Test
    @DisplayName("Given no element with specified id, when locking, then creates and locks state")
    fun testNotFoundCaseOnLock() {
        // Given
        every { elasticStateService.get(any()) } throws NoSuchElementException()

        // When
        elasticAdapter.lock(testProjectName, testTfLockInfo)

        // Then
        verify(exactly = 1) { elasticStateService.get(any()) }
        verify(exactly = 0) { testLockedState.ensureIsNotLocked(any()) }
        verify(exactly = 0) { testUnlockedState.ensureIsNotLocked(any()) }
        verify(exactly = 0) { elasticStateService.lock(any(), any(), any()) }
        verify(exactly = 1) { elasticStateService.createAndLock(any(), any()) }
    }

    @Test
    @DisplayName("Given state is locked by someone else, when locking, then throws locked exception")
    fun testLockedCaseOnLock() {
        // Given
        every { elasticStateService.get(any()) } returns testLockedState

        // When / Then
        try {
            elasticAdapter.lock(testProjectName, testTfLockInfo)

            fail("Should not just run")
        } catch (e: LockedException) {
            verify(exactly = 1) { elasticStateService.get(any()) }
            verify(exactly = 1) { testLockedState.ensureIsNotLocked(any()) }
            verify(exactly = 0) { testUnlockedState.ensureIsNotLocked(any()) }
            verify(exactly = 0) { elasticStateService.lock(any(), any(), any()) }
            verify(exactly = 0) { elasticStateService.createAndLock(any(), any()) }
            assertEquals(testTfLockInfo, e.lockInfo)
        }
    }

    @Test
    @DisplayName("Given nominal case, when unlocking, then unlocks state")
    fun testNominalCaseOnUnlock() {
        // Given
        every { elasticStateService.get(any()) } returns testLockedState

        // When
        elasticAdapter.unlock(testProjectName, testTfLockInfo)

        // Then
        verify(exactly = 1) { elasticStateService.get(any()) }
        verify(exactly = 1) { testLockedState.ensureIsLocked() }
        verify(exactly = 1) { testLockedState.ensureLockOwnership(any(), any()) }
        verify(exactly = 0) { testUnlockedState.ensureIsLocked() }
        verify(exactly = 0) { testUnlockedState.ensureLockOwnership(any(), any()) }
        verify(exactly = 1) { elasticStateService.unlock(any(), any()) }
    }

    @Test
    @DisplayName("Given no element with specified id, when unlocking, then throws conflict exception")
    fun testNotFoundCaseOnUnlock() {
        // Given
        every { elasticStateService.get(any()) } throws NoSuchElementException()

        // When / Then
        try {
            elasticAdapter.unlock(testProjectName, testTfLockInfo)

            fail("Should not just run")
        } catch (e: ConflictException) {
            verify(exactly = 1) { elasticStateService.get(any()) }
            verify(exactly = 0) { testLockedState.ensureIsLocked() }
            verify(exactly = 0) { testLockedState.ensureLockOwnership(any(), any()) }
            verify(exactly = 0) { testUnlockedState.ensureIsLocked() }
            verify(exactly = 0) { testUnlockedState.ensureLockOwnership(any(), any()) }
            verify(exactly = 0) { elasticStateService.unlock(any(), any()) }
            assertEquals(testTfLockInfo, e.lockInfo)
        }
    }

    @Test
    @DisplayName("Given state is not locked, when unlocking, then throws conflict exception")
    fun testNotLockedCaseOnUnlock() {
        // Given - nothing

        // When / Then
        try {
            elasticAdapter.unlock(testProjectName, testTfLockInfo)

            fail("Should not just run")
        } catch (e: ConflictException) {
            verify(exactly = 1) { elasticStateService.get(any()) }
            verify(exactly = 0) { testLockedState.ensureIsLocked() }
            verify(exactly = 0) { testLockedState.ensureLockOwnership(any(), any()) }
            verify(exactly = 1) { testUnlockedState.ensureIsLocked() }
            verify(exactly = 0) { testUnlockedState.ensureLockOwnership(any(), any()) }
            verify(exactly = 0) { elasticStateService.unlock(any(), any()) }
            assertEquals(testTfLockInfo, e.lockInfo)
        }
    }

    @Test
    @DisplayName("Given state is locked by someone else, when unlocking, then throws locked exception")
    fun testLockMismatchedCaseOnUnlock() {
        // Given
        every { elasticStateService.get(any()) } returns testLockedState
        every { testLockedState.ensureLockOwnership(any(), any()) } throws StateLockMismatchException(testTfLockInfo)

        // When / Then
        try {
            elasticAdapter.unlock(testProjectName, testTfLockInfo)

            fail("Should not just run")
        } catch (e: LockedException) {
            verify(exactly = 1) { elasticStateService.get(any()) }
            verify(exactly = 1) { testLockedState.ensureIsLocked() }
            verify(exactly = 1) { testLockedState.ensureLockOwnership(any(), any()) }
            verify(exactly = 0) { testUnlockedState.ensureIsLocked() }
            verify(exactly = 0) { testUnlockedState.ensureLockOwnership(any(), any()) }
            verify(exactly = 0) { elasticStateService.unlock(any(), any()) }
            assertEquals(testTfLockInfo, e.lockInfo)
        }
    }

    @Test
    @DisplayName("Given nominal case, when forcing unlock, then forces unlock state")
    fun testNominalCaseOnForceUnlock() {
        // Given - nothing

        // When
        elasticAdapter.forceUnlock(testProjectName)

        // Then
        verify(exactly = 1) { elasticStateService.get(any()) }
        verify(exactly = 1) { elasticStateService.unlock(any(), any()) }
    }

    @Test
    @DisplayName("Given no element with specified id, when forcing unlock, then throws bad request exception")
    fun testNotFoundCaseOnForceUnlock() {
        // Given
        every { elasticStateService.get(any()) } throws NoSuchElementException()

        // When / Then
        try {
            elasticAdapter.forceUnlock(testProjectName)

            fail("Should not just run")
        } catch (e: BadRequestException) {
            verify(exactly = 1) { elasticStateService.get(any()) }
            verify(exactly = 0) { elasticStateService.unlock(any(), any()) }
        }
    }
}
