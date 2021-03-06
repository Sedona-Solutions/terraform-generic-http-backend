package fr.sedona.terraform.storage.database.repository

import com.fasterxml.jackson.databind.ObjectMapper
import fr.sedona.terraform.http.model.TfLockInfo
import fr.sedona.terraform.http.model.TfState
import fr.sedona.terraform.storage.model.State
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase
import org.jboss.logging.Logger
import java.util.*
import java.util.stream.Stream
import javax.enterprise.context.ApplicationScoped
import javax.transaction.Transactional


@ApplicationScoped
class TerraformStateRepository(
    private val objectMapper: ObjectMapper
) : PanacheRepositoryBase<State, String> {
    private val logger = Logger.getLogger(TerraformStateRepository::class.java)

    fun paginate(pageIndex: Int, pageSize: Int): Stream<State> {
        return findAll()
            .page<State>(pageIndex, pageSize)
            .stream<State>()
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
            "lastModified = ?2, locked = ?3, lockId = ?4, lockInfo = ?5, version = ?6, " +
                    "tfVersion = ?7, serial = ?8, state = ?9 WHERE name = ?1",
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
        val defaultState = TfState(
            version = 4,
            tfVersion = updatedLock.version,
            serial = 1,
            lineage = null,
            outputs = null,
            resources = null
        )
        val state = State(
            name = project,
            lastModified = Date(),
            locked = true,
            lockId = updatedLock.id,
            tfVersion = updatedLock.version,
            lockInfo = objectMapper.writeValueAsString(updatedLock),
            state = objectMapper.writeValueAsString(defaultState)
        )
        persist(state)

        logger.info("State for project $project created and locked")
        return state
    }

    @Transactional
    fun lock(project: String, stateToLock: State, lockInfo: TfLockInfo): State {
        logger.debug("Locking state for project $project")
        // Update the locking path
        val updatedLock = lockInfo.copy(
            path = project
        )

        // Update the existing state
        val stringifiedLockInfo = objectMapper.writeValueAsString(updatedLock)
        val updatedAt = Date()
        update(
            "lastModified = ?2, locked = true, lockId = ?3, lockInfo = ?4 WHERE name = ?1",
            project, updatedAt, lockInfo.id, stringifiedLockInfo
        )

        logger.info("State for project $project locked")
        return stateToLock.copy(
            lastModified = updatedAt,
            locked = true,
            lockId = updatedLock.id,
            tfVersion = updatedLock.version,
            lockInfo = stringifiedLockInfo
        )
    }

    @Transactional
    fun unlock(project: String, stateToUnlock: State): State {
        logger.debug("Unlocking state for project $project")
        update("locked = false, lockId = NULL, lockInfo = NULL WHERE name = ?1", project)

        logger.info("State for project $project unlocked")
        return stateToUnlock.copy(
            locked = false,
            lockId = null,
            lockInfo = null
        )
    }
}
