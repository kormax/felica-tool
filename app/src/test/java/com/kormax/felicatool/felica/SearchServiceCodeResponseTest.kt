package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

/** Unit tests for SearchServiceCodeResponse */
class SearchServiceCodeResponseTest {

    private val IDM = "013933333333e6f5"

    @Test
    fun testSearchServiceCodeResponse_fromByteArray() {
        val testCases =
            arrayOf(
                "0e0b${IDM}0000feff" to
                    Area(0, AreaAttribute.CAN_CREATE_SUB_AREA, 1023, AreaAttribute.END_ROOT_AREA),
                "0c0b${IDM}8855" to Service(342, ServiceAttribute.RANDOM_RW_WITH_KEY),
                "0e0b${IDM}8055bf56" to
                    Area(342, AreaAttribute.CAN_CREATE_SUB_AREA, 346, AreaAttribute.END_SUB_AREA),
                "0e0b${IDM}8155bf56" to
                    Area(342, AreaAttribute.CANNOT_CREATE_SUB_AREA, 346, AreaAttribute.END_SUB_AREA),
            )

        val expectedIdm = IDM.hexToByteArray()

        testCases.forEach { (responseHex, expectedNode) ->
            val responseBytes = responseHex.hexToByteArray()
            val response = SearchServiceCodeResponse.fromByteArray(responseBytes)

            assertArrayEquals(
                "Failed for ${expectedNode::class.simpleName}",
                expectedIdm,
                response.idm,
            )
            assertNotNull("Failed for ${expectedNode::class.simpleName}", response.node)

            when (expectedNode) {
                is Area -> {
                    assertTrue(
                        "Failed for ${expectedNode::class.simpleName}",
                        response.node is Area,
                    )
                    val area = response.node as Area
                    assertEquals(
                        "Failed for ${expectedNode::class.simpleName} number",
                        expectedNode.number,
                        area.number,
                    )
                    assertEquals(
                        "Failed for ${expectedNode::class.simpleName} attribute",
                        expectedNode.attribute,
                        area.attribute,
                    )
                    assertEquals(
                        "Failed for ${expectedNode::class.simpleName} endNumber",
                        expectedNode.endNumber,
                        area.endNumber,
                    )
                    assertEquals(
                        "Failed for ${expectedNode::class.simpleName} endAttribute",
                        expectedNode.endAttribute,
                        area.endAttribute,
                    )
                }
                is Service -> {
                    assertTrue(
                        "Failed for ${expectedNode::class.simpleName}",
                        response.node is Service,
                    )
                    val service = response.node as Service
                    assertEquals(
                        "Failed for ${expectedNode::class.simpleName} number",
                        expectedNode.number,
                        service.number,
                    )
                    assertEquals(
                        "Failed for ${expectedNode::class.simpleName} attribute",
                        expectedNode.attribute,
                        service.attribute,
                    )
                }
                else -> fail("Unexpected node type")
            }
        }
    }

    @Test
    fun testSearchServiceCodeResponse_toByteArray() {
        val idm = IDM.hexToByteArray()
        val node = Service(342, ServiceAttribute.RANDOM_RW_WITH_KEY)
        val response = SearchServiceCodeResponse(idm, node)
        val bytes = response.toByteArray()
        val expected = "0c0b${IDM}8855".hexToByteArray()
        assertArrayEquals(expected, bytes)
    }
}
