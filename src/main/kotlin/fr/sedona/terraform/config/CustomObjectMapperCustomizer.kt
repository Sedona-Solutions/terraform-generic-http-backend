package fr.sedona.terraform.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import io.quarkus.jackson.ObjectMapperCustomizer
import javax.inject.Singleton

@Singleton
class CustomObjectMapperCustomizer : ObjectMapperCustomizer {
    override fun customize(objectMapper: ObjectMapper) {
        // Includes only non-null fields
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)
    }
}
