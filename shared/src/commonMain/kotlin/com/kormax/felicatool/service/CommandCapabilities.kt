package com.kormax.felicatool.service

import com.kormax.felicatool.felica.Authentication1DesNodeListHierarchyValidation
import com.kormax.felicatool.felica.ErrorLocationIndication
import com.kormax.felicatool.felica.IllegalNumberErrorPreference

interface CommandCapability {
    val supported: CommandSupport
    val trailingDataSupported: CommandSupport

    val available: Boolean
        get() = supported == CommandSupport.SUPPORTED
}

data class CommandCapabilities(
    val polling: PollingCommandCapability = PollingCommandCapability(),
    val requestResponse: BasicCommandCapability = BasicCommandCapability(),
    val requestSystemCode: BasicCommandCapability = BasicCommandCapability(),
    val requestSpecificationVersion: BasicCommandCapability = BasicCommandCapability(),
    val getSystemStatus: BasicCommandCapability = BasicCommandCapability(),
    val requestCodeList: BasicCommandCapability = BasicCommandCapability(),
    val searchServiceCode: BasicCommandCapability = BasicCommandCapability(),
    val requestService: RequestServiceCommandCapability = RequestServiceCommandCapability(),
    val requestServiceV2: BasicCommandCapability = BasicCommandCapability(),
    val setParameter: BasicCommandCapability = BasicCommandCapability(),
    val getContainerIssueInformation: BasicCommandCapability = BasicCommandCapability(),
    val requestProductInformation: BasicCommandCapability = BasicCommandCapability(),
    val getContainerId: BasicCommandCapability = BasicCommandCapability(),
    val echo: EchoCommandCapability = EchoCommandCapability(),
    val resetMode: BasicCommandCapability = BasicCommandCapability(),
    val getNodeProperty: GetNodePropertyCommandCapability = GetNodePropertyCommandCapability(),
    val requestBlockInformation: BasicCommandCapability = BasicCommandCapability(),
    val requestBlockInformationEx: BasicCommandCapability = BasicCommandCapability(),
    val readWithoutEncryption: ReadWithoutEncryptionCommandCapability =
        ReadWithoutEncryptionCommandCapability(),
    val writeWithoutEncryption: WriteWithoutEncryptionCommandCapability =
        WriteWithoutEncryptionCommandCapability(),
    val getAreaInformation: BasicCommandCapability = BasicCommandCapability(),
    val getContainerProperty: BasicCommandCapability = BasicCommandCapability(),
    val authentication1Des: Authentication1DesCommandCapability =
        Authentication1DesCommandCapability(),
    val authentication1Aes: BasicCommandCapability = BasicCommandCapability(),
    val internalAuthenticateAndRead: BasicCommandCapability = BasicCommandCapability(),
)

data class BasicCommandCapability(
    override val supported: CommandSupport = CommandSupport.UNKNOWN,
    override val trailingDataSupported: CommandSupport = CommandSupport.UNKNOWN,
) : CommandCapability

data class PollingCommandCapability(
    override val supported: CommandSupport = CommandSupport.UNKNOWN,
    override val trailingDataSupported: CommandSupport = CommandSupport.UNKNOWN,
    val systemCodeSupported: CommandSupport = CommandSupport.UNKNOWN,
    val communicationPerformanceSupported: CommandSupport = CommandSupport.UNKNOWN,
) : CommandCapability

data class RequestServiceCommandCapability(
    override val supported: CommandSupport = CommandSupport.UNKNOWN,
    override val trailingDataSupported: CommandSupport = CommandSupport.UNKNOWN,
    val unknownNodeAttributesSupported: CommandSupport = CommandSupport.UNKNOWN,
) : CommandCapability

data class ReadWithoutEncryptionCommandCapability(
    override val supported: CommandSupport = CommandSupport.UNKNOWN,
    override val trailingDataSupported: CommandSupport = CommandSupport.UNKNOWN,
    val errorLocationIndication: ErrorLocationIndication = ErrorLocationIndication.FLAG,
    val maxBlocksPerRequest: Int? = null,
    val maxServicesPerRequest: Int? = null,
    val illegalNumberErrorPreference: IllegalNumberErrorPreference? = null,
) : CommandCapability

data class WriteWithoutEncryptionCommandCapability(
    override val supported: CommandSupport = CommandSupport.UNKNOWN,
    override val trailingDataSupported: CommandSupport = CommandSupport.UNKNOWN,
    val errorLocationIndication: ErrorLocationIndication? = null,
    val maxBlocksPerRequest: Int? = null,
) : CommandCapability

data class EchoCommandCapability(
    override val supported: CommandSupport = CommandSupport.UNKNOWN,
    override val trailingDataSupported: CommandSupport = CommandSupport.UNKNOWN,
    val maxPayloadSize: Int? = null,
) : CommandCapability

data class GetNodePropertyCommandCapability(
    override val trailingDataSupported: CommandSupport = CommandSupport.UNKNOWN,
    val macCommunicationSupported: CommandSupport = CommandSupport.UNKNOWN,
    val valueLimitedServiceSupported: CommandSupport = CommandSupport.UNKNOWN,
) : CommandCapability {
    override val supported: CommandSupport
        get() =
            when {
                macCommunicationSupported == CommandSupport.SUPPORTED ||
                    valueLimitedServiceSupported == CommandSupport.SUPPORTED ->
                    CommandSupport.SUPPORTED
                macCommunicationSupported == CommandSupport.UNSUPPORTED &&
                    valueLimitedServiceSupported == CommandSupport.UNSUPPORTED ->
                    CommandSupport.UNSUPPORTED
                else -> CommandSupport.UNKNOWN
            }
}

data class Authentication1DesCommandCapability(
    override val supported: CommandSupport = CommandSupport.UNKNOWN,
    override val trailingDataSupported: CommandSupport = CommandSupport.UNKNOWN,
    val nodeListHierarchyValidation: Authentication1DesNodeListHierarchyValidation =
        Authentication1DesNodeListHierarchyValidation.UNKNOWN,
) : CommandCapability
