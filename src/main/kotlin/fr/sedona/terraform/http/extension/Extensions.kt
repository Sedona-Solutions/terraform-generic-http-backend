package fr.sedona.terraform.http.extension

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import fr.sedona.terraform.http.model.*
import fr.sedona.terraform.model.State
import java.util.*

fun TfState.toInternal(): State {
    val objectMapper = ObjectMapper()
    val state = State()
    state.name = this.name
    state.lastModified = this.lastModified
    state.locked = this.locked
    state.lockInfo = this.lock?.toInternal(objectMapper)
    state.version = this.version
    state.tfVersion = this.tfVersion
    state.serial = this.serial
    state.lineage = this.lineage
    state.remote = this.remote?.toInternal(objectMapper)
    state.backend = this.backend?.toInternal(objectMapper)
    state.modules = this.modules.toInternal(objectMapper)
    return state
}

fun TfLockInfo.toInternal(objectMapper: ObjectMapper): String {
    return objectMapper.writeValueAsString(this)
}

fun TfRemoteState.toInternal(objectMapper: ObjectMapper): String {
    return objectMapper.writeValueAsString(this)
}

fun TfBackendState.toInternal(objectMapper: ObjectMapper): String {
    return objectMapper.writeValueAsString(this)
}

fun List<TfModuleState>.toInternal(objectMapper: ObjectMapper): String {
    return objectMapper.writeValueAsString(this)
}

fun State.toTerraform(): TfState {
    val objectMapper = ObjectMapper()
    return TfState(
        lastModified = this.lastModified ?: Date(),
        name = this.name ?: "N/A",
        locked = this.locked,
        lock = objectMapper.readValue(this.lockInfo, TfLockInfo::class.java),
        version = this.version,
        tfVersion = this.tfVersion,
        serial = this.serial,
        lineage = this.lineage,
        remote = objectMapper.readValue(this.remote, TfRemoteState::class.java),
        backend = objectMapper.readValue(this.backend, TfBackendState::class.java),
        modules = objectMapper.readValue(this.modules, object : TypeReference<List<TfModuleState>>() {})
    )
}
