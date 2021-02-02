package fr.sedona.terraform.http.exception

import fr.sedona.terraform.http.model.TfLockInfo

class BadRequestException : RuntimeException()

class ResourceNotFoundException(
    val name: String
) : RuntimeException()

class LockedException(
    val lockInfo: TfLockInfo
) : RuntimeException()

class ConflictException(
    val lockInfo: TfLockInfo
) : RuntimeException()
