package fr.sedona.terraform.http.model

/**
 * TfRemoteState is used to track the information about a remote
 * state store that we push/pull state to.
 */
data class TfRemoteState(
    /**
     * Type controls the client we use for the remote state
     */
    val type: String,

    /**
     * Config is used to store arbitrary configuration that
     * is type specific
     */
    val config: Map<String, String>
)
