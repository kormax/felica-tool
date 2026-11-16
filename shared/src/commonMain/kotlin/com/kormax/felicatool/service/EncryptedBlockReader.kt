package com.kormax.felicatool.service

import com.kormax.felicatool.authentication.SecureChannel
import com.kormax.felicatool.felica.BlockListElement
import com.kormax.felicatool.felica.ErrorLocationIndication
import com.kormax.felicatool.felica.ReadWithEncryptionDes
import com.kormax.felicatool.felica.Service
import com.kormax.felicatool.nfc.TransceiveTimeoutException

internal class EncryptedBlockReader(
    private val channel: SecureChannel,
    maxBlocksPerRequest: Int,
    errorLocationIndication: ErrorLocationIndication,
) :
    BlockReader(
        maxBlocksPerRequest,
        channel.scope.nodeCodes.size,
        errorLocationIndication,
    ) {
    override suspend fun readBatch(
        services: List<Service>,
        blocks: List<BlockListElement>,
    ): BatchResult {
        val remapped = blocks.map { element ->
            val code = services[element.serviceCodeListOrder].code.toHexString().uppercase()
            val scopeOrder = channel.scope.nodeCodes.indexOf(code)
            require(scopeOrder >= 0) { "Service $code is outside the authenticated scope" }
            element.copy(serviceCodeListOrder = scopeOrder)
        }
        val response =
            channel.transceive(ReadWithEncryptionDes.Command.Plain(remapped))
                ?: throw TransceiveTimeoutException("No response to encrypted read")
        return BatchResult(status = response.status, blocks = response.blockData)
    }
}
