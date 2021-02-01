package fr.sedona.terraform.repository

import fr.sedona.terraform.model.State
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase
import javax.enterprise.context.ApplicationScoped


@ApplicationScoped
class TerraformStateRepository : PanacheRepositoryBase<State, String> {
    fun findByLocked(locked: Boolean): List<State> {
        return list("locked", locked)
    }
}
