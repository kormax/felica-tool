package com.kormax.felicatool.felica

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadWithoutEncryptionCommandTest {

    companion object {
        private val IDM = "0102030405060708".hexToByteArray()
    }

    @Test
    fun testReadWithoutEncryptionAllowsUnusedServiceCodeEntries() {
        val command =
            ReadWithoutEncryptionCommand(
                idm = IDM,
                serviceCodes = arrayOf("0B00".hexToByteArray(), "4B00".hexToByteArray()),
                blockListElements =
                    arrayOf(BlockListElement(serviceCodeListOrder = 1, blockNumber = 0)),
            )

        val serialized = command.toByteArray()
        val parsed = ReadWithoutEncryptionCommand.fromByteArray(serialized)

        assertEquals("12060102030405060708020B004B00018100", serialized.toHexString().uppercase())
        assertEquals(command.idm.toList(), parsed.idm.toList())
        assertEquals(
            command.serviceCodes.map { it.toList() },
            parsed.serviceCodes.map { it.toList() },
        )
        assertEquals(command.blockListElements.toList(), parsed.blockListElements.toList())
    }

    @Test
    fun testReadWithoutEncryptionAllowsUnusedUnknownAttributeServiceCodeEntries() {
        val command =
            ReadWithoutEncryptionCommand(
                idm = IDM,
                serviceCodes = arrayOf("0200".hexToByteArray(), "0B00".hexToByteArray()),
                blockListElements =
                    arrayOf(BlockListElement(serviceCodeListOrder = 1, blockNumber = 0)),
            )

        val serialized = command.toByteArray()
        val parsed = ReadWithoutEncryptionCommand.fromByteArray(serialized)

        assertEquals("120601020304050607080202000B00018100", serialized.toHexString().uppercase())
        assertEquals(
            command.serviceCodes.map { it.toList() },
            parsed.serviceCodes.map { it.toList() },
        )
        assertEquals(command.blockListElements.toList(), parsed.blockListElements.toList())
    }
}
