package fr.sedona.terraform.util

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import fr.sedona.terraform.exception.StateAlreadyLockedException
import fr.sedona.terraform.exception.StateLockMismatchException
import fr.sedona.terraform.exception.StateNotLockedException
import fr.sedona.terraform.http.model.TfLockInfo
import fr.sedona.terraform.http.model.TfState
import fr.sedona.terraform.storage.model.DEFAULT_SERIAL
import fr.sedona.terraform.storage.model.DEFAULT_STATE_VERSION
import fr.sedona.terraform.storage.model.State
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*
import kotlin.test.*

@ExtendWith(MockKExtension::class)
class ExtensionsTest {
    private val testProjectName = "test-project"
    private val testLockId = "lock-id"
    private val testTfVersion = "0.14.0"
    private val testTfState = TfState(
        version = DEFAULT_STATE_VERSION,
        tfVersion = testTfVersion,
        serial = DEFAULT_SERIAL,
        lineage = "",
        outputs = null,
        resources = null
    )
    private val testTfLockInfo = TfLockInfo(
        id = testLockId,
        version = testTfVersion,
        operation = "some-operation",
        created = Date(),
        info = "",
        who = "test@test.com",
        path = "test-project"
    )
    private val objectMapper = ObjectMapper()

    private lateinit var testUnlockedState: State
    private lateinit var testLockedState: State

    @BeforeTest
    fun setup() {
        // Init the object mapper
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)
        objectMapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
        objectMapper.registerKotlinModule()

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
        testLockedState.lockInfo = objectMapper.writeValueAsString(testTfLockInfo)
        testLockedState.locked = true
    }

    @Test
    @DisplayName(
        "Given nominal case (with lock), " +
                "when converting TfState to internal, " +
                "then returns the corresponding locked state"
    )
    fun testNominalCaseOnTfStateToInternal() {
        // Given
        val stringifiedLockInfo = objectMapper.writeValueAsString(testTfLockInfo)

        // When
        val result = testTfState.toInternal(testProjectName, testLockId, stringifiedLockInfo, objectMapper)

        // Then
        assertNotNull(result)
        assertEquals(testProjectName, result.name)
        assertEquals(testLockId, result.lockId)
        assertEquals(stringifiedLockInfo, result.lockInfo)
        assertEquals(DEFAULT_STATE_VERSION, result.version)
        assertEquals(testTfVersion, result.tfVersion)
        assertEquals(DEFAULT_SERIAL, result.serial)
    }

    @Test
    @DisplayName(
        "Given no lock, " +
                "when converting TfState to internal, " +
                "then returns the corresponding unlocked state"
    )
    fun testNoLockCaseOnTfStateToInternal() {
        // Given - nothing

        // When
        val result = testTfState.toInternal(testProjectName, null, null, objectMapper)

        // Then
        assertNotNull(result)
        assertEquals(testProjectName, result.name)
        assertNull(result.lockId)
        assertNull(result.lockInfo)
        assertEquals(DEFAULT_STATE_VERSION, result.version)
        assertEquals(testTfVersion, result.tfVersion)
        assertEquals(DEFAULT_SERIAL, result.serial)
    }

    @Test
    @DisplayName(
        "Given nominal case (with lock), " +
                "when converting TfState to internal (simple), " +
                "then returns the corresponding locked state"
    )
    fun testNominalCaseOnTfStateToInternalSimple() {
        // Given
        val stringifiedLockInfo = objectMapper.writeValueAsString(testTfLockInfo)

        // When
        val result = testTfState.toInternal(testProjectName, testTfLockInfo, objectMapper)

        // Then
        assertNotNull(result)
        assertEquals(testProjectName, result.name)
        assertEquals(testLockId, result.lockId)
        assertEquals(stringifiedLockInfo, result.lockInfo)
        assertEquals(DEFAULT_STATE_VERSION, result.version)
        assertEquals(testTfVersion, result.tfVersion)
        assertEquals(DEFAULT_SERIAL, result.serial)
    }

    @Test
    @DisplayName(
        "Given no lock, " +
                "when converting TfState to internal (simple), " +
                "then returns the corresponding unlocked state"
    )
    fun testNoLockCaseOnTfStateToInternalSimple() {
        // Given - nothing

        // When
        val result = testTfState.toInternal(testProjectName, null, objectMapper)

        // Then
        assertNotNull(result)
        assertEquals(testProjectName, result.name)
        assertNull(result.lockId)
        assertNull(result.lockInfo)
        assertEquals(DEFAULT_STATE_VERSION, result.version)
        assertEquals(testTfVersion, result.tfVersion)
        assertEquals(DEFAULT_SERIAL, result.serial)
    }

    @Test
    @DisplayName(
        "Given nominal case, " +
                "when converting TfLockInfo to internal, " +
                "then returns the lock info as string"
    )
    fun testNominalCaseOnTfLockInfoToInternal() {
        // Given
        val stringifiedLockInfo = objectMapper.writeValueAsString(testTfLockInfo)

        // When
        val result = testTfLockInfo.toInternal(objectMapper)

        // Then
        assertNotNull(result)
        assertEquals(stringifiedLockInfo, result)
    }

    @Test
    @DisplayName(
        "Given nominal case, " +
                "when ensuring is not locked, " +
                "then just run"
    )
    fun testNominalCaseOnEnsureIsNotLocked() {
        // Given - nothing

        // When / Then
        assertDoesNotThrow {
            testUnlockedState.ensureIsNotLocked(objectMapper)
        }
    }

    @Test
    @DisplayName(
        "Given state is locked, " +
                "when ensuring is not locked, " +
                "then throws state already locked exception"
    )
    fun testLockedCaseOnEnsureIsNotLocked() {
        // Given - nothing

        // When / Then
        try {
            testLockedState.ensureIsNotLocked(objectMapper)

            fail("Should not just run")
        } catch (e: StateAlreadyLockedException) {
            assertEquals(testTfLockInfo, e.lockInfo)
        }
    }

    @Test
    @DisplayName(
        "Given nominal case, " +
                "when ensuring is locked, " +
                "then just run"
    )
    fun testNominalCaseOnEnsureIsLocked() {
        // Given - nothing

        // When / Then
        assertDoesNotThrow {
            testLockedState.ensureIsLocked()
        }
    }

    @Test
    @DisplayName(
        "Given state is unlocked, " +
                "when ensuring is locked, " +
                "then throws state not locked exception"
    )
    fun testUnlockedCaseOnEnsureIsLocked() {
        // Given - nothing

        // When / Then
        assertThrows<StateNotLockedException> {
            testUnlockedState.ensureIsLocked()
        }
    }

    @Test
    @DisplayName(
        "Given nominal case, " +
                "when ensuring lock ownership, " +
                "then just run"
    )
    fun testNominalCaseOnEnsureLockOwnership() {
        // Given - nothing

        // When / Then
        assertDoesNotThrow {
            testLockedState.ensureLockOwnership(testLockId, objectMapper)
        }
    }

    @Test
    @DisplayName(
        "Given state is locked by someone else, " +
                "when ensuring lock ownership, " +
                "then throws state lock mismatch exception"
    )
    fun testLockMismatchedCaseOnEnsureLockOwnership() {
        // Given
        val anotherLockId = "another-lock-id"

        // When / Then
        try {
            testLockedState.ensureLockOwnership(anotherLockId, objectMapper)

            fail("Should not just run")
        } catch (e: StateLockMismatchException) {
            assertEquals(testTfLockInfo, e.lockInfo)
        }
    }
}
