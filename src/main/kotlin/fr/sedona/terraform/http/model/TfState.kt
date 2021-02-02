package fr.sedona.terraform.http.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*

/**
 * TfState is a Terraform state
 */
data class TfState(
    // FIXME LastModified time.Time `json:"last_modified"`
    @JsonProperty("last_modified")
    var lastModified: Date,

    var name: String,

    /**
     * Keep lock info
     */
    var locked: Boolean,
    var lock: TfLockInfo?,

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

    /**
     * Remote is used to track the metadata required to
     * pull and push state files from a remote storage endpoint.
     */
    var remote: TfRemoteState?,

    /**
     * Backend tracks the configuration for the backend in use with
     * this state. This is used to track any changes in the backend
     * configuration.
     */
    val backend: TfBackendState?,

    /**
     * Modules contains all the modules in a breadth-first order
     */
    val modules: List<TfModuleState>?
)
