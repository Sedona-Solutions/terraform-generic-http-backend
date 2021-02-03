package fr.sedona.terraform.http

import com.fasterxml.jackson.databind.ObjectMapper
import fr.sedona.terraform.http.annotation.LOCK
import fr.sedona.terraform.http.annotation.UNLOCK
import fr.sedona.terraform.util.toTerraform
import fr.sedona.terraform.http.model.TfLockInfo
import fr.sedona.terraform.http.model.TfState
import fr.sedona.terraform.storage.adapter.StorageAdapter
import org.jboss.logging.Logger
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response


@Path("/tf-state")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class TerraformStateResource(
    private val storageAdapter: StorageAdapter,
    private val objectMapper: ObjectMapper
) {
    private val logger = Logger.getLogger(TerraformStateResource::class.java)

    @GET
    fun listAllStates(): List<TfState> {
        logger.debug("Received GET for all TF states")
        return storageAdapter.listAll()
            .map { it.toTerraform(objectMapper) }
    }

    @GET
    @Path("/{project}")
    fun getState(
        @PathParam("project") project: String
    ): TfState {
        logger.debug("Received GET for TF state of project $project")
        return storageAdapter.findById(project)
            .toTerraform(objectMapper)
    }

    @POST
    @Path("/{project}")
    fun updateState(
        @PathParam("project") project: String,
        @QueryParam("ID") lockId: String?,
        state: TfState
    ): Response {
        logger.debug("Received POST for TF state of project $project")
        if (lockId != null) {
            storageAdapter.updateWithLock(project, lockId, state)
        } else {
            storageAdapter.updateWithoutLock(project, state)
        }
        return Response.ok()
            .build()
    }

    @DELETE
    @Path("/{project}")
    fun deleteState(
        @PathParam("project") project: String
    ): Response {
        logger.debug("Received DELETE for TF state of project $project")
        storageAdapter.delete(project)
        return Response.ok()
            .build()
    }

    @LOCK
    @Path("/{project}")
    fun lockState(
        @PathParam("project") project: String,
        lockInfo: TfLockInfo
    ): Response {
        logger.debug("Received LOCK for TF state of project $project")
        return doLockState(project, lockInfo)
    }

    @POST
    @Path("/{project}/lock")
    fun lockStateAlternative(
        @PathParam("project") project: String,
        lockInfo: TfLockInfo
    ): Response {
        logger.debug("Received POST to lock TF state of project $project")
        return doLockState(project, lockInfo)
    }

    private fun doLockState(project: String, lockInfo: TfLockInfo): Response {
        storageAdapter.lock(project, lockInfo)
        return Response.ok()
            .build()
    }

    @UNLOCK
    @Path("/{project}")
    fun unlockState(
        @PathParam("project") project: String,
        lockInfo: TfLockInfo?
    ): Response {
        logger.debug("Received UNLOCK for TF state of project $project")
        return doUnlockState(project, lockInfo)
    }

    @POST
    @Path("/{project}/unlock")
    fun unlockStateAlternative(
        @PathParam("project") project: String,
        lockInfo: TfLockInfo?
    ): Response {
        logger.debug("Received POST to unlock TF state of project $project")
        return doUnlockState(project, lockInfo)
    }

    private fun doUnlockState(project: String, lockInfo: TfLockInfo?): Response {
        if (lockInfo != null) {
            storageAdapter.unlock(project, lockInfo)
        } else {
            storageAdapter.forceUnlock(project)
        }
        return Response.ok()
            .build()
    }
}
