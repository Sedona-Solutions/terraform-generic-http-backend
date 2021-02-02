package fr.sedona.terraform.model

import java.util.*
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
    var version: Int = 0
    var tfVersion: String? = null
    var serial: Int = 0
    var lineage: String? = null
    var remote: String? = null
    var backend: String? = null
    var modules: String? = null
}
