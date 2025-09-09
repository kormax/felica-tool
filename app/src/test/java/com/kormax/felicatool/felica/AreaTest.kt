package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

/** Unit tests for Area */
class AreaTest {

    companion object {
        private val ROOT_AREA_CODE = "0000feff".hexToByteArray()
        private val ROOT_AREA = Area.fromByteArray(ROOT_AREA_CODE)

        // Test data from user specifications
        private val COMMON_AREA_AREAS =
            listOf(
                    "0000feff",
                    "00103f17",
                    "01103f17",
                    "8055bf56",
                    "8155bf56",
                    "c067ff68",
                    "c167ff68",
                )
                .map { it.hexToByteArray() }

        private val FELICA_CE_AREAS =
            listOf("0000feff", "0010ff7f", "0110ff7f", "0080ffdf", "0180ffaf", "01b0ffbf").map {
                it.hexToByteArray()
            }

        private val CJRC_AREAS =
            listOf(
                    "0000feff",
                    "4000ff07",
                    "c000ff00",
                    "0008bf0f",
                    "c00fff7f",
                    "0010bf17",
                    "c017ff7f",
                    "00183f1a",
                    "001f3f1f",
                    "00233f24",
                )
                .map { it.hexToByteArray() }
    }

    @Test
    fun testArea_fromByteArray_basic() {
        // Test basic Area creation from bytes
        val area = Area.fromByteArray(ROOT_AREA_CODE)

        assertEquals(0, area.number)
        assertEquals(AreaAttribute.CAN_CREATE_SUB_AREA, area.attribute)
        assertEquals(1023, area.endNumber)
        assertEquals(AreaAttribute.END_ROOT_AREA, area.endAttribute)
    }

    @Test
    fun testArea_toByteArray_basic() {
        // Test basic Area to bytes conversion
        val area = Area(0, AreaAttribute.CAN_CREATE_SUB_AREA, 1023, AreaAttribute.END_ROOT_AREA)
        val bytes = area.toByteArray()
        assertArrayEquals(ROOT_AREA_CODE, bytes)
    }

    @Test
    fun testArea_roundTrip() {
        // Test that fromByteArray and toByteArray are inverses
        val area = Area.fromByteArray(ROOT_AREA_CODE)
        val roundTripData = area.toByteArray()
        assertArrayEquals(ROOT_AREA_CODE, roundTripData)
    }

    @Test
    fun testArea_fromByteArray_allTestAreas() {
        // Test all area collections for round-trip parsing
        val allTestAreaCollections =
            listOf(
                "COMMON_AREA_AREAS" to COMMON_AREA_AREAS,
                "FELICA_CE_AREAS" to FELICA_CE_AREAS,
                "CJRC_AREAS" to CJRC_AREAS,
            )

        allTestAreaCollections.forEach { (collectionName, areas) ->
            areas.forEach { data ->
                val area = Area.fromByteArray(data)
                val roundTripData = area.toByteArray()
                assertArrayEquals(
                    "Round trip failed for $collectionName data ${data.toHexString()}, parsed as $area ${area.toByteArray().toHexString()}",
                    data,
                    roundTripData,
                )
            }
        }
    }

    @Test
    fun testArea_belongsTo_relationships() {
        // Test hierarchical relationships
        val parentArea = Area(0, AreaAttribute.CAN_CREATE_SUB_AREA, 100, AreaAttribute.END_SUB_AREA)
        val childArea = Area(10, AreaAttribute.CAN_CREATE_SUB_AREA, 50, AreaAttribute.END_SUB_AREA)
        val grandchildArea =
            Area(20, AreaAttribute.CAN_CREATE_SUB_AREA, 30, AreaAttribute.END_SUB_AREA)

        // Child should belong to parent
        assertTrue("Child area should belong to parent area", childArea.belongsTo(parentArea))

        // Grandchild should belong to parent
        assertTrue(
            "Grandchild area should belong to parent area",
            grandchildArea.belongsTo(parentArea),
        )

        // Grandchild should belong to child
        assertTrue(
            "Grandchild area should belong to child area",
            grandchildArea.belongsTo(childArea),
        )

        // Parent should not belong to child
        assertFalse("Parent area should not belong to child area", parentArea.belongsTo(childArea))
    }

    @Test
    fun testArea_belongsTo_service() {
        // Test that areas do not belong to services
        val area = Area.fromByteArray(ROOT_AREA_CODE)
        val service = Service(100, ServiceAttribute.RANDOM_RW_WITH_KEY)

        assertFalse("Area should not belong to service", area.belongsTo(service))
    }

    @Test
    fun testArea_belongsTo_edgeCases() {
        // Test edge cases for belongsTo
        val area1 = Area(0, AreaAttribute.CAN_CREATE_SUB_AREA, 100, AreaAttribute.END_SUB_AREA)
        val area2 =
            Area(
                50,
                AreaAttribute.CAN_CREATE_SUB_AREA,
                150,
                AreaAttribute.END_SUB_AREA,
            ) // Overlaps but not contained
        val area3 =
            Area(
                200,
                AreaAttribute.CAN_CREATE_SUB_AREA,
                300,
                AreaAttribute.END_SUB_AREA,
            ) // Completely outside

        // Overlapping but not contained should return false
        assertFalse("Overlapping area should not belong to another area", area2.belongsTo(area1))

        // Completely outside range should return false
        assertFalse("Area outside range should not belong to another area", area3.belongsTo(area1))
    }
}
