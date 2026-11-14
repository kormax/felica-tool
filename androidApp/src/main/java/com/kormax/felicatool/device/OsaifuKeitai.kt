package com.kormax.felicatool.device

import android.os.Build
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class OsaifuKeitaiStatus(val label: String) {
    NO_FELICA_CONFIGURATION("Not supported"),
    BLOCKED_MODEL_CODE("Blocked for this model"),
    SUPPORTED_IN_JAPAN("Supported in Japan"),
    SUPPORTED("Supported"),
    UNKNOWN("Unknown"),
}

internal data class OsaifuKeitaiInfo(
    val status: OsaifuKeitaiStatus,
    val configurationFiles: Map<String, ByteArray?>,
)

internal object OsaifuKeitai {
    private val directories =
        listOf("/product/etc", "/vendor/etc", "/system/etc", "/odm/etc", "/system_ext/etc", "/etc")
            .let { roots ->
                roots.map { "$it/felica" } + roots.flatMap { listOf(it, "$it/nfc") } + "/data/nfc"
            }
    private val configurationNames = listOf("common.cfg", "mfm.cfg", "mfs.cfg")

    val modelCode: String
        get() = Build.SKU.takeUnless { it.isBlank() || it.equals("unknown", true) } ?: Build.ODM_SKU

    private val isPixel: Boolean
        get() = Build.MANUFACTURER.equals("Google", true) && Build.MODEL.startsWith("Pixel", true)

    suspend fun detect(): OsaifuKeitaiInfo =
        withContext(Dispatchers.IO) {
            val files =
                directories
                    .flatMap { directory -> configurationNames.map { File(directory, it) } }
                    .filter(File::isFile)
            val contents = files.associate { file ->
                file.path to
                    runCatching {
                        file.takeIf { it.length() <= MAXIMUM_CONFIGURATION_SIZE }?.readBytes()
                    }
                        .getOrNull()
            }
            val common =
                files
                    .firstOrNull { it.name == "common.cfg" }
                    ?.let { contents[it.path] }
                    ?.let { parse(it.decodeToString()) }
                    .orEmpty()
            OsaifuKeitaiInfo(status(files.isNotEmpty(), common), contents)
        }

    private fun status(hasConfiguration: Boolean, common: Map<String, String>) =
        when {
            !hasConfiguration -> OsaifuKeitaiStatus.NO_FELICA_CONFIGURATION
            isPixel && LOCATION_CHECK_KEY in common ->
                when (modelCode.uppercase(Locale.ROOT)) {
                    in PixelNfcModels.supported -> OsaifuKeitaiStatus.SUPPORTED
                    in PixelNfcModels.supportedWhenInJapan -> OsaifuKeitaiStatus.SUPPORTED_IN_JAPAN
                    "",
                    "UNKNOWN" -> OsaifuKeitaiStatus.UNKNOWN
                    else -> OsaifuKeitaiStatus.BLOCKED_MODEL_CODE
                }
            else -> OsaifuKeitaiStatus.SUPPORTED
        }

    private fun parse(config: String): Map<String, String> =
        config
            .lineSequence()
            .map { it.trim() }
            .filterNot { it.isBlank() || it.startsWith('#') }
            .mapNotNull { line ->
                val parts = line.split(',', limit = 2)
                if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
            }
            .toMap()

    private const val MAXIMUM_CONFIGURATION_SIZE = 256 * 1024
    private const val LOCATION_CHECK_KEY = "00000014"
}
