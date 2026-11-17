package com.kormax.felicatool.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.security.keystore.KeyProperties
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.kormax.felicatool.BuildConfig
import com.kormax.felicatool.R
import com.kormax.felicatool.device.BackendMeasurement
import com.kormax.felicatool.device.ImportMeasurement
import com.kormax.felicatool.device.KeystoreBackend
import com.kormax.felicatool.device.KeystoreProfile
import com.kormax.felicatool.device.OsaifuKeitaiInfo
import com.kormax.felicatool.device.Support
import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.CardScanContext
import com.kormax.felicatool.service.CommandCapability
import com.kormax.felicatool.service.CommandSupport
import com.kormax.felicatool.service.SystemScanContext
import com.kormax.felicatool.service.logging.CommunicationLogEntry
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.datetime.LocalDate
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
        // Mask IDM (data bytes 0-7, hex chars 0-15) for messages with IDM
        if (message is FelicaMessageWithIdm && result.length >= 16) {
            result = maskIdm(result.substring(0, 16)) + result.substring(16)
        }
        // Mask block data in read responses
        if (
            message is ReadWithoutEncryptionResponse ||
                message is InternalAuthenticateAndReadResponse
        ) {
            result = maskResponseBlockHex(result, statusFlagsOffset = 8)
        }
        if (message is ReadWithEncryptionDes.Response.Plain) {
            result = maskResponseBlockHex(result, statusFlagsOffset = 0)
        }
        if (message is Authentication2Des.Response.Plain) {
            result =
                maskIdi(message.issueId.toHexString().uppercase()) +
                    maskPmi(message.issueParameter.toHexString().uppercase())
        }
        // Mask product information data (data bytes 11+, keep first 8 bytes of product data)
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
        // Data byte 10 (hex chars 20-21) is the data length, product data starts at data byte 11
        // (hex char 22)
        val productDataStart = 22
        if (hex.length <= productDataStart) return hex
        val productDataHex = hex.substring(productDataStart)
        val masked = maskProductInfo(productDataHex)
        return hex.substring(0, productDataStart) + masked
    }

    /**
     * Masks block data in a response hex string (for ReadWithoutEncryption,
     * InternalAuthenticateAndRead and ReadWithEncryptionDes)
     */
    private fun maskResponseBlockHex(hex: String, statusFlagsOffset: Int): String {
        val statusStart = statusFlagsOffset * 2
        if (hex.length < statusStart + 6) return hex
        val statusFlag1 = hex.substring(statusStart, statusStart + 2)
        if (statusFlag1 != "00") return hex
        val numberOfBlocks = hex.substring(statusStart + 4, statusStart + 6).toInt(16)
        val blockDataStart = statusStart + 6
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

            shareJson(context, fileName, jsonContent)
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
            obj.put(
                "type",
                if (message is FelicaCommand<*> || message is SecureCommand<*>) "COMMAND"
                else "RESPONSE",
            )
            obj.put("layer", entry.layer.name)
            obj.put("name", message.messageName)
            obj.put("timestamp_ns", entry.timestamp - baseTimestamp)
            obj.put("code", entry.codeBytes.joinToString(separator = "") { "%02X".format(it) })
            val hex = entry.data.joinToString(separator = "") { "%02X".format(it) }
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

            shareJson(context, fileName, jsonContent)
        } catch (e: Exception) {
            // Handle error - could show a toast or error dialog
            android.util.Log.e("ExportUtils", "Failed to export flat list", e)
        }
    }

    internal fun shareDeviceInfo(
        context: Context,
        osaifuKeitai: OsaifuKeitaiInfo,
        profile: KeystoreProfile,
    ) {
        val report =
            JSONObject()
                .put(
                    "app",
                    JSONObject()
                        .put("name", context.getString(R.string.app_name))
                        .put("package_name", context.packageName)
                        .put("version_name", BuildConfig.VERSION_NAME)
                        .put("version_code", BuildConfig.VERSION_CODE),
                )
                .put(
                    "device",
                    JSONObject()
                        .put("manufacturer", Build.MANUFACTURER)
                        .put("brand", Build.BRAND)
                        .put("model", Build.MODEL)
                        .put("device", Build.DEVICE)
                        .put("product", Build.PRODUCT)
                        .put("sku", Build.SKU)
                        .put("odm_sku", Build.ODM_SKU)
                        .put("soc_manufacturer", Build.SOC_MANUFACTURER)
                        .put("soc_model", Build.SOC_MODEL)
                        .put("android_version", Build.VERSION.RELEASE)
                        .put("api_level", Build.VERSION.SDK_INT)
                        .put(
                            "api_level_minor",
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA)
                                Build.getMinorSdkVersion(Build.VERSION.SDK_INT_FULL)
                            else JSONObject.NULL,
                        )
                        .put("security_patch", Build.VERSION.SECURITY_PATCH),
                )
                .put(
                    "osaifu_keitai",
                    JSONObject()
                        .put("status", osaifuKeitai.status.name.lowercase())
                        .put(
                            "configuration_files",
                            JSONArray(
                                osaifuKeitai.configurationFiles.map { (path, content) ->
                                    JSONObject()
                                        .put("path", path)
                                        .put(
                                            "content",
                                            content?.let(::fileContent) ?: JSONObject.NULL,
                                        )
                                },
                            ),
                        ),
                )
                .put(
                    "keystore",
                    JSONObject()
                        .put(
                            "levels",
                            JSONObject().apply {
                                for (level in profile.backends) {
                                    put(level.backend.name.lowercase(), level.toReportJson())
                                }
                            },
                        ),
                )
        try {
            shareJson(context, "felica_tool_info.json", report.toString(2).replace("\\/", "/"))
        } catch (e: Exception) {
            Log.e("ExportUtils", "Failed to share device info", e)
            Toast.makeText(context, "Couldn’t share device info", Toast.LENGTH_SHORT).show()
        }
    }

    private val Support.jsonValue: Any
        get() =
            when (this) {
                Support.YES -> true
                Support.NO -> false
                Support.INCONCLUSIVE -> JSONObject.NULL
            }

    private fun BackendMeasurement.toReportJson(): JSONObject =
        JSONObject()
            .apply {
                if (
                    backend == KeystoreBackend.STRONGBOX &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN
                ) {
                    put("manufacturer", Build.STRONGBOX_MANUFACTURER)
                    put("model", Build.STRONGBOX_MODEL)
                }
            }
            .put("implementation", implementation ?: JSONObject.NULL)
            .put("attested_version", attestedVersionLabel ?: JSONObject.NULL)
            .put("attestation", attestation.jsonValue)
            .put("attestation_device_ids", JSONArray(attestedIds.map { it.name.lowercase() }))
            .put("concurrent_occupancy", concurrentOccupancy ?: JSONObject.NULL)
            .put(
                "algorithms",
                JSONObject().apply {
                    for ((algorithm, result) in algorithms) {
                        put(
                            algorithm.label,
                            JSONObject()
                                .put("generation", result.generation.jsonValue)
                                .putImport("raw_import", result.rawImport)
                                .putImport("wrapped_import", result.wrappedImport)
                                .put(
                                    "timings_ns",
                                    JSONObject()
                                        .put("generation", JSONArray(result.generationNanos))
                                        .put("encryption", JSONArray(result.encryptionNanos))
                                        .put("decryption", JSONArray(result.decryptionNanos)),
                                ),
                        )
                    }
                },
            )

    private fun JSONObject.putImport(name: String, result: ImportMeasurement): JSONObject {
        put(name, result.support.jsonValue)
        val info = JSONObject()
        result.reportedSecurityLevel?.let {
            info.put(
                "security_level",
                when (it) {
                    KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> "tee"
                    KeyProperties.SECURITY_LEVEL_STRONGBOX -> "strongbox"
                    KeyProperties.SECURITY_LEVEL_SOFTWARE -> "software"
                    KeyProperties.SECURITY_LEVEL_UNKNOWN_SECURE -> "unknown_secure"
                    else -> "unknown"
                },
            )
        }
        result.reportedOrigin?.let {
            info.put(
                "origin",
                when (it) {
                    KeyProperties.ORIGIN_GENERATED -> "generated"
                    KeyProperties.ORIGIN_IMPORTED -> "imported"
                    KeyProperties.ORIGIN_SECURELY_IMPORTED -> "securely_imported"
                    else -> "unknown"
                },
            )
        }
        result.reportedKeySize?.let { info.put("key_size_bits", it) }
        result.keyInfoError?.let { info.put("error", it) }
        if (info.length() > 0) put("${name}_key_info", info)
        return this
    }

    private fun fileContent(bytes: ByteArray): JSONObject {
        val text = runCatching {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        }
            .getOrNull()
        return if (text != null && text.none { it.isISOControl() && it !in "\t\n\r" })
            JSONObject().put("encoding", "utf8").put("data", text)
        else
            JSONObject()
                .put("encoding", "base64")
                .put("data", Base64.getEncoder().encodeToString(bytes))
    }

    private fun shareJson(context: Context, fileName: String, json: String) =
        shareFile(context, fileName, json, "application/json")

    fun shareFile(context: Context, fileName: String, content: String, mimeType: String) {
        val root = File(context.cacheDir, "exports")
        check(root.mkdirs() || root.isDirectory)
        val cutoff = java.lang.System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        root
            .listFiles()
            ?.filter { it.isDirectory && it.lastModified() < cutoff }
            ?.forEach { it.deleteRecursively() }
        val directory = File(root, UUID.randomUUID().toString())
        check(directory.mkdirs())
        try {
            val file = File(directory, fileName)
            file.writeText(content, Charsets.UTF_8)
            val uri =
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            shareFile(context, uri, fileName, mimeType)
        } catch (e: Exception) {
            directory.deleteRecursively()
            throw e
        }
    }

    /** Helper function to share JSON file via Intent */
    private fun shareFile(context: Context, fileUri: Uri, fileName: String, mimeType: String) {
        val shareIntent =
            Intent().apply {
                action = Intent.ACTION_SEND
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, fileUri)
                clipData = ClipData.newUri(context.contentResolver, fileName, fileUri)
                putExtra(Intent.EXTRA_SUBJECT, "FeliCa Tool File")
                putExtra(Intent.EXTRA_TITLE, fileName) // Add explicit title
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        val chooserIntent = Intent.createChooser(shareIntent, "Share $fileName")
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooserIntent)
    }

    /** Generates JSON representation of the CardScanContext as a flat list organized per system */
    internal fun generateFlatListJson(
        scanContext: CardScanContext,
        privacy: Boolean = false,
        currentTimeMillis: Long = java.lang.System.currentTimeMillis(),
        buildDateEpochMillis: Long = BuildConfig.BUILD_DATE_EPOCH_MILLIS,
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
            metadataJson.put("creation_time", currentTimeMillis / 1000L)
            metadataJson.put("app_version", BuildConfig.VERSION_NAME)
            metadataJson.put("android_sdk_version", Build.VERSION.SDK_INT)
            metadataJson.put("android_version", Build.VERSION.RELEASE)
            metadataJson.put("device_manufacturer", Build.MANUFACTURER)
            metadataJson.put("device_model", Build.MODEL)
        }
        scanContext.scanDurationMillis?.let { metadataJson.put("scan_duration_ms", it) }
        json.put("metadata", metadataJson)

        // Primary attributes from CardScanContext
        scanContext.primaryIdm?.let {
            val hex = it.toHexString().lowercase()
            json.put("primary_idm", if (privacy) maskIdm(hex) else hex)
        }

        val inferredJson = JSONObject()
        CardTypeInferrer.infer(scanContext)?.let { inference ->
            inference.cardId?.let { productType ->
                inferredJson.put("product_type", productType)
            }
            inferredJson.put("medium_type", inference.media.name)
        }
        val manufacturingDate =
            ManufacturingDateResolver.resolve(
                context = scanContext,
                currentTimeMillis = currentTimeMillis,
                buildDateEpochMillis = buildDateEpochMillis,
            )
        manufacturingDate?.let { inferredJson.put("chip_manufacturing_date", it.toString()) }

        scanContext.pmm?.let { pmm -> json.put("pmm", pmm.toHexString().lowercase()) }

        // IC type from PMM data
        scanContext.pmm?.let { pmm ->
            IcTypeRegistry.resolveIcType(pmm.icType, pmm.romType)?.let { resolution ->
                inferredJson.put(
                    "ic_type_name",
                    if (resolution.isUncertain) "${resolution.name}?" else resolution.name,
                )
            }
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
            MobileDeviceRegistry.resolve(container)?.let { device ->
                inferredJson.put("container_device_model_name", device.name)
                device.model?.let { model ->
                    inferredJson.put("container_device_model_number", model)
                }
                device.carrier?.let { carrier ->
                    inferredJson.put("container_device_carrier_name", carrier)
                }
            }
            json.put("container_issue_information", containerJson)
        }
        json.put("inferred", inferredJson)

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
            val errorIndication = scanContext.commands.readWithoutEncryption.errorLocationIndication
            if (errorIndication != ErrorLocationIndication.UNKNOWN) {
                put("error_location_indication", errorIndication.name)
            }
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
            putBooleanIfKnown(
                "service_code_addressing_supported",
                scanContext.commands.readWithoutEncryption.serviceCodeAddressingSupported,
            )
            putBooleanIfKnown(
                "extended_block_list_element_supported",
                scanContext.commands.readWithoutEncryption.extendedBlockListElementSupported,
            )
            putBooleanIfKnown(
                "unused_invalid_service_supported",
                scanContext.commands.readWithoutEncryption.unusedInvalidServiceSupported,
            )
        }
        commandsJson.putAvailableCommand(
            "read_with_encryption_des",
            scanContext.commands.readWithEncryptionDes,
        ) {
            val errorIndication = scanContext.commands.readWithEncryptionDes.errorLocationIndication
            if (errorIndication != ErrorLocationIndication.UNKNOWN) {
                put("error_location_indication", errorIndication.name)
            }
            scanContext.commands.readWithEncryptionDes.maxBlocksPerRequest?.let {
                put("max_blocks_per_request", it)
            }
            putBooleanIfKnown(
                "extended_block_list_element_supported",
                scanContext.commands.readWithEncryptionDes.extendedBlockListElementSupported,
            )
        }
        commandsJson.putAvailableCommand(
            "write_without_encryption",
            scanContext.commands.writeWithoutEncryption,
        ) {
            val errorIndication =
                scanContext.commands.writeWithoutEncryption.errorLocationIndication
            if (errorIndication != ErrorLocationIndication.UNKNOWN) {
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
                "area_list_without_node_immediate_parent_area_supported",
                scanContext.commands.authentication1Des
                    .areaListWithoutNodeImmediateParentAreaSupported,
            )
            putBooleanIfKnown(
                "area_list_without_root_area_supported",
                scanContext.commands.authentication1Des.areaListWithoutRootAreaSupported,
            )
            putBooleanIfKnown(
                "trailing_authentication_required_service_in_area_list_supported",
                scanContext.commands.authentication1Des
                    .trailingAuthenticationRequiredServiceInAreaListSupported,
            )
            putBooleanIfKnown(
                "leading_system_node_in_area_list_supported",
                scanContext.commands.authentication1Des.leadingSystemNodeInAreaListSupported,
            )
            putBooleanIfKnown(
                "leading_authentication_required_service_in_area_list_supported",
                scanContext.commands.authentication1Des
                    .leadingAuthenticationRequiredServiceInAreaListSupported,
            )
            putBooleanIfKnown(
                "leading_authentication_not_required_service_in_node_list_supported",
                scanContext.commands.authentication1Des
                    .leadingAuthenticationNotRequiredServiceInNodeListSupported,
            )
        }
        commandsJson.putAvailableCommand(
            "authentication1_aes",
            scanContext.commands.authentication1Aes,
        )
        json.put("commands", commandsJson)

        // Organize nodes per system code; an empty key represents an unknown code.
        val systemsJson = JSONObject()

        scanContext.systemScanContexts.forEach { systemContext ->
            val systemCodeHex = systemContext.systemCode?.toHexString()
            val systemJson = JSONObject()

            // System IDM
            systemContext.idm?.let {
                val hex = it.toHexString()
                systemJson.put("idm", if (privacy) maskIdm(hex) else hex)
            }
            systemContext.idi?.let {
                systemJson.put("idi", if (privacy) maskIdi(it.toHexString()) else it.toHexString())
            }
            systemContext.pmi?.let {
                systemJson.put("pmi", if (privacy) maskPmi(it.toHexString()) else it.toHexString())
            }

            if (!privacy) {
                val interpretation =
                    SystemIssueInformationInterpreter.interpret(
                        systemCode = systemCodeHex.orEmpty(),
                        idi = systemContext.idi,
                        pmi = systemContext.pmi,
                        manufacturingDate = manufacturingDate,
                        currentDate = currentTimeMillis.toUtcLocalDate(),
                    )
                val systemInterpretedJson = JSONObject()
                interpretation.issuanceDate?.let { date ->
                    systemInterpretedJson.put("issuance_date", date.toString())
                }
                interpretation.serialNumber?.let { serial ->
                    systemInterpretedJson.put(
                        "serial_number",
                        SystemIssueInformationInterpreter.formatSerialNumberForDisplay(
                            systemCodeHex.orEmpty(),
                            systemContext.idi,
                            serial,
                        ),
                    )
                }
                if (systemInterpretedJson.length() > 0) {
                    systemJson.put("interpreted", systemInterpretedJson)
                }
            }

            val inferredSystemJson = JSONObject()

            // System name inferred from the node registry
            val systemName = systemCodeHex?.let {
                NodeRegistry.getNodeName(it, it, NodeDefinitionType.SYSTEM)
            }
            systemName?.let { inferredSystemJson.put("name", it) }
            systemJson.put("inferred", inferredSystemJson)

            systemContext.systemStatus?.let {
                val bytes = it.toByteArray()
                val systemStatusJson = JSONObject()
                systemStatusJson.put(
                    "format_version",
                    "%02X".format(it.formatVersion.toUByte().toInt()),
                )
                systemStatusJson.put("data", bytes.copyOfRange(1, bytes.size).toHexString())
                systemJson.put("system_status", systemStatusJson)

                if (it is SystemStatus.V0) {
                    val inferredSystemStatusJson = JSONObject()
                    inferredSystemStatusJson.put(
                        "des_system_initialization_commands_disabled",
                        it.desSystemInitializationCommandsDisabled,
                    )
                    inferredSystemStatusJson.put(
                        "des_node_issuance_commands_disabled",
                        it.desNodeIssuanceCommandsDisabled,
                    )
                    inferredSystemStatusJson.put(
                        "des_authentication_strict_area_list_validation",
                        it.desAuthenticationStrictAreaListValidation,
                    )
                    inferredSystemStatusJson.put("unknown_flag", it.unknownFlag)
                    inferredSystemJson.put("system_status", inferredSystemStatusJson)
                }
            }

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
            systemsJson.put(systemCodeHex.orEmpty(), systemJson)
        }

        json.put("systems", systemsJson)

        return json
    }

    private fun Long.toUtcLocalDate(): LocalDate {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.timeInMillis = this
        return LocalDate(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH),
        )
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
        systemCodeHex: String?,
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
                val areaName = systemCodeHex?.let {
                    NodeRegistry.getNodeName(
                        it,
                        node.fullCode.toHexString().uppercase(),
                        parentCode,
                        NodeDefinitionType.AREA,
                    )
                }
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
                nodeJson.put(
                    "code",
                    if (node is AnonymousService) JSONObject.NULL else node.code.toHexString(),
                )
                if (node !is AnonymousService) nodeJson.put("number", node.number)

                // Service name
                val parentCode = parentArea?.fullCode?.toHexString()?.uppercase()
                val serviceName =
                    systemCodeHex
                        ?.takeUnless { node is AnonymousService }
                        ?.let {
                            NodeRegistry.getNodeName(
                                it,
                                node.fullCode.toHexString().uppercase(),
                                parentCode,
                                NodeDefinitionType.SERVICE,
                                blockData = systemContext.serviceBlockData[node],
                            )
                        }
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
