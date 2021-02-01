package fr.sedona.terraform.http.model

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * TfModuleState is used to track all the state relevant to a single
 * module. Previous to Terraform 0.3, all state belonged to the "root"
 * module.
 */
data class TfModuleState(
    /**
     * Path is the import path from the root module. Modules imports are
     * always disjoint, so the path represents a module tree
     */
    val path: List<String>,

    /**
     * Outputs declared by the module and maintained for each module
     * even though only the root module technically needs to be kept.
     * This allows operators to inspect values at the boundaries.
     */
    val outputs: Map<String, TfOutputState>,

    /**
     * Resources is a mapping of the logically named resource to
     * the state of the resource. Each resource may actually have
     * N instances underneath, although a user only needs to think
     * about the 1:1 case.
     */
    val resources: Map<String, TfResourceState>,

    /**
     * Dependencies are a list of things that this module relies on
     * existing to remain intact. For example: an module may depend
     * on a VPC ID given by an aws_vpc resource.
     *
     * Terraform uses this information to build valid destruction
     * orders and to warn the user if they're destroying a module that
     * another resource depends on.
     *
     * Things can be put into this list that may not be managed by
     * Terraform. If Terraform doesn't find a matching ID in the
     * overall state, then it assumes it isn't managed and doesn't
     * worry about it.
     */
    @JsonProperty("depends_on")
    val dependsOn: List<String>
)
