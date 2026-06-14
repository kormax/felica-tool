package com.kormax.felicatool.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.kormax.felicatool.BuildConfig
import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.CardScanContext
import com.kormax.felicatool.service.CommandCapability
import com.kormax.felicatool.service.CommandSupport
import com.kormax.felicatool.service.SystemScanContext
import com.kormax.felicatool.service.logging.CommunicationLogEntry
import java.text.SimpleDateFormat
import java.util.*
import org.json.JSONArray
import org.json.JSONObject

object ExportUtils {
    private fun CommandSupport.toBooleanOrNull(): Boolean? =
        when (this) {
            CommandSupport.UNKNOWN -> null
            CommandSupport.SUPPORTED -> true
            CommandSupport.UNSUPPORTED -> false
        }

    private fun JSONObject.putBooleanIfKnown(
        name: String,
        support: CommandSupport,
    ) {
        support.toBooleanOrNull()?.let { put(name, it) }
    }

    private fun JSONObject.putAvailableCommand(
        name: String,
        capability: CommandCapability,
        addDetails: JSONObject.() -> Unit = {},
    ) {
        if (!capability.available) {
            return
        }

        val commandJson = JSONObject()
        commandJson.putBooleanIfKnown("trailing_data_supported", capability.trailingDataSupported)
        commandJson.addDetails()
        put(name, commandJson)
    }

    /** Masks an IDM hex string, keeping first 2 bytes and last 1 byte visible */
    private fun maskIdm(hex: String): String {
        if (hex.length < 6) return hex
        val prefix = hex.substring(0, 4) // first 2 bytes
        val suffix = hex.substring(hex.length - 2) // last 1 byte
        val middleLength = hex.length - 6
        val masked = "XX".repeat(middleLength / 2)
        return prefix + masked + suffix
    }

    /**
     * Masks block data hex string by half-blocks (8 bytes = 16 hex chars each). A half-block is
     * kept if all its bytes are the same value, otherwise masked with XX.
     */
    private fun maskBlockData(hex: String): String {
        val halfBlockSize = 16 // 8 bytes = 16 hex chars
        return hex.chunked(halfBlockSize).joinToString("") { half ->
            if (half.length < 2) {
                "XX".repeat(half.length / 2)
            } else {
                val firstByte = half.substring(0, 2)
                val allSame = half.chunked(2).all { it.equals(firstByte, ignoreCase = true) }
                if (allSame) half else "XX".repeat(half.length / 2)
            }
        }
    }

    /**
     * Applies privacy masking to a FeliCa communication log entry hex string based on message type
     */
    private fun maskCommunicationLogHex(hex: String, message: Any): String {
        var result = hex
        // Mask IDM (bytes 2-9, hex chars 4-19) for messages with IDM
        if (message is FelicaMessageWithIdm && result.length >= 20) {
            val idmHex = result.substring(4, 20) // 8 bytes = 16 hex chars
            result = result.substring(0, 4) + maskIdm(idmHex) + result.substring(20)
        }
        // Mask block data in read responses
        if (
            message is ReadWithoutEncryptionResponse ||
                message is InternalAuthenticateAndReadResponse
        ) {
            result = maskResponseBlockHex(result)
        }
        // Mask product information data (bytes 13+, keep first 8 bytes of product data)
        if (message is RequestProductInformationResponse) {
            result = maskProductInfoHex(result)
        }
        return result
    }

    /**
     * Masks product info in a RequestProductInformation response hex string. Keeps header (up to
     * byte 13) and first 8 bytes of product data, masks the rest.
     */
    private fun maskProductInfoHex(hex: String): String {
        // Byte 12 (hex chars 24-25) is the data length, product data starts at byte 13 (hex char
        // 26)
        val productDataStart = 26
        if (hex.length <= productDataStart) return hex
        val productDataHex = hex.substring(productDataStart)
        val masked = maskProductInfo(productDataHex)
        return hex.substring(0, productDataStart) + masked
    }

    /**
     * Masks block data in a response hex string (for ReadWithoutEncryption and
     * InternalAuthenticateAndRead)
     */
    private fun maskResponseBlockHex(hex: String): String {
        // Status flags at bytes 10-11 (hex chars 20-23), block count at byte 12 (hex chars 24-25)
        if (hex.length < 26) return hex
        val statusFlag1 = hex.substring(20, 22)
        if (statusFlag1 != "00") return hex
        val numberOfBlocks = hex.substring(24, 26).toInt(16)
        val blockDataStart = 26 // byte 13 = hex char 26
        val sb = StringBuilder(hex.substring(0, blockDataStart))
        for (block in 0 until numberOfBlocks) {
            val offset = blockDataStart + block * 32 // 16 bytes = 32 hex chars
            if (offset + 32 > hex.length) break
            val blockHex = hex.substring(offset, offset + 32)
            sb.append(maskBlockData(blockHex))
        }
        // Append any remaining data after block data (e.g. challenge + MAC)
        val afterBlocks = blockDataStart + numberOfBlocks * 32
        if (afterBlocks < hex.length) {
            sb.append(hex.substring(afterBlocks))
        }
        return sb.toString()
    }

    /** Masks product info hex string, keeping first 8 bytes visible */
    private fun maskProductInfo(hex: String): String {
        if (hex.length <= 16) return hex
        val prefix = hex.substring(0, 16) // first 8 bytes
        val remainingBytes = (hex.length - 16) / 2
        return prefix + "XX".repeat(remainingBytes)
    }

    /** Exports the communication log as a JSON file and shares it */
    fun exportCommunicationLog(
        context: Context,
        log: List<CommunicationLogEntry>,
        privacy: Boolean = false,
    ) {
        try {
            val jsonArray = generateCommunicationLogJson(log, privacy)
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
    fun generateCommunicationLogJson(
        log: List<CommunicationLogEntry>,
        privacy: Boolean = false,
    ): JSONArray {
        val jsonArray = JSONArray()

        // Get the timestamp of the first entry to use as baseline
        val baseTimestamp = log.firstOrNull()?.timestamp ?: 0L

        log.forEach { entry ->
            val obj = JSONObject()
            val message = entry.message
            obj.put("type", if (message is FelicaCommand<*>) "COMMAND" else "RESPONSE")
            obj.put("name", message::class.simpleName)
            obj.put("timestamp_ns", entry.timestamp - baseTimestamp)
            val hex = entry.toByteArray().joinToString(separator = "") { "%02X".format(it) }
            obj.put("data", if (privacy) maskCommunicationLogHex(hex, message) else hex)
            jsonArray.put(obj)
        }
        return jsonArray
    }

    /** Exports the scan data as a flat list to a JSON file */
    fun exportFlatList(context: Context, scanContext: CardScanContext, privacy: Boolean = false) {
        try {
            val json = generateFlatListJson(scanContext, privacy)

            // Create filename with primary IDM and timestamp
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val primaryIdmPrefix =
                scanContext.primaryIdm?.toHexString()?.let {
                    "${if (privacy) maskIdm(it) else it}_"
                } ?: ""
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
    private fun generateFlatListJson(
        scanContext: CardScanContext,
        privacy: Boolean = false,
    ): JSONObject {
        val json = JSONObject()

        // Metadata
        val metadataJson = JSONObject()
        if (privacy) {
            metadataJson.put("creation_time", 0)
            metadataJson.put("app_version", BuildConfig.VERSION_NAME)
            metadataJson.put("android_sdk_version", 0)
            metadataJson.put("android_version", "0")
            metadataJson.put("device_manufacturer", "")
            metadataJson.put("device_model", "")
        } else {
            metadataJson.put("creation_time", java.lang.System.currentTimeMillis() / 1000L)
            metadataJson.put("app_version", BuildConfig.VERSION_NAME)
            metadataJson.put("android_sdk_version", Build.VERSION.SDK_INT)
            metadataJson.put("android_version", Build.VERSION.RELEASE)
            metadataJson.put("device_manufacturer", Build.MANUFACTURER)
            metadataJson.put("device_model", Build.MODEL)
        }
        json.put("metadata", metadataJson)

        // Primary attributes from CardScanContext
        scanContext.scanDurationMillis?.let { json.put("scan_duration_ms", it) }

        scanContext.primaryIdm?.let {
            val hex = it.toHexString().lowercase()
            json.put("primary_idm", if (privacy) maskIdm(hex) else hex)
        }

        scanContext.pmm?.let { pmm -> json.put("pmm", pmm.toHexString().lowercase()) }

        // IC type from PMM data
        scanContext.pmm?.let { pmm ->
            val icTypeName =
                IcTypeRegistry.resolveIcType(pmm.icType, pmm.romType)?.let { resolution ->
                    if (resolution.isUncertain) "${resolution.name}?" else resolution.name
                }
            json.put("ic_type_name", icTypeName)
        }

        scanContext.primarySystemCode?.let { json.put("primary_system_code", it.toHexString()) }

        scanContext.containerIdm?.let {
            val hex = it.toHexString()
            json.put("container_idm", if (privacy) maskIdm(hex) else hex)
        }

        // Specification version
        scanContext.specificationVersion?.let { spec ->
            val specJson = JSONObject()
            specJson.put("format_version", "%02X".format(spec.formatVersion.toUByte().toInt()))
            specJson.put("basic_version", spec.basicVersion.toString())
            spec.desOptionVersion?.let { des -> specJson.put("des_option_version", des.toString()) }
            spec.specialOptionVersion?.let { special ->
                specJson.put("special_option_version", special.toString())
            }
            spec.extendedOverlapOptionVersion?.let { extended ->
                specJson.put("extended_overlap_option_version", extended.toString())
            }
            spec.valueLimitedPurseServiceOptionVersion?.let { valueLimited ->
                specJson.put("value_limited_purse_service_option_version", valueLimited.toString())
            }
            spec.communicationWithMacOptionVersion?.let { aes ->
                specJson.put("communication_with_mac_option_version", aes.toString())
            }
            spec.randomIdOptionVersion?.let { randomId ->
                specJson.put("random_id_option_version", randomId.toString())
            }
            json.put("specification_version", specJson)
        }

        scanContext.productInformation?.let { productInformationResponse ->
            val hex = productInformationResponse.productInformationData.toHexString()
            json.put("product_information", if (privacy) maskProductInfo(hex) else hex)
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

        // Container property values
        if (scanContext.containerPropertyValues.isNotEmpty()) {
            val containerPropertyJson = JSONObject()
            scanContext.containerPropertyValues.forEach { (property, data) ->
                containerPropertyJson.put(property.toByteArray().toHexString(), data.toHexString())
            }
            json.put("container_properties", containerPropertyJson)
        }

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

        val commandsJson = JSONObject()
        commandsJson.putAvailableCommand("polling", scanContext.commands.polling) {
            putBooleanIfKnown(
                "system_code_request_supported",
                scanContext.commands.polling.systemCodeSupported,
            )
            putBooleanIfKnown(
                "communication_performance_request_supported",
                scanContext.commands.polling.communicationPerformanceSupported,
            )
        }
        commandsJson.putAvailableCommand("request_response", scanContext.commands.requestResponse)
        commandsJson.putAvailableCommand(
            "request_system_code",
            scanContext.commands.requestSystemCode,
        )
        commandsJson.putAvailableCommand(
            "request_specification_version",
            scanContext.commands.requestSpecificationVersion,
        )
        commandsJson.putAvailableCommand(
            "request_product_information",
            scanContext.commands.requestProductInformation,
        )
        commandsJson.putAvailableCommand("get_system_status", scanContext.commands.getSystemStatus)
        commandsJson.putAvailableCommand(
            "search_service_code",
            scanContext.commands.searchServiceCode,
        )
        commandsJson.putAvailableCommand("request_code_list", scanContext.commands.requestCodeList)
        commandsJson.putAvailableCommand("request_service", scanContext.commands.requestService) {
            putBooleanIfKnown(
                "unknown_node_attributes_supported",
                scanContext.commands.requestService.unknownNodeAttributesSupported,
            )
        }
        commandsJson.putAvailableCommand(
            "request_service_v2",
            scanContext.commands.requestServiceV2,
        )
        commandsJson.putAvailableCommand(
            "request_block_information",
            scanContext.commands.requestBlockInformation,
        )
        commandsJson.putAvailableCommand(
            "request_block_information_ex",
            scanContext.commands.requestBlockInformationEx,
        )
        commandsJson.putAvailableCommand(
            "get_node_property",
            scanContext.commands.getNodeProperty,
        ) {
            putBooleanIfKnown(
                "mac_communication_supported",
                scanContext.commands.getNodeProperty.macCommunicationSupported,
            )
            putBooleanIfKnown(
                "value_limited_service_supported",
                scanContext.commands.getNodeProperty.valueLimitedServiceSupported,
            )
        }
        commandsJson.putAvailableCommand(
            "read_without_encryption",
            scanContext.commands.readWithoutEncryption,
        ) {
            put(
                "error_location_indication",
                scanContext.commands.readWithoutEncryption.errorLocationIndication.name,
            )
            scanContext.commands.readWithoutEncryption.illegalNumberErrorPreference?.let {
                preference ->
                put("illegal_number_error_preference", preference.name)
            }
            scanContext.commands.readWithoutEncryption.maxServicesPerRequest?.let {
                put("max_services_per_request", it)
            }
            scanContext.commands.readWithoutEncryption.maxBlocksPerRequest?.let {
                put("max_blocks_per_request", it)
            }
        }
        commandsJson.putAvailableCommand(
            "write_without_encryption",
            scanContext.commands.writeWithoutEncryption,
        ) {
            scanContext.commands.writeWithoutEncryption.errorLocationIndication?.let {
                errorIndication ->
                put("error_location_indication", errorIndication.name)
            }
            scanContext.commands.writeWithoutEncryption.maxBlocksPerRequest?.let {
                put("max_blocks_per_request", it)
            }
        }
        commandsJson.putAvailableCommand(
            "get_area_information",
            scanContext.commands.getAreaInformation,
        )
        commandsJson.putAvailableCommand(
            "get_container_property",
            scanContext.commands.getContainerProperty,
        )
        commandsJson.putAvailableCommand("set_parameter", scanContext.commands.setParameter)
        commandsJson.putAvailableCommand(
            "get_container_issue_information",
            scanContext.commands.getContainerIssueInformation,
        )
        commandsJson.putAvailableCommand("get_container_id", scanContext.commands.getContainerId)
        commandsJson.putAvailableCommand("echo", scanContext.commands.echo) {
            scanContext.commands.echo.maxPayloadSize?.let { put("max_payload_size", it) }
        }
        commandsJson.putAvailableCommand("reset_mode", scanContext.commands.resetMode)
        commandsJson.putAvailableCommand(
            "internal_authenticate_and_read",
            scanContext.commands.internalAuthenticateAndRead,
        )
        commandsJson.putAvailableCommand(
            "authentication1_des",
            scanContext.commands.authentication1Des,
        ) {
            putBooleanIfKnown(
                "incomplete_area_path_for_node_supported",
                scanContext.commands.authentication1Des.incompleteAreaPathForNodeSupported,
            )
            putBooleanIfKnown(
                "service_in_area_path_supported",
                scanContext.commands.authentication1Des.serviceInAreaPathSupported,
            )
        }
        commandsJson.putAvailableCommand(
            "authentication1_aes",
            scanContext.commands.authentication1Aes,
        )
        json.put("commands", commandsJson)

        // Systems array - organize nodes per system
        val systemsJson = JSONObject()

        scanContext.systemScanContexts.forEach { systemContext ->
            val systemCodeHex = systemContext.systemCode?.toHexString() ?: "UNKNOWN"
            val systemJson = JSONObject()

            // System IDM
            systemContext.idm?.let {
                val hex = it.toHexString()
                systemJson.put("idm", if (privacy) maskIdm(hex) else hex)
            }

            // System name
            val systemName =
                NodeRegistry.getNodeName(systemCodeHex, systemCodeHex, NodeDefinitionType.SYSTEM)
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
                val parentArea = findContainingArea(node, systemContext)
                val nodeJson =
                    buildNodeJson(node, systemContext, systemCodeHex, privacy, parentArea)
                nodesArray.put(nodeJson)
            }

            systemJson.put("nodes", nodesArray)
            systemsJson.put(systemCodeHex, systemJson)
        }

        json.put("systems", systemsJson)

        return json
    }

    /** Finds the immediate containing area for a node. */
    private fun findContainingArea(node: Node, context: SystemScanContext): Area? {
        val areas = context.nodes.filterIsInstance<Area>()
        return when (node) {
            is Service ->
                areas
                    .filter { candidate -> node.belongsTo(candidate) }
                    .minByOrNull { it.endNumber - it.number }
            is Area ->
                areas
                    .filter { candidate ->
                        candidate != node &&
                            node.belongsTo(candidate) &&
                            // Strict containment avoids choosing same-range variants/self.
                            (candidate.number < node.number || candidate.endNumber > node.endNumber)
                    }
                    .minByOrNull { it.endNumber - it.number }
            else -> null
        }
    }

    /** Builds JSON representation for a single node */
    private fun buildNodeJson(
        node: Node,
        systemContext: SystemScanContext,
        systemCodeHex: String,
        privacy: Boolean = false,
        parentArea: Area? = null,
    ): JSONObject {
        val nodeJson = JSONObject()

        when (node) {
            is Area -> {
                nodeJson.put("type", "area")
                nodeJson.put("code", node.fullCode.toHexString())
                nodeJson.put("number", node.number)
                nodeJson.put("end_number", node.endNumber)

                // Area name
                val parentCode = parentArea?.fullCode?.toHexString()?.uppercase()
                val areaName =
                    NodeRegistry.getNodeName(
                        systemCodeHex,
                        node.fullCode.toHexString().uppercase(),
                        parentCode,
                        NodeDefinitionType.AREA,
                    )
                areaName?.let { nodeJson.put("name", it) }

                // Area attribute
                val attributeJson = JSONObject()
                attributeJson.put(
                    "can_create_sub_area",
                    node.attribute == AreaAttribute.CanCreateSubArea,
                )
                attributeJson.put("pin_required", false)
                nodeJson.put("attribute", attributeJson)
            }

            is Service -> {
                nodeJson.put("type", "service")
                nodeJson.put("code", node.code.toHexString())
                nodeJson.put("number", node.number)

                // Service name
                val parentCode = parentArea?.fullCode?.toHexString()?.uppercase()
                val serviceName =
                    NodeRegistry.getNodeName(
                        systemCodeHex,
                        node.fullCode.toHexString().uppercase(),
                        parentCode,
                        NodeDefinitionType.SERVICE,
                        blockData = systemContext.serviceBlockData[node],
                    )
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
                systemContext.serviceBlockData[node]?.let { blockDataMap ->
                    val dataJson = JSONObject()

                    // Block data is now stored as Map<Int, ByteArray>
                    for ((blockNumber, blockData) in blockDataMap.entries.sortedBy { it.key }) {
                        // Use 4-character hex format for block numbers
                        val blockKey = blockNumber.toString(16).uppercase().padStart(4, '0')
                        val hex = blockData.toHexString()
                        dataJson.put(blockKey, if (privacy) maskBlockData(hex) else hex)
                    }

                    nodeJson.put("data", dataJson)
                } ?: nodeJson.put("data", null)

                // Hidden flag - node was discovered via force discovery
                if (systemContext.hiddenNodes.contains(node)) {
                    nodeJson.put("hidden", true)
                }
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
        systemContext.nodeBlockCounts[node]?.let { blockInfo ->
            nodeJson.put("blocks_total", blockInfo.toInt())
        }
        systemContext.nodeAssignedBlockCounts[node]?.let { blockInfo ->
            nodeJson.put("blocks_allocated", blockInfo.toInt())
        }
        systemContext.nodeFreeBlockCounts[node]?.let { freeBlockInfo ->
            nodeJson.put("blocks_free", freeBlockInfo.toInt())
        }

        return nodeJson
    }
}
