package fr.sedona.terraform.storage.adapter

import com.fasterxml.jackson.databind.ObjectMapper
import fr.sedona.terraform.exception.StateAlreadyLockedException
import fr.sedona.terraform.exception.StateNotLockedException
import fr.sedona.terraform.http.model.TfLockInfo
import fr.sedona.terraform.http.model.TfState
import fr.sedona.terraform.storage.database.repository.TerraformStateRepository
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
import java.util.stream.Stream
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ExtendWith(MockKExtension::class)
class DatabaseAdapterTest {
    private lateinit var databaseAdapter: DatabaseAdapter

    @RelaxedMockK
    private lateinit var terraformStateRepository: TerraformStateRepository

    @RelaxedMockK
    private lateinit var objectMapper: ObjectMapper

    private val testTfState = TfState(
        version = 4,
        tfVersion = "0.14.0",
        serial = 1,
        lineage = "",
        outputs = null,
        resources = null
    )
    private val testTfLockInfo = TfLockInfo(
        id = "lock-id",
        version = "0.14.0",
        operation = "some-operation",
        created = Date(),
        info = null,
        who = "test@test.com",
        path = "test-project"
    )
    private lateinit var testUnlockedState: State
    private lateinit var testLockedState: State

    @BeforeTest
    fun setup() {
        // Init unlocked state
        testUnlockedState = State()
        testUnlockedState.name = "test-project"
        testUnlockedState.lastModified = Date()
        testUnlockedState.lockId = null
        testUnlockedState.lockInfo = null
        testUnlockedState.locked = false

        // Init locked state
        testLockedState = State()
        testLockedState.name = "test-project"
        testLockedState.lastModified = Date()
        testLockedState.lockId = "lock-id"
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

        every { terraformStateRepository.streamAll() } returns Stream.of(testUnlockedState)
        every { terraformStateRepository.findByIdOptional(any()) } returns Optional.of(testUnlockedState)
        every { terraformStateRepository.add(any()) } just Runs
        every { terraformStateRepository.update(any() as State) } just Runs
        every { terraformStateRepository.delete(any() as State) } just Runs
        every { terraformStateRepository.createAndLock(any(), any()) } returns testLockedState
        every { terraformStateRepository.lock(any(), any(), any()) } returns testLockedState
        every { terraformStateRepository.unlock(any(), any()) } returns testUnlockedState

        databaseAdapter = DatabaseAdapter(terraformStateRepository, objectMapper)
    }

    @Test
    @DisplayName("Given nominal case, when listing all states, then returns a list of states")
    fun testNominalCaseOnListAll() {
        // Given - nothing

        // When
        val result = databaseAdapter.listAll()

        // Then
        verify(exactly = 1) { terraformStateRepository.streamAll() }
        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals(testUnlockedState, result.firstOrNull())
    }
}
