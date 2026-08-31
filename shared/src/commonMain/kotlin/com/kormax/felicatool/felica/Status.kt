package com.kormax.felicatool.felica

sealed class Status(
    val statusFlag1: Byte,
    val statusFlag2: Byte,
) {
    sealed class Success(statusFlag1: Byte, statusFlag2: Byte) : Status(statusFlag1, statusFlag2)

    sealed class Failure(statusFlag1: Byte, statusFlag2: Byte) : Status(statusFlag1, statusFlag2)

    sealed interface Unknown

    data object Ok : Success(OK.toByte(), OK.toByte())

    class RewriteLimitWarning internal constructor(statusFlag1: Byte) :
        Success(statusFlag1, REWRITE_LIMIT_WARNING.toByte())

    class IllegalNumberOfService internal constructor(statusFlag1: Byte) :
        Failure(statusFlag1, ILLEGAL_NUMBER_OF_SERVICE.toByte())

    class IllegalNumberOfBlock internal constructor(statusFlag1: Byte) :
        Failure(statusFlag1, ILLEGAL_NUMBER_OF_BLOCK.toByte())

    class IllegalBlockListServiceOrder internal constructor(statusFlag1: Byte) :
        Failure(statusFlag1, ILLEGAL_BLOCK_LIST_SERVICE_ORDER.toByte())

    class IllegalBlockNumber internal constructor(statusFlag1: Byte) :
        Failure(statusFlag1, ILLEGAL_BLOCK_NUMBER.toByte())

    class AuthenticationRequired internal constructor(statusFlag1: Byte) :
        Failure(statusFlag1, AUTHENTICATION_REQUIRED.toByte())

    class RandomChallengeWriteRequired internal constructor(statusFlag1: Byte) :
        Failure(statusFlag1, RANDOM_CHALLENGE_WRITE_REQUIRED.toByte())

    class UnknownSuccess internal constructor(statusFlag2: Byte) :
        Success(OK.toByte(), statusFlag2), Unknown

    class UnknownError internal constructor(statusFlag1: Byte, statusFlag2: Byte) :
        Failure(statusFlag1, statusFlag2), Unknown

    companion object {
        private const val OK = 0x00
        private const val REWRITE_LIMIT_WARNING = 0x71
        private const val ILLEGAL_NUMBER_OF_SERVICE = 0xA1
        private const val ILLEGAL_NUMBER_OF_BLOCK = 0xA2
        private const val ILLEGAL_BLOCK_LIST_SERVICE_ORDER = 0xA3
        private const val ILLEGAL_BLOCK_NUMBER = 0xA8
        private const val AUTHENTICATION_REQUIRED = 0xB1
        private const val RANDOM_CHALLENGE_WRITE_REQUIRED = 0xB2

        fun from(statusFlag1: Byte, statusFlag2: Byte): Status {
            val statusCode = statusFlag2.toInt() and 0xFF
            return when {
                statusFlag1 == OK.toByte() && statusCode == OK -> Ok
                statusCode == REWRITE_LIMIT_WARNING -> RewriteLimitWarning(statusFlag1)
                statusCode == ILLEGAL_NUMBER_OF_SERVICE -> IllegalNumberOfService(statusFlag1)
                statusCode == ILLEGAL_NUMBER_OF_BLOCK -> IllegalNumberOfBlock(statusFlag1)
                statusCode == ILLEGAL_BLOCK_LIST_SERVICE_ORDER ->
                    IllegalBlockListServiceOrder(statusFlag1)
                statusCode == ILLEGAL_BLOCK_NUMBER -> IllegalBlockNumber(statusFlag1)
                statusCode == AUTHENTICATION_REQUIRED -> AuthenticationRequired(statusFlag1)
                statusCode == RANDOM_CHALLENGE_WRITE_REQUIRED ->
                    RandomChallengeWriteRequired(statusFlag1)
                statusFlag1 == OK.toByte() -> UnknownSuccess(statusFlag2)
                else -> UnknownError(statusFlag1, statusFlag2)
            }
        }
    }
}
