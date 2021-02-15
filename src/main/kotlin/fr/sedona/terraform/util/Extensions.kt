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
    return State(
        name = project,
        lastModified = Date(),
        locked = lockInfo != null,
        lockId = lockId,
        lockInfo = lockInfo,
        version = this.version,
        tfVersion = this.tfVersion,
        serial = this.serial,
        state = objectMapper.writeValueAsString(this)
    )
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
