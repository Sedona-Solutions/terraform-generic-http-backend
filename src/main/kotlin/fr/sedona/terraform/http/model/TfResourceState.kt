package fr.sedona.terraform.http.model

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * ResourceState holds the state of a resource that is used so that
 * a provider can find and manage an existing resource as well as for
 * storing attributes that are used to populate variables of child
 * resources.
 */
data class TfResourceState(
    /**
     * This is filled in and managed by Terraform, and is the resource
     * type itself such as "mycloud_instance". If a resource provider sets
     * this value, it won't be persisted.
     */
    val type: String,

    /**
     * Dependencies are a list of things that this resource relies on
     * existing to remain intact. For example: an AWS instance might
     * depend on a subnet (which itself might depend on a VPC, and so
     * on).
     *
     * Terraform uses this information to build valid destruction
     * orders and to warn the user if they're destroying a resource that
     * another resource depends on.
     *
     * Things can be put into this list that may not be managed by
     * Terraform. If Terraform doesn't find a matching ID in the
     * overall state, then it assumes it isn't managed and doesn't
     * worry about it.
     */
    @JsonProperty("depends_on")
    val dependsOn: List<String>,

    /**
     * Primary is the current active instance for this resource.
     * It can be replaced but only after a successful creation.
     * This is the instances on which providers will act.
     */
    // FIXME: Primary *InstanceState `json:"primary"`
    val primary: TfInstanceState,

    /**
     * Deposed is used in the mechanics of CreateBeforeDestroy: the existing
     * Primary is Deposed to get it out of the way for the replacement Primary to
     * be created by Apply. If the replacement Primary creates successfully, the
     * Deposed instance is cleaned up.
     *
     * If there were problems creating the replacement Primary, the Deposed
     * instance and the (now tainted) replacement Primary will be swapped so the
     * tainted replacement will be cleaned up instead.
     *
     * An instance will remain in the Deposed list until it is successfully
     * destroyed and purged.
     */
    // FIXME Deposed []*InstanceState `json:"deposed"`
    val deposed: List<TfInstanceState>,

    /**
     * Provider is used when a resource is connected to a provider with an alias.
     * If this string is empty, the resource is connected to the default provider,
     * e.g. "aws_instance" goes with the "aws" provider.
     * If the resource block contained a "provider" key, that value will be set here.
     */
    val provider: String
)
