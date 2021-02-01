package fr.sedona.terraform.http.model

import java.util.*

/**
 * TfLockInfo stores lock metadata
 */
data class TfLockInfo(
    /**
     * Unique ID for the lock. NewLockInfo provides a random ID, but this may
     * be overridden by the lock implementation. The final value if ID will be
     * returned by the call to Lock.
     */
    val id: String?,

    /**
     * Terraform operation, provided by the caller.
     */
    val operation: String?,

    /**
     * Extra information to store with the lock, provided by the caller.
     */
    val info: String?,

    /**
     * user@hostname when available
     */
    val who: String?,

    /**
     * Terraform version
     */
    val version: String?,

    /**
     * Time that the lock was taken.
     */
    // FIXME Created *time.Time `json:"created,omitempty"`
    val created: Date?,

    /**
     * Path to the state file when applicable. Set by the Lock implementation.
     */
    val path: String?
)
