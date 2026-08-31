package com.kormax.felicatool.service

import com.kormax.felicatool.felica.BlockListElement
import com.kormax.felicatool.felica.ErrorLocationIndication
import com.kormax.felicatool.felica.ReadWithoutEncryptionCommand
import com.kormax.felicatool.felica.Service

internal class UnencryptedBlockReader(
    private val session: ScanSession,
    private val systemCode: ByteArray?,
    maxBlocksPerRequest: Int,
    maxServicesPerRequest: Int,
    errorLocationIndication: ErrorLocationIndication,
) : BlockReader(maxBlocksPerRequest, maxServicesPerRequest, errorLocationIndication) {
    override suspend fun readBatch(
        services: List<Service>,
        blocks: List<BlockListElement>,
    ): BatchResult {
        val response =
            session.executeCommand(withSelectedSystemCode = systemCode) {
                ReadWithoutEncryptionCommand(
                    idm = idm,
                    serviceCodes = services.map { it.code }.toTypedArray(),
                    blockListElements = blocks.toTypedArray(),
                )
            }
        return BatchResult(
            status = response.status,
            blocks = response.blockData.toList(),
        )
    }
}
