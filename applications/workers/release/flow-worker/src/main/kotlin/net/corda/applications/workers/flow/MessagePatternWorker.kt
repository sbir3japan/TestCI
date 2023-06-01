package net.corda.applications.workers.flow

import net.corda.applications.workers.workercommon.ApplicationBanner
import net.corda.applications.workers.workercommon.DefaultWorkerParams
import net.corda.applications.workers.workercommon.JavaSerialisationFilter
import net.corda.applications.workers.workercommon.PathAndConfig
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.getBootstrapConfig
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.getParams
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.loggerStartupInfo
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.printHelpOrVersion
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.setupMonitor
import net.corda.applications.workers.workercommon.WorkerMonitor
import net.corda.libs.configuration.secret.SecretsServiceFactoryResolver
import net.corda.libs.configuration.validation.ConfigurationValidatorFactory
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.processors.messagepattern.MessagePatternProcessor
import net.corda.schema.configuration.BootConfig
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import picocli.CommandLine
import picocli.CommandLine.Mixin

/** The worker for handling message pattern tests. */
@Suppress("Unused", "LongParameterList")
@Component(service = [Application::class])
class MessagePatternWorker @Activate constructor(
    @Reference(service = MessagePatternProcessor::class)
    private val messagePatternProcessor: MessagePatternProcessor,
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = WorkerMonitor::class)
    private val workerMonitor: WorkerMonitor,
    @Reference(service = ConfigurationValidatorFactory::class)
    private val configurationValidatorFactory: ConfigurationValidatorFactory,
    @Reference(service = PlatformInfoProvider::class)
    val platformInfoProvider: PlatformInfoProvider,
    @Reference(service = ApplicationBanner::class)
    val applicationBanner: ApplicationBanner,
    @Reference(service = SecretsServiceFactoryResolver::class)
    val secretsServiceFactoryResolver: SecretsServiceFactoryResolver,
) : Application {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    /** Parses the arguments, then initialises and starts the [messagePatternProcessor]. */
    override fun startup(args: Array<String>) {
        logger.info("Message Pattern worker starting.")
        logger.loggerStartupInfo(platformInfoProvider)

        applicationBanner.show("Message Pattern Worker", platformInfoProvider)


        JavaSerialisationFilter.install()

        val params = getParams(args, MessagePatternWorkerParams())
        if (printHelpOrVersion(params.defaultParams, MessagePatternWorker::class.java, shutDownService)) return
        setupMonitor(workerMonitor, params.defaultParams, this.javaClass.simpleName)

        val patternConfig = PathAndConfig("pattern", params.patternParams)

        val config = getBootstrapConfig(
            secretsServiceFactoryResolver,
            params.defaultParams,
            configurationValidatorFactory.createConfigValidator(),
            listOf(patternConfig)
        )

        logger.info("Message Pattern Worker params: ${args.map { "$it, " }}")

        messagePatternProcessor.start(config)
    }

    override fun shutdown() {
        logger.info("Message pattern worker stopping.")
        messagePatternProcessor.stop()
        workerMonitor.stop()
    }
}

/** Additional parameters for the flow worker are added here. */
private class MessagePatternWorkerParams {
    @Mixin
    var defaultParams = DefaultWorkerParams()

    @CommandLine.Option(names = ["-P", "--pattern"], description = ["Message pattern parameters for the worker."])
    var patternParams = emptyMap<String, String>()
}

