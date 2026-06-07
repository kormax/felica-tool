package com.kormax.felicatool.service

import com.kormax.felicatool.felica.Area
import com.kormax.felicatool.felica.Node
import com.kormax.felicatool.felica.Service
import com.kormax.felicatool.felica.System
import com.kormax.felicatool.felica.WithStatusFlags
import kotlin.time.Duration

internal fun byteToHex(statusFlag: Number): String =
    (statusFlag.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0')

internal fun formatBlockNumberHex(blockNumber: Int): String =
    blockNumber.toString(16).uppercase().padStart(4, '0')

internal fun formatStatus(
    statusFlag1: Number?,
    statusFlag2: Number?,
    prefix: String = "status{n}=",
): String {
    val value1 = "0x${statusFlag1?.let { byteToHex(it) } ?: "??"}"
    val value2 = "0x${statusFlag2?.let { byteToHex(it) } ?: "??"}"
    if (prefix.isEmpty()) {
        return "$value1 $value2"
    }

    val label1 = prefix.replace("{n}", "1")
    val label2 = prefix.replace("{n}", "2")
    val entry1 = if (label1.endsWith("=")) "$label1$value1" else "$label1=$value1"
    val entry2 = if (label2.endsWith("=")) "$label2$value2" else "$label2=$value2"
    return "$entry1, $entry2"
}

internal fun formatStatus(response: WithStatusFlags, prefix: String = "status{n}="): String =
    formatStatus(response.statusFlag1, response.statusFlag2, prefix)

internal fun formatTimeoutFormula(
    constant: Duration,
    perUnit: Duration,
    isSupported: Boolean,
): String {
    if (!isSupported) {
        return "Not supported"
    }
    val constMs = formatTwoDecimals(constant.inWholeNanoseconds / 1_000_000.0)
    val perUnitMs = formatTwoDecimals(perUnit.inWholeNanoseconds / 1_000_000.0)
    return "${constMs} + (${perUnitMs} * n) ms"
}

private fun formatTwoDecimals(value: Double): String {
    val scaled = kotlin.math.round(value * 100).toLong()
    val whole = scaled / 100
    val fraction = (scaled % 100).toString().padStart(2, '0')
    return "$whole.$fraction"
}

internal fun formatSystemCodeLabel(systemCode: ByteArray?): String =
    systemCode?.toHexString()?.uppercase() ?: "unknown"

internal fun describeNode(
    node: Node,
    includeNodeNumber: Boolean = true,
    includeCode: Boolean = true,
): String {
    val label =
        when (node) {
            is Area ->
                if (node.isRoot) {
                    "Root Area"
                } else if (includeNodeNumber) {
                    "Area ${node.number}-${node.endNumber}"
                } else {
                    "Area"
                }
            is Service ->
                if (includeNodeNumber) {
                    "Service ${node.number}"
                } else {
                    "Service"
                }
            is System -> "System"
            else -> "Node"
        }

    return if (includeCode) {
        "$label (${node.code.toHexString().uppercase()})"
    } else {
        label
    }
}
