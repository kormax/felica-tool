package com.kormax.felicatool.util

import com.kormax.felicatool.felica.Service
import com.kormax.felicatool.service.SystemScanContext
import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceGrouperTest {
    @Test
    fun groupServicesDeduplicatesRepeatedServiceNodes() {
        val service = Service.fromHexString("0b00")
        val context = SystemScanContext(nodes = listOf(service, service, service))

        val groups = ServiceGrouper.groupServices(context)

        assertEquals(1, groups.size)
        assertEquals(listOf(service), groups.single().services)
    }
}
