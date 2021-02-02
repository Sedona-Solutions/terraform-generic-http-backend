package fr.sedona.terraform.repository

import com.fasterxml.jackson.databind.ObjectMapper
import fr.sedona.terraform.http.model.TfLockInfo
import fr.sedona.terraform.http.model.TfState
import fr.sedona.terraform.model.State
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase
import org.jboss.logging.Logger
import java.util.*
import javax.enterprise.context.ApplicationScoped
import javax.transaction.Transactional


@ApplicationScoped
class TerraformStateRepository(
    private val objectMapper: ObjectMapper
) : PanacheRepositoryBase<State, String> {
    private val logger = Logger.getLogger(TerraformStateRepository::class.java)

    fun findByLockId(lockId: String): Optional<State> {
        return find("lockId", lockId)
            .firstResultOptional<State>()
    }

    override fun findById(project: String): State {
        return findByIdOptional(project).get()
    }

    @Transactional
    fun add(stateToSave: State) {
        logger.debug("Persisting state for project ${stateToSave.name}")
        persist(stateToSave)
        logger.info("State for project ${stateToSave.name} persisted successfully")
    }

    @Transactional
    fun update(stateToUpdate: State) {
        logger.debug("Updating state for project ${stateToUpdate.name}")
        update(
            "lastModified = ?2, locked = ?3, lockId = ?4, lockInfo = ?5, version = ?6, tfVersion = ?7, serial = ?8, state = ?9 WHERE name = ?1",
            stateToUpdate.name,
            stateToUpdate.lastModified,
            stateToUpdate.locked,
            stateToUpdate.lockId,
            stateToUpdate.lockInfo,
            stateToUpdate.version,
            stateToUpdate.tfVersion,
            stateToUpdate.serial,
            stateToUpdate.state
        )
        logger.info("State for project ${stateToUpdate.name} updated successfully")
    }

    @Transactional
    override fun delete(stateToDelete: State) {
        logger.debug("Deleting state for project ${stateToDelete.name}")
        super.delete(stateToDelete)
        logger.info("State for project ${stateToDelete.name} deleted successfully")
    }

    @Transactional
    fun createAndLock(project: String, lockInfo: TfLockInfo): State {
        logger.debug("Creating and locking state for project $project")
        // Update the locking path
        val updatedLock = lockInfo.copy(
            path = project
        )

        // Create the default empty state
        val state = State()
        state.name = project
        state.lastModified = Date()
        state.locked = true
        state.lockId = updatedLock.id!!
        state.tfVersion = updatedLock.version
        state.lockInfo = objectMapper.writeValueAsString(updatedLock)
        val defaultState = TfState(
            version = 4,
            tfVersion = updatedLock.version,
            serial = 1,
            lineage = null,
            outputs = null,
            resources = null
        )
        state.state = objectMapper.writeValueAsString(defaultState)
        persist(state)

        logger.info("State for project $project created and locked")
        return state
    }

    @Transactional
    fun lock(project: String, stateToUpdate: State, lockInfo: TfLockInfo): State {
        logger.debug("Locking state for project $project")
        // Update the locking path
        val updatedLock = lockInfo.copy(
            path = project
        )

        // Update the existing state
        stateToUpdate.lastModified = Date()
        stateToUpdate.locked = true
        stateToUpdate.lockId = updatedLock.id!!
        stateToUpdate.tfVersion = updatedLock.version
        stateToUpdate.lockInfo = objectMapper.writeValueAsString(updatedLock)
        update(
            "lastModified = ?2, locked = true, lockId = ?3, lockInfo = ?4 WHERE name = ?1",
            project, Date(), lockInfo.id, objectMapper.writeValueAsString(updatedLock)
        )

        logger.info("State for project $project locked")
        return stateToUpdate
    }

    @Transactional
    fun unlock(project: String, stateToUnlock: State): State {
        logger.debug("Unlocking state for project $project")
        stateToUnlock.locked = false
        stateToUnlock.lockId = null
        stateToUnlock.lockInfo = null
        update("locked = false, lockId = NULL, lockInfo = NULL WHERE name = ?1", project)

        logger.info("State for project $project unlocked")
        return stateToUnlock
    }
}
