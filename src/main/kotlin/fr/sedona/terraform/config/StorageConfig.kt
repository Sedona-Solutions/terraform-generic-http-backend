package fr.sedona.terraform.config

import com.fasterxml.jackson.databind.ObjectMapper
import fr.sedona.terraform.storage.adapter.DatabaseAdapter
import fr.sedona.terraform.storage.adapter.ElasticAdapter
import fr.sedona.terraform.storage.adapter.StorageAdapter
import fr.sedona.terraform.storage.database.repository.TerraformStateRepository
import io.quarkus.arc.DefaultBean
import io.quarkus.arc.properties.IfBuildProperty
import org.jboss.logging.Logger
import javax.enterprise.context.Dependent
import javax.enterprise.inject.Produces

@Dependent
class StorageConfig {
    private val logger = Logger.getLogger(StorageConfig::class.java)

    @Produces
    @DefaultBean
    fun defaultStorageAdapter(
        repository: TerraformStateRepository,
        objectMapper: ObjectMapper
    ): StorageAdapter {
        logger.info("Using database storage adapter")
        return DatabaseAdapter(repository, objectMapper)
    }

    @Produces
    @IfBuildProperty(name = "application.storage.adapter", stringValue = "elastic")
    fun elasticStorageAdapter(
        repository: TerraformStateRepository,
        objectMapper: ObjectMapper
    ): StorageAdapter {
        logger.info("Using elastic storage adapter")
        return ElasticAdapter()
    }
}
