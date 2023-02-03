package net.corda.ledger.verification.sanbox.impl

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.ledger.verification.exceptions.NotReadyException
import net.corda.ledger.verification.exceptions.VirtualNodeException
import net.corda.ledger.verification.sanbox.VerificationSandboxService
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.sandboxgroupcontext.MutableSandboxGroupContext
import net.corda.sandboxgroupcontext.RequireSandboxAMQP
import net.corda.sandboxgroupcontext.RequireSandboxJSON
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.getObjectByKey
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.sandboxgroupcontext.service.registerCordappCustomSerializers
import net.corda.sandboxgroupcontext.service.registerCustomCryptography
import net.corda.sandboxgroupcontext.service.registerCustomJsonDeserializers
import net.corda.sandboxgroupcontext.service.registerCustomJsonSerializers
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

/**
 * This is a sandbox service that is internal to this component.
 *
 * It gets/creates a sandbox with a per-sandbox:
 *
 *   * serializer
 *   * entity manager factory
 *
 */
@Suppress("LongParameterList")
@RequireSandboxAMQP
@RequireSandboxJSON
@Component(service = [ VerificationSandboxService::class ])
class VerificationSandboxServiceImpl @Activate constructor(
    @Reference
    private val sandboxService: SandboxGroupContextComponent,
    @Reference
    private val cpiInfoService: CpiInfoReadService
) : VerificationSandboxService {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun get(holdingIdentity: HoldingIdentity, cpiId: CpiIdentifier): SandboxGroupContext {
        val cpksMetadata = cpiInfoService.get(cpiId)?.cpksMetadata
            ?: throw VirtualNodeException("Could not get list of CPKs for $cpiId")

        // Verification sandbox uses only Contract CPKs
        val cpkFileChecksums = cpksMetadata
            .filter(CpkMetadata::isContractCpk)
            .mapTo(mutableSetOf(), CpkMetadata::fileChecksum)

        if (!sandboxService.hasCpks(cpkFileChecksums)) {
            // TODO Retries when CPKs not available (CORE-9382)

            // We're throwing internal exceptions so that we can relay some information back to the flow worker
            // on how to proceed with any request to us that fails.
            throw NotReadyException("CPKs not available (yet): $cpkFileChecksums")
        }

        return sandboxService.getOrCreate(getVirtualNodeContext(holdingIdentity, cpkFileChecksums)) { _, ctx ->
            initializeSandbox(holdingIdentity, ctx)
        }
    }

    private fun initializeSandbox(
        holdingIdentity: HoldingIdentity,
        ctx: MutableSandboxGroupContext
    ): AutoCloseable {
        val customCrypto = sandboxService.registerCustomCryptography(ctx)
        val customSerializers = sandboxService.registerCordappCustomSerializers(ctx)
        val jsonDeserializers = sandboxService.registerCustomJsonDeserializers(ctx)
        val jsonSerializers = sandboxService.registerCustomJsonSerializers(ctx)

        // Instruct all CustomMetadataConsumers to accept their metadata.
        sandboxService.acceptCustomMetadata(ctx)

        // TODO What services do we end up with? We want only verification side of crypto, serialization (AMQP and maybe
        //  JSON, but not kryo). Need to review what is reachable from this sandbox. (CORE-9379)

        logger.info("Initialising Verification Sandbox for $holdingIdentity")

        return AutoCloseable {
            logger.info("Closing Verification Sandbox for $holdingIdentity")
            jsonSerializers.close()
            jsonDeserializers.close()
            customSerializers.close()
            customCrypto.close()
        }
    }

    /** NOTE THE SANDBOX GROUP TYPE HERE */
    private fun getVirtualNodeContext(holdingIdentity: HoldingIdentity, cpkFileChecksums: Set<SecureHash>) =
        VirtualNodeContext(
            holdingIdentity,
            cpkFileChecksums,
            SandboxGroupType.VERIFICATION,
            null
        )
}

fun SandboxGroupContext.getSerializationService(): SerializationService =
    getObjectByKey(RequireSandboxAMQP.AMQP_SERIALIZATION_SERVICE)
        ?: throw CordaRuntimeException(
            "Verification serialization service not found within the sandbox for identity: " +
                    "${virtualNodeContext.holdingIdentity}"
        )
