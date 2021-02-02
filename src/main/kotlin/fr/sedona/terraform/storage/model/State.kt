package fr.sedona.terraform.storage.model

import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id


@Entity
class State {
    @Id
    var name: String? = null

    var lastModified: Date? = null

    var locked: Boolean = false

    var lockId: String? = null

    var lockInfo: String? = null

    var version: Int = 4

    var tfVersion: String? = null

    var serial: Int = 1

    @Column(columnDefinition = "TEXT")
    var state: String? = null
}
