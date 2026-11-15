package com.kormax.felicatool.felica

sealed interface Authentication1Des {
    class Command(
        idm: ByteArray,
        val areaCodes: Array<ByteArray>,
        val nodeCodes: Array<ByteArray>,
        val challenge1A: ByteArray,
        trailingData: ByteArray = ByteArray(0),
    ) : FelicaCommandWithIdm<Response>(idm, trailingData), Authentication1Des {

        init {
            require(areaCodes.isNotEmpty() || nodeCodes.isNotEmpty()) {
                "At least one area or node code must be specified"
            }
            require(areaCodes.size + nodeCodes.size <= MAX_NODES) {
                "Maximum $MAX_NODES nodes can be authenticated at once"
            }
            require(areaCodes.all { it.size == 2 }) { "Each area code must be exactly 2 bytes" }
            require(nodeCodes.all { it.size == 2 }) { "Each node code must be exactly 2 bytes" }
            require(challenge1A.size == 8) {
                "Challenge1A must be exactly 8 bytes, got ${challenge1A.size}"
            }
            requireFrameLength(BASE_LENGTH + 2 + (areaCodes.size * 2) + (nodeCodes.size * 2) + 8)
        }

        constructor(
            idm: ByteArray,
            areaNodes: List<Area>,
            nodes: List<Node>,
            challenge1A: ByteArray,
            trailingData: ByteArray = ByteArray(0),
        ) : this(
            idm,
            areaNodes.map { it.code }.toTypedArray(),
            nodes.map { it.code }.toTypedArray(),
            challenge1A,
            trailingData,
        )

        override val commandClass: CommandClass = COMMAND_CLASS
        override val timeoutUnits: Int = areaCodes.size + nodeCodes.size

        override fun responseFromByteArray(data: ByteArray) = Response.fromByteArray(data)

        override fun toByteArray(): ByteArray =
            buildFelicaCommandMessage(
                COMMAND_CODE,
                idm,
                capacity =
                    BASE_LENGTH +
                        2 +
                        (areaCodes.size * 2) +
                        (nodeCodes.size * 2) +
                        challenge1A.size,
            ) {
                addByte(areaCodes.size)
                areaCodes.forEach { addBytes(it) }
                addByte(nodeCodes.size)
                nodeCodes.forEach { addBytes(it) }
                addBytes(challenge1A)
            }

        companion object : CommandCompanion {
            override val COMMAND_CODE: Short = 0x10
            override val COMMAND_CLASS: CommandClass = CommandClass.MUTUAL_AUTH

            const val MIN_LENGTH: Int = BASE_LENGTH + 1 + 1 + 8
            const val MAX_NODES = 64

            fun fromByteArray(data: ByteArray): Command =
                parseFelicaCommandWithIdm(data, COMMAND_CODE, minLength = MIN_LENGTH) { idm ->
                    val numberOfAreaCodes = uByte()
                    val areaCodes = Array(numberOfAreaCodes) { bytes(2) }

                    val numberOfNodeCodes = uByte()
                    require(numberOfAreaCodes + numberOfNodeCodes > 0) {
                        "At least one area or node code must be specified"
                    }
                    require(numberOfAreaCodes + numberOfNodeCodes <= MAX_NODES) {
                        "Maximum $MAX_NODES nodes can be authenticated at once"
                    }

                    val nodeCodes = Array(numberOfNodeCodes) { bytes(2) }
                    val challenge1A = bytes(8)

                    Command(idm, areaCodes, nodeCodes, challenge1A, bytes(remaining()))
                }
        }
    }

    class Response(
        idm: ByteArray,
        val challenge1B: ByteArray,
        val challenge2A: ByteArray,
    ) : FelicaResponseWithIdm(idm), Authentication1Des {

        init {
            require(challenge1B.size == 8) {
                "Challenge1B must be exactly 8 bytes, got ${challenge1B.size}"
            }
            require(challenge2A.size == 8) {
                "Challenge2A must be exactly 8 bytes, got ${challenge2A.size}"
            }
        }

        override fun toByteArray(): ByteArray =
            buildFelicaMessage(RESPONSE_CODE, idm, capacity = EXPECTED_LENGTH) {
                addBytes(challenge1B)
                addBytes(challenge2A)
            }

        companion object {
            const val RESPONSE_CODE: Short = 0x11
            const val EXPECTED_LENGTH = BASE_LENGTH + 16

            fun fromByteArray(data: ByteArray): Response =
                parseFelicaResponseWithIdm(
                    data,
                    RESPONSE_CODE,
                    minLength = EXPECTED_LENGTH,
                    maxLength = EXPECTED_LENGTH,
                ) { idm ->
                    Response(idm, bytes(8), bytes(8))
                }
        }
    }
}
