package com.kormax.felicatool.service

import com.kormax.felicatool.service.steps.Authentication1AesStep
import com.kormax.felicatool.service.steps.Authentication1DesDetermineSupportedStep
import com.kormax.felicatool.service.steps.Authentication1DesNodeListHierarchyValidationStep
import com.kormax.felicatool.service.steps.DiscoverNodesStep
import com.kormax.felicatool.service.steps.EchoDetermineMaxPayloadSizeStep
import com.kormax.felicatool.service.steps.EchoDetermineSupportedStep
import com.kormax.felicatool.service.steps.ForceDiscoverBlocksStep
import com.kormax.felicatool.service.steps.ForceDiscoverNodesStep
import com.kormax.felicatool.service.steps.GetAreaInformationStep
import com.kormax.felicatool.service.steps.GetContainerIdStep
import com.kormax.felicatool.service.steps.GetContainerIssueInformationStep
import com.kormax.felicatool.service.steps.GetContainerPropertyStep
import com.kormax.felicatool.service.steps.GetNodeKeyVersionsStep
import com.kormax.felicatool.service.steps.GetNodePropertyMacCommunicationStep
import com.kormax.felicatool.service.steps.GetNodePropertyValueLimitedServiceStep
import com.kormax.felicatool.service.steps.GetPlatformInformationStep
import com.kormax.felicatool.service.steps.GetSystemStatusStep
import com.kormax.felicatool.service.steps.InitialInfoStep
import com.kormax.felicatool.service.steps.InternalAuthenticateAndReadStep
import com.kormax.felicatool.service.steps.PollingCommunicationPerformanceStep
import com.kormax.felicatool.service.steps.PollingDetermineTrailingDataSupportedStep
import com.kormax.felicatool.service.steps.PollingSystemCodeStep
import com.kormax.felicatool.service.steps.ProbeSystemCodesManuallyStep
import com.kormax.felicatool.service.steps.ReadBlocksWithoutEncryptionStep
import com.kormax.felicatool.service.steps.ReadWithoutEncryptionDetectIllegalNumberErrorPreferenceStep
import com.kormax.felicatool.service.steps.ReadWithoutEncryptionDetermineErrorIndicationStep
import com.kormax.felicatool.service.steps.ReadWithoutEncryptionDetermineMaxBlocksStep
import com.kormax.felicatool.service.steps.ReadWithoutEncryptionDetermineMaxServicesStep
import com.kormax.felicatool.service.steps.ReadWithoutEncryptionDetermineSupportedStep
import com.kormax.felicatool.service.steps.RequestBlockInformationExStep
import com.kormax.felicatool.service.steps.RequestBlockInformationStep
import com.kormax.felicatool.service.steps.RequestCodeListDetermineSupportedStep
import com.kormax.felicatool.service.steps.RequestResponseStep
import com.kormax.felicatool.service.steps.RequestServiceDetermineSupportedStep
import com.kormax.felicatool.service.steps.RequestServiceUnknownNodeAttributesStep
import com.kormax.felicatool.service.steps.RequestServiceV2DetermineSupportedStep
import com.kormax.felicatool.service.steps.RequestSpecificationVersionStep
import com.kormax.felicatool.service.steps.RequestSystemCodeStep
import com.kormax.felicatool.service.steps.ResetModeStep
import com.kormax.felicatool.service.steps.ScanOverviewStep
import com.kormax.felicatool.service.steps.SearchServiceCodeDetermineSupportedStep
import com.kormax.felicatool.service.steps.SetParameterStep
import com.kormax.felicatool.service.steps.WriteWithoutEncryptionDetermineErrorIndicationStep
import com.kormax.felicatool.service.steps.WriteWithoutEncryptionDetermineMaxBlocksStep
import com.kormax.felicatool.service.steps.WriteWithoutEncryptionDetermineSupportedStep

internal object ScanStepRegistry {
    private val orderedSteps =
        listOf(
            InitialInfoStep,
            PollingSystemCodeStep,
            PollingCommunicationPerformanceStep,
            PollingDetermineTrailingDataSupportedStep,
            RequestResponseStep,
            RequestSystemCodeStep,
            ProbeSystemCodesManuallyStep,
            RequestSpecificationVersionStep,
            GetPlatformInformationStep,
            GetSystemStatusStep,
            RequestCodeListDetermineSupportedStep,
            SearchServiceCodeDetermineSupportedStep,
            RequestServiceDetermineSupportedStep,
            RequestServiceUnknownNodeAttributesStep,
            RequestServiceV2DetermineSupportedStep,
            DiscoverNodesStep,
            GetNodeKeyVersionsStep,
            ForceDiscoverNodesStep,
            RequestBlockInformationStep,
            RequestBlockInformationExStep,
            GetNodePropertyValueLimitedServiceStep,
            GetNodePropertyMacCommunicationStep,
            ReadWithoutEncryptionDetermineSupportedStep,
            ReadWithoutEncryptionDetermineErrorIndicationStep,
            ReadWithoutEncryptionDetectIllegalNumberErrorPreferenceStep,
            ReadWithoutEncryptionDetermineMaxServicesStep,
            ReadWithoutEncryptionDetermineMaxBlocksStep,
            ReadBlocksWithoutEncryptionStep,
            ForceDiscoverBlocksStep,
            WriteWithoutEncryptionDetermineSupportedStep,
            WriteWithoutEncryptionDetermineErrorIndicationStep,
            WriteWithoutEncryptionDetermineMaxBlocksStep,
            GetAreaInformationStep,
            GetContainerPropertyStep,
            SetParameterStep,
            GetContainerIssueInformationStep,
            GetContainerIdStep,
            ResetModeStep,
            EchoDetermineSupportedStep,
            EchoDetermineMaxPayloadSizeStep,
            InternalAuthenticateAndReadStep,
            Authentication1DesDetermineSupportedStep,
            Authentication1DesNodeListHierarchyValidationStep,
            Authentication1AesStep,
            ScanOverviewStep,
        )

    private val stepsById = orderedSteps.associateBy { it.descriptor.id }

    fun descriptors(settings: ScanSettings): List<ScanStepDescriptor> =
        orderedSteps.filter { it.isEnabled(settings) }.map { it.descriptor }

    fun find(id: String): ScanStep? = stepsById[id]
}
