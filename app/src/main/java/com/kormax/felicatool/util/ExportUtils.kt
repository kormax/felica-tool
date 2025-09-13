package com.kormax.felicatool.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.CardScanContext
import com.kormax.felicatool.service.CommandSupport
import com.kormax.felicatool.service.SystemScanContext
import com.kormax.felicatool.service.logging.CommunicationLogEntry
import java.text.SimpleDateFormat
import java.util.*
import org.json.JSONArray
import org.json.JSONObject

object ExportUtils {
    /** Exports the communication log as a JSON file and shares it */
    fun exportCommunicationLog(context: Context, log: List<CommunicationLogEntry>) {
        try {
            val jsonArray = generateCommunicationLogJson(log)
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "felicatools_communication_$timestamp.json"
            val jsonContent = jsonArray.toString(2)

            val values =
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

            val uri =
                context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            uri?.let { fileUri ->
                context.contentResolver.openOutputStream(fileUri)?.use { output ->
                    output.write(jsonContent.toByteArray())
                }
                shareJsonFile(context, fileUri, fileName)
            }
        } catch (e: Exception) {
            android.util.Log.e("ExportUtils", "Failed to export communication log", e)
        }
    }

    /** Generates JSON representation of the communication log */
    fun generateCommunicationLogJson(log: List<CommunicationLogEntry>): JSONArray {
        val jsonArray = JSONArray()

        // Get the timestamp of the first entry to use as baseline
        val baseTimestamp = log.firstOrNull()?.timestamp ?: 0L

        log.forEach { entry ->
            val obj = JSONObject()
            obj.put("type", entry.type.name)
            entry.name?.let { obj.put("name", it) }
            obj.put("timestamp_ns", entry.timestamp - baseTimestamp)
            obj.put("data", entry.data?.joinToString(separator = "") { "%02X".format(it) })
            jsonArray.put(obj)
        }
        return jsonArray
    }

    /** Exports the scan data as a flat list to a JSON file */
    fun exportFlatList(context: Context, scanContext: CardScanContext) {
        try {
            val json = generateFlatListJson(scanContext)

            // Create filename with primary IDM and timestamp
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val primaryIdmPrefix = scanContext.primaryIdm?.toHexString()?.let { "${it}_" } ?: ""
            val fileName = "felicatools_scan_${primaryIdmPrefix}$timestamp.json"
            val jsonContent = json.toString(2)

            val values =
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

            val uri =
                context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            uri?.let { fileUri ->
                context.contentResolver.openOutputStream(fileUri)?.use { output ->
                    output.write(jsonContent.toByteArray())
                }

                // Share the actual file
                shareJsonFile(context, fileUri, fileName)
            }
        } catch (e: Exception) {
            // Handle error - could show a toast or error dialog
            android.util.Log.e("ExportUtils", "Failed to export flat list", e)
        }
    }

    /** Helper function to share JSON file via Intent */
    private fun shareJsonFile(context: Context, fileUri: Uri, fileName: String) {
        val shareIntent =
            Intent().apply {
                action = Intent.ACTION_SEND
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                putExtra(Intent.EXTRA_SUBJECT, "FeliCa Tools File")
                putExtra(Intent.EXTRA_TITLE, fileName) // Add explicit title
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        val chooserIntent = Intent.createChooser(shareIntent, "Share $fileName")
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooserIntent)
    }

    /** Generates JSON representation of the CardScanContext as a flat list organized per system */
    private fun generateFlatListJson(scanContext: CardScanContext): JSONObject {
        val json = JSONObject()

        // Primary attributes from CardScanContext
        scanContext.primaryIdm?.let { json.put("primary_idm", it.toHexString().lowercase()) }

        scanContext.pmm?.let { pmm -> json.put("pmm", pmm.toHexString().lowercase()) }

        // IC type from PMM data
        scanContext.pmm?.let { pmm ->
            val icTypeFormatted = IcTypeMapping.getFormattedIcType(pmm.icType)
            json.put("ic_type_name", icTypeFormatted)
        }

        scanContext.primarySystemCode?.let { json.put("primary_system_code", it.toHexString()) }

        scanContext.containerIdm?.let { json.put("container_idm", it.toHexString()) }

        // Specification version
        scanContext.specificationVersion?.let { spec ->
            val specJson = JSONObject()
            spec.formatVersion?.let {
                specJson.put("format_version", "%02X".format(it.toUByte().toInt()))
            }
            spec.basicVersion?.let { basic -> specJson.put("basic_version", basic.toString()) }
            spec.desOptionVersion?.let { des -> specJson.put("des_option_version", des.toString()) }
            spec.specialOptionVersion?.let { special ->
                specJson.put("special_option_version", special.toString())
            }
            spec.extendedOverlapOptionVersion?.let { extended ->
                specJson.put("extended_overlap_option_version", extended.toString())
            }
            spec.communicationWithMacOptionVersion?.let { aes ->
                specJson.put("communication_with_mac_option_version", aes.toString())
            }
            spec.valueLimitedPurseServiceOptionVersion?.let { valueLimited ->
                specJson.put("value_limited_purse_service_option_version", valueLimited.toString())
            }
            json.put("specification_version", specJson)
        }

        // Container issue information
        scanContext.containerIssueInformation?.let { container ->
            val containerJson = JSONObject()
            containerJson.put(
                "format_version_carrier_info",
                container.formatVersionCarrierInformation.toHexString(),
            )

            val modelString =
                try {
                    val printableBytes =
                        container.mobilePhoneModelInformation.filter { it in 32..126 }
                    if (printableBytes.size >= 3) {
                        String(printableBytes.toByteArray(), Charsets.UTF_8)
                    } else {
                        container.mobilePhoneModelInformation.toHexString()
                    }
                } catch (e: Exception) {
                    container.mobilePhoneModelInformation.toHexString()
                }
            containerJson.put("mobile_phone_model_info", modelString)
            json.put("container_issue_information", containerJson)
        }

        // Error indication mode
        json.put("error_indication_mode", scanContext.errorLocationIndication.name)

        // Max systems/blocks per read
        scanContext.maxServicesPerRequest?.let { json.put("max_systems_per_read", it) }
        scanContext.maxBlocksPerRequest?.let { json.put("max_blocks_per_read", it) }

        // Supported commands - build as a list of only supported commands
        val supportedCommandsArray = JSONArray()
        val commandMappings =
            listOf(
                "polling" to scanContext.pollingSupport,
                "polling_system_code" to scanContext.pollingSystemCodeSupport,
                "polling_communication_performance" to
                    scanContext.pollingCommunicationPerformanceSupport,
                "request_response" to scanContext.requestResponseSupport,
                "reset_mode" to scanContext.resetModeSupport,
                "request_system_code" to scanContext.requestSystemCodeSupport,
                "request_specification_version" to scanContext.requestSpecificationVersionSupport,
                "get_platform_information" to scanContext.getPlatformInformationSupport,
                "get_system_status" to scanContext.getSystemStatusSupport,
                "request_code_list" to scanContext.requestCodeListSupport,
                "search_service_code" to scanContext.searchServiceCodeSupport,
                "request_service" to scanContext.requestServiceSupport,
                "request_service_v2" to scanContext.requestServiceV2Support,
                "request_block_information" to scanContext.requestBlockInformationSupport,
                "request_block_information_ex" to scanContext.requestBlockInformationExSupport,
                "get_node_property_value_limited_service" to
                    scanContext.getNodePropertyValueLimitedServiceSupport,
                "get_node_property_mac_communication" to
                    scanContext.getNodePropertyMacCommunicationSupport,
                "read_without_encryption" to scanContext.readBlocksWithoutEncryptionSupport,
                "get_area_information" to scanContext.getAreaInformationSupport,
                "get_container_property" to scanContext.getContainerPropertySupport,
                "set_parameter" to scanContext.setParameterSupport,
                "authentication1_des" to scanContext.authentication1DesSupport,
                "authentication1_aes" to scanContext.authentication1AesSupport,
                "get_container_issue_information" to
                    scanContext.getContainerIssueInformationSupport,
                "get_container_id" to scanContext.getContainerIdSupport,
                "echo" to scanContext.echoSupport,
            )

        commandMappings.forEach { (commandName, supportStatus) ->
            if (supportStatus == CommandSupport.SUPPORTED) {
                supportedCommandsArray.put(commandName)
            }
        }

        json.put("supported_commands", supportedCommandsArray)

        // Encryption identifier - check if any system has encryption info
        val encryptionIdentifier =
            scanContext.systemScanContexts
                .firstOrNull { it.encryptionIdentifier != null }
                ?.encryptionIdentifier
        encryptionIdentifier?.let { encId ->
            val encryptionJson = JSONObject()
            encryptionJson.put("aes_key_type", encId.aesKeyType.name)
            encryptionJson.put("des_key_type", encId.desKeyType.name)
            json.put("encryption_identifier", encryptionJson)
        }

        // Systems array - organize nodes per system
        val systemsJson = JSONObject()

        scanContext.systemScanContexts.forEach { systemContext ->
            val systemCodeHex = systemContext.systemCode?.toHexString() ?: "UNKNOWN"
            val systemJson = JSONObject()

            // System IDM
            systemContext.idm?.let { systemJson.put("idm", it.toHexString()) }

            // System name
            val systemName = NodeNaming.getSystemName(systemCodeHex)
            systemName?.let { systemJson.put("name", it) }

            // System status
            systemContext.systemStatus?.let { systemJson.put("status", it.toHexString()) }

            // System DES and AES key versions
            val systemNode = System
            systemContext.nodeDesKeyVersions[systemNode]?.let { keyVersion ->
                systemJson.put("des_key_version", "%04X".format(keyVersion.toInt()))
            }
            systemContext.nodeAesKeyVersions[systemNode]?.let { keyVersion ->
                systemJson.put("aes_key_version", "%04X".format(keyVersion.toInt()))
            }
            // Fallback to generic (DES) key version if no specific DES/AES versions are available
            if (
                systemContext.nodeDesKeyVersions[systemNode] == null &&
                    systemContext.nodeAesKeyVersions[systemNode] == null
            ) {
                systemContext.nodeKeyVersions[systemNode]?.let { keyVersion ->
                    systemJson.put("des_key_version", "%04X".format(keyVersion.toInt()))
                }
            }

            // Collect and sort all nodes within this system by node number, excluding system node
            val systemNodes =
                systemContext.nodes
                    .filterNot { it is System } // Filter out system node
                    .sortedBy { it.number }
            val nodesArray = JSONArray()

            systemNodes.forEach { node ->
                val nodeJson = buildNodeJson(node, systemContext, systemCodeHex)
                nodesArray.put(nodeJson)
            }

            systemJson.put("nodes", nodesArray)
            systemsJson.put(systemCodeHex, systemJson)
        }

        json.put("systems", systemsJson)

        return json
    }

    /** Builds JSON representation for a single node */
    private fun buildNodeJson(
        node: Node,
        systemContext: SystemScanContext,
        systemCodeHex: String,
    ): JSONObject {
        val nodeJson = JSONObject()

        when (node) {
            is Area -> {
                nodeJson.put("type", "area")
                nodeJson.put("code", node.fullCode.toHexString())
                nodeJson.put("number", node.number)
                nodeJson.put("end_number", node.endNumber)

                // Area name
                val areaName = NodeNaming.getAreaName(systemCodeHex, node.fullCode.toHexString())
                areaName?.let { nodeJson.put("name", it) }

                // Area attribute
                val attributeJson = JSONObject()
                attributeJson.put(
                    "can_create_sub_area",
                    node.attribute == AreaAttribute.CAN_CREATE_SUB_AREA,
                )
                attributeJson.put("pin_required", false)
                nodeJson.put("attribute", attributeJson)
            }

            is Service -> {
                nodeJson.put("type", "service")
                nodeJson.put("code", node.code.toHexString())
                nodeJson.put("number", node.number)

                // Service name
                val serviceName = NodeNaming.getServiceName(node, systemContext)
                serviceName?.let { nodeJson.put("name", it) }

                // Service attribute
                val attributeJson = JSONObject()
                attributeJson.put("type", node.attribute.type.name)
                attributeJson.put("mode", node.attribute.mode.name)
                attributeJson.put("authentication_required", node.attribute.authenticationRequired)
                attributeJson.put("pin_required", node.attribute.pinRequired)
                nodeJson.put("attribute", attributeJson)

                // Properties object
                val propertiesJson = JSONObject()
                var hasProperties = false

                systemContext.nodeMacCommunicationProperties[node]?.let { macProperty ->
                    propertiesJson.put("mac", macProperty.enabled)
                    hasProperties = true
                }

                systemContext.nodeValueLimitedPurseProperties[node]?.let { vlpsProperty ->
                    val vlpsJson = JSONObject()
                    vlpsJson.put("enabled", vlpsProperty.enabled)
                    if (vlpsProperty.enabled) {
                        vlpsJson.put("upper_limit", vlpsProperty.upperLimit)
                        vlpsJson.put("lower_limit", vlpsProperty.lowerLimit)
                        vlpsJson.put("generation_number", vlpsProperty.generationNumber)
                    }
                    propertiesJson.put("value_limited_purse_service", vlpsJson)
                    hasProperties = true
                }

                if (hasProperties) {
                    nodeJson.put("properties", propertiesJson)
                }

                // Data field - present as dict of 16-byte blocks by block number
                systemContext.serviceBlockData[node]?.let { blockData ->
                    val dataJson = JSONObject()

                    // Split the data into 16-byte blocks
                    for (i in blockData.indices step BlockListElement.BLOCK_SIZE) {
                        val endIndex = minOf(i + BlockListElement.BLOCK_SIZE, blockData.size)
                        val block = blockData.sliceArray(i until endIndex)
                        val blockNumber = i / BlockListElement.BLOCK_SIZE
                        dataJson.put(blockNumber.toString(), block.toHexString())
                    }

                    nodeJson.put("data", dataJson)
                } ?: nodeJson.put("data", null)
            }
        }

        // Common fields for all node types

        // DES and AES key versions
        systemContext.nodeDesKeyVersions[node]?.let { keyVersion ->
            nodeJson.put("des_key_version", "%04X".format(keyVersion.toInt()))
        }
        systemContext.nodeAesKeyVersions[node]?.let { keyVersion ->
            nodeJson.put("aes_key_version", "%04X".format(keyVersion.toInt()))
        }
        // Fallback to generic (DES) key version if no specific DES/AES versions are available
        if (
            systemContext.nodeDesKeyVersions[node] == null &&
                systemContext.nodeAesKeyVersions[node] == null
        ) {
            systemContext.nodeKeyVersions[node]?.let { keyVersion ->
                nodeJson.put("des_key_version", "%04X".format(keyVersion.toInt()))
            }
        }

        // Block counts
        (systemContext.nodeAssignedBlockCounts[node] ?: systemContext.nodeBlockCounts[node])?.let {
            blockInfo ->
            nodeJson.put("blocks_allocated", blockInfo.toInt())
        }
        systemContext.nodeFreeBlockCounts[node]?.let { freeBlockInfo ->
            nodeJson.put("blocks_free", freeBlockInfo.toInt())
        }

        return nodeJson
    }
}
