package fr.sedona.terraform.repository

import fr.sedona.terraform.model.State
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase
import java.util.*
import javax.enterprise.context.ApplicationScoped


@ApplicationScoped
class TerraformStateRepository : PanacheRepositoryBase<State, String> {
    fun findByLockId(lockId: String): Optional<State> {
        return find("lockId", lockId)
            .firstResultOptional<State>()
    }

    fun findByLocked(locked: Boolean): List<State> {
        return list("locked", locked)
    }

    fun lockState(project: String, lockId: String, lockInfo: String): State {
        val state = State()
        state.name = project
        state.lastModified = Date()
        state.locked = true
        state.lockId = lockId
        state.lockInfo = lockInfo
        persist(state)
        return state
    }

    fun unlockState(project: String, stateToUnlock: State): State {
        stateToUnlock.locked = false
        stateToUnlock.lockId = null
        stateToUnlock.lockInfo = null
        persist(stateToUnlock)
        return stateToUnlock
    }
}
