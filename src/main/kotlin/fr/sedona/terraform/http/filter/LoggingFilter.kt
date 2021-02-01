package fr.sedona.terraform.http.filter

import java.util.logging.Logger
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.container.ContainerRequestFilter
import javax.ws.rs.ext.Provider


@Provider
class LoggingFilter : ContainerRequestFilter {
    private val logger = Logger.getLogger(LoggingFilter::class.java.toString())

    override fun filter(context: ContainerRequestContext) {
        logger.fine("Received request ${context.method} ${context.uriInfo.path}")
    }
}
