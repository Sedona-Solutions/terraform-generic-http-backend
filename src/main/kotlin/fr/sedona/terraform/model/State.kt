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
    var lockInfo: String? = null
    var version: Number = 0
    var tfVersion: String? = null
    var serial: Number = 0
    var lineage: String? = null
    var remote: String? = null
    var backend: String? = null
    var modules: String? = null
}
