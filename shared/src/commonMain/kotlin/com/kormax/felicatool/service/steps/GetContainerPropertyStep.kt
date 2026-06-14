package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object GetContainerPropertyStep :
    CommandSupportScanStep(
        id = "get_container_property",
        title = "Get Container Property",
        description = "Get container property data by index",
        icon = ScanStepIcon.INFO,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.getContainerPropertySupport

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.copy(getContainerPropertySupport = support)

    override suspend fun ScanSession.perform(): StepOutput {
        val results = mutableListOf<String>()
        val containerPropertyValues =
            mutableMapOf<GetContainerPropertyCommand.Property, ByteArray>()

        // Test both known property types
        val propertiesToTest =
            listOf(
                GetContainerPropertyCommand.Property.PROPERTY_1,
                GetContainerPropertyCommand.Property.PROPERTY_2,
            )

        var successfulCommands = 0

        propertiesToTest.forEach { property ->
            val getContainerPropertyResponse = executeCommand {
                GetContainerPropertyCommand(property)
            }

            // Store the property value in the map using Property object as key
            containerPropertyValues[property] = getContainerPropertyResponse.data

            successfulCommands++
            results.add(
                buildString {
                    appendLine("Property ${property.name} (index 0x${byteToHex(property.index)}):")
                    appendLine("  Command: SUCCESS")
                    appendLine(
                        "  Response Data: ${getContainerPropertyResponse.data.toHexString()}"
                    )
                    appendLine("  Data Size: ${getContainerPropertyResponse.data.size} bytes")
                }
            )
        }

        // Store container property values in scan context
        scanContext = scanContext.copy(containerPropertyValues = containerPropertyValues.toMap())

        return StepOutput(
            buildString {
                    appendLine(
                        "Get Container Property Results: $successfulCommands/${propertiesToTest.size} properties retrieved"
                    )
                    appendLine()
                    results.forEach { result -> appendLine(result.trimEnd()) }
                }
                .trim()
        )
    }
}
