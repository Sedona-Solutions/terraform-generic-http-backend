package fr.sedona.terraform.storage.adapter

import fr.sedona.terraform.http.model.TfLockInfo
import fr.sedona.terraform.http.model.TfState
import fr.sedona.terraform.storage.model.State

interface StorageAdapter {
    fun listAll(): List<State>

    fun findById(project: String): State

    fun updateWithLock(project: String, lockId: String, state: TfState)

    fun updateWithoutLock(project: String, state: TfState)

    fun delete(project: String)

    fun lock(project: String, lockInfo: TfLockInfo)

    fun unlock(project: String, lockInfo: TfLockInfo)

    fun forceUnlock(project: String)
}
