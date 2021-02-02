package fr.sedona.terraform.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.quarkus.jackson.ObjectMapperCustomizer
import javax.inject.Singleton

@Singleton
object CustomObjectMapperCustomizer : ObjectMapperCustomizer {
    override fun customize(objectMapper: ObjectMapper) {
        // Includes only non-null fields
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)
        // Make case insensitive conversions
        objectMapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
        objectMapper.registerKotlinModule()
    }
}
