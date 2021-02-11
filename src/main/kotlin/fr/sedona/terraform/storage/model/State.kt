package fr.sedona.terraform.storage.model

import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id

const val DEFAULT_STATE_VERSION = 4
const val DEFAULT_SERIAL = 1

@Entity
open class State {
    @Id
    open var name: String? = null

    open var lastModified: Date? = null

    open var locked: Boolean = false

    open var lockId: String? = null

    open var lockInfo: String? = null

    open var version: Int = DEFAULT_STATE_VERSION

    open var tfVersion: String? = null

    open var serial: Int = DEFAULT_SERIAL

    @Column(columnDefinition = "TEXT")
    open var state: String? = null
}
