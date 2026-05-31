package com.kormax.felicatool.felica

import kotlin.time.Duration
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeliCaTargetStateTest {
    @Test
    fun aliasesExposeCurrentValues() {
        val target = FakeFeliCaTarget()
        val newIdm = "1122334455667788".hexToByteArray()

        target.idm = newIdm
        target.currentSystemCode = SYSTEM_CODE_FE00

        assertArrayEquals(newIdm, target.currentIdm)
        assertArrayEquals(newIdm, target.idm)
        assertArrayEquals(SYSTEM_CODE_FE00, target.systemCode)
    }

    @Test
    fun pollingWithoutSystemCodeRequestUpdatesCurrentIdmAndClearsCurrentSystemCode() = runBlocking {
        val responseIdm = "1122334455667788".hexToByteArray()
        val target =
            FakeFeliCaTarget(
                response = PollingResponse(responseIdm, PMM.toByteArray()).toByteArray()
            )

        target.transceive(PollingCommand(systemCode = SYSTEM_CODE_FE00))

        assertArrayEquals(responseIdm, target.currentIdm)
        assertNull(target.currentSystemCode)
    }

    @Test
    fun pollingWithSystemCodeRequestUpdatesCurrentIdmAndCurrentSystemCode() = runBlocking {
        val responseIdm = "1122334455667788".hexToByteArray()
        val target =
            FakeFeliCaTarget(
                response =
                    PollingResponse(
                            idm = responseIdm,
                            pmm = PMM.toByteArray(),
                            requestData = SYSTEM_CODE_FE00,
                        )
                        .toByteArray()
            )

        target.transceive(
            PollingCommand(
                systemCode = SYSTEM_CODE_FE00,
                requestCode = RequestCode.SYSTEM_CODE_REQUEST,
            )
        )

        assertArrayEquals(responseIdm, target.currentIdm)
        assertArrayEquals(SYSTEM_CODE_FE00, target.currentSystemCode)
    }

    @Test
    fun idmCommandKeepsCurrentSystemCodeWhenCurrentIdmDoesNotChange() = runBlocking {
        val target = FakeFeliCaTarget(response = TestResponse().toByteArray())
        target.currentSystemCode = SYSTEM_CODE_FE00

        target.transceive(TestCommand(target.currentIdm))

        assertArrayEquals(INITIAL_IDM, target.currentIdm)
        assertArrayEquals(SYSTEM_CODE_FE00, target.currentSystemCode)
    }

    @Test
    fun idmCommandClearsCurrentSystemCodeWhenCurrentIdmChanges() = runBlocking {
        val commandIdm = "1122334455667788".hexToByteArray()
        val target = FakeFeliCaTarget(response = TestResponse().toByteArray())
        target.currentSystemCode = SYSTEM_CODE_FE00

        target.transceive(TestCommand(commandIdm))

        assertArrayEquals(commandIdm, target.currentIdm)
        assertNull(target.currentSystemCode)
    }

    private class FakeFeliCaTarget(response: ByteArray = TestResponse().toByteArray()) :
        FeliCaTarget {
        private val responses = mutableListOf(response)

        override val initialIdm: ByteArray = INITIAL_IDM.copyOf()
        override val initialSystemCode: ByteArray? = SYSTEM_CODE_FE0F.copyOf()
        override var currentIdm: ByteArray = initialIdm.copyOf()
            set(value) {
                field = value.copyOf()
            }

        override var currentSystemCode: ByteArray? = initialSystemCode?.copyOf()
            set(value) {
                field = value?.copyOf()
            }

        override val pmm: Pmm = PMM

        override suspend fun transceive(data: ByteArray, timeout: Duration?): ByteArray =
            responses.removeAt(0)
    }

    private class TestCommand(idm: ByteArray) : FelicaCommandWithIdm<TestResponse>(idm) {
        override fun toByteArray(): ByteArray = byteArrayOf(0x0A, 0x7F) + idm

        override fun responseFromByteArray(data: ByteArray): TestResponse = TestResponse()
    }

    private class TestResponse : FelicaResponseWithoutIdm() {
        override fun toByteArray(): ByteArray = byteArrayOf(0x02, 0x80.toByte())
    }

    private companion object {
        val INITIAL_IDM = "0102030405060708".hexToByteArray()
        val SYSTEM_CODE_FE0F = "FE0F".hexToByteArray()
        val SYSTEM_CODE_FE00 = "FE00".hexToByteArray()
        val PMM = Pmm("1112131415161718".hexToByteArray())
    }
}
