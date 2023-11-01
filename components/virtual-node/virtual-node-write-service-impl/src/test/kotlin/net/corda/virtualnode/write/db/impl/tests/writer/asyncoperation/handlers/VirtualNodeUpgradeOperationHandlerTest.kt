package net.corda.virtualnode.write.db.impl.tests.writer.asyncoperation.handlers

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.ShortHash
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.SignedData
import net.corda.data.membership.common.RegistrationRequestDetails
import net.corda.data.membership.common.v2.RegistrationStatus
import net.corda.data.virtualnode.VirtualNodeUpgradeRequest
import net.corda.libs.cpi.datamodel.CpkDbChangeLog
import net.corda.libs.cpi.datamodel.CpkDbChangeLogIdentifier
import net.corda.libs.cpi.datamodel.repository.CpkDbChangeLogRepository
import net.corda.libs.external.messaging.ExternalMessagingRouteConfigGenerator
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.virtualnode.common.exception.LiquibaseDiffCheckFailedException
import net.corda.libs.virtualnode.datamodel.dto.VirtualNodeOperationStateDto
import net.corda.libs.virtualnode.datamodel.dto.VirtualNodeOperationType
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepository
import net.corda.membership.client.MemberResourceClient
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants
import net.corda.membership.lib.grouppolicy.GroupPolicyParser
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.VirtualNode.VIRTUAL_NODE_INFO_TOPIC
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.OperationalStatus
import net.corda.virtualnode.OperationalStatus.ACTIVE
import net.corda.virtualnode.OperationalStatus.INACTIVE
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.write.db.VirtualNodeWriteServiceException
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeEntityRepository
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.MigrationUtility
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.factories.RecordFactory
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.handlers.VirtualNodeUpgradeOperationHandler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction

class VirtualNodeUpgradeOperationHandlerTest {
    private val oldVirtualNodeEntityRepository = mock<VirtualNodeEntityRepository>()
    private val virtualNodeInfoPublisher = mock<Publisher>()
    private val virtualNodeRepository = mock<VirtualNodeRepository>()
    private val entityTransaction = mock<EntityTransaction>()
    private val em = mock<EntityManager>()
    private val entityManagerFactory = mock<EntityManagerFactory>()
    private val migrationUtility = mock<MigrationUtility> {
        whenever(it.areChangesetsDeployedOnVault(any(), any(), any())).thenReturn(false)
    }
    private val memberResourceClient = mock<MemberResourceClient>()
    private val membershipQueryClient = mock<MembershipQueryClient>().apply {
        whenever(
            queryRegistrationRequests(
                any(),
                anyOrNull(),
                any(),
                anyOrNull()
            )
        ).thenReturn(MembershipQueryResult.Success(emptyList()))
    }
    private val externalMessagingRouteConfig = """ { "dummy1":"dummy1" } """
    private val newExternalMessagingRouteConfig = """ { "dummy2":"dummy2" } """

    private val externalMessagingRouteConfigGenerator = mock<ExternalMessagingRouteConfigGenerator>().apply {
        whenever(generateNewConfig(any(), any(), any())).thenReturn(externalMessagingRouteConfig)
        whenever(generateUpgradeConfig(any(), any(), any())).thenReturn(newExternalMessagingRouteConfig)
    }

    private val vnodeId = "123456789011"

    private val mockChangelog1 = mock<CpkDbChangeLog> { changelog ->
        whenever(changelog.id).thenReturn(
            CpkDbChangeLogIdentifier(
                SecureHashImpl("SHA-256", "abc".toByteArray()),
                "cpk1"
            )
        )
        whenever(changelog.content).thenReturn("dog.xml")
    }
    private val mockChangelog2 = mock<CpkDbChangeLog> { changelog ->
        whenever(changelog.id).thenReturn(
            CpkDbChangeLogIdentifier(
                SecureHashImpl("SHA-256", "abc".toByteArray()),
                "cpk1"
            )
        )
        whenever(changelog.content).thenReturn("cat.xml")
    }
    private val cpkDbChangelogs = listOf(mockChangelog1, mockChangelog2)
    private val mockCpkDbChangeLogRepository = mock<CpkDbChangeLogRepository> {
        whenever(it.findByCpiId(any(), any())).thenReturn(cpkDbChangelogs)
    }

    private val deserializer = mock<CordaAvroDeserializer<KeyValuePairList>>()
    private val cordaAvroSerializationFactory = mock<CordaAvroSerializationFactory> {
        on { createAvroDeserializer(any(), eq(KeyValuePairList::class.java)) } doReturn deserializer
    }
    private val recordFactory = mock<RecordFactory>()

    private val mgmX500Name = MemberX500Name.parse("CN=MGM, O=MGM Corp, L=LDN, C=GB")
    private val newMgmInfo = mock<MemberInfo> {
        on { name } doReturn mgmX500Name
    }
    private val groupPolicyParser = mock<GroupPolicyParser> {
        on { getMgmInfo(any(), any()) } doReturn newMgmInfo
    }

    private val oldMgmInfo = mock<MemberInfo>()
    private val membershipGroupReader = mock<MembershipGroupReader> {
        on { lookup(eq(mgmX500Name), any()) } doReturn oldMgmInfo
    }
    private val membershipGroupReaderProvider = mock<MembershipGroupReaderProvider> {
        on { getGroupReader(any()) } doReturn membershipGroupReader
    }

    private val handler = VirtualNodeUpgradeOperationHandler(
        entityManagerFactory,
        oldVirtualNodeEntityRepository,
        virtualNodeInfoPublisher,
        migrationUtility,
        membershipGroupReaderProvider,
        memberResourceClient,
        membershipQueryClient,
        externalMessagingRouteConfigGenerator,
        cordaAvroSerializationFactory,
        recordFactory,
        groupPolicyParser,
        mockCpkDbChangeLogRepository,
        virtualNodeRepository,
    )

    private val holdingIdentity = HoldingIdentity(
        x500Name = MemberX500Name.parse("CN=Bob,OU=Unit1,O=Alice,L=London,ST=State1,C=GB"),
        groupId = "group-id"
    )
    private val sshBytes = ByteArray(16)
    private val ssh = SecureHashImpl("SHA-256", sshBytes)
    private val sshString = ssh.toString()
    private val cpiName = "someCpi"
    private val cpiId = CpiIdentifier(cpiName, "v1", ssh)
    private val currentCpiId = CpiIdentifier(cpiName, "v1", ssh)
    private val now = Instant.now()

    private val vNode = VirtualNodeInfo(
        holdingIdentity,
        currentCpiId,
        vaultDmlConnectionId = UUID.randomUUID(),
        cryptoDmlConnectionId = UUID.randomUUID(),
        uniquenessDmlConnectionId = UUID.randomUUID(),
        flowOperationalStatus = INACTIVE,
        flowStartOperationalStatus = INACTIVE,
        flowP2pOperationalStatus = INACTIVE,
        vaultDbOperationalStatus = INACTIVE,
        externalMessagingRouteConfig = externalMessagingRouteConfig,
        timestamp = now
    )
    private val staticGroupPolicy = genGroupPolicy(UUID.randomUUID().toString())
    private val dynamicGroupPolicy = genDynamicGroupPolicy(UUID.randomUUID().toString())
    private val groupId = GroupPolicyParser.groupIdFromJson(staticGroupPolicy)

    private fun genGroupPolicy(groupId: String): String {
        return """
                {
                    "${GroupPolicyConstants.PolicyKeys.Root.GROUP_ID}": "$groupId",
                    "${GroupPolicyConstants.PolicyKeys.Root.PROTOCOL_PARAMETERS}": {
                        "${GroupPolicyConstants.PolicyKeys.ProtocolParameters.STATIC_NETWORK}": {}
                    }
                }
                """.trimIndent()
    }

    private fun genDynamicGroupPolicy(groupId: String): String {
        return """
                {
                    "${GroupPolicyConstants.PolicyKeys.Root.GROUP_ID}": "$groupId"
                }
                """.trimIndent()
    }

    private val targetCpiChecksum = "targetCpi"
    private val currentCpiMetadata = mock<CpiMetadata> {
        whenever(it.groupPolicy).thenReturn(staticGroupPolicy)
    }
    private val targetCpiId = CpiIdentifier(cpiName, "v2", ssh)
    private val targetCpiMetadata = mock<CpiMetadata> {
        whenever(it.groupPolicy).thenReturn(staticGroupPolicy)
        whenever(it.cpiId).thenReturn(targetCpiId)
    }
    private val nonStaticTargetCpiMetadata = mock<CpiMetadata> {
        whenever(it.groupPolicy).thenReturn(dynamicGroupPolicy)
        whenever(it.cpiId).thenReturn(targetCpiId)
    }

    private val x500Name = MemberX500Name("Alice", "Alice Corp", "LDN", "GB")
    private val mockHoldingIdentity = HoldingIdentity(x500Name, groupId)
    private val vaultDmlConnectionId = UUID.randomUUID()
    private val vaultDdlConnectionId = UUID.randomUUID()
    private val requestId = "req1"
    private val request = VirtualNodeUpgradeRequest(vnodeId, targetCpiChecksum, null, false)
    private val forceUpgradeRequest = VirtualNodeUpgradeRequest(vnodeId, targetCpiChecksum, null, true)

    private val inProgressOpVnodeInfo = VirtualNodeInfo(
        holdingIdentity = mockHoldingIdentity,
        cpiIdentifier = targetCpiId,
        vaultDdlConnectionId = vaultDdlConnectionId,
        vaultDmlConnectionId = vaultDmlConnectionId,
        cryptoDdlConnectionId = null,
        cryptoDmlConnectionId = UUID.randomUUID(),
        uniquenessDdlConnectionId = null,
        uniquenessDmlConnectionId = UUID.randomUUID(),
        hsmConnectionId = UUID.randomUUID(),
        operationInProgress = requestId,
        externalMessagingRouteConfig = newExternalMessagingRouteConfig,
        timestamp = now
    )
    private val inProgressVnodeInfoWithoutVaultDdl = inProgressOpVnodeInfo.copy(vaultDdlConnectionId = null)
    private val noInProgressOpVnodeInfo = inProgressOpVnodeInfo.copy(operationInProgress = null)

    private fun withRejectedOperation(state: VirtualNodeOperationStateDto, reason: String, block: () -> Unit) {
        whenever(
            virtualNodeRepository.failedOperation(
                eq(em),
                eq(vnodeId),
                eq(requestId),
                eq(request.toString()),
                any(),
                eq(reason),
                eq(VirtualNodeOperationType.UPGRADE),
                eq(state)
            )
        ).thenReturn(noInProgressOpVnodeInfo)

        block()
    }

    @BeforeEach
    fun setUp() {
        whenever(em.transaction)
            .thenReturn(entityTransaction)
            .thenReturn(entityTransaction)
            .thenReturn(entityTransaction)
        whenever(entityManagerFactory.createEntityManager())
            .thenReturn(em)
            .thenReturn(em)
    }

    @Test
    fun `upgrade handler validates virtual node identifier is not null`() {
        assertThrows<IllegalArgumentException> {
            val virtualNodeUpgradeRequest = VirtualNodeUpgradeRequest(null, "aaaa", null, false)
            handler.handle(now, requestId, virtualNodeUpgradeRequest)
        }
    }

    @Test
    fun `upgrade handler validates target cpiFileChecksum is not null`() {
        assertThrows<IllegalArgumentException> {
            val virtualNodeUpgradeRequest = VirtualNodeUpgradeRequest(vnodeId, null, null, false)
            handler.handle(now, requestId, virtualNodeUpgradeRequest)
        }
    }

    @Test
    fun `upgrade handler validates it can find virtual node`() {
        findReturnsVnode(null)

        withRejectedOperation(VirtualNodeOperationStateDto.VALIDATION_FAILED, "Holding identity $vnodeId not found") {
            handler.handle(now, requestId, request)
        }
    }

    @Test
    fun `upgrade handler validates vault_db_operational_status is INACTIVE`() {
        val activeVnode = noInProgressOpVnodeInfo.copy(
            cpiIdentifier = currentCpiId,
            flowOperationalStatus = ACTIVE,
            flowStartOperationalStatus = ACTIVE,
            flowP2pOperationalStatus = ACTIVE,
            vaultDbOperationalStatus = ACTIVE
        )
        findReturnsVnode(activeVnode)

        withRejectedOperation(VirtualNodeOperationStateDto.VALIDATION_FAILED, "Virtual node must be in maintenance") {
            handler.handle(
                now,
                requestId,
                request
            )
        }
    }

    @Test
    fun `upgrade handler validates there is no operation in progress`() {
        val vNode = inProgressOpVnodeInfo.copy(
            cpiIdentifier = currentCpiId,
            flowOperationalStatus = INACTIVE,
            flowStartOperationalStatus = INACTIVE,
            flowP2pOperationalStatus = INACTIVE,
            vaultDbOperationalStatus = INACTIVE,
            operationInProgress = "some-op",
        )
        findReturnsVnode(vNode)
        whenever(oldVirtualNodeEntityRepository.getCpiMetadataByChecksum(targetCpiChecksum)).thenReturn(null)

        withRejectedOperation(VirtualNodeOperationStateDto.VALIDATION_FAILED, "Operation some-op already in progress") {
            handler.handle(
                now,
                requestId,
                request
            )
        }
    }

    @Test
    fun `upgrade handler allows upgrade to proceed with operation in progress if forceUpgrade is specified`() {
        val vNode = inProgressOpVnodeInfo.copy(
            cpiIdentifier = currentCpiId,
            flowP2pOperationalStatus = INACTIVE,
            flowStartOperationalStatus = INACTIVE,
            flowOperationalStatus = INACTIVE,
            vaultDbOperationalStatus = INACTIVE,
            operationInProgress = "Upgrade vNode"
        )

        findReturnsVnode(vNode)
        whenever(
            virtualNodeRepository.upgradeVirtualNodeCpi(
                any(), any(), any(), any(), any(), any(), any(), any(), any(),
            )
        ).thenReturn(vNode)
        whenever(virtualNodeRepository.completedOperation(any(), any())).thenReturn(vNode)
        getCpiMetadataReturnsTargetCpi()
        whenever(oldVirtualNodeEntityRepository.getCPIMetadataById(any(), any())).thenReturn(currentCpiMetadata)
        whenever(virtualNodeInfoPublisher.publish(any())).thenReturn(emptyList())

        handler.handle(
            now,
            requestId,
            forceUpgradeRequest
        )

        verify(virtualNodeRepository, times(1)).upgradeVirtualNodeCpi(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        )
    }

    @Test
    fun `upgrade handler can't find target CPI throws`() {
        findReturnsVnode()
        whenever(oldVirtualNodeEntityRepository.getCpiMetadataByChecksum(targetCpiChecksum)).thenReturn(null)

        withRejectedOperation(
            VirtualNodeOperationStateDto.VALIDATION_FAILED,
            "CPI with file checksum $targetCpiChecksum was not found"
        ) {
            handler.handle(
                now,
                requestId,
                request
            )
        }
    }

    @Test
    fun `upgrade handler can't find current CPI associated with target CPI throws`() {
        findReturnsVnode()
        getCpiMetadataReturnsTargetCpi()
        whenever(oldVirtualNodeEntityRepository.getCPIMetadataById(eq(em), eq(cpiId)))
            .thenReturn(null)

        withRejectedOperation(
            VirtualNodeOperationStateDto.VALIDATION_FAILED,
            "CPI with name ${targetCpiMetadata.cpiId.name}, version v1 was not found"
        ) {
            handler.handle(
                now,
                requestId,
                request
            )
        }
    }

    @Test
    fun `upgrade handler validates target CPI and current CPI are in the same group`() {
        val cpiInDifferentGroup = mock<CpiMetadata> { whenever(it.groupPolicy).thenReturn(genGroupPolicy("group-b")) }
        whenever(virtualNodeRepository.find(em, ShortHash.Companion.of(vnodeId))).thenReturn(vNode)
        getCpiMetadataReturnsTargetCpi()
        whenever(oldVirtualNodeEntityRepository.getCPIMetadataById(eq(em), eq(cpiId)))
            .thenReturn(cpiInDifferentGroup)

        withRejectedOperation(
            VirtualNodeOperationStateDto.VALIDATION_FAILED,
            "Expected MGM GroupId group-b but was $groupId in CPI"
        ) {
            handler.handle(
                now,
                requestId,
                request
            )
        }
    }

    @Test
    fun `upgrade handler fails to upgrade, rolls back transaction`() {
        findReturnsVnode()
        getCpiMetadataReturnsTargetCpi()
        getCpiMetadataReturnsCurrentCpi()
        whenever(
            virtualNodeRepository.upgradeVirtualNodeCpi(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        )
            .thenThrow(IllegalArgumentException("err"))

        whenever(entityTransaction.rollbackOnly).thenReturn(true).thenReturn(false)

        withRejectedOperation(VirtualNodeOperationStateDto.UNEXPECTED_FAILURE, "err" ) {
            handler.handle(
                now,
                requestId,
                request
            )
        }

        verify(entityTransaction, times(1)).rollback()
    }

    @Test
    fun `upgrade handler successfully persists and publishes a single vnode info when no vault DDL provided`() {
        val requestTimestamp = now

        findReturnsVnode()
        getCpiMetadataReturnsTargetCpi()
        getCpiMetadataReturnsCurrentCpi()
        whenever(
            virtualNodeRepository.upgradeVirtualNodeCpi(
                eq(em),
                eq(vnodeId),
                eq(cpiName),
                eq("v2"),
                eq(sshString),
                eq(newExternalMessagingRouteConfig),
                eq(requestId),
                eq(requestTimestamp),
                eq(request.toString())
            )
        ).thenReturn(inProgressVnodeInfoWithoutVaultDdl)
        whenever(virtualNodeRepository.completedOperation(any(), any())).thenReturn(inProgressVnodeInfoWithoutVaultDdl)

        val vnodeInfoCapture =
            argumentCaptor<List<Record<net.corda.data.identity.HoldingIdentity, net.corda.data.virtualnode.VirtualNodeInfo>>>()

        handler.handle(requestTimestamp, requestId, request)

        verify(virtualNodeInfoPublisher, times(2)).publish(vnodeInfoCapture.capture())

        assertUpgradedVnodeInfoIsPublished(
            vnodeInfoCapture.firstValue,
            expectedOperationInProgress = requestId
        )
    }

    @Test
    fun `upgrade handler re-publishes updated mgm information when group policy changed`() {
        val requestTimestamp = now

        findReturnsVnode()
        whenever(oldVirtualNodeEntityRepository.getCpiMetadataByChecksum(targetCpiChecksum)).thenReturn(
            nonStaticTargetCpiMetadata
        )
        whenever(oldVirtualNodeEntityRepository.getCPIMetadataById(eq(em), eq(cpiId)))
            .thenReturn(nonStaticTargetCpiMetadata)
        whenever(
            virtualNodeRepository.upgradeVirtualNodeCpi(
                eq(em),
                eq(vnodeId),
                eq(cpiName),
                eq("v2"),
                eq(sshString),
                eq(newExternalMessagingRouteConfig),
                eq(requestId),
                eq(requestTimestamp),
                eq(request.toString())
            )
        ).thenReturn(inProgressVnodeInfoWithoutVaultDdl)
        whenever(virtualNodeRepository.completedOperation(any(), any())).thenReturn(inProgressVnodeInfoWithoutVaultDdl)
        val mgmRecord = mock<Record<*, *>>()
        whenever(recordFactory.createMgmInfoRecord(any(), eq(newMgmInfo))).thenReturn(mgmRecord)

        handler.handle(requestTimestamp, requestId, request)

        verify(virtualNodeInfoPublisher).publish(eq(listOf(mgmRecord)))
    }

    @Test
    fun `serial from registration request is increased when not null for automated re-registration`() {
        val requestTimestamp = now

        findReturnsVnode()
        whenever(oldVirtualNodeEntityRepository.getCpiMetadataByChecksum(targetCpiChecksum)).thenReturn(
            nonStaticTargetCpiMetadata
        )
        whenever(oldVirtualNodeEntityRepository.getCPIMetadataById(eq(em), eq(cpiId)))
            .thenReturn(nonStaticTargetCpiMetadata)
        whenever(
            virtualNodeRepository.upgradeVirtualNodeCpi(
                eq(em),
                eq(vnodeId),
                eq(cpiName),
                eq("v2"),
                eq(sshString),
                eq(newExternalMessagingRouteConfig),
                eq(requestId),
                eq(requestTimestamp),
                eq(request.toString())
            )
        ).thenReturn(inProgressVnodeInfoWithoutVaultDdl)
        whenever(virtualNodeRepository.completedOperation(any(), any())).thenReturn(inProgressVnodeInfoWithoutVaultDdl)

        val memberBytes = byteArrayOf(1, 2)
        val registrationRequest = mock<RegistrationRequestDetails> {
            on { serial } doReturn 1L
            on { memberProvidedContext } doReturn SignedData(ByteBuffer.wrap(memberBytes), mock(), mock())
        }
        whenever(deserializer.deserialize(any()))
            .thenReturn(KeyValuePairList(listOf(KeyValuePair("key", "value"))))
        whenever(
            membershipQueryClient.queryRegistrationRequests(
                eq(mockHoldingIdentity),
                eq(mockHoldingIdentity.x500Name),
                eq(listOf(RegistrationStatus.APPROVED)),
                anyOrNull(),
            )
        ).thenReturn(MembershipQueryResult.Success(listOf(registrationRequest)))
        val requestCaptor = argumentCaptor<Map<String, String>>()
        whenever(memberResourceClient.startRegistration(eq(mockHoldingIdentity.shortHash), requestCaptor.capture()))
            .thenReturn(mock())

        handler.handle(requestTimestamp, requestId, request)

        assertThat(requestCaptor.allValues).hasSize(1)
        assertThat(requestCaptor.firstValue)
            .containsAllEntriesOf(mapOf("key" to "value", MemberInfoExtension.SERIAL to "2"))
    }

    @Test
    fun `serial from registration request is not attached when null for automated re-registration`() {
        val requestTimestamp = now

        findReturnsVnode()
        whenever(oldVirtualNodeEntityRepository.getCpiMetadataByChecksum(targetCpiChecksum)).thenReturn(
            nonStaticTargetCpiMetadata
        )
        whenever(oldVirtualNodeEntityRepository.getCPIMetadataById(eq(em), eq(cpiId)))
            .thenReturn(nonStaticTargetCpiMetadata)
        whenever(
            virtualNodeRepository.upgradeVirtualNodeCpi(
                eq(em),
                eq(vnodeId),
                eq(cpiName),
                eq("v2"),
                eq(sshString),
                eq(newExternalMessagingRouteConfig),
                eq(requestId),
                eq(requestTimestamp),
                eq(request.toString())
            )
        ).thenReturn(inProgressVnodeInfoWithoutVaultDdl)
        whenever(virtualNodeRepository.completedOperation(any(), any())).thenReturn(inProgressVnodeInfoWithoutVaultDdl)

        val memberBytes = byteArrayOf(1, 2)
        val registrationRequest = mock<RegistrationRequestDetails> {
            on { serial } doReturn null
            on { memberProvidedContext } doReturn SignedData(ByteBuffer.wrap(memberBytes), mock(), mock())
        }
        whenever(deserializer.deserialize(any()))
            .thenReturn(KeyValuePairList(listOf(KeyValuePair("key", "value"))))
        whenever(
            membershipQueryClient.queryRegistrationRequests(
                eq(mockHoldingIdentity),
                eq(mockHoldingIdentity.x500Name),
                eq(listOf(RegistrationStatus.APPROVED)),
                anyOrNull(),
            )
        ).thenReturn(MembershipQueryResult.Success(listOf(registrationRequest)))
        val requestCaptor = argumentCaptor<Map<String, String>>()
        whenever(memberResourceClient.startRegistration(eq(mockHoldingIdentity.shortHash), requestCaptor.capture()))
            .thenReturn(mock())

        handler.handle(requestTimestamp, requestId, request)

        assertThat(requestCaptor.allValues).hasSize(1)
        assertThat(requestCaptor.firstValue)
            .containsAllEntriesOf(mapOf("key" to "value"))
    }

    @Test
    fun `migrations thrown an exception, operation is written with the details`() {
        val requestTimestamp = now

        findReturnsVnode()
        getCpiMetadataReturnsTargetCpi()
        getCpiMetadataReturnsCurrentCpi()
        upgradeReturnsInProgressVnode(requestTimestamp)
        whenever(migrationUtility.runVaultMigrations(any(), any(), any()))
            .thenThrow(VirtualNodeWriteServiceException("Outer exception", Exception("Inner exception")))

        val vnodeInfoCapture =
            argumentCaptor<List<Record<net.corda.data.identity.HoldingIdentity, net.corda.data.virtualnode.VirtualNodeInfo>>>()

        withRejectedOperation(VirtualNodeOperationStateDto.MIGRATIONS_FAILED, "Inner exception" ) {
            handler.handle(requestTimestamp, requestId, request)
        }

        verify(virtualNodeInfoPublisher, times(2)).publish(vnodeInfoCapture.capture())

        assertUpgradedVnodeInfoIsPublished(vnodeInfoCapture.firstValue, requestId)
        assertUpgradedVnodeInfoIsPublished(vnodeInfoCapture.secondValue, null)
    }

    @Test
    fun `liquibase diff checker fails with exception, operation is written for this failure`() {
        val requestTimestamp = now
        whenever(
            migrationUtility.areChangesetsDeployedOnVault(
                any(),
                any(),
                any()
            )
        ).thenThrow(LiquibaseDiffCheckFailedException("outer error", java.lang.Exception("Inner error")))

        findReturnsVnode()
        getCpiMetadataReturnsTargetCpi()
        getCpiMetadataReturnsCurrentCpi()
        upgradeReturnsInProgressVnode(requestTimestamp)

        val vnodeInfoCapture =
            argumentCaptor<List<Record<net.corda.data.identity.HoldingIdentity, net.corda.data.virtualnode.VirtualNodeInfo>>>()

        withRejectedOperation(VirtualNodeOperationStateDto.LIQUIBASE_DIFF_CHECK_FAILED, "outer error") {
            handler.handle(requestTimestamp, requestId, request)
        }

        verify(virtualNodeInfoPublisher, times(2)).publish(vnodeInfoCapture.capture())
        verify(migrationUtility, times(0)).runVaultMigrations(any(), any(), any())

        assertUpgradedVnodeInfoIsPublished(vnodeInfoCapture.firstValue, requestId)
        assertUpgradedVnodeInfoIsPublished(vnodeInfoCapture.secondValue, null)
    }

    @Test
    fun `upgrade handler successfully persists, runs migrations with vault ddl, publishes vnode info and completes operation`() {
        val requestTimestamp = now

        findReturnsVnode()
        getCpiMetadataReturnsTargetCpi()
        getCpiMetadataReturnsCurrentCpi()
        upgradeReturnsInProgressVnode(requestTimestamp)
        completedOperationReturnsNotInProgressVnode()

        val vnodeInfoRecordsCapture =
            argumentCaptor<List<Record<net.corda.data.identity.HoldingIdentity, net.corda.data.virtualnode.VirtualNodeInfo>>>()

        handler.handle(requestTimestamp, requestId, request)

        verify(virtualNodeInfoPublisher, times(2)).publish(vnodeInfoRecordsCapture.capture())
        verify(migrationUtility).runVaultMigrations(
            eq(ShortHash.of(request.virtualNodeShortHash)),
            eq(cpkDbChangelogs),
            eq(vaultDdlConnectionId)
        )

        assertUpgradedVnodeInfoIsPublished(
            vnodeInfoRecordsCapture.firstValue,
            requestId
        )
        assertUpgradedVnodeInfoIsPublished(vnodeInfoRecordsCapture.secondValue, null)
    }

    @Test
    fun `upgrade handler successfully persists, no migrations required`() {
        val requestTimestamp = now
        val migrationUtility = mock<MigrationUtility> {
            whenever(it.areChangesetsDeployedOnVault(any(), any(), any())).thenReturn(false)
        }

        findReturnsVnode()
        getCpiMetadataReturnsTargetCpi()
        getCpiMetadataReturnsCurrentCpi()
        upgradeReturnsInProgressVnode(requestTimestamp)
        whenever(
            migrationUtility.areChangesetsDeployedOnVault(
                request.virtualNodeShortHash,
                cpkDbChangelogs,
                vaultDmlConnectionId
            )
        )
            .thenReturn(true)
        completedOperationReturnsNotInProgressVnode()

        val vnodeInfoRecordsCapture =
            argumentCaptor<List<Record<net.corda.data.identity.HoldingIdentity, net.corda.data.virtualnode.VirtualNodeInfo>>>()

        handler.handle(requestTimestamp, requestId, request)

        verify(virtualNodeInfoPublisher, times(2)).publish(vnodeInfoRecordsCapture.capture())
        verify(migrationUtility, times(0)).runVaultMigrations(any(), any(), any())

        assertUpgradedVnodeInfoIsPublished(
            vnodeInfoRecordsCapture.firstValue,
            requestId
        )
        assertUpgradedVnodeInfoIsPublished(vnodeInfoRecordsCapture.secondValue, null)
    }

    private fun completedOperationReturnsNotInProgressVnode() {
        whenever(virtualNodeRepository.completedOperation(em, request.virtualNodeShortHash)).thenReturn(
            noInProgressOpVnodeInfo
        )
    }

    private fun upgradeReturnsInProgressVnode(requestTimestamp: Instant) {
        whenever(
            virtualNodeRepository.upgradeVirtualNodeCpi(
                eq(em),
                eq(vnodeId),
                eq(cpiName),
                eq("v2"),
                eq(sshString),
                eq(newExternalMessagingRouteConfig),
                eq(requestId),
                eq(requestTimestamp),
                eq(request.toString())
            )
        ).thenReturn(inProgressOpVnodeInfo)
    }

    private fun getCpiMetadataReturnsCurrentCpi() {
        whenever(oldVirtualNodeEntityRepository.getCPIMetadataById(eq(em), eq(cpiId)))
            .thenReturn(currentCpiMetadata)
    }

    private fun getCpiMetadataReturnsTargetCpi() {
        whenever(oldVirtualNodeEntityRepository.getCpiMetadataByChecksum(targetCpiChecksum)).thenReturn(
            targetCpiMetadata
        )
    }

    private fun findReturnsVnode() {
        findReturnsVnode(vNode)
    }

    private fun findReturnsVnode(vNode: VirtualNodeInfo?) {
        whenever(virtualNodeRepository.find(em, ShortHash.of(vnodeId))).thenReturn(vNode)
    }

    private fun assertUpgradedVnodeInfoIsPublished(
        publishedRecordList: List<Record<net.corda.data.identity.HoldingIdentity, net.corda.data.virtualnode.VirtualNodeInfo>>,
        expectedOperationInProgress: String?
    ) {
        assertThat(publishedRecordList).isNotNull
        assertThat(publishedRecordList).hasSize(1)

        val publishedRecord = publishedRecordList[0]
        assertThat(publishedRecord.topic).isEqualTo(VIRTUAL_NODE_INFO_TOPIC)

        val holdingIdentity = publishedRecord.key
        assertThat(holdingIdentity.groupId).isEqualTo(GroupPolicyParser.groupIdFromJson(staticGroupPolicy))
        assertThat(holdingIdentity.x500Name).isEqualTo(x500Name.toString())

        assertThat(publishedRecord.value).isNotNull
        val virtualNodeInfo = publishedRecord.value!!
        assertThat(virtualNodeInfo.cpiIdentifier.name).isEqualTo(cpiName)
        assertThat(virtualNodeInfo.cpiIdentifier.version).isEqualTo("v2")
        assertThat(virtualNodeInfo.operationInProgress).isEqualTo(expectedOperationInProgress)
        assertThat(virtualNodeInfo.externalMessagingRouteConfig).isEqualTo(newExternalMessagingRouteConfig)
    }
}