package com.kormax.felicatool.felica

sealed interface Authentication2Des {
    class Command(
        idm: ByteArray,
        val challenge2B: ByteArray,
        trailingData: ByteArray = ByteArray(0),
    ) : FelicaCommandWithIdm<Response.Encrypted>(idm, trailingData), Authentication2Des {
        override val code: Short = COMMAND_CODE

        init {
            require(challenge2B.size == 8) {
                "Challenge2B must be exactly 8 bytes, got ${challenge2B.size}"
            }
            requireFrameLength(EXPECTED_LENGTH)
        }

        override val commandClass: CommandClass = COMMAND_CLASS

        override fun responseFromByteArray(data: ByteArray) = Response.Encrypted.fromByteArray(data)

        override fun toByteArray(): ByteArray =
            buildFelicaCommandMessage(COMMAND_CODE, idm, capacity = EXPECTED_LENGTH) {
                addBytes(challenge2B)
            }

        companion object : CommandCompanion {
            override val COMMAND_CODE: Short = 0x12
            override val COMMAND_CLASS: CommandClass = CommandClass.MUTUAL_AUTH

            const val EXPECTED_LENGTH: Int = BASE_LENGTH + 8

            fun fromByteArray(data: ByteArray): Command =
                parseFelicaCommandWithIdm(data, COMMAND_CODE, minLength = EXPECTED_LENGTH) { idm ->
                    Command(idm, bytes(8), bytes(remaining()))
                }
        }
    }

    sealed interface Response : Authentication2Des {
        class Encrypted(
            override val encryptedData: ByteArray,
        ) : FelicaResponseWithoutIdm(), EncryptedResponse, Response {
            override val code: Short = RESPONSE_CODE

            init {
                require(encryptedData.size == ENCRYPTED_DATA_LENGTH) {
                    "Encrypted data must be exactly $ENCRYPTED_DATA_LENGTH bytes, got ${encryptedData.size}"
                }
            }

            override fun toByteArray(): ByteArray =
                buildFelicaMessage(RESPONSE_CODE, capacity = EXPECTED_LENGTH) {
                    addBytes(encryptedData)
                }

            companion object {
                const val RESPONSE_CODE: Short = 0x13
                const val ENCRYPTED_DATA_LENGTH = 32
                const val EXPECTED_LENGTH = BASE_LENGTH + ENCRYPTED_DATA_LENGTH

                fun fromByteArray(data: ByteArray): Encrypted =
                    parseFelicaResponseWithoutIdm(
                        data,
                        RESPONSE_CODE,
                        minLength = EXPECTED_LENGTH,
                        maxLength = EXPECTED_LENGTH,
                    ) {
                        Encrypted(bytes(ENCRYPTED_DATA_LENGTH))
                    }
            }
        }

        class Plain(
            val issueId: ByteArray,
            val issueParameter: ByteArray,
        ) : Response, SecureResponse {
            override val code: Short = Encrypted.RESPONSE_CODE

            init {
                require(issueId.size == 8) { "Issue ID must be exactly 8 bytes" }
                require(issueParameter.size == 8) { "Issue parameter must be exactly 8 bytes" }
            }

            override fun toByteArray(): ByteArray = issueId + issueParameter

            companion object {
                const val LENGTH = 16

                fun fromByteArray(data: ByteArray): Plain {
                    require(data.size >= LENGTH) { "Plain data must be at least $LENGTH bytes" }
                    return Plain(data.copyOfRange(0, 8), data.copyOfRange(8, 16))
                }
            }
        }
    }
}
