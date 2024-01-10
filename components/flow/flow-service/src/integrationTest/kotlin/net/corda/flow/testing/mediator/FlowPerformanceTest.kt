package net.corda.flow.testing.mediator

import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpiinfo.read.fake.CpiInfoReadServiceFake
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.testing.tests.ALICE_HOLDING_IDENTITY
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.testing.sandboxes.CpiLoader
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.fake.VirtualNodeInfoReadServiceFake
import net.corda.virtualnode.toCorda
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.osgi.framework.BundleContext
import org.osgi.service.cm.ConfigurationAdmin
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.time.Instant
import java.util.*

@ExtendWith(ServiceExtension::class)
@Execution(ExecutionMode.SAME_THREAD)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FlowPerformanceTest {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        private const val TIMEOUT_MILLIS = 5000L
        private const val CPB1 = "META-INF/calculator.cpb"
    }

    private val stateManagerFactory = TestStateManagerFactoryImpl()

//    @InjectService(timeout = TIMEOUT_MILLIS)
//    lateinit var testContext: FlowServiceTestContext

    @InjectService(timeout = TIMEOUT_MILLIS)
    lateinit var cpiLoader: CpiLoader

    @InjectService(timeout = TIMEOUT_MILLIS)
    lateinit var configurationReadService: ConfigurationReadService

    @InjectService(timeout = TIMEOUT_MILLIS)
    lateinit var cpiInfoReadService: CpiInfoReadServiceFake

    @InjectService(timeout = TIMEOUT_MILLIS)
    lateinit var virtualNodeInfoReadService: VirtualNodeInfoReadServiceFake

    @InjectService(timeout = TIMEOUT_MILLIS)
    lateinit var flowEventMediatorFactory: TestFlowEventMediatorFactory

    @InjectService(timeout = 1000)
    lateinit var sandboxSetup: SandboxSetup

    @BeforeAll
    fun setup(
        @InjectService
        configAdmin: ConfigurationAdmin,
        @InjectBundleContext
        bundleContext: BundleContext,
        @TempDir
        testDirectory: Path
    ) {
        virtualNodeInfoReadService.start()
        cpiInfoReadService.start()
        virtualNodeInfoReadService.waitUntilRunning()
        cpiInfoReadService.waitUntilRunning()


        val testBundle = bundleContext.bundle
        logger.info("Configuring sandboxes for ${testBundle.symbolicName}")
        logger.info("configAdmin = $configAdmin")
        logger.info("testDirectory = $testDirectory")

        logger.info("setting up sandbox")

        sandboxSetup.configure(bundleContext, Path.of("C:/dev/corda-runtime-os-2/tmp/"))
    }

    @Test
    fun `Run flow`() {
        val cpi = cpiLoader.loadCPI(CPB1)
        val cpiMetadata = cpi.metadata
        val cpiIdentifier =  CpiIdentifier(
            cpi.metadata.cpiId.name,
            cpi.metadata.cpiId.version,
            cpi.metadata.cpiId.signerSummaryHash
        )

        cpiInfoReadService.addOrUpdate(cpiMetadata)
        virtualNodeInfoReadService.addOrUpdate(
            getVirtualNodeInfo(cpiIdentifier, ALICE_HOLDING_IDENTITY)
        )

        val configs = TestConfig().toSmartConfigs()

        val messageBus = TestLoadGenerator(
            "cpiName",
            ALICE_HOLDING_IDENTITY,
            "com.r3.corda.testing.calculator.CalculatorFlow",
            "flowStartArgs",
        )

        val stateManager = stateManagerFactory.create(SmartConfigImpl.empty())
        val eventMediator = flowEventMediatorFactory.create(configs, messageBus, stateManager)
        assertNotNull(eventMediator)

        Thread.sleep(2000L)
        eventMediator.start()
        Thread.sleep(60000L)
        eventMediator.close()
    }

    private fun getVirtualNodeInfo(
        cpiId: CpiIdentifier,
        holdingId: HoldingIdentity,
    ) : VirtualNodeInfo {
        val emptyUUID = UUID(0, 0)
        return VirtualNodeInfo(
            holdingId.toCorda(),
            cpiId,
            emptyUUID,
            emptyUUID,
            emptyUUID,
            emptyUUID,
            emptyUUID,
            emptyUUID,
            emptyUUID,
            flowP2pOperationalStatus = VirtualNodeInfo.DEFAULT_INITIAL_STATE,
            flowStartOperationalStatus = VirtualNodeInfo.DEFAULT_INITIAL_STATE,
            flowOperationalStatus = VirtualNodeInfo.DEFAULT_INITIAL_STATE,
            vaultDbOperationalStatus = VirtualNodeInfo.DEFAULT_INITIAL_STATE,
            timestamp = Instant.now()
        )
    }
}
