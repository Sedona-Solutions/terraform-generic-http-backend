package fr.sedona.terraform.exception

import fr.sedona.terraform.http.model.TfLockInfo

class StateNotLockedException : RuntimeException()

class StateAlreadyLockedException(
    val lockInfo: TfLockInfo
) : RuntimeException()

class StateLockMismatchException(
    val lockInfo: TfLockInfo
) : RuntimeException()
