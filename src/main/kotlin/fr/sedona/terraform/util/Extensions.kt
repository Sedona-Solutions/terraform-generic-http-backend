package fr.sedona.terraform.util

import com.fasterxml.jackson.databind.ObjectMapper
import fr.sedona.terraform.exception.StateAlreadyLockedException
import fr.sedona.terraform.exception.StateLockMismatchException
import fr.sedona.terraform.exception.StateNotLockedException
import fr.sedona.terraform.http.model.TfLockInfo
import fr.sedona.terraform.http.model.TfState
import fr.sedona.terraform.storage.model.State
import java.util.*


fun TfState.toInternal(
    project: String,
    lockId: String?,
    lockInfo: String?,
    objectMapper: ObjectMapper
): State {
    val state = State()
    state.name = project
    state.lastModified = Date()
    state.locked = lockInfo != null
    state.lockId = lockId
    state.lockInfo = lockInfo
    state.version = this.version
    state.tfVersion = this.tfVersion
    state.serial = this.serial
    state.state = objectMapper.writeValueAsString(this)
    return state
}

fun TfState.toInternal(
    project: String,
    lockInfo: TfLockInfo?,
    objectMapper: ObjectMapper
): State {
    return this.toInternal(project, lockInfo?.id, lockInfo?.toInternal(objectMapper), objectMapper)
}

fun TfLockInfo.toInternal(objectMapper: ObjectMapper): String {
    return objectMapper.writeValueAsString(this)
}

fun State.toTerraform(objectMapper: ObjectMapper): TfState {
    return objectMapper.readValue(this.state, TfState::class.java)
}

fun State.ensureIsNotLocked(objectMapper: ObjectMapper) {
    if (this.locked) {
        throw StateAlreadyLockedException(objectMapper.readValue(this.lockInfo, TfLockInfo::class.java))
    }
}

fun State.ensureIsLocked() {
    if (!this.locked) {
        throw StateNotLockedException()
    }
}

fun State.ensureLockOwnership(lockId: String, objectMapper: ObjectMapper) {
    if (this.lockId != lockId) {
        throw StateLockMismatchException(objectMapper.readValue(this.lockInfo, TfLockInfo::class.java))
    }
}
