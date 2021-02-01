package fr.sedona.terraform.http.model

/**
 * TfInstanceState is used to track the unique state information belonging
 * to a given instance.
 */
data class TfInstanceState(
    /**
     * A unique ID for this resource. This is opaque to Terraform
     * and is only meant as a lookup mechanism for the providers.
     */
    val id: String,

    /**
     * Attributes are basic information about the resource. Any keys here
     * are accessible in variable format within Terraform configurations:
     * ${resourcetype.name.attribute}.
     */
    val attributes: Map<String, String>,

    /**
     * Meta is a simple K/V map that is persisted to the State but otherwise
     * ignored by Terraform core. It's meant to be used for accounting by
     * external client code. The value here must only contain Go primitives
     * and collections.
     */
    val meta: Map<String, Any>,

    /**
     * Tainted is used to mark a resource for recreation.
     */
    val tainted: Boolean
)
