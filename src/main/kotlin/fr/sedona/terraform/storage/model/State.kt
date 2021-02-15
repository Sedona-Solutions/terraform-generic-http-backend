package fr.sedona.terraform.storage.model

import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id

const val DEFAULT_STATE_VERSION = 4
const val DEFAULT_SERIAL = 1

@Entity
data class State(
    @Id val name: String? = null,
    val lastModified: Date? = null,
    val locked: Boolean = false,
    val lockId: String? = null,
    val lockInfo: String? = null,
    val version: Int = DEFAULT_STATE_VERSION,
    val tfVersion: String? = null,
    val serial: Int = DEFAULT_SERIAL,
    @Column(columnDefinition = "TEXT") val state: String? = null
)
