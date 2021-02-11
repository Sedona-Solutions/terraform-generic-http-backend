package fr.sedona.terraform.http.exception

import fr.sedona.terraform.http.TerraformStateResource
import org.jboss.logging.Logger
import javax.ws.rs.core.Response
import javax.ws.rs.ext.ExceptionMapper
import javax.ws.rs.ext.Provider

const val LOCKED_STATUS_CODE = 423

@Provider
class HttpExceptionMapper : ExceptionMapper<RuntimeException> {
    private val logger = Logger.getLogger(TerraformStateResource::class.java)

    override fun toResponse(exception: RuntimeException?): Response {
        return when (exception) {
            // 400 - Bad Request
            is BadRequestException -> Response.status(Response.Status.BAD_REQUEST)
                .entity("Bad request")
                .build()

            // 404 - Resource Not Found
            is ResourceNotFoundException -> Response.status(Response.Status.NOT_FOUND)
                .entity("Project ${exception.name} not found")
                .build()

            // 409 - Conflict
            is ConflictException -> Response.status(Response.Status.CONFLICT)
                .entity(exception.lockInfo)
                .build()

            // 423 - Locked
            is LockedException -> Response.status(LOCKED_STATUS_CODE)
                .entity(exception.lockInfo)
                .build()

            // 500 - Internal Server Error
            else -> {
                logger.warn("Unexpected exception thrown: $exception")
                Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("An unexpected error occurred")
                    .build()
            }
        }
    }
}
