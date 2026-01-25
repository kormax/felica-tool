package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

class WriteWithoutEncryptionCommandTest {

    companion object {
        private val IDM = "0102030405060708".hexToByteArray()
    }

    @Test
    fun testWriteWithoutEncryptionCommandToByteArray() {
        val serviceCodes = arrayOf("0B00".hexToByteArray()) // Service code 0x000B in LE
        val blockListElements = arrayOf(BlockListElement(0, 0))
        val blockData = arrayOf("00112233445566778899AABBCCDDEEFF".hexToByteArray())

        val command = WriteWithoutEncryptionCommand(IDM, serviceCodes, blockListElements, blockData)
        val data = command.toByteArray()

        // Expected: length(1) + command_code(1) + idm(8) + num_services(1) + service_code(2) +
        //           num_blocks(1) + block_list(2) + block_data(16) = 32 bytes
        assertEquals(32, data.size)
        assertEquals(32.toByte(), data[0]) // Length
        assertEquals(0x08.toByte(), data[1]) // Command code
        assertEquals(IDM.toList(), data.sliceArray(2..9).toList()) // IDM
        assertEquals(1.toByte(), data[10]) // Number of services
        assertEquals(
            "0B00".hexToByteArray().toList(),
            data.sliceArray(11..12).toList(),
        ) // Service code
        assertEquals(1.toByte(), data[13]) // Number of blocks
        assertEquals(0x80.toByte(), data[14]) // Block list element D0 (2-byte format, service 0)
        assertEquals(0x00.toByte(), data[15]) // Block list element D1 (block 0)
        assertEquals(
            "00112233445566778899AABBCCDDEEFF".hexToByteArray().toList(),
            data.sliceArray(16..31).toList(),
        ) // Block data
    }

    @Test
    fun testWriteWithoutEncryptionCommandFromByteArray() {
        // Build test data: write 1 block to service 0x000B
        val rawData =
            ("20" + // Length: 32
                    "08" + // Command code
                    "0102030405060708" + // IDM
                    "01" + // Number of services
                    "0B00" + // Service code (0x000B in LE)
                    "01" + // Number of blocks
                    "8000" + // Block list element (service 0, block 0)
                    "00112233445566778899AABBCCDDEEFF" // Block data
                )
                .hexToByteArray()

        val command = WriteWithoutEncryptionCommand.fromByteArray(rawData)

        assertEquals(IDM.toList(), command.idm.toList())
        assertEquals(1, command.serviceCodes.size)
        assertEquals("0B00".hexToByteArray().toList(), command.serviceCodes[0].toList())
        assertEquals(1, command.blockListElements.size)
        assertEquals(0, command.blockListElements[0].serviceCodeListOrder)
        assertEquals(0, command.blockListElements[0].blockNumber)
        assertEquals(1, command.blockData.size)
        assertEquals(
            "00112233445566778899AABBCCDDEEFF".hexToByteArray().toList(),
            command.blockData[0].toList(),
        )
    }

    @Test
    fun testWriteWithoutEncryptionCommandRoundTrip() {
        val serviceCodes = arrayOf("0B00".hexToByteArray())
        val blockListElements = arrayOf(BlockListElement(0, 1))
        val blockData = arrayOf("FFEEDDCCBBAA99887766554433221100".hexToByteArray())

        val original =
            WriteWithoutEncryptionCommand(IDM, serviceCodes, blockListElements, blockData)
        val serialized = original.toByteArray()
        val parsed = WriteWithoutEncryptionCommand.fromByteArray(serialized)

        assertEquals(original.idm.toList(), parsed.idm.toList())
        assertEquals(original.serviceCodes.size, parsed.serviceCodes.size)
        assertEquals(original.serviceCodes[0].toList(), parsed.serviceCodes[0].toList())
        assertEquals(original.blockListElements.size, parsed.blockListElements.size)
        assertEquals(
            original.blockListElements[0].blockNumber,
            parsed.blockListElements[0].blockNumber,
        )
        assertEquals(original.blockData.size, parsed.blockData.size)
        assertEquals(original.blockData[0].toList(), parsed.blockData[0].toList())
    }
}
