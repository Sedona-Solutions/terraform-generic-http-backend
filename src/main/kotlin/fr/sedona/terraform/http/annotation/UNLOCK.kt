package fr.sedona.terraform.http.annotation

import javax.ws.rs.HttpMethod

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
@kotlin.annotation.Retention(AnnotationRetention.RUNTIME)
@HttpMethod("UNLOCK")
annotation class UNLOCK
