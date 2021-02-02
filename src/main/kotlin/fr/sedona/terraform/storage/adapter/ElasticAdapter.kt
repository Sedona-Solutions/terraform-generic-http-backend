package fr.sedona.terraform.storage.adapter

import fr.sedona.terraform.http.model.TfLockInfo
import fr.sedona.terraform.http.model.TfState
import fr.sedona.terraform.storage.model.State
import org.jboss.logging.Logger

class ElasticAdapter : StorageAdapter {
    private val logger = Logger.getLogger(ElasticAdapter::class.java)

    override fun listAll(): List<State> {
        TODO("Not yet implemented")
    }

    override fun findById(project: String): State {
        TODO("Not yet implemented")
    }

    override fun updateWithLock(project: String, lockId: String, state: TfState) {
        TODO("Not yet implemented")
    }

    override fun updateWithoutLock(project: String, state: TfState) {
        TODO("Not yet implemented")
    }

    override fun delete(project: String) {
        TODO("Not yet implemented")
    }

    override fun lock(project: String, lockInfo: TfLockInfo) {
        TODO("Not yet implemented")
    }

    override fun unlock(project: String, lockInfo: TfLockInfo) {
        TODO("Not yet implemented")
    }

    override fun forceUnlock(project: String) {
        TODO("Not yet implemented")
    }
}
