package com.kormax.felicatool.util

import com.kormax.felicatool.shared.resources.Res
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Loads the shared chips.json dataset once and exposes IC type lookups. */
object IcTypeRegistry {
    private val json = Json { ignoreUnknownKeys = true }
    private val initializationMutex = Mutex()
    private var initialized = false
    private var icDefinitions: Map<String, IcCodeDefinition> = emptyMap()
    private var fullCodeDefinitions: Map<String, IcCodeDefinition> = emptyMap()
    private var definitionsByIc: Map<String, List<IcCodeDefinition>> = emptyMap()

    suspend fun ensureReady() {
        if (initialized) {
            return
        }

        initializationMutex.withLock {
            if (!initialized) {
                ensureInitialized(Res.readBytes("files/chips.json").decodeToString())
            }
        }
    }

    fun ensureInitialized(jsonText: String) {
        if (initialized) {
            return
        }

        val (icDefinitions, fullCodeDefinitions, definitionsByIc) = parseDefinitions(jsonText)
        this.icDefinitions = icDefinitions
        this.fullCodeDefinitions = fullCodeDefinitions
        this.definitionsByIc = definitionsByIc
        initialized = true
    }

    fun isReady(): Boolean = initialized

    /**
     * Returns the IC name for the given IC type byte.
     *
     * @param icType The IC type byte value
     * @return The corresponding IC name, or null if the IC type is unknown
     */
    fun getIcName(icType: Byte): String? {
        return getIcName(icType, null)
    }

    /**
     * Returns the IC name for the given ROM and IC type bytes. Resolution prefers an exact two-byte
     * IC code match, then an IC-only entry, then a same-IC entry from another ROM.
     *
     * @param icType The IC type byte value
     * @param romType The optional ROM type byte value
     * @return The corresponding IC name, or null if the IC type is unknown
     */
    fun getIcName(icType: Byte, romType: Byte?): String? {
        return resolveIcType(icType, romType)?.name
    }

    fun resolveIcType(icType: Byte, romType: Byte?): IcTypeResolution? {
        val ic = icType.toHexByte()
        val exactDefinition =
            romType?.let { fullCodeDefinitions[fullCodeKey(it.toHexByte(), ic)] }
        if (exactDefinition != null) {
            return IcTypeResolution(exactDefinition.name, IcTypeResolutionConfidence.EXACT)
        }

        val icDefinition = icDefinitions[ic]
        if (icDefinition != null) {
            return IcTypeResolution(icDefinition.name, IcTypeResolutionConfidence.IC_ONLY)
        }

        val sameIcDefinition = definitionsByIc[ic]?.firstOrNull()
        return sameIcDefinition?.let {
            IcTypeResolution(it.name, IcTypeResolutionConfidence.ROM_MISMATCH)
        }
    }

    private fun parseDefinitions(
        jsonText: String
    ): Triple<
        Map<String, IcCodeDefinition>,
        Map<String, IcCodeDefinition>,
        Map<String, List<IcCodeDefinition>>,
    > {
        val icResult = mutableMapOf<String, IcCodeDefinition>()
        val fullCodeResult = mutableMapOf<String, IcCodeDefinition>()
        val definitionsByIc = mutableMapOf<String, MutableList<IcCodeDefinition>>()
        val root = json.parseToJsonElement(jsonText).jsonArray

        for (definitionElement in root) {
            val definitionObject = definitionElement.jsonObject
            val ic = definitionObject.optionalRegistryString("ic")?.normalizeCode() ?: continue
            val rom = definitionObject.optionalRegistryString("rom")?.normalizeCode()
            val name = definitionObject.optionalRegistryString("name") ?: continue
            val definition =
                IcCodeDefinition(
                    ic = ic,
                    rom = rom,
                    name = name,
                )

            if (rom == null) {
                icResult[ic] = definition
            } else {
                fullCodeResult[fullCodeKey(rom, ic)] = definition
            }
            definitionsByIc.getOrPut(ic) { mutableListOf() } += definition
        }

        return Triple(icResult, fullCodeResult, definitionsByIc)
    }

    private fun fullCodeKey(rom: String, ic: String): String = "$rom:$ic"

    private fun Byte.toHexByte(): String = toUByte().toString(16).uppercase().padStart(2, '0')

    private fun String.normalizeCode(): String =
        trim().removePrefix("0x").removePrefix("0X").uppercase().padStart(2, '0')

    private fun JsonObject.optionalRegistryString(key: String): String? {
        return this[key]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
    }
}

private data class IcCodeDefinition(
    val ic: String,
    val rom: String?,
    val name: String,
)

data class IcTypeResolution(
    val name: String,
    val confidence: IcTypeResolutionConfidence,
) {
    val isUncertain: Boolean
        get() = confidence == IcTypeResolutionConfidence.ROM_MISMATCH
}

enum class IcTypeResolutionConfidence {
    EXACT,
    IC_ONLY,
    ROM_MISMATCH,
}
