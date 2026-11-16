package com.kormax.felicatool.felica

sealed interface ReadWithEncryptionDes {
    sealed interface Command : ReadWithEncryptionDes {
        class Plain(
            val blockListElements: List<BlockListElement>,
        ) : SecureCommand<Response.Plain>, Command {
            init {
                require(blockListElements.isNotEmpty()) {
                    "At least one block list element must be specified"
                }
                require(blockListElements.size <= MAX_BLOCKS) {
                    "Maximum $MAX_BLOCKS blocks can be read at once"
                }
            }

            override val code: Int = COMMAND_CODE.toInt()

            override fun toByteArray(): ByteArray {
                val payload = ArrayList<Byte>()
                payload.addByte(blockListElements.size)
                blockListElements.forEach { payload.addBytes(it.toByteArray()) }
                return payload.toByteArray()
            }

            override fun encrypted(encryptedData: ByteArray) =
                Encrypted(encryptedData, timeoutUnits = blockListElements.size)

            override fun responseFromByteArray(data: ByteArray) = Response.Plain.fromByteArray(data)
        }

        class Encrypted(
            override val encryptedData: ByteArray,
            override val timeoutUnits: Int = 0,
        ) :
            FelicaCommandWithoutIdm<Response.Encrypted>(),
            EncryptedCommand<Response.Encrypted>,
            Command {
            override val commandClass: CommandClass = CommandClass.DATA_READ

            override fun responseFromByteArray(data: ByteArray) =
                Response.Encrypted.fromByteArray(data)

            override fun toByteArray(): ByteArray =
                buildFelicaCommandMessage(
                    COMMAND_CODE,
                    capacity = BASE_LENGTH + encryptedData.size,
                ) {
                    addBytes(encryptedData)
                }
        }

        companion object {
            const val COMMAND_CODE: Short = 0x14
            const val MAX_BLOCKS: Int = 14
        }
    }

    sealed interface Response : ReadWithEncryptionDes {
        class Encrypted(
            override val encryptedData: ByteArray,
        ) : FelicaResponseWithoutIdm(), EncryptedResponse, Response {
            override fun toByteArray(): ByteArray =
                buildFelicaMessage(RESPONSE_CODE, capacity = BASE_LENGTH + encryptedData.size) {
                    addBytes(encryptedData)
                }

            companion object {
                fun fromByteArray(data: ByteArray): Encrypted =
                    parseFelicaResponseWithoutIdm(data, RESPONSE_CODE, minLength = BASE_LENGTH) {
                        Encrypted(bytes(remaining()))
                    }
            }
        }

        class Plain(
            override val statusFlag1: Byte,
            override val statusFlag2: Byte,
            val blockData: List<ByteArray>,
        ) : SecureResponse, WithStatusFlags, Response {
            init {
                require(blockData.all { it.size == BlockListElement.BLOCK_SIZE }) {
                    "Each block must be exactly ${BlockListElement.BLOCK_SIZE} bytes"
                }
            }

            companion object {
                fun fromByteArray(data: ByteArray): Plain {
                    val cursor = ByteCursor(data)
                    val statusFlag1 = cursor.byte()
                    val statusFlag2 = cursor.byte()
                    if (statusFlag1 != 0x00.toByte()) {
                        return Plain(statusFlag1, statusFlag2, emptyList())
                    }
                    val numberOfBlocks = cursor.uByte()
                    require(cursor.remaining() >= numberOfBlocks * BlockListElement.BLOCK_SIZE) {
                        "Block data truncated: need ${numberOfBlocks * BlockListElement.BLOCK_SIZE} " +
                            "bytes, have ${cursor.remaining()}"
                    }
                    val blockData =
                        List(numberOfBlocks) { cursor.bytes(BlockListElement.BLOCK_SIZE) }
                    return Plain(statusFlag1, statusFlag2, blockData)
                }
            }
        }

        companion object {
            const val RESPONSE_CODE: Short = 0x15
        }
    }
}
