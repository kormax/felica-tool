package com.kormax.felicatool.device

import android.content.Context
import android.os.Build
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Log
import androidx.core.util.writeText
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Serializable
internal enum class KeystoreBackend(val label: String, val level: Int) {
    TEE("TEE", KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT),
    STRONGBOX("StrongBox", KeyProperties.SECURITY_LEVEL_STRONGBOX),
}

@Serializable
internal enum class KeyAlgorithm(val label: String, val algorithm: String, val bits: Int) {
    AES128("AES-128", "AES", 128),
    DESEDE("DESede", "DESede", 168),
}

@Serializable
internal enum class Support {
    YES,
    NO,
    INCONCLUSIVE,
}

@Serializable
internal enum class AttestationId(val label: String, val tag: Int) {
    BRAND("Brand", 710),
    DEVICE("Device", 711),
    PRODUCT("Product", 712),
    MANUFACTURER("Manufacturer", 716),
    MODEL("Model", 717),
}

@Serializable
internal data class ImportMeasurement(
    val support: Support,
    val reportedSecurityLevel: Int? = null,
    val reportedOrigin: Int? = null,
    val reportedKeySize: Int? = null,
    val keyInfoError: String? = null,
)

@Serializable
internal data class AlgorithmMeasurement(
    val generation: Support,
    val rawImport: ImportMeasurement,
    val wrappedImport: ImportMeasurement,
    val generationNanos: List<Long>,
    val encryptionNanos: List<Long>,
    val decryptionNanos: List<Long>,
)

@Serializable
internal data class BackendMeasurement(
    val backend: KeystoreBackend,
    val implementation: String?,
    val attestation: Support,
    val attestedKeymasterVersion: Int?,
    val attestedIds: List<AttestationId> = emptyList(),
    val algorithms: Map<KeyAlgorithm, AlgorithmMeasurement>,
    val concurrentOccupancy: Int?,
) {
    val attestedVersionLabel: String?
        get() = attestedKeymasterVersion?.let {
            when {
                it >= 100 -> "KeyMint ${it / 100}"
                it == 41 -> "Keymaster 4.1"
                else -> "Keymaster $it"
            }
        }
}

@Serializable
internal data class KeystoreProfile(
    val schema: Int,
    val androidApi: String,
    val backends: List<BackendMeasurement>,
)

internal val androidApiVersion: String
    get() =
        if (Build.VERSION.SDK_INT >= 36) {
            val full = Build.VERSION.SDK_INT_FULL
            "${Build.getMajorSdkVersion(full)}.${Build.getMinorSdkVersion(full)}"
        } else {
            Build.VERSION.SDK_INT.toString()
        }

internal sealed interface MeasurementState {
    data class Measuring(val detail: String = "Loading keystore profile") : MeasurementState

    data class Ready(
        val profile: KeystoreProfile,
        val refreshing: KeystoreBackend? = null,
        val refreshFailed: KeystoreBackend? = null,
    ) : MeasurementState

    data object Failed : MeasurementState
}

internal object KeystoreCapabilities {
    private const val SCHEMA = 2
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val state = MutableStateFlow<MeasurementState>(MeasurementState.Measuring())
    val measurements = state.asStateFlow()
    private var started = false

    private fun profileFile(context: Context) =
        AtomicFile(File(context.noBackupFilesDir, "keystore-profile.json"))

    @Synchronized
    fun start(context: Context) {
        if (started && state.value != MeasurementState.Failed) return
        started = true
        state.value = MeasurementState.Measuring()
        val app = context.applicationContext
        scope.launch {
            try {
                val file = profileFile(app)
                val cached =
                    try {
                        Json.decodeFromString<KeystoreProfile>(file.readFully().decodeToString())
                    } catch (_: FileNotFoundException) {
                        null
                    } catch (_: SerializationException) {
                        null
                    }
                val api = androidApiVersion
                val profile =
                    cached?.takeIf { it.schema == SCHEMA && it.androidApi == api }
                        ?: run {
                            val backends =
                                KeystoreProfiler(app.packageManager).measure {
                                    state.value = MeasurementState.Measuring(it)
                                }
                            KeystoreProfile(SCHEMA, api, backends).also {
                                file.writeText(Json.encodeToString(it))
                            }
                        }
                state.value = MeasurementState.Ready(profile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("KeystoreCapabilities", "Could not measure or store keystore capabilities", e)
                state.value = MeasurementState.Failed
            }
        }
    }

    @Synchronized
    fun refresh(context: Context, backend: KeystoreBackend) {
        val ready = state.value as? MeasurementState.Ready ?: return
        if (ready.refreshing != null) return
        state.value = ready.copy(refreshing = backend, refreshFailed = null)
        val app = context.applicationContext
        scope.launch {
            try {
                val measured = KeystoreProfiler(app.packageManager).measure(backend)
                val profile =
                    ready.profile.copy(
                        backends =
                            ready.profile.backends.map {
                                if (it.backend == backend) measured else it
                            },
                    )
                profileFile(app).writeText(Json.encodeToString(profile))
                state.value = MeasurementState.Ready(profile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("KeystoreCapabilities", "Could not refresh ${backend.label} measurements", e)
                state.value = ready.copy(refreshFailed = backend)
            }
        }
    }
}
