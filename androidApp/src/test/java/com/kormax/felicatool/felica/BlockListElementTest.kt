package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

/** Unit tests for BlockListElement */
class BlockListElementTest {

    @Test
    fun testBlockListElement_creation_normal() {
        val element =
            BlockListElement(
                serviceCodeListOrder = 0,
                blockNumber = 5,
                accessMode = BlockListElement.AccessMode.NORMAL,
                extended = false,
            )

        assertEquals(0, element.serviceCodeListOrder)
        assertEquals(5, element.blockNumber)
        assertEquals(BlockListElement.AccessMode.NORMAL, element.accessMode)
        assertFalse(element.extended)
    }

    @Test
    fun testBlockListElement_creation_extended() {
        val element =
            BlockListElement(
                serviceCodeListOrder = 1,
                blockNumber = 0x1234,
                accessMode = BlockListElement.AccessMode.CASHBACK,
                extended = true,
            )

        assertEquals(1, element.serviceCodeListOrder)
        assertEquals(0x1234, element.blockNumber)
        assertEquals(BlockListElement.AccessMode.CASHBACK, element.accessMode)
        assertTrue(element.extended)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testBlockListElement_invalidServiceCodeListOrder_negative() {
        BlockListElement(serviceCodeListOrder = -1, blockNumber = 5, extended = false)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testBlockListElement_invalidServiceCodeListOrder_tooLarge() {
        BlockListElement(serviceCodeListOrder = 16, blockNumber = 5, extended = false)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testBlockListElement_invalidBlockNumber_negative() {
        BlockListElement(serviceCodeListOrder = 0, blockNumber = -1, extended = false)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testBlockListElement_invalidBlockNumber_normal_tooLarge() {
        BlockListElement(
            serviceCodeListOrder = 0,
            blockNumber = 256, // 0x100, too large for normal format
            extended = false,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun testBlockListElement_invalidBlockNumber_extended_tooLarge() {
        BlockListElement(
            serviceCodeListOrder = 0,
            blockNumber = 0x10000, // Too large for extended format (3 bytes)
            extended = true,
        )
    }

    @Test
    fun testBlockListElement_toByteArray_normal() {
        val element =
            BlockListElement(
                serviceCodeListOrder = 0,
                blockNumber = 5,
                accessMode = BlockListElement.AccessMode.NORMAL,
                extended = false,
            )

        val bytes = element.toByteArray()

        // Should be 2 bytes: [D0, D1]
        assertEquals(2, bytes.size)

        // D0: Length(1) + AccessMode(0) + ServiceCodeListOrder(0) = 0x80
        assertEquals(0x80.toByte(), bytes[0])

        // D1: Block number = 5
        assertEquals(5.toByte(), bytes[1])
    }

    @Test
    fun testBlockListElement_toByteArray_extended() {
        val element =
            BlockListElement(
                serviceCodeListOrder = 1,
                blockNumber = 0x1234,
                accessMode = BlockListElement.AccessMode.CASHBACK,
                extended = true,
            )

        val bytes = element.toByteArray()

        // Should be 3 bytes: [D0, D1, D2]
        assertEquals(3, bytes.size)

        // D0: Length(0) + AccessMode(1) + ServiceCodeListOrder(1) = 0x11
        assertEquals(0x11.toByte(), bytes[0])

        // D1: Block number low byte = 0x34
        assertEquals(0x34.toByte(), bytes[1])

        // D2: Block number high byte = 0x12
        assertEquals(0x12.toByte(), bytes[2])
    }

    @Test
    fun testBlockListElement_toByteArray_serviceCodeListOrder() {
        val element =
            BlockListElement(
                serviceCodeListOrder = 15, // Maximum valid value
                blockNumber = 10,
                accessMode = BlockListElement.AccessMode.NORMAL,
                extended = false,
            )

        val bytes = element.toByteArray()

        // D0: Length(1) + AccessMode(0) + ServiceCodeListOrder(15) = 0x8F
        assertEquals(0x8F.toByte(), bytes[0])
        assertEquals(10.toByte(), bytes[1])
    }

    @Test
    fun testBlockListElement_fromByteArray_normal() {
        // Normal format: 2 bytes
        // D0: 0x80 (Length=1, AccessMode=0, ServiceCodeListOrder=0)
        // D1: 0x05 (Block number = 5)
        val data = byteArrayOf(0x80.toByte(), 0x05)

        val element = BlockListElement.fromByteArray(data)

        assertEquals(0, element.serviceCodeListOrder)
        assertEquals(5, element.blockNumber)
        assertEquals(BlockListElement.AccessMode.NORMAL, element.accessMode)
        assertFalse(element.extended)
    }

    @Test
    fun testBlockListElement_fromByteArray_extended() {
        // Extended format: 3 bytes
        // D0: 0x11 (Length=0, AccessMode=1, ServiceCodeListOrder=1)
        // D1: 0x34 (Block number low byte)
        // D2: 0x12 (Block number high byte)
        val data = byteArrayOf(0x11.toByte(), 0x34.toByte(), 0x12.toByte())

        val element = BlockListElement.fromByteArray(data)

        assertEquals(1, element.serviceCodeListOrder)
        assertEquals(0x1234, element.blockNumber)
        assertEquals(BlockListElement.AccessMode.CASHBACK, element.accessMode)
        assertTrue(element.extended)
    }

    @Test
    fun testBlockListElement_fromByteArray_serviceCodeListOrder() {
        // Test maximum service code list order
        val data = byteArrayOf(0x8F.toByte(), 0x0A) // D0=0x8F, D1=0x0A

        val element = BlockListElement.fromByteArray(data)

        assertEquals(15, element.serviceCodeListOrder)
        assertEquals(10, element.blockNumber)
        assertEquals(BlockListElement.AccessMode.NORMAL, element.accessMode)
        assertFalse(element.extended)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testBlockListElement_fromByteArray_invalidSize_1() {
        val data = byteArrayOf(0x80.toByte()) // Too short
        BlockListElement.fromByteArray(data)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testBlockListElement_fromByteArray_invalidSize_4() {
        val data = byteArrayOf(0x80.toByte(), 0x05, 0x00, 0x00) // Too long
        BlockListElement.fromByteArray(data)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testBlockListElement_fromByteArray_invalidAccessMode() {
        // Access mode = 2 (invalid)
        // D0: 0xC0 = 1100 0000 (Length=1, AccessMode=2, ServiceCodeListOrder=0)
        val data = byteArrayOf(0xC0.toByte(), 0x05)
        BlockListElement.fromByteArray(data)
    }

    @Test
    fun testBlockListElement_roundTrip_normal() {
        val original =
            BlockListElement(
                serviceCodeListOrder = 3,
                blockNumber = 42,
                accessMode = BlockListElement.AccessMode.NORMAL,
                extended = false,
            )

        val bytes = original.toByteArray()
        val reconstructed = BlockListElement.fromByteArray(bytes)

        assertEquals(original.serviceCodeListOrder, reconstructed.serviceCodeListOrder)
        assertEquals(original.blockNumber, reconstructed.blockNumber)
        assertEquals(original.accessMode, reconstructed.accessMode)
        assertEquals(original.extended, reconstructed.extended)
    }

    @Test
    fun testBlockListElement_roundTrip_extended() {
        val original =
            BlockListElement(
                serviceCodeListOrder = 7,
                blockNumber = 0xABCD,
                accessMode = BlockListElement.AccessMode.CASHBACK,
                extended = true,
            )

        val bytes = original.toByteArray()
        val reconstructed = BlockListElement.fromByteArray(bytes)

        assertEquals(original.serviceCodeListOrder, reconstructed.serviceCodeListOrder)
        assertEquals(original.blockNumber, reconstructed.blockNumber)
        assertEquals(original.accessMode, reconstructed.accessMode)
        assertEquals(original.extended, reconstructed.extended)
    }

    @Test
    fun testBlockListElement_toString() {
        val element =
            BlockListElement(
                serviceCodeListOrder = 2,
                blockNumber = 100,
                accessMode = BlockListElement.AccessMode.NORMAL,
                extended = false,
            )

        val expected =
            "BlockListElement(serviceCodeListOrder=2, blockNumber=100, accessMode=NORMAL, extended=false)"
        assertEquals(expected, element.toString())
    }

    @Test
    fun testBlockListElement_boundaryValues() {
        // Test boundary values for normal format
        val normalMax =
            BlockListElement(
                serviceCodeListOrder = 15,
                blockNumber = 255,
                accessMode = BlockListElement.AccessMode.CASHBACK,
                extended = false,
            )
        assertEquals(15, normalMax.serviceCodeListOrder)
        assertEquals(255, normalMax.blockNumber)

        // Test boundary values for extended format
        val extendedMax =
            BlockListElement(
                serviceCodeListOrder = 15,
                blockNumber = 0xFFFF,
                accessMode = BlockListElement.AccessMode.CASHBACK,
                extended = true,
            )
        assertEquals(15, extendedMax.serviceCodeListOrder)
        assertEquals(0xFFFF, extendedMax.blockNumber)
    }

    @Test
    fun testAccessMode_enumValues() {
        assertEquals(0, BlockListElement.AccessMode.NORMAL.value)
        assertEquals(1, BlockListElement.AccessMode.CASHBACK.value)
    }
}
