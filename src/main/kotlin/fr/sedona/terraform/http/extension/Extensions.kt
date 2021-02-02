package fr.sedona.terraform.http.extension

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import fr.sedona.terraform.config.CustomObjectMapperCustomizer
import fr.sedona.terraform.http.model.*
import fr.sedona.terraform.model.State
import java.util.Date

fun TfState.toInternal(): State {
    val objectMapper = ObjectMapper()
    val state = State()
    state.name = this.name
    state.lastModified = this.lastModified
    state.locked = this.locked
    state.lockId = this.lock?.id
    state.lockInfo = this.lock?.toInternal(objectMapper)
    state.version = this.version
    state.tfVersion = this.tfVersion
    state.serial = this.serial
    state.lineage = this.lineage
    state.remote = this.remote?.toInternal(objectMapper)
    state.backend = this.backend?.toInternal(objectMapper)
    state.modules = this.modules?.toInternal(objectMapper)
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
    CustomObjectMapperCustomizer.customize(objectMapper)
    return TfState(
        lastModified = this.lastModified ?: Date(),
        name = this.name ?: "N/A",
        locked = this.locked,
        lock = this.lockInfo?.let { objectMapper.readValue(it, TfLockInfo::class.java) },
        version = this.version,
        tfVersion = this.tfVersion,
        serial = this.serial,
        lineage = this.lineage,
        remote = this.remote?.let { objectMapper.readValue(it, TfRemoteState::class.java) },
        backend = this.backend?.let { objectMapper.readValue(it, TfBackendState::class.java) },
        modules = this.modules?.let { objectMapper.readValue(it, object : TypeReference<List<TfModuleState>>() {}) }
    )
}
