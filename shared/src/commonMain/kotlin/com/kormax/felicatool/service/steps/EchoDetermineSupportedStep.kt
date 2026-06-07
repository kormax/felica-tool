package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object EchoDetermineSupportedStep :
    EchoScanStep(
        id = "echo_determine_supported",
        title = "Echo: Determine Supported",
        description = "Check whether Echo is available",
        icon = ScanStepIcon.REFRESH,
    ) {
    override suspend fun ScanSession.perform(): StepOutput {
        ensureCardPresence(target)

        val payload = ByteArray(0)
        val response = target.transceive(EchoCommand(payload))
        if (!response.data.contentEquals(payload)) {
            throw RuntimeException(
                "Echo mismatch (${response.data.size} bytes returned): ${response.data.toHexString()}"
            )
        }

        return StepOutput("Echo command is supported (response matched ${payload.size} bytes)")
    }
}
