package fr.sedona.terraform.http.exception

import fr.sedona.terraform.http.model.TfLockInfo
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ExtendWith(MockKExtension::class)
class HttpExceptionMapperTest {
    private lateinit var httpExceptionMapper: HttpExceptionMapper

    private val testProjectName = "test-project"
    private val testTfLockInfo = TfLockInfo(
        id = "lock-id",
        version = "0.14.0",
        operation = "some-operation",
        created = Date(),
        info = null,
        who = "test@test.com",
        path = "test-project"
    )

    @BeforeTest
    fun setup() {
        httpExceptionMapper = HttpExceptionMapper()
    }

    @Test
    @DisplayName("Given bad request exception, when converting to response, then returns a response with HTTP status 400")
    fun testBadRequestExceptionCaseOnToResponse() {
        // Given
        val testException = BadRequestException()

        // When
        val result = httpExceptionMapper.toResponse(testException)

        // Then
        assertNotNull(result)
        assertEquals(400, result.status)
    }

    @Test
    @DisplayName("Given resource not found exception, when converting to response, then returns a response with HTTP status 404")
    fun testResourceNotFoundExceptionCaseOnToResponse() {
        // Given
        val testException = ResourceNotFoundException(testProjectName)

        // When
        val result = httpExceptionMapper.toResponse(testException)

        // Then
        assertNotNull(result)
        assertEquals(404, result.status)
    }

    @Test
    @DisplayName("Given conflict exception, when converting to response, then returns a response with HTTP status 409")
    fun testConflictExceptionCaseOnToResponse() {
        // Given
        val testException = ConflictException(testTfLockInfo)

        // When
        val result = httpExceptionMapper.toResponse(testException)

        // Then
        assertNotNull(result)
        assertEquals(409, result.status)
    }

    @Test
    @DisplayName("Given locked exception, when converting to response, then returns a response with HTTP status 423")
    fun testLockedExceptionCaseOnToResponse() {
        // Given
        val testException = LockedException(testTfLockInfo)

        // When
        val result = httpExceptionMapper.toResponse(testException)

        // Then
        assertNotNull(result)
        assertEquals(423, result.status)
    }

    @Test
    @DisplayName("Given unexpected exception, when converting to response, then returns a response with HTTP status 500")
    fun testUnexpectedExceptionCaseOnToResponse() {
        // Given
        val testException = RuntimeException()

        // When
        val result = httpExceptionMapper.toResponse(testException)

        // Then
        assertNotNull(result)
        assertEquals(500, result.status)
    }
}
