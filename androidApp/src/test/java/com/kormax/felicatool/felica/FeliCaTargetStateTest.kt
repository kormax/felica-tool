package com.kormax.felicatool.felica

import com.kormax.felicatool.nfc.NfcReaderSession
import kotlin.time.Duration
import org.junit.Assert.assertArrayEquals
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

    private class FakeFeliCaTarget : FeliCaTarget {
        override val readerSession: NfcReaderSession =
            object : NfcReaderSession {
                override suspend fun discoverFeliCaTarget(timeout: Duration): FeliCaTarget {
                    throw UnsupportedOperationException("Fake target does not support discovery")
                }

                override fun close() = Unit
            }

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
        override val isAvailable: Boolean = true

        override suspend fun drop() = Unit

        override suspend fun transceive(data: ByteArray, timeout: Duration?): ByteArray {
            throw UnsupportedOperationException("Fake target does not support transceive")
        }
    }

    private companion object {
        val INITIAL_IDM = "0102030405060708".hexToByteArray()
        val SYSTEM_CODE_FE0F = "FE0F".hexToByteArray()
        val SYSTEM_CODE_FE00 = "FE00".hexToByteArray()
        val PMM = Pmm("1112131415161718".hexToByteArray())
    }
}
