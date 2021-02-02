package fr.sedona.terraform.http.filter

import org.jboss.logging.Logger
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.container.ContainerRequestFilter
import javax.ws.rs.ext.Provider


@Provider
class LoggingFilter : ContainerRequestFilter {
    private val logger = Logger.getLogger(LoggingFilter::class.java)

    override fun filter(context: ContainerRequestContext) {
        logger.info("Received request ${context.method} ${context.uriInfo.path} with query params=${context.uriInfo.queryParameters}")
    }
}
