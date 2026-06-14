package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.FelicaCommand
import com.kormax.felicatool.felica.FelicaResponse
import com.kormax.felicatool.nfc.TransceiveTimeoutException
import com.kormax.felicatool.service.CardScanContext
import com.kormax.felicatool.service.CommandExecutionScope
import com.kormax.felicatool.service.CommandSupport
import com.kormax.felicatool.service.SYSTEM_CODE_WILDCARD
import com.kormax.felicatool.service.ScanSession
import com.kormax.felicatool.service.ScanSettings
import com.kormax.felicatool.service.ScanStep
import com.kormax.felicatool.service.StepOutput
import com.kormax.felicatool.service.StepSkipped
import com.kormax.felicatool.ui.ScanStepIcon

private val COMMAND_TRAILING_DATA_PROBE_BYTES = byteArrayOf(0x00)

internal abstract class CommandTrailingDataSupportedScanStep<T : FelicaResponse>(
    id: String,
    title: String,
    description: String,
    icon: ScanStepIcon,
    private val commandName: String,
) :
    ScanStep(
        id = id,
        title = title,
        description = description,
        icon = icon,
    ) {
    override fun isEnabled(settings: ScanSettings): Boolean = settings.testTrailingDataCommands

    protected abstract fun readSupport(context: CardScanContext): CommandSupport

    protected abstract fun writeTrailingDataSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext

    protected abstract fun ScanSession.createCommand(
        scope: CommandExecutionScope,
        trailingData: ByteArray,
    ): FelicaCommand<T>

    protected open fun ScanSession.selectedSystemCode(): ByteArray? = SYSTEM_CODE_WILDCARD

    protected open fun ScanSession.resetToMode0AfterCommand(): Boolean = false

    protected open fun responseLines(response: T): List<String> = emptyList()

    override fun commandSupport(context: CardScanContext): CommandSupport = readSupport(context)

    override suspend fun ScanSession.perform(): StepOutput {
        if (readSupport(scanContext) != CommandSupport.SUPPORTED) {
            throw StepSkipped(
                "$commandName support must be confirmed before checking trailing data"
            )
        }

        var commandLength = 0
        val response =
            try {
                executeCommand(
                    withSelectedSystemCode = selectedSystemCode(),
                    withResetToMode0 = resetToMode0AfterCommand(),
                ) {
                    val command = createCommand(this, COMMAND_TRAILING_DATA_PROBE_BYTES)
                    commandLength = command.toByteArray().size
                    command
                }
            } catch (e: TransceiveTimeoutException) {
                null
            }

        val trailingDataSupport =
            if (response != null) CommandSupport.SUPPORTED else CommandSupport.UNSUPPORTED
        scanContext = writeTrailingDataSupport(scanContext, trailingDataSupport)

        return StepOutput(
            buildString {
                    val supportLabel = if (response != null) "supported" else "not supported"
                    appendLine("$commandName with trailing data: $supportLabel")
                    appendLine("Command length: $commandLength bytes")
                    appendLine("Trailing data: ${COMMAND_TRAILING_DATA_PROBE_BYTES.toHexString()}")
                    if (response != null) {
                        responseLines(response).forEach { appendLine(it) }
                    } else {
                        appendLine("No response")
                    }
                }
                .trim()
        )
    }
}
