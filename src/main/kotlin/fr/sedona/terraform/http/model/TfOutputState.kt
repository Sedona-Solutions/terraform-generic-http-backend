package fr.sedona.terraform.http.model

/**
 * TfOutputState is used to track the state relevant to a single output.
 */
data class TfOutputState(
    /**
     * Sensitive describes whether the output is considered sensitive,
     * which may lead to masking the value on screen in some cases.
     */
    val sensitive: Boolean,

    /**
     * Type describes the structure of Value. Valid values are "string",
     * "map" and "list"
     */
    val type: String,

    /**
     * Value contains the value of the output, in the structure described
     * by the Type field.
     */
    val value: Any
)
