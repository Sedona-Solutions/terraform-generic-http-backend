package fr.sedona.terraform.util

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import fr.sedona.terraform.exception.StateAlreadyLockedException
import fr.sedona.terraform.exception.StateLockMismatchException
import fr.sedona.terraform.exception.StateNotLockedException
import fr.sedona.terraform.http.model.TfLockInfo
import fr.sedona.terraform.storage.model.State
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.fail

@ExtendWith(MockKExtension::class)
class ExtensionsTest {
    private val testProjectName = "test-project"
    private val testLockId = "lock-id"
    private val testTfLockInfo = TfLockInfo(
        id = testLockId,
        version = "0.14.0",
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
    @DisplayName("Given nominal case, when ensuring is not locked, then just run")
    fun testNominalCaseOnEnsureIsNotLocked() {
        // Given - nothing

        // When / Then
        assertDoesNotThrow {
            testUnlockedState.ensureIsNotLocked(objectMapper)
        }
    }

    @Test
    @DisplayName("Given state is locked, when ensuring is not locked, then throws state already locked exception")
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
    @DisplayName("Given nominal case, when ensuring is locked, then just run")
    fun testNominalCaseOnEnsureIsLocked() {
        // Given - nothing

        // When / Then
        assertDoesNotThrow {
            testLockedState.ensureIsLocked()
        }
    }

    @Test
    @DisplayName("Given state is unlocked, when ensuring is locked, then throws state not locked exception")
    fun testUnlockedCaseOnEnsureIsLocked() {
        // Given - nothing

        // When / Then
        assertThrows<StateNotLockedException> {
            testUnlockedState.ensureIsLocked()
        }
    }

    @Test
    @DisplayName("Given nominal case, when ensuring lock ownership, then just run")
    fun testNominalCaseOnEnsureLockOwnership() {
        // Given - nothing

        // When / Then
        assertDoesNotThrow {
            testLockedState.ensureLockOwnership(testLockId, objectMapper)
        }
    }

    @Test
    @DisplayName("Given state is locked by someone else, when ensuring lock ownership, then throws state lock mismatch exception")
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
