package fr.sedona.terraform.http.model

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * TfState is a Terraform state
 */
data class TfState(
    /**
     * Version is the state file protocol version.
     */
    var version: Int,

    /**
     * TFVersion is the version of Terraform that wrote this state.
     */
    @JsonProperty("terraform_version")
    var tfVersion: String?,

    /**
     * Serial is incremented on any operation that modifies
     * the State file. It is used to detect potentially conflicting
     * updates.
     */
    var serial: Int,

    /**
     * Lineage is set when a new, blank state is created and then
     * never updated. This allows us to determine whether the serials
     * of two states can be meaningfully compared.
     * Apart from the guarantee that collisions between two lineages
     * are very unlikely, this value is opaque and external callers
     * should only compare lineage strings byte-for-byte for equality.
     */
    var lineage: String?,

    val outputs: Map<String, Any>?,

    val resources: List<Any>?
)
