package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

class GetNodePropertyResponseTest {

    companion object {
        private val IDM = "0102030405060708".hexToByteArray()
    }

    @Test
    fun testGetNodePropertyResponseErrorResponse() {
        val idm = IDM
        val statusFlag1 = 0xFF.toByte()
        val statusFlag2 = 0x00.toByte()

        val response = GetNodePropertyResponse(idm, statusFlag1, statusFlag2, emptyArray())

        assertEquals(idm.toList(), response.idm.toList())
        assertEquals(statusFlag1, response.statusFlag1)
        assertEquals(statusFlag2, response.statusFlag2)
        assertEquals(0, response.nodeProperties.size)
        assertFalse(response.isStatusSuccessful)
    }

    @Test
    fun testGetNodePropertyResponseSuccessWithValueLimitedPurse() {
        val idm = IDM
        val statusFlag1 = 0x00.toByte()
        val statusFlag2 = 0x00.toByte()
        val property =
            ValueLimitedPurseServiceProperty(
                enabled = true,
                upperLimit = 10000,
                lowerLimit = -5000,
                generationNumber = 42,
            )
        val nodeProperties = arrayOf<NodeProperty>(property)

        val response = GetNodePropertyResponse(idm, statusFlag1, statusFlag2, nodeProperties)

        assertEquals(idm.toList(), response.idm.toList())
        assertEquals(statusFlag1, response.statusFlag1)
        assertEquals(statusFlag2, response.statusFlag2)
        assertEquals(1, response.nodeProperties.size)
        assertTrue(response.isStatusSuccessful)

        val retrievedProperty = response.nodeProperties[0] as ValueLimitedPurseServiceProperty
        assertNotNull(retrievedProperty)
        assertEquals(property, retrievedProperty)
    }

    @Test
    fun testGetNodePropertyResponseSuccessWithMacCommunication() {
        val idm = IDM
        val statusFlag1 = 0x00.toByte()
        val statusFlag2 = 0x00.toByte()
        val property = MacCommunicationProperty(enabled = true)
        val nodeProperties = arrayOf<NodeProperty>(property)

        val response = GetNodePropertyResponse(idm, statusFlag1, statusFlag2, nodeProperties)

        assertEquals(idm.toList(), response.idm.toList())
        assertEquals(statusFlag1, response.statusFlag1)
        assertEquals(statusFlag2, response.statusFlag2)
        assertEquals(1, response.nodeProperties.size)
        assertTrue(response.isStatusSuccessful)

        val retrievedProperty = response.nodeProperties[0] as MacCommunicationProperty
        assertNotNull(retrievedProperty)
        assertEquals(property, retrievedProperty)
    }

    @Test
    fun testGetNodePropertyResponseToByteArrayError() {
        val idm = IDM
        val statusFlag1 = 0xFF.toByte()
        val statusFlag2 = 0x01.toByte()

        val response = GetNodePropertyResponse(idm, statusFlag1, statusFlag2, emptyArray())
        val data = response.toByteArray()

        // Expected: length(1) + response_code(1) + idm(8) + status_flags(2) = 12 bytes
        assertEquals(12, data.size)
        assertEquals(12.toByte(), data[0]) // Length
        assertEquals(0x29.toByte(), data[1]) // Response code
        assertEquals(idm.toList(), data.sliceArray(2..9).toList()) // IDM
        assertEquals(statusFlag1, data[10]) // Status flag 1
        assertEquals(statusFlag2, data[11]) // Status flag 2
    }

    @Test
    fun testGetNodePropertyResponseToByteArraySuccessMac() {
        val idm = IDM
        val statusFlag1 = 0x00.toByte()
        val statusFlag2 = 0x00.toByte()
        val property = MacCommunicationProperty(enabled = false)
        val nodeProperties = arrayOf<NodeProperty>(property)

        val response = GetNodePropertyResponse(idm, statusFlag1, statusFlag2, nodeProperties)
        val data = response.toByteArray()

        // Expected: length(1) + response_code(1) + idm(8) + status_flags(2) + num_nodes(1) +
        // property(1) = 14 bytes
        assertEquals(14, data.size)
        assertEquals(14.toByte(), data[0]) // Length
        assertEquals(0x29.toByte(), data[1]) // Response code
        assertEquals(idm.toList(), data.sliceArray(2..9).toList()) // IDM
        assertEquals(statusFlag1, data[10]) // Status flag 1
        assertEquals(statusFlag2, data[11]) // Status flag 2
        assertEquals(1.toByte(), data[12]) // Number of nodes
        assertEquals(0x00.toByte(), data[13]) // MAC communication flag (disabled)
    }

    @Test
    fun testGetNodePropertyResponseFromByteArrayError() {
        val data = "0C29010203040506070810FF".hexToByteArray()
        val response = GetNodePropertyResponse.fromByteArray(data)

        assertEquals("0102030405060708".hexToByteArray().toList(), response.idm.toList())
        assertEquals(0x10.toByte(), response.statusFlag1)
        assertEquals(0xFF.toByte(), response.statusFlag2)
        assertEquals(0, response.nodeProperties.size)
        assertFalse(response.isStatusSuccessful)
    }

    @Test
    fun testGetNodePropertyResponseFromByteArraySuccessMac() {
        val data = "0E29010203040506070800000101".hexToByteArray()
        val response = GetNodePropertyResponse.fromByteArray(data)

        assertEquals("0102030405060708".hexToByteArray().toList(), response.idm.toList())
        assertEquals(0x00.toByte(), response.statusFlag1)
        assertEquals(0x00.toByte(), response.statusFlag2)
        assertEquals(1, response.nodeProperties.size)
        assertTrue(response.isStatusSuccessful)

        val property = response.nodeProperties[0] as MacCommunicationProperty
        assertNotNull(property)
        assertTrue(property!!.enabled)
    }

    @Test
    fun testGetNodePropertyResponseFromByteArraySuccessValueLimitedPurse() {
        // 10 bytes property: enabled(1) + upperLimit(4) + lowerLimit(4) + generation(1)
        val propertyData =
            "01" + "10270000" + "C2F7FFFF" + "2A" // enabled=true, upper=10000, lower=-2110, gen=42
        val data =
            ("17" + "29" + "0102030405060708" + "0000" + "01" + propertyData).hexToByteArray()
        val response = GetNodePropertyResponse.fromByteArray(data)

        assertEquals("0102030405060708".hexToByteArray().toList(), response.idm.toList())
        assertEquals(0x00.toByte(), response.statusFlag1)
        assertEquals(0x00.toByte(), response.statusFlag2)
        assertEquals(1, response.nodeProperties.size)
        assertTrue(response.isStatusSuccessful)

        val property = response.nodeProperties[0] as ValueLimitedPurseServiceProperty
        assertNotNull(property)
        assertTrue(property.enabled)
        assertEquals(10000, property.upperLimit)
        assertEquals(-2110, property.lowerLimit)
        assertEquals(42, property.generationNumber)
    }

    @Test
    fun testGetNodePropertyResponseFromByteArrayInvalidResponseCode() {
        val data =
            "0C28010203040506070810FF".hexToByteArray() // Wrong response code 0x28 instead of 0x29

        assertThrows(IllegalArgumentException::class.java) {
            GetNodePropertyResponse.fromByteArray(data)
        }
    }

    @Test
    fun testGetNodePropertyResponseInvalidErrorConstruction() {
        val idm = IDM
        val statusFlag1 = 0xFF.toByte() // Error status
        val nodeProperties =
            arrayOf<NodeProperty>(MacCommunicationProperty(true)) // Should be empty for error

        assertThrows(IllegalArgumentException::class.java) {
            GetNodePropertyResponse(idm, statusFlag1, 0x00.toByte(), nodeProperties)
        }
    }

    @Test
    fun testGetNodePropertyResponseInvalidSuccessConstruction() {
        val idm = IDM
        val statusFlag1 = 0x00.toByte() // Success status
        val nodeProperties = emptyArray<NodeProperty>() // Should not be empty for success

        assertThrows(IllegalArgumentException::class.java) {
            GetNodePropertyResponse(idm, statusFlag1, 0x00.toByte(), nodeProperties)
        }
    }
}
