package fr.sedona.terraform.http

import com.fasterxml.jackson.databind.ObjectMapper
import fr.sedona.terraform.http.annotation.LOCK
import fr.sedona.terraform.http.annotation.UNLOCK
import fr.sedona.terraform.http.exception.LockedException
import fr.sedona.terraform.http.exception.ResourceNotFoundException
import fr.sedona.terraform.http.extension.toInternal
import fr.sedona.terraform.http.extension.toTerraform
import fr.sedona.terraform.http.model.TfLockInfo
import fr.sedona.terraform.http.model.TfState
import fr.sedona.terraform.model.State
import fr.sedona.terraform.repository.TerraformStateRepository
import java.util.logging.Logger
import java.util.stream.Collectors
import javax.enterprise.inject.Default
import javax.inject.Inject
import javax.ws.rs.*
import javax.ws.rs.core.MediaType


@Path("/tf-state")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class TerraformStateResource {
    private val logger = Logger.getLogger(TerraformStateResource::class.java.toString())

    @Inject
    @field: Default
    lateinit var tfStateRepository: TerraformStateRepository

    @GET
    fun listAllStates(): List<TfState> {
        logger.fine("Received GET for all TF states")
        return tfStateRepository.streamAll()
            .map { it.toTerraform() }
            .collect(Collectors.toList())
    }

    @GET
    @Path("/{project}")
    fun getState(
        @PathParam("project") project: String
    ): TfState {
        logger.fine("Received GET for TF state of project $project")
        val result = tfStateRepository.findByIdOptional(project)
        if (result.isEmpty) {
            throw ResourceNotFoundException(project)
        }

        return result.get().toTerraform()
    }

    @POST
    @Path("/{project}")
    fun updateState(
        @PathParam("project") project: String,
        @QueryParam("lockId") lockId: String,
        state: TfState
    ) {
        logger.fine("Received POST for TF state of project $project")
        val storedState = tfStateRepository.findById(project)
        if (storedState == null) {
            logger.fine("State for project $project does not exist -> adding new state")
            doPersistState(state)
        } else {
            logger.fine("State for project $project exists -> verifying lock before updating state")
            ensureStateIsNotLocked(storedState)

            logger.fine("State for project $project is not locked -> updating state")
            doPersistState(state)
        }
        // TODO Check what to return to Terraform
    }

    private fun ensureStateIsNotLocked(state: State) {
        if (state.locked) {
            logger.warning("State for project ${state.name} is already locked")
            val objectMapper = ObjectMapper()
            throw LockedException(objectMapper.readValue(state.lockInfo, TfLockInfo::class.java))
        }
    }

    private fun doPersistState(state: TfState) {
        logger.fine("Persisting state for project ${state.name}")
        tfStateRepository.persist(state.toInternal())
        logger.info("State for project ${state.name} persisted successfully")
    }

    @DELETE
    @Path("/{project}")
    fun deleteState(
        @PathParam("project") project: String
    ) {
        logger.fine("Received DELETE for TF state of project $project")
        val storedState = tfStateRepository.findById(project)
        if (storedState == null) {
            logger.fine("State for project $project does not exist -> throwing exception")
            throw ResourceNotFoundException(project)
        } else {
            logger.fine("State for project $project exists -> verifying lock before deleting state")
            ensureStateIsNotLocked(storedState)

            logger.fine("State for project $project is not locked -> deleting state")
            doDeleteState(storedState)
        }
        // TODO Check what to return to Terraform
    }

    private fun doDeleteState(stateToDelete: State) {
        logger.fine("Deleting state for project ${stateToDelete.name}")
        tfStateRepository.delete(stateToDelete)
        logger.info("State for project ${stateToDelete.name} deleted successfully")
    }

    @LOCK
    @Path("/{project}")
    fun lockState(
        @PathParam("project") project: String
    ): TfLockInfo? {
        logger.fine("Received LOCK for TF state of project $project")
        return doLockState(project)
    }

    @POST
    @Path("/{project}/lock")
    fun lockStateAlternative(
        @PathParam("project") project: String
    ): TfLockInfo? {
        logger.fine("Received POST to lock TF state of project $project")
        return doLockState(project)
    }

    private fun doLockState(project: String): TfLockInfo? {
        // TODO: implement lock mechanism
        return null
    }

    @UNLOCK
    @Path("/{project}")
    fun unlockState(
        @PathParam("project") project: String
    ): TfLockInfo? {
        logger.fine("Received UNLOCK for TF state of project $project")
        return doUnlockState(project)
    }

    @POST
    @Path("/{project}/unlock")
    fun unlockStateAlternative(
        @PathParam("project") project: String
    ): TfLockInfo? {
        logger.fine("Received POST to unlock TF state of project $project")
        return doUnlockState(project)
    }

    private fun doUnlockState(project: String): TfLockInfo? {
        // TODO: implement unlock mechanism
        return null
    }
}
