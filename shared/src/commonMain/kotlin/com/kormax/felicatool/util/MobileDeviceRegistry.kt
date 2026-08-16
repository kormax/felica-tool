package com.kormax.felicatool.util

import com.kormax.felicatool.felica.ContainerInformation
import com.kormax.felicatool.shared.resources.Res
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Resolves mobile FeliCa container issue information to known device metadata. */
object MobileDeviceRegistry {
    private const val FORMAT_VERSION_CARRIER_INFO_SIZE = 5
    private const val MOBILE_PHONE_MODEL_INFO_SIZE = 11

    private val json = Json { ignoreUnknownKeys = true }
    private val initializationMutex = Mutex()
    private var initialized = false
    private var definitions: Map<ContainerIssueInformationKey, MobileDeviceResolution> = emptyMap()

    suspend fun ensureReady() {
        if (initialized) {
            return
        }

        initializationMutex.withLock {
            if (!initialized) {
                ensureInitialized(Res.readBytes("files/mobile_devices.json").decodeToString())
            }
        }
    }

    fun ensureInitialized(jsonText: String) {
        if (initialized) {
            return
        }

        definitions = parseDefinitions(jsonText)
        initialized = true
    }

    fun isReady(): Boolean = initialized

    fun resolve(containerInformation: ContainerInformation): MobileDeviceResolution? =
        resolve(
            formatVersionCarrierInformation = containerInformation.formatVersionCarrierInformation,
            mobilePhoneModelInformation = containerInformation.mobilePhoneModelInformation,
        )

    fun resolve(
        formatVersionCarrierInformation: ByteArray,
        mobilePhoneModelInformation: ByteArray,
    ): MobileDeviceResolution? {
        if (
            formatVersionCarrierInformation.size != FORMAT_VERSION_CARRIER_INFO_SIZE ||
                mobilePhoneModelInformation.size != MOBILE_PHONE_MODEL_INFO_SIZE
        ) {
            return null
        }

        return definitions[
            ContainerIssueInformationKey(
                formatVersionCarrierInfo =
                    formatVersionCarrierInformation.toHexString().uppercase(),
                mobilePhoneModelInfo = mobilePhoneModelInformation.toHexString().uppercase(),
            )]
    }

    private fun parseDefinitions(
        jsonText: String
    ): Map<ContainerIssueInformationKey, MobileDeviceResolution> = buildMap {
        for (definitionElement in json.parseToJsonElement(jsonText).jsonArray) {
            val definition = definitionElement.jsonObject
            val name = definition.optionalRegistryString("name") ?: continue
            val formatInfo =
                definition
                    .optionalRegistryString("format_version_carrier_info")
                    ?.uppercase()
                    ?.takeIf { it.length == FORMAT_VERSION_CARRIER_INFO_SIZE * 2 }
                    ?.takeIf { value -> value.all { it.digitToIntOrNull(16) != null } } ?: continue
            val modelInfo = definition.optionalRegistryString("mobile_phone_model_info") ?: continue
            val modelInfoBytes = modelInfo.encodeToByteArray()
            if (
                modelInfoBytes.isEmpty() ||
                    modelInfoBytes.size > MOBILE_PHONE_MODEL_INFO_SIZE ||
                    modelInfoBytes.any { it.toInt() !in 0x20..0x7E }
            ) {
                continue
            }

            val paddedModelInfo = ByteArray(MOBILE_PHONE_MODEL_INFO_SIZE)
            modelInfoBytes.copyInto(paddedModelInfo)
            put(
                ContainerIssueInformationKey(
                    formatVersionCarrierInfo = formatInfo,
                    mobilePhoneModelInfo = paddedModelInfo.toHexString().uppercase(),
                ),
                MobileDeviceResolution(
                    name = name,
                    model = definition.optionalRegistryString("model"),
                    carrier = definition.optionalRegistryString("carrier"),
                ),
            )
        }
    }

    private fun JsonObject.optionalRegistryString(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf {
            it.isNotBlank() && !it.equals("null", ignoreCase = true)
        }
}

private data class ContainerIssueInformationKey(
    val formatVersionCarrierInfo: String,
    val mobilePhoneModelInfo: String,
)

data class MobileDeviceResolution(
    val name: String,
    val model: String?,
    val carrier: String?,
)
