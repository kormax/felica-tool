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
        assertEquals(AreaAttribute.CanCreateSubArea, area.attribute)
        assertEquals(1023, area.endNumber)
        assertEquals(AreaAttribute.EndRootArea, area.endAttribute)
    }

    @Test
    fun testArea_toByteArray_basic() {
        // Test basic Area to bytes conversion
        val area = Area(0, AreaAttribute.CanCreateSubArea, 1023, AreaAttribute.EndRootArea)
        val bytes = area.toByteArray()
        assertArrayEquals(ROOT_AREA_CODE, bytes)
    }

    @Test
    fun testArea_fromByteArray_unknownAttributeSupported() {
        val unknownAttributeValue = 0b010010
        val areaNumber = 1
        val endAttribute = AreaAttribute.EndSubArea

        val areaCode = ((areaNumber shl 6) or unknownAttributeValue).toShort()
        val endAreaCode = ((areaNumber shl 6) or endAttribute.value).toShort()

        val data =
            byteArrayOf(
                areaCode.toByte(),
                (areaCode.toInt() shr 8).toByte(),
                endAreaCode.toByte(),
                (endAreaCode.toInt() shr 8).toByte(),
            )

        val area = Area.fromByteArray(data)

        assertEquals(areaNumber, area.number)
        assertEquals(AreaAttribute.Unknown(unknownAttributeValue), area.attribute)
        assertTrue(area.attribute is AreaAttribute.Unknown)
        assertEquals(endAttribute, area.endAttribute)
        assertFalse(endAttribute is AreaAttribute.Unknown)
        assertArrayEquals(data, area.toByteArray())
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
        val parentArea = Area(0, AreaAttribute.CanCreateSubArea, 100, AreaAttribute.EndSubArea)
        val childArea = Area(10, AreaAttribute.CanCreateSubArea, 50, AreaAttribute.EndSubArea)
        val grandchildArea = Area(20, AreaAttribute.CanCreateSubArea, 30, AreaAttribute.EndSubArea)

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
        val service = Service(100, ServiceAttribute.RandomRwWithKey)

        assertFalse("Area should not belong to service", area.belongsTo(service))
    }

    @Test
    fun testArea_belongsTo_edgeCases() {
        // Test edge cases for belongsTo
        val area1 = Area(0, AreaAttribute.CanCreateSubArea, 100, AreaAttribute.EndSubArea)
        val area2 =
            Area(
                50,
                AreaAttribute.CanCreateSubArea,
                150,
                AreaAttribute.EndSubArea,
            ) // Overlaps but not contained
        val area3 =
            Area(
                200,
                AreaAttribute.CanCreateSubArea,
                300,
                AreaAttribute.EndSubArea,
            ) // Completely outside

        // Overlapping but not contained should return false
        assertFalse("Overlapping area should not belong to another area", area2.belongsTo(area1))

        // Completely outside range should return false
        assertFalse("Area outside range should not belong to another area", area3.belongsTo(area1))
    }
}
