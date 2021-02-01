package fr.sedona.terraform.http.model

/**
 * TfBackendState stores the configuration to connect to a remote backend.
 */
data class TfBackendState(
    /**
     * Backend type
     */
    val type: String,

    /**
     * Backend raw config
     */
    val config: Map<String, Any>,

    /**
     * Hash is the hash code to uniquely identify the original source
     * configuration. We use this to detect when there is a change in
     * configuration even when "type" isn't changed.
     */
    val hash: Number
)
