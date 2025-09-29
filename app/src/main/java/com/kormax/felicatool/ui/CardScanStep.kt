package com.kormax.felicatool.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.time.Duration

enum class StepStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    ERROR,
}

data class CardScanStep(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val status: StepStatus = StepStatus.PENDING,
    val result: String? = null,
    val collapsedResult: String? = null,
    val isCollapsed: Boolean = true,
    val errorMessage: String? = null,
    val duration: Duration? = null,
) {
    companion object {
        fun createInitialSteps(): List<CardScanStep> =
            listOf(
                CardScanStep(
                    id = "polling",
                    title = "Initial Info",
                    description = "Reading card IDM and PMM data",
                    icon = Icons.Default.Info,
                ),
                CardScanStep(
                    id = "polling_system_code",
                    title = "Polling - System Code",
                    description = "Request primary system code of the card using polling command",
                    icon = Icons.Default.Settings,
                ),
                CardScanStep(
                    id = "polling_communication_performance",
                    title = "Polling - Communication Performance",
                    description =
                        "Request information about supported communication speeds using polling command",
                    icon = Icons.Default.Phone,
                ),
                CardScanStep(
                    id = "request_response",
                    title = "Request Response",
                    description = "Request response from the card",
                    icon = Icons.Default.Settings,
                ),
                CardScanStep(
                    id = "request_system_code",
                    title = "Request System Code",
                    description = "Request all system codes registered to the card",
                    icon = Icons.Default.Settings,
                ),
                CardScanStep(
                    id = "request_specification_version",
                    title = "Request Specification Version",
                    description = "Getting card OS version and supported option versions",
                    icon = Icons.Default.Info,
                ),
                CardScanStep(
                    id = "get_platform_information",
                    title = "Get Platform Information",
                    description = "Get platform information from the card",
                    icon = Icons.Default.Info,
                ),
                CardScanStep(
                    id = "get_system_status",
                    title = "Get System Status",
                    description = "Getting current system status information from the card",
                    icon = Icons.Default.Info,
                ),
                CardScanStep(
                    id = "request_code_list",
                    title = "Request Code List",
                    description = "Request a list of nodes for given a root node iteratively",
                    icon = Icons.AutoMirrored.Filled.List,
                ),
                CardScanStep(
                    id = "search_service_code",
                    title = "Search Service Codes",
                    description = "Search all available nodes on the card iteratively",
                    icon = Icons.AutoMirrored.Filled.List,
                ),
                CardScanStep(
                    id = "request_service",
                    title = "Request Service",
                    description = "Request key versions for discovered nodes",
                    icon = Icons.Default.CheckCircle,
                ),
                CardScanStep(
                    id = "request_service_v2",
                    title = "Request Service V2",
                    description =
                        "Request AES and DES key versions for nodes alongside the used key type identifier",
                    icon = Icons.Default.CheckCircle,
                ),
                CardScanStep(
                    id = "request_block_information",
                    title = "Request Block Information",
                    description = "Request the amount of allocated blocks for nodes",
                    icon = Icons.Default.Info,
                ),
                CardScanStep(
                    id = "request_block_information_ex",
                    title = "Request Block Information Ex",
                    description = "Request the amount of allocated and free blocks for nodes",
                    icon = Icons.Default.Info,
                ),
                CardScanStep(
                    id = "get_node_property_value_limited_service",
                    title = "Get Node Property - Value Limited Service",
                    description = "Get value-limited purse service properties for discovered nodes",
                    icon = Icons.Default.Info,
                ),
                CardScanStep(
                    id = "get_node_property_mac_communication",
                    title = "Get Node Property - MAC Communication",
                    description = "Get MAC communication properties for discovered nodes",
                    icon = Icons.Default.Info,
                ),
                CardScanStep(
                    id = "read_without_encryption_determine_error_indication",
                    title = "Determine type of error indication",
                    description = "How errors are indicated when reading blocks",
                    icon = Icons.Default.Search,
                ),
                CardScanStep(
                    id = "read_without_encryption_detect_illegal_number_error_preference",
                    title = "Detect Illegal Number Error Preference",
                    description =
                        "Check which error type is preferred by the card when Read Without Encryption exceeds both block and service limits",
                    icon = Icons.Default.Search,
                ),
                CardScanStep(
                    id = "read_without_encryption_determine_max_services",
                    title = "Determine Max Services",
                    description = "How many services can be read in a request",
                    icon = Icons.Default.Search,
                ),
                CardScanStep(
                    id = "read_without_encryption_determine_max_blocks",
                    title = "Determine Max Blocks",
                    description = "How many blocks can be read in a request",
                    icon = Icons.Default.Search,
                ),
                CardScanStep(
                    id = "read_blocks_without_encryption",
                    title = "Read Blocks Without Encryption",
                    description =
                        "Reading block data from services that don't require authentication",
                    icon = Icons.Default.Search,
                ),
                CardScanStep(
                    id = "get_area_information",
                    title = "Get Area Information",
                    description = "Get information about discovered areas",
                    icon = Icons.Default.Info,
                ),
                CardScanStep(
                    id = "get_container_property",
                    title = "Get Container Property",
                    description = "Get container property data by index",
                    icon = Icons.Default.Info,
                ),
                CardScanStep(
                    id = "set_parameter",
                    title = "Set Parameter",
                    description = "Set card parameters (encryption type and packet type)",
                    icon = Icons.Default.Build,
                ),
                CardScanStep(
                    id = "get_container_issue_information",
                    title = "Get Container Issue Information",
                    description =
                        "Get container-specific information including format version and mobile phone model",
                    icon = Icons.Default.Info,
                ),
                CardScanStep(
                    id = "get_container_id",
                    title = "Get Container ID",
                    description = "Get container IDM from mobile FeliCa cards",
                    icon = Icons.Default.Info,
                ),
                CardScanStep(
                    id = "reset_mode",
                    title = "Reset Mode",
                    description = "Reset card mode to Mode0",
                    icon = Icons.Default.Refresh,
                ),
                CardScanStep(
                    id = "echo",
                    title = "Echo",
                    description = "Test echo command",
                    icon = Icons.Default.Refresh,
                ),
                CardScanStep(
                    id = "authentication1_des",
                    title = "Authenticate1 DES",
                    description = "Attempt DES authentication with discovered nodes",
                    icon = Icons.Default.Lock,
                ),
                CardScanStep(
                    id = "authentication1_aes",
                    title = "Authenticate1 AES",
                    description = "Attempt AES authentication with discovered nodes",
                    icon = Icons.Default.Lock,
                ),
                CardScanStep(
                    id = "scan_overview",
                    title = "View Comprehensive Data",
                    description = "Display all collected card information in a detailed view",
                    icon = Icons.AutoMirrored.Filled.List,
                ),
            )
    }
}
