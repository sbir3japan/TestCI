package net.corda.flow.testing.mediator

import net.corda.data.identity.HoldingIdentity
import net.corda.flow.ALICE_X500
import net.corda.flow.testing.context.FlowServiceTestContext
import net.corda.flow.testing.context.TestConfig
import net.corda.flow.testing.tests.ALICE_HOLDING_IDENTITY
import net.corda.flow.testing.tests.CPI1
import net.corda.flow.testing.tests.CPK1
import net.corda.flow.testing.tests.CPK1_CHECKSUM
import net.corda.libs.configuration.SmartConfigImpl
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.osgi.test.junit5.service.ServiceExtension
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.osgi.test.common.annotation.InjectService

@ExtendWith(ServiceExtension::class)
@Execution(ExecutionMode.SAME_THREAD)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FlowPerformanceTest {
    companion object {
        private const val TIMEOUT_MILLIS = 5000L
    }

    private val stateManagerFactory = TestStateManagerFactoryImpl()

    @InjectService(timeout = TIMEOUT_MILLIS)
    lateinit var testContext: FlowServiceTestContext

    @InjectService(timeout = TIMEOUT_MILLIS)
    lateinit var flowEventMediatorFactory: TestFlowEventMediatorFactory

    @BeforeEach
    fun setup() {
        testContext.resetTestContext()
        testContext.start()
    }

    @Test
    fun `Run flow`() {
        testContext.virtualNode(CPI1, ALICE_HOLDING_IDENTITY)
        testContext.cpkMetadata(CPI1, CPK1, CPK1_CHECKSUM)
        testContext.sandboxCpk(CPK1_CHECKSUM)
        testContext.membershipGroupFor(ALICE_HOLDING_IDENTITY)

        val configs = TestConfig().toSmartConfigs()

        val messageBus = TestLoadGenerator(
            "cpiName",
            HoldingIdentity(ALICE_X500, "group1"),
            "flowClassName",
            "flowStartArgs",
        )

        val stateManager = stateManagerFactory.create(SmartConfigImpl.empty())

        val eventMediator = flowEventMediatorFactory.create(configs, messageBus, stateManager)

        assertNotNull(eventMediator)

        eventMediator.start()

        Thread.sleep(5 * 60000L)

        eventMediator.close()
    }
}
