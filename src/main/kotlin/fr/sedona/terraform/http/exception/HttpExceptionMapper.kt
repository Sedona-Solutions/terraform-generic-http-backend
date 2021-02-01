package fr.sedona.terraform.http.exception

import java.util.*
import javax.ws.rs.core.Response
import javax.ws.rs.ext.ExceptionMapper
import javax.ws.rs.ext.Provider

@Provider
class HttpExceptionMapper : ExceptionMapper<RuntimeException> {
    override fun toResponse(exception: RuntimeException?): Response {
        return when (exception) {
            // 404 - Resource Not Found
            is ResourceNotFoundException -> Response.status(Response.Status.NOT_FOUND)
                .entity("Project ${exception.name} not found")
                .build()

            // 409 - Conflict
            is ConflictException -> Response.status(Response.Status.CONFLICT)
                .entity(exception.lockInfo)
                .build()

            // 423 - Locked
            is LockedException -> Response.status(423)
                .entity(exception.lockInfo)
                .build()

            // 500 - Internal Server Error
            else -> Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("An unexpected error occurred")
                .build()
        }
    }
}
