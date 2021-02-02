package fr.sedona.terraform.http

import com.fasterxml.jackson.databind.ObjectMapper
import fr.sedona.terraform.exception.StateAlreadyLockedException
import fr.sedona.terraform.exception.StateLockMismatchException
import fr.sedona.terraform.exception.StateNotLockedException
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
import org.jboss.logging.Logger
import javax.enterprise.inject.Default
import javax.inject.Inject
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import kotlin.streams.toList


@Path("/tf-state")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class TerraformStateResource(
    private val objectMapper: ObjectMapper
) {
    private val logger = Logger.getLogger(TerraformStateResource::class.java)

    @Inject
    @field: Default
    lateinit var tfStateRepository: TerraformStateRepository

    @GET
    fun listAllStates(): List<TfState> {
        logger.debug("Received GET for all TF states")
        return tfStateRepository.streamAll()
            .map { it.toTerraform(objectMapper) }
            .toList()
    }

    @GET
    @Path("/{project}")
    fun getState(
        @PathParam("project") project: String
    ): TfState {
        logger.debug("Received GET for TF state of project $project")
        try {
            return tfStateRepository.findById(project)
                .toTerraform(objectMapper)

        } catch (e: NoSuchElementException) {
            logger.warn("State for project $project does not exist -> returning resource not found error")
            throw ResourceNotFoundException(project)
        }
    }

    @POST
    @Path("/{project}")
    fun updateState(
        @PathParam("project") project: String,
        @QueryParam("ID") lockId: String?,
        state: TfState
    ): Response {
        logger.debug("Received POST for TF state of project $project")
        return if (lockId != null) {
            updateStateWithLockId(project, lockId, state)
        } else {
            updateStateWithoutLockId(project, state)
        }
    }

    private fun updateStateWithLockId(
        project: String,
        lockId: String,
        state: TfState
    ): Response {
        logger.info("Updating state for project $project with lock $lockId")
        try {
            val storedState = tfStateRepository.findById(project)

            logger.debug("State for project $project exists -> verifying if state is locked")
            ensureStateIsLocked(storedState)

            logger.debug("State for project $project is locked -> verifying lock ownership")
            ensureStateLockMatches(storedState, lockId)

            logger.debug("State for project $project is not locked -> updating state")
            tfStateRepository.update(state.toInternal(project, lockId, storedState.lockInfo, objectMapper))

        } catch (e: NoSuchElementException) {
            logger.debug("State for project $project does not exist -> adding new state")
            tfStateRepository.add(state.toInternal(project, null, objectMapper))

        } catch (e: StateNotLockedException) {
            logger.warn("State for project $project is not locked -> returning bad request error")
            throw BadRequestException()

        } catch (e: StateLockMismatchException) {
            logger.warn("State for project $project is already locked by someone else -> returning locked error")
            throw LockedException(e.lockInfo)
        }

        return Response.ok()
            .build()
    }

    private fun updateStateWithoutLockId(
        project: String,
        state: TfState
    ): Response {
        logger.info("Updating state for project $project without lock")
        try {
            tfStateRepository.findById(project)

            logger.debug("Updating state for project $project")
            tfStateRepository.update(state.toInternal(project, null, null, objectMapper))

        } catch (e: NoSuchElementException) {
            logger.debug("State for project $project does not exist -> adding new state")
            tfStateRepository.add(state.toInternal(project, null, objectMapper))
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
        try {
            val storedState = tfStateRepository.findById(project)

            logger.debug("State for project $project exists -> verifying lock before deleting state")
            ensureStateIsNotLocked(storedState)

            logger.debug("State for project $project is not locked -> deleting state")
            tfStateRepository.delete(storedState)

            // Return 200 when everything is done
            return Response.ok()
                .build()
        } catch (e: NoSuchElementException) {
            logger.warn("State for project $project does not exist -> returning resource not found error")
            throw ResourceNotFoundException(project)
        }
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
        logger.debug("Locking state of project $project with lock ${lockInfo.id} -> verifying that state exists")
        try {
            val storedState = tfStateRepository.findById(project)

            logger.warn("State of project $project exists -> verifying that state is not locked")
            ensureStateIsNotLocked(storedState)

            logger.warn("State of project $project exists and is not locked -> locking state")
            tfStateRepository.lock(project, storedState, lockInfo)

            return Response.ok()
                .build()

        } catch (e: NoSuchElementException) {
            logger.debug("State of project $project has no lock -> locking state")
            tfStateRepository.createAndLock(project, lockInfo)
            return Response.ok()
                .build()

        } catch (e: StateAlreadyLockedException) {
            logger.warn("State of project $project is already locked -> returning locked error")
            throw LockedException(e.lockInfo)
        }
    }

    @UNLOCK
    @Path("/{project}")
    fun unlockState(
        @PathParam("project") project: String,
        lockInfo: TfLockInfo?
    ): Response {
        logger.debug("Received UNLOCK for TF state of project $project")
        return if (lockInfo != null) {
            doUnlockState(project, lockInfo)
        } else {
            doForceUnlockState(project)
        }
    }

    @POST
    @Path("/{project}/unlock")
    fun unlockStateAlternative(
        @PathParam("project") project: String,
        lockInfo: TfLockInfo?
    ): Response {
        logger.debug("Received POST to unlock TF state of project $project")
        return if (lockInfo != null) {
            doUnlockState(project, lockInfo)
        } else {
            doForceUnlockState(project)
        }
    }

    private fun doUnlockState(project: String, lockInfo: TfLockInfo): Response {
        logger.info("Unlock state for project $project with lock ${lockInfo.id} -> verifying that state exists")
        try {
            val storedState = tfStateRepository.findById(project)

            logger.warn("State of project $project exists -> verifying that state is locked")
            ensureStateIsLocked(storedState)

            logger.warn("State of project $project exists and is locked -> verifying lock ownership")
            ensureStateLockMatches(storedState, lockInfo.id!!)

            logger.debug("State of project $project is locked -> unlocking state")
            tfStateRepository.unlock(project, storedState)

            return Response.ok()
                .build()

        } catch (e: NoSuchElementException) {
            logger.warn("State of project $project is already unlocked -> returning conflict error")
            throw ConflictException(lockInfo)

        } catch (e: StateNotLockedException) {
            logger.warn("State of project $project exists but is not locked -> returning conflict error")
            throw ConflictException(lockInfo)

        } catch (e: StateLockMismatchException) {
            logger.warn("State of project $project is locked by someone else -> returning locked error")
            throw LockedException(e.lockInfo)
        }
    }

    private fun doForceUnlockState(project: String): Response {
        logger.info("Forcing unlock state for project $project -> verifying that state exists")
        try {
            val storedState = tfStateRepository.findById(project)

            logger.debug("State of project $project exists -> unlocking state")
            tfStateRepository.unlock(project, storedState)

            return Response.ok()
                .build()

        } catch (e: NoSuchElementException) {
            logger.warn("State of project $project does not exist -> returning bad request error")
            throw BadRequestException()
        }
    }

    private fun ensureStateIsNotLocked(state: State) {
        if (state.locked) {
            throw StateAlreadyLockedException(objectMapper.readValue(state.lockInfo, TfLockInfo::class.java))
        }
    }

    private fun ensureStateIsLocked(state: State) {
        if (!state.locked) {
            throw StateNotLockedException()
        }
    }

    private fun ensureStateLockMatches(state: State, lockId: String) {
        if (state.lockId != lockId) {
            throw StateLockMismatchException(objectMapper.readValue(state.lockInfo, TfLockInfo::class.java))
        }
    }
}
