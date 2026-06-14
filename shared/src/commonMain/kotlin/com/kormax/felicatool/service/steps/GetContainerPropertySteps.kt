package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

private val CONTAINER_PROPERTIES_TO_READ =
    listOf(
        GetContainerPropertyCommand.Property.PROPERTY_1,
        GetContainerPropertyCommand.Property.PROPERTY_2,
    )

internal object GetContainerPropertyDetermineSupportedStep :
    CommandSupportScanStep(
        id = "get_container_property_determine_supported",
        title = "Get Container Property - Supported",
        description = "Check whether Get Container Property is available",
        icon = ScanStepIcon.INFO,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.getContainerProperty.supported

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(getContainerProperty = getContainerProperty.copy(supported = support))
    }

    override suspend fun ScanSession.perform(): StepOutput {
        val property = GetContainerPropertyCommand.Property.PROPERTY_1
        val response =
            executeCommand(withSelectedSystemCode = SYSTEM_CODE_WILDCARD) {
                GetContainerPropertyCommand(property)
            }

        return StepOutput(
            buildString {
                    appendLine("Get Container Property command is supported (response received)")
                    appendLine("Property: ${property.label()}")
                    appendLine("Response Data: ${response.data.toHexString()}")
                    appendLine("Data Size: ${response.data.size} bytes")
                }
                .trim()
        )
    }
}

internal object GetContainerPropertyDetermineTrailingDataSupportedStep :
    CommandTrailingDataSupportedScanStep<GetContainerPropertyResponse>(
        id = "get_container_property_determine_trailing_data_supported",
        title = "Get Container Property - Trailing Data Supported",
        description = "Check whether Get Container Property accepts trailing data bytes",
        icon = ScanStepIcon.SEARCH,
        commandName = "Get Container Property",
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.getContainerProperty.supported

    override fun writeTrailingDataSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(getContainerProperty = getContainerProperty.copy(trailingDataSupported = support))
    }

    override fun ScanSession.createCommand(
        scope: CommandExecutionScope,
        trailingData: ByteArray,
    ): FelicaCommand<GetContainerPropertyResponse> =
        GetContainerPropertyCommand(
            property = GetContainerPropertyCommand.Property.PROPERTY_1,
            trailingData = trailingData,
        )

    override fun responseLines(response: GetContainerPropertyResponse): List<String> =
        listOf(
            "Response Data: ${response.data.toHexString()}",
            "Data Size: ${response.data.size} bytes",
        )
}

internal object GetContainerPropertiesStep :
    ScanStep(
        id = "get_container_properties",
        title = "Get Container Properties",
        description = "Get known container property values",
        icon = ScanStepIcon.INFO,
    ) {
    override fun commandSupport(context: CardScanContext): CommandSupport =
        context.commands.getContainerProperty.supported

    override suspend fun ScanSession.perform(): StepOutput {
        val containerPropertyValues =
            mutableMapOf<GetContainerPropertyCommand.Property, ByteArray>()
        val results = mutableListOf<String>()

        for (property in CONTAINER_PROPERTIES_TO_READ) {
            val response =
                executeCommand(withSelectedSystemCode = SYSTEM_CODE_WILDCARD) {
                    GetContainerPropertyCommand(property)
                }

            containerPropertyValues[property] = response.data
            results.add(
                buildString {
                    appendLine("Property: ${property.label()}")
                    appendLine("  Response Data: ${response.data.toHexString()}")
                    appendLine("  Data Size: ${response.data.size} bytes")
                }
            )
        }

        scanContext =
            scanContext.copy(
                containerPropertyValues =
                    scanContext.containerPropertyValues + containerPropertyValues
            )

        return StepOutput(
            buildString {
                    appendLine(
                        "Container properties retrieved: ${containerPropertyValues.size}/${CONTAINER_PROPERTIES_TO_READ.size}"
                    )
                    appendLine()
                    results.forEach { result -> appendLine(result.trimEnd()) }
                }
                .trim()
        )
    }
}

private fun GetContainerPropertyCommand.Property.label(): String =
    "${name} (index 0x${(index.toInt() and 0xFFFF).toString(16).uppercase().padStart(4, '0')})"
