package com.kormax.felicatool.service

import com.kormax.felicatool.service.steps.Authentication1AesDetermineTrailingDataSupportedStep
import com.kormax.felicatool.service.steps.Authentication1AesStep
import com.kormax.felicatool.service.steps.Authentication1DesDetermineSupportedStep
import com.kormax.felicatool.service.steps.Authentication1DesDetermineTrailingDataSupportedStep
import com.kormax.felicatool.service.steps.Authentication1DesNodeListHierarchyValidationStep
import com.kormax.felicatool.service.steps.DiscoverNodesStep
import com.kormax.felicatool.service.steps.EchoDetermineMaxPayloadSizeStep
import com.kormax.felicatool.service.steps.EchoDetermineSupportedStep
import com.kormax.felicatool.service.steps.ForceDiscoverBlocksStep
import com.kormax.felicatool.service.steps.ForceDiscoverNodesStep
import com.kormax.felicatool.service.steps.GetAreaInformationDetermineSupportedStep
import com.kormax.felicatool.service.steps.GetAreaInformationDetermineTrailingDataSupportedStep
import com.kormax.felicatool.service.steps.GetAreaInformationStep
import com.kormax.felicatool.service.steps.GetContainerIdDetermineTrailingDataSupportedStep
import com.kormax.felicatool.service.steps.GetContainerIdStep
import com.kormax.felicatool.service.steps.GetContainerIssueInformationDetermineTrailingDataSupportedStep
import com.kormax.felicatool.service.steps.GetContainerIssueInformationStep
import com.kormax.felicatool.service.steps.GetContainerPropertiesStep
import com.kormax.felicatool.service.steps.GetContainerPropertyDetermineSupportedStep
import com.kormax.felicatool.service.steps.GetContainerPropertyDetermineTrailingDataSupportedStep
import com.kormax.felicatool.service.steps.GetNodeBlockInformationStep
import com.kormax.felicatool.service.steps.GetNodeKeyVersionsStep
import com.kormax.felicatool.service.steps.GetNodePropertyDetermineTrailingDataSupportedStep
import com.kormax.felicatool.service.steps.GetNodePropertyMacCommunicationDetermineSupportedStep
import com.kormax.felicatool.service.steps.GetNodePropertyStep
import com.kormax.felicatool.service.steps.GetNodePropertyValueLimitedServiceDetermineSupportedStep
import com.kormax.felicatool.service.steps.GetSystemStatusDetermineSupportedStep
import com.kormax.felicatool.service.steps.GetSystemStatusDetermineTrailingDataSupportedStep
import com.kormax.felicatool.service.steps.GetSystemStatusesStep
import com.kormax.felicatool.service.steps.InitialInfoStep
import com.kormax.felicatool.service.steps.InternalAuthenticateAndReadDetermineTrailingDataSupportedStep
import com.kormax.felicatool.service.steps.InternalAuthenticateAndReadStep
import com.kormax.felicatool.service.steps.PollingCommunicationPerformanceStep
import com.kormax.felicatool.service.steps.PollingDetermineTrailingDataSupportedStep
import com.kormax.felicatool.service.steps.PollingSystemCodeStep
import com.kormax.felicatool.service.steps.ProbeSystemCodesManuallyStep
import com.kormax.felicatool.service.steps.ReadBlocksWithoutEncryptionStep
import com.kormax.felicatool.service.steps.ReadWithoutEncryptionDetermineErrorIndicationStep
import com.kormax.felicatool.service.steps.ReadWithoutEncryptionDetermineIllegalNumberErrorPreferenceStep
import com.kormax.felicatool.service.steps.ReadWithoutEncryptionDetermineMaxBlocksStep
import com.kormax.felicatool.service.steps.ReadWithoutEncryptionDetermineMaxServicesStep
import com.kormax.felicatool.service.steps.ReadWithoutEncryptionDetermineSupportedStep
import com.kormax.felicatool.service.steps.ReadWithoutEncryptionDetermineTrailingDataSupportedStep
import com.kormax.felicatool.service.steps.RequestBlockInformationDetermineSupportedStep
import com.kormax.felicatool.service.steps.RequestBlockInformationDetermineTrailingDataSupportedStep
import com.kormax.felicatool.service.steps.RequestBlockInformationExDetermineSupportedStep
import com.kormax.felicatool.service.steps.RequestBlockInformationExDetermineTrailingDataSupportedStep
import com.kormax.felicatool.service.steps.RequestCodeListDetermineSupportedStep
import com.kormax.felicatool.service.steps.RequestCodeListDetermineTrailingDataSupportedStep
import com.kormax.felicatool.service.steps.RequestProductInformationDetermineSupportedStep
import com.kormax.felicatool.service.steps.RequestProductInformationDetermineTrailingDataSupportedStep
import com.kormax.felicatool.service.steps.RequestResponseDetermineSupportedStep
import com.kormax.felicatool.service.steps.RequestResponseDetermineTrailingDataSupportedStep
import com.kormax.felicatool.service.steps.RequestServiceDetermineSupportedStep
import com.kormax.felicatool.service.steps.RequestServiceDetermineTrailingDataSupportedStep
import com.kormax.felicatool.service.steps.RequestServiceUnknownNodeAttributesStep
import com.kormax.felicatool.service.steps.RequestServiceV2DetermineSupportedStep
import com.kormax.felicatool.service.steps.RequestServiceV2DetermineTrailingDataSupportedStep
import com.kormax.felicatool.service.steps.RequestSpecificationVersionDetermineSupportedStep
import com.kormax.felicatool.service.steps.RequestSpecificationVersionDetermineTrailingDataSupportedStep
import com.kormax.felicatool.service.steps.RequestSystemCodeDetermineSupportedStep
import com.kormax.felicatool.service.steps.RequestSystemCodeDetermineTrailingDataSupportedStep
import com.kormax.felicatool.service.steps.ResetModeDetermineTrailingDataSupportedStep
import com.kormax.felicatool.service.steps.ResetModeStep
import com.kormax.felicatool.service.steps.ScanOverviewStep
import com.kormax.felicatool.service.steps.SearchServiceCodeDetermineSupportedStep
import com.kormax.felicatool.service.steps.SearchServiceCodeDetermineTrailingDataSupportedStep
import com.kormax.felicatool.service.steps.SetParameterDetermineTrailingDataSupportedStep
import com.kormax.felicatool.service.steps.SetParameterStep
import com.kormax.felicatool.service.steps.WriteWithoutEncryptionDetermineErrorIndicationStep
import com.kormax.felicatool.service.steps.WriteWithoutEncryptionDetermineMaxBlocksStep
import com.kormax.felicatool.service.steps.WriteWithoutEncryptionDetermineSupportedStep
import com.kormax.felicatool.service.steps.WriteWithoutEncryptionDetermineTrailingDataSupportedStep

internal object ScanStepRegistry {
    private val orderedSteps =
        listOf(
            InitialInfoStep,
            PollingSystemCodeStep,
            PollingCommunicationPerformanceStep,
            PollingDetermineTrailingDataSupportedStep,
            RequestResponseDetermineSupportedStep,
            RequestResponseDetermineTrailingDataSupportedStep,
            RequestSystemCodeDetermineSupportedStep,
            RequestSystemCodeDetermineTrailingDataSupportedStep,
            ProbeSystemCodesManuallyStep,
            RequestSpecificationVersionDetermineSupportedStep,
            RequestSpecificationVersionDetermineTrailingDataSupportedStep,
            RequestProductInformationDetermineSupportedStep,
            RequestProductInformationDetermineTrailingDataSupportedStep,
            GetSystemStatusDetermineSupportedStep,
            GetSystemStatusDetermineTrailingDataSupportedStep,
            GetSystemStatusesStep,
            SearchServiceCodeDetermineSupportedStep,
            SearchServiceCodeDetermineTrailingDataSupportedStep,
            RequestCodeListDetermineSupportedStep,
            RequestCodeListDetermineTrailingDataSupportedStep,
            RequestServiceDetermineSupportedStep,
            RequestServiceDetermineTrailingDataSupportedStep,
            RequestServiceUnknownNodeAttributesStep,
            RequestServiceV2DetermineSupportedStep,
            RequestServiceV2DetermineTrailingDataSupportedStep,
            DiscoverNodesStep,
            GetNodeKeyVersionsStep,
            ForceDiscoverNodesStep,
            RequestBlockInformationDetermineSupportedStep,
            RequestBlockInformationDetermineTrailingDataSupportedStep,
            RequestBlockInformationExDetermineSupportedStep,
            RequestBlockInformationExDetermineTrailingDataSupportedStep,
            GetNodeBlockInformationStep,
            GetNodePropertyValueLimitedServiceDetermineSupportedStep,
            GetNodePropertyMacCommunicationDetermineSupportedStep,
            GetNodePropertyDetermineTrailingDataSupportedStep,
            GetNodePropertyStep,
            ReadWithoutEncryptionDetermineSupportedStep,
            ReadWithoutEncryptionDetermineTrailingDataSupportedStep,
            ReadWithoutEncryptionDetermineErrorIndicationStep,
            ReadWithoutEncryptionDetermineIllegalNumberErrorPreferenceStep,
            ReadWithoutEncryptionDetermineMaxServicesStep,
            ReadWithoutEncryptionDetermineMaxBlocksStep,
            ReadBlocksWithoutEncryptionStep,
            ForceDiscoverBlocksStep,
            WriteWithoutEncryptionDetermineSupportedStep,
            WriteWithoutEncryptionDetermineTrailingDataSupportedStep,
            WriteWithoutEncryptionDetermineErrorIndicationStep,
            WriteWithoutEncryptionDetermineMaxBlocksStep,
            GetAreaInformationDetermineSupportedStep,
            GetAreaInformationDetermineTrailingDataSupportedStep,
            GetAreaInformationStep,
            GetContainerPropertyDetermineSupportedStep,
            GetContainerPropertyDetermineTrailingDataSupportedStep,
            GetContainerPropertiesStep,
            SetParameterStep,
            SetParameterDetermineTrailingDataSupportedStep,
            GetContainerIssueInformationStep,
            GetContainerIssueInformationDetermineTrailingDataSupportedStep,
            GetContainerIdStep,
            GetContainerIdDetermineTrailingDataSupportedStep,
            EchoDetermineSupportedStep,
            EchoDetermineMaxPayloadSizeStep,
            ResetModeStep,
            ResetModeDetermineTrailingDataSupportedStep,
            InternalAuthenticateAndReadStep,
            InternalAuthenticateAndReadDetermineTrailingDataSupportedStep,
            Authentication1DesDetermineSupportedStep,
            Authentication1DesDetermineTrailingDataSupportedStep,
            Authentication1DesNodeListHierarchyValidationStep,
            Authentication1AesStep,
            Authentication1AesDetermineTrailingDataSupportedStep,
            ScanOverviewStep,
        )

    private val stepsById = orderedSteps.associateBy { it.descriptor.id }

    fun descriptors(settings: ScanSettings): List<ScanStepDescriptor> =
        orderedSteps.filter { it.isEnabled(settings) }.map { it.descriptor }

    fun find(id: String): ScanStep? = stepsById[id]
}
