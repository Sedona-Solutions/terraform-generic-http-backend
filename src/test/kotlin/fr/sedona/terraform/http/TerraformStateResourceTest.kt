package fr.sedona.terraform.http

import com.fasterxml.jackson.databind.ObjectMapper
import fr.sedona.terraform.http.exception.BadRequestException
import fr.sedona.terraform.http.exception.ConflictException
import fr.sedona.terraform.http.exception.LockedException
import fr.sedona.terraform.http.exception.ResourceNotFoundException
import fr.sedona.terraform.http.model.TfLockInfo
import fr.sedona.terraform.http.model.TfState
import fr.sedona.terraform.storage.adapter.DatabaseAdapter
import fr.sedona.terraform.storage.model.State
import fr.sedona.terraform.util.toTerraform
import io.mockk.*
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

@ExtendWith(MockKExtension::class)
class TerraformStateResourceTest {
    private lateinit var terraformStateResource: TerraformStateResource

    @RelaxedMockK
    private lateinit var storageAdapter: DatabaseAdapter

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
    private val testUnlockedState = State(
        name = testProjectName,
        lastModified = Date(),
        lockId = null,
        lockInfo = null,
        locked = false
    )
    private val defaultPageIndex = 1
    private val defaultPageSize = 25

    @BeforeTest
    fun setup() {
        mockkStatic(
            "fr.sedona.terraform.util.ExtensionsKt"
        )

        every { testUnlockedState.toTerraform(any()) } returns testTfState

        every { storageAdapter.listAll() } returns listOf(testUnlockedState)
        every { storageAdapter.paginate(any(), any()) } returns listOf(testUnlockedState)
        every { storageAdapter.findById(any()) } returns testUnlockedState
        every { storageAdapter.updateWithLock(any(), any(), any()) } just runs
        every { storageAdapter.updateWithoutLock(any(), any()) } just runs
        every { storageAdapter.delete(any()) } just runs
        every { storageAdapter.lock(any(), any()) } just runs
        every { storageAdapter.unlock(any(), any()) } just runs
        every { storageAdapter.forceUnlock(any()) } just runs

        terraformStateResource = TerraformStateResource(storageAdapter, objectMapper)
    }

    @Test
    @DisplayName("Given nominal case, " +
            "when listing states, " +
            "then returns a list of states")
    fun testNominalCaseOnListStates() {
        // Given - nothing

        // When
        val result = terraformStateResource.listStates(null, null)

        // Then
        verify(exactly = 1) { storageAdapter.listAll() }
        verify(exactly = 0) { storageAdapter.paginate(any(), any()) }
        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals(testTfState, result.firstOrNull())
    }

    @Test
    @DisplayName("Given specified page index, " +
            "when listing states, " +
            "then returns the page index of 25 states")
    fun testSpecifiedPageIndexCaseOnListStates() {
        // Given
        val testPageIndex = 3
        val capturedPageIndex = slot<Int>()
        val capturedPageSize = slot<Int>()
        every { storageAdapter.paginate(capture(capturedPageIndex), capture(capturedPageSize)) } returns
                listOf(testUnlockedState)

        // When
        val result = terraformStateResource.listStates(testPageIndex, null)

        // Then
        verify(exactly = 0) { storageAdapter.listAll() }
        verify(exactly = 1) { storageAdapter.paginate(any(), any()) }
        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals(testTfState, result.firstOrNull())
        assertEquals(testPageIndex, capturedPageIndex.captured)
        assertEquals(defaultPageSize, capturedPageSize.captured)
    }

    @Test
    @DisplayName("Given specified page size, " +
            "when listing states, " +
            "then returns the 1st page of n states")
    fun testSpecifiedPageSizeCaseOnListStates() {
        // Given
        val testPageSize = 50
        val capturedPageIndex = slot<Int>()
        val capturedPageSize = slot<Int>()
        every { storageAdapter.paginate(capture(capturedPageIndex), capture(capturedPageSize)) } returns
                listOf(testUnlockedState)

        // When
        val result = terraformStateResource.listStates(defaultPageIndex, testPageSize)

        // Then
        verify(exactly = 0) { storageAdapter.listAll() }
        verify(exactly = 1) { storageAdapter.paginate(any(), any()) }
        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals(testTfState, result.firstOrNull())
        assertEquals(defaultPageIndex, capturedPageIndex.captured)
        assertEquals(testPageSize, capturedPageSize.captured)
    }

    @Test
    @DisplayName("Given specified page params, " +
            "when listing states, " +
            "then returns the n page of m states")
    fun testSpecifiedPageParamsCaseOnListStates() {
        // Given
        val testPageIndex = 5
        val testPageSize = 50
        val capturedPageIndex = slot<Int>()
        val capturedPageSize = slot<Int>()
        every { storageAdapter.paginate(capture(capturedPageIndex), capture(capturedPageSize)) } returns
                listOf(testUnlockedState)

        // When
        val result = terraformStateResource.listStates(testPageIndex, testPageSize)

        // Then
        verify(exactly = 0) { storageAdapter.listAll() }
        verify(exactly = 1) { storageAdapter.paginate(any(), any()) }
        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals(testTfState, result.firstOrNull())
        assertEquals(testPageIndex, capturedPageIndex.captured)
        assertEquals(testPageSize, capturedPageSize.captured)
    }

    @Test
    @DisplayName("Given nominal case," +
            "when getting state, " +
            "then returns the state")
    fun testNominalCaseOnGetState() {
        // Given - nothing

        // When
        val result = terraformStateResource.getState(testProjectName)

        // Then
        verify(exactly = 1) { storageAdapter.findById(any()) }
        assertNotNull(result)
        assertEquals(testTfState, result)
    }

    @Test
    @DisplayName("Given no element with specified id, " +
            "when getting state, " +
            "then throws resource not found exception")
    fun testNotFoundCaseOnGetState() {
        // Given
        every { storageAdapter.findById(any()) } throws ResourceNotFoundException(testProjectName)

        // When / Then
        try {
            terraformStateResource.getState(testProjectName)

            fail("Should not just run")
        } catch (e: ResourceNotFoundException) {
            verify(exactly = 1) { storageAdapter.findById(any()) }
            assertEquals(testProjectName, e.name)
        }
    }

    @Test
    @DisplayName("Given nominal case, " +
            "when updating state, " +
            "then returns an empty response with status OK")
    fun testNominalCaseOnUpdateState() {
        // Given - nothing

        // When
        val result = terraformStateResource.updateState(testProjectName, null, testTfState)

        // Then
        verify(exactly = 0) { storageAdapter.updateWithLock(any(), any(), any()) }
        verify(exactly = 1) { storageAdapter.updateWithoutLock(any(), any()) }
        assertNotNull(result)
        assertEquals(200, result.status)
    }

    @Test
    @DisplayName("Given correct lock, " +
            "when updating state, " +
            "then returns an empty response with status OK")
    fun testCorrectLockCaseOnUpdateState() {
        // Given - nothing

        // When
        val result = terraformStateResource.updateState(testProjectName, testLockId, testTfState)

        // Then
        verify(exactly = 1) { storageAdapter.updateWithLock(any(), any(), any()) }
        verify(exactly = 0) { storageAdapter.updateWithoutLock(any(), any()) }
        assertNotNull(result)
        assertEquals(200, result.status)
    }

    @Test
    @DisplayName("Given state not locked but lock id specified, " +
            "when updating state, " +
            "then throws bad request exception")
    fun testNotLockedCaseOnUpdateState() {
        // Given
        every { storageAdapter.updateWithLock(any(), any(), any()) } throws BadRequestException()

        // When / Then
        try {
            terraformStateResource.updateState(testProjectName, testLockId, testTfState)

            fail("Should not just run")
        } catch (e: BadRequestException) {
            verify(exactly = 1) { storageAdapter.updateWithLock(any(), any(), any()) }
            verify(exactly = 0) { storageAdapter.updateWithoutLock(any(), any()) }
        }
    }

    @Test
    @DisplayName("Given state locked by someone else, " +
            "when updating state, " +
            "then throws locked exception")
    fun testLockMismatchedCaseOnUpdateState() {
        // Given
        every { storageAdapter.updateWithLock(any(), any(), any()) } throws LockedException(testTfLockInfo)

        // When / Then
        try {
            terraformStateResource.updateState(testProjectName, testLockId, testTfState)

            fail("Should not just run")
        } catch (e: LockedException) {
            verify(exactly = 1) { storageAdapter.updateWithLock(any(), any(), any()) }
            verify(exactly = 0) { storageAdapter.updateWithoutLock(any(), any()) }
            assertEquals(testTfLockInfo, e.lockInfo)
        }
    }

    @Test
    @DisplayName("Given nominal case, " +
            "when deleting state, " +
            "then returns an empty response with status OK")
    fun testNominalCaseOnDeleteState() {
        // Given - nothing

        // When
        val result = terraformStateResource.deleteState(testProjectName)

        // Then
        verify(exactly = 1) { storageAdapter.delete(any()) }
        assertNotNull(result)
        assertEquals(200, result.status)
    }

    @Test
    @DisplayName("Given no specified element, " +
            "when deleting state, " +
            "then throws resource not found exception")
    fun testNoResourceCaseOnDeleteState() {
        // Given
        every { storageAdapter.delete(any()) } throws ResourceNotFoundException(testProjectName)

        // When / Then
        try {
            terraformStateResource.deleteState(testProjectName)

            fail("Should not just run")
        } catch (e: ResourceNotFoundException) {
            verify(exactly = 1) { storageAdapter.delete(any()) }
            assertEquals(testProjectName, e.name)
        }
    }

    @Test
    @DisplayName("Given state is locked, " +
            "when deleting state, " +
            "then throws locked exception")
    fun testLockedCaseOnDeleteState() {
        // Given
        every { storageAdapter.delete(any()) } throws LockedException(testTfLockInfo)

        // When / Then
        try {
            terraformStateResource.deleteState(testProjectName)

            fail("Should not just run")
        } catch (e: LockedException) {
            verify(exactly = 1) { storageAdapter.delete(any()) }
            assertEquals(testTfLockInfo, e.lockInfo)
        }
    }

    @Test
    @DisplayName("Given nominal case, " +
            "when locking state, " +
            "then returns an empty response with status OK")
    fun testNominalCaseOnLockState() {
        // Given - nothing

        // When
        val result = terraformStateResource.lockState(testProjectName, testTfLockInfo)

        // Then
        verify(exactly = 1) { storageAdapter.lock(any(), any()) }
        assertNotNull(result)
        assertEquals(200, result.status)
    }

    @Test
    @DisplayName("Given state is locked, " +
            "when locking state, " +
            "then throws locked exception")
    fun testLockedCaseOnLockState() {
        // Given
        every { storageAdapter.lock(any(), any()) } throws LockedException(testTfLockInfo)

        // When / Then
        try {
            terraformStateResource.lockState(testProjectName, testTfLockInfo)

            fail("Should not just run")
        } catch (e: LockedException) {
            verify(exactly = 1) { storageAdapter.lock(any(), any()) }
            assertEquals(testTfLockInfo, e.lockInfo)
        }
    }

    @Test
    @DisplayName("Given nominal case, " +
            "when locking state (alt), " +
            "then returns an empty response with status OK")
    fun testNominalCaseOnLockStateAlt() {
        // Given - nothing

        // When
        val result = terraformStateResource.lockState(testProjectName, testTfLockInfo)

        // Then
        verify(exactly = 1) { storageAdapter.lock(any(), any()) }
        assertNotNull(result)
        assertEquals(200, result.status)
    }

    @Test
    @DisplayName("Given state is locked, " +
            "when locking state (alt), " +
            "then throws locked exception")
    fun testLockedCaseOnLockStateAlt() {
        // Given
        every { storageAdapter.lock(any(), any()) } throws LockedException(testTfLockInfo)

        // When / Then
        try {
            terraformStateResource.lockState(testProjectName, testTfLockInfo)

            fail("Should not just run")
        } catch (e: LockedException) {
            verify(exactly = 1) { storageAdapter.lock(any(), any()) }
            assertEquals(testTfLockInfo, e.lockInfo)
        }
    }

    @Test
    @DisplayName("Given nominal case, " +
            "when unlocking state, " +
            "then returns an empty response with status OK")
    fun testNominalCaseOnUnlockState() {
        // Given - nothing

        // When
        val result = terraformStateResource.unlockState(testProjectName, testTfLockInfo)

        // Then
        verify(exactly = 1) { storageAdapter.unlock(any(), any()) }
        verify(exactly = 0) { storageAdapter.forceUnlock(any()) }
        assertNotNull(result)
        assertEquals(200, result.status)
    }

    @Test
    @DisplayName("Given no specified element, " +
            "when unlocking state, " +
            "then throws bad request exception")
    fun testNoElementCaseOnUnlockState() {
        // Given
        every { storageAdapter.unlock(any(), any()) } throws BadRequestException()

        // When / Then
        try {
            terraformStateResource.unlockState(testProjectName, testTfLockInfo)

            fail("Should not just run")
        } catch (e: BadRequestException) {
            verify(exactly = 1) { storageAdapter.unlock(any(), any()) }
            verify(exactly = 0) { storageAdapter.forceUnlock(any()) }
        }
    }

    @Test
    @DisplayName("Given state is not locked, " +
            "when unlocking state, " +
            "then throws conflict exception")
    fun testNotLockedCaseOnUnlockState() {
        // Given
        every { storageAdapter.unlock(any(), any()) } throws ConflictException(testTfLockInfo)

        // When / Then
        try {
            terraformStateResource.unlockState(testProjectName, testTfLockInfo)

            fail("Should not just run")
        } catch (e: ConflictException) {
            verify(exactly = 1) { storageAdapter.unlock(any(), any()) }
            verify(exactly = 0) { storageAdapter.forceUnlock(any()) }
            assertEquals(testTfLockInfo, e.lockInfo)
        }
    }

    @Test
    @DisplayName("Given state is locked by someone else, " +
            "when unlocking state, " +
            "then throws locked exception")
    fun testLockMismatchCaseOnUnlockState() {
        // Given
        every { storageAdapter.unlock(any(), any()) } throws LockedException(testTfLockInfo)

        // When / Then
        try {
            terraformStateResource.unlockState(testProjectName, testTfLockInfo)

            fail("Should not just run")
        } catch (e: LockedException) {
            verify(exactly = 1) { storageAdapter.unlock(any(), any()) }
            verify(exactly = 0) { storageAdapter.forceUnlock(any()) }
            assertEquals(testTfLockInfo, e.lockInfo)
        }
    }

    @Test
    @DisplayName("Given force unlock, " +
            "when unlocking state, " +
            "then returns an empty response with status OK")
    fun testForceUnlockCaseOnUnlockState() {
        // Given - nothing

        // When
        val result = terraformStateResource.unlockState(testProjectName, null)

        // Then
        verify(exactly = 0) { storageAdapter.unlock(any(), any()) }
        verify(exactly = 1) { storageAdapter.forceUnlock(any()) }
        assertNotNull(result)
        assertEquals(200, result.status)
    }

    @Test
    @DisplayName("Given force unlock on no specified element, " +
            "when unlocking state, " +
            "then throws bad request exception")
    fun testNoElementForceUnlockCaseOnUnlockState() {
        // Given
        every { storageAdapter.forceUnlock(any()) } throws BadRequestException()

        // When / Then
        try {
            terraformStateResource.unlockState(testProjectName, null)

            fail("Should not just run")
        } catch (e: BadRequestException) {
            verify(exactly = 0) { storageAdapter.unlock(any(), any()) }
            verify(exactly = 1) { storageAdapter.forceUnlock(any()) }
        }
    }

    @Test
    @DisplayName("Given nominal case, " +
            "when unlocking state (alt), " +
            "then returns an empty response with status OK")
    fun testNominalCaseOnUnlockStateAlt() {
        // Given - nothing

        // When
        val result = terraformStateResource.unlockState(testProjectName, testTfLockInfo)

        // Then
        verify(exactly = 1) { storageAdapter.unlock(any(), any()) }
        verify(exactly = 0) { storageAdapter.forceUnlock(any()) }
        assertNotNull(result)
        assertEquals(200, result.status)
    }

    @Test
    @DisplayName("Given no specified element, " +
            "when unlocking state (alt), " +
            "then throws bad request exception")
    fun testNoElementCaseOnUnlockStateAlt() {
        // Given
        every { storageAdapter.unlock(any(), any()) } throws BadRequestException()

        // When / Then
        try {
            terraformStateResource.unlockState(testProjectName, testTfLockInfo)

            fail("Should not just run")
        } catch (e: BadRequestException) {
            verify(exactly = 1) { storageAdapter.unlock(any(), any()) }
            verify(exactly = 0) { storageAdapter.forceUnlock(any()) }
        }
    }

    @Test
    @DisplayName("Given state is not locked, " +
            "when unlocking state (alt), " +
            "then throws conflict exception")
    fun testNotLockedCaseOnUnlockStateAlt() {
        // Given
        every { storageAdapter.unlock(any(), any()) } throws ConflictException(testTfLockInfo)

        // When / Then
        try {
            terraformStateResource.unlockState(testProjectName, testTfLockInfo)

            fail("Should not just run")
        } catch (e: ConflictException) {
            verify(exactly = 1) { storageAdapter.unlock(any(), any()) }
            verify(exactly = 0) { storageAdapter.forceUnlock(any()) }
            assertEquals(testTfLockInfo, e.lockInfo)
        }
    }

    @Test
    @DisplayName("Given state is locked by someone else, " +
            "when unlocking state (alt), " +
            "then throws locked exception")
    fun testLockMismatchCaseOnUnlockStateAlt() {
        // Given
        every { storageAdapter.unlock(any(), any()) } throws LockedException(testTfLockInfo)

        // When / Then
        try {
            terraformStateResource.unlockState(testProjectName, testTfLockInfo)

            fail("Should not just run")
        } catch (e: LockedException) {
            verify(exactly = 1) { storageAdapter.unlock(any(), any()) }
            verify(exactly = 0) { storageAdapter.forceUnlock(any()) }
            assertEquals(testTfLockInfo, e.lockInfo)
        }
    }

    @Test
    @DisplayName("Given force unlock, " +
            "when unlocking state (alt), " +
            "then returns an empty response with status OK")
    fun testForceUnlockCaseOnUnlockStateAlt() {
        // Given - nothing

        // When
        val result = terraformStateResource.unlockState(testProjectName, null)

        // Then
        verify(exactly = 0) { storageAdapter.unlock(any(), any()) }
        verify(exactly = 1) { storageAdapter.forceUnlock(any()) }
        assertNotNull(result)
        assertEquals(200, result.status)
    }

    @Test
    @DisplayName("Given force unlock on no specified element, " +
            "when unlocking state (alt), " +
            "then throws bad request exception")
    fun testNoElementForceUnlockCaseOnUnlockStateAlt() {
        // Given
        every { storageAdapter.forceUnlock(any()) } throws BadRequestException()

        // When / Then
        try {
            terraformStateResource.unlockState(testProjectName, null)

            fail("Should not just run")
        } catch (e: BadRequestException) {
            verify(exactly = 0) { storageAdapter.unlock(any(), any()) }
            verify(exactly = 1) { storageAdapter.forceUnlock(any()) }
        }
    }
}
