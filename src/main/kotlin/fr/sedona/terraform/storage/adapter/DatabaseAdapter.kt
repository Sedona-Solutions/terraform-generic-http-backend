package fr.sedona.terraform.storage.adapter

import com.fasterxml.jackson.databind.ObjectMapper
import fr.sedona.terraform.exception.StateAlreadyLockedException
import fr.sedona.terraform.exception.StateLockMismatchException
import fr.sedona.terraform.exception.StateNotLockedException
import fr.sedona.terraform.http.exception.ConflictException
import fr.sedona.terraform.http.exception.LockedException
import fr.sedona.terraform.http.exception.ResourceNotFoundException
import fr.sedona.terraform.http.extension.ensureIsLocked
import fr.sedona.terraform.http.extension.ensureIsNotLocked
import fr.sedona.terraform.http.extension.ensureLockOwnership
import fr.sedona.terraform.http.extension.toInternal
import fr.sedona.terraform.http.model.TfLockInfo
import fr.sedona.terraform.http.model.TfState
import fr.sedona.terraform.storage.database.repository.TerraformStateRepository
import fr.sedona.terraform.storage.model.State
import org.jboss.logging.Logger
import javax.ws.rs.BadRequestException
import kotlin.streams.toList

class DatabaseAdapter(
    private val repository: TerraformStateRepository,
    private val objectMapper: ObjectMapper
) : StorageAdapter {
    private val logger = Logger.getLogger(DatabaseAdapter::class.java)

    override fun listAll(): List<State> {
        return repository.streamAll()
            .toList()
    }

    override fun findById(project: String): State {
        try {
            return repository.findByIdOptional(project).get()

        } catch (e: NoSuchElementException) {
            logger.warn("State for project $project does not exist -> returning resource not found error")
            throw ResourceNotFoundException(project)
        }
    }

    override fun updateWithLock(project: String, lockId: String, state: TfState) {
        logger.info("Updating state for project $project with lock $lockId")
        try {
            val storedState = repository.findByIdOptional(project).get()

            logger.debug("State for project $project exists -> verifying if state is locked")
            storedState.ensureIsLocked()

            logger.debug("State for project $project is locked -> verifying lock ownership")
            storedState.ensureLockOwnership(lockId, objectMapper)

            logger.debug("State for project $project is not locked and owned -> updating state")
            repository.update(state.toInternal(project, lockId, storedState.lockInfo, objectMapper))

        } catch (e: NoSuchElementException) {
            logger.debug("State for project $project does not exist -> adding new state")
            repository.add(state.toInternal(project, null, objectMapper))

        } catch (e: StateNotLockedException) {
            logger.warn("State for project $project is not locked -> returning bad request error")
            throw BadRequestException()

        } catch (e: StateLockMismatchException) {
            logger.warn("State for project $project is already locked by someone else -> returning locked error")
            throw LockedException(e.lockInfo)
        }
    }

    override fun updateWithoutLock(project: String, state: TfState) {
        logger.info("Updating state for project $project without lock")
        try {
            repository.findByIdOptional(project).get()

            logger.debug("Updating state for project $project")
            repository.update(state.toInternal(project, null, null, objectMapper))

        } catch (e: NoSuchElementException) {
            logger.debug("State for project $project does not exist -> adding new state")
            repository.add(state.toInternal(project, null, objectMapper))
        }
    }

    override fun delete(project: String) {
        try {
            val storedState = repository.findByIdOptional(project).get()

            logger.debug("State for project $project exists -> verifying lock before deleting state")
            storedState.ensureIsNotLocked(objectMapper)

            logger.debug("State for project $project is not locked -> deleting state")
            repository.delete(storedState)
        } catch (e: NoSuchElementException) {
            logger.warn("State for project $project does not exist -> returning resource not found error")
            throw ResourceNotFoundException(project)
        }
    }

    override fun lock(project: String, lockInfo: TfLockInfo) {
        logger.debug("Locking state of project $project with lock ${lockInfo.id} -> verifying that state exists")
        try {
            val storedState = repository.findByIdOptional(project).get()

            logger.warn("State of project $project exists -> verifying that state is not locked")
            storedState.ensureIsNotLocked(objectMapper)

            logger.warn("State of project $project exists and is not locked -> locking state")
            repository.lock(project, storedState, lockInfo)

        } catch (e: NoSuchElementException) {
            logger.debug("State of project $project has no lock -> locking state")
            repository.createAndLock(project, lockInfo)

        } catch (e: StateAlreadyLockedException) {
            logger.warn("State of project $project is already locked -> returning locked error")
            throw LockedException(e.lockInfo)
        }
    }

    override fun unlock(project: String, lockInfo: TfLockInfo) {
        logger.info("Unlock state for project $project with lock ${lockInfo.id} -> verifying that state exists")
        try {
            val storedState = repository.findByIdOptional(project).get()

            logger.warn("State of project $project exists -> verifying that state is locked")
            storedState.ensureIsLocked()

            logger.warn("State of project $project exists and is locked -> verifying lock ownership")
            storedState.ensureLockOwnership(lockInfo.id!!, objectMapper)

            logger.debug("State of project $project is locked and owned -> unlocking state")
            repository.unlock(project, storedState)

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

    override fun forceUnlock(project: String) {
        logger.info("Forcing unlock state for project $project -> verifying that state exists")
        try {
            val storedState = repository.findByIdOptional(project).get()

            logger.debug("State of project $project exists -> unlocking state")
            repository.unlock(project, storedState)

        } catch (e: NoSuchElementException) {
            logger.warn("State of project $project does not exist -> returning bad request error")
            throw BadRequestException()
        }
    }
}
