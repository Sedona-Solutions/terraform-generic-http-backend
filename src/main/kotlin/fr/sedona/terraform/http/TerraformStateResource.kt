package fr.sedona.terraform.http

import com.fasterxml.jackson.databind.ObjectMapper
import fr.sedona.terraform.http.annotation.LOCK
import fr.sedona.terraform.http.annotation.UNLOCK
import fr.sedona.terraform.http.exception.ConflictException
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
import javax.ws.rs.core.Response


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
        try {
            return tfStateRepository.findByIdOptional(project).get().toTerraform()
        } catch (e: NoSuchElementException) {
            throw ResourceNotFoundException(project)
        }
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
        try {
            val storedState = tfStateRepository.findByIdOptional(project).get()

            logger.fine("State for project $project exists -> verifying lock before deleting state")
            ensureStateIsNotLocked(storedState)

            logger.fine("State for project $project is not locked -> deleting state")
            doDeleteState(storedState)
        } catch (e: NoSuchElementException) {
            logger.warning("State for project $project does not exist -> throwing exception")
            throw ResourceNotFoundException(project)
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
        @PathParam("project") project: String,
        lockInfo: TfLockInfo
    ): Response {
        logger.fine("Received LOCK for TF state of project $project")
        return doLockState(project, lockInfo)
    }

    @POST
    @Path("/{project}/lock")
    fun lockStateAlternative(
        @PathParam("project") project: String,
        lockInfo: TfLockInfo
    ): Response {
        logger.fine("Received POST to lock TF state of project $project")
        return doLockState(project, lockInfo)
    }

    private fun doLockState(project: String, lockInfo: TfLockInfo): Response {
        logger.fine("Verifying if state of project $project is already locked")
        val objectMapper = ObjectMapper()
        try {
            // If a lock already exists, return HTTP 'locked' response
            val storedStateWithLock = tfStateRepository.findByLockId(lockInfo.id!!).get()

            logger.warning("State of project $project is already locked -> returning HTTP locked response")

            throw LockedException(objectMapper.readValue(storedStateWithLock.lockInfo, TfLockInfo::class.java))
        } catch (e: NoSuchElementException) {
            logger.fine("State of project $project has no lock -> locking state")
            tfStateRepository.lockState(project, lockInfo.id!!, objectMapper.writeValueAsString(lockInfo))
            logger.info("State of project $project locked successfully")
            return Response.ok()
                .build()
        }
    }

    @UNLOCK
    @Path("/{project}")
    fun unlockState(
        @PathParam("project") project: String,
        lockInfo: TfLockInfo
    ): Response {
        logger.fine("Received UNLOCK for TF state of project $project")
        return doUnlockState(project, lockInfo)
    }

    @POST
    @Path("/{project}/unlock")
    fun unlockStateAlternative(
        @PathParam("project") project: String,
        lockInfo: TfLockInfo
    ): Response {
        logger.fine("Received POST to unlock TF state of project $project")
        return doUnlockState(project, lockInfo)
    }

    private fun doUnlockState(project: String, lockInfo: TfLockInfo): Response {
        logger.fine("Verifying if state of project $project is locked")
        val objectMapper = ObjectMapper()
        try {
            // If a lock still exist, unlock the state
            val storedState = tfStateRepository.findByIdOptional(project).get()
            if (!storedState.locked) {
                logger.warning("State of project $project exists but is not locked -> returning an error")
                throw NoSuchElementException()
            }

            if (storedState.lockId != lockInfo.id) {
                logger.warning("State of project $project is locked by someone else -> returning HTTP locked response")
                throw LockedException(objectMapper.readValue(storedState.lockInfo, TfLockInfo::class.java))
            }

            logger.fine("State of project $project is locked -> unlocking state")
            tfStateRepository.unlockState(project, storedState)
            logger.info("State of project $project unlocked successfully")
            return Response.ok()
                .build()
        } catch (e: NoSuchElementException) {
            logger.warning("State of project $project is already unlocked -> returning HTTP conflict response")
            throw ConflictException(lockInfo)
        }
    }
}
