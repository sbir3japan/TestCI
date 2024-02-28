package net.corda.libs.statemanager.impl.factory

import com.datastax.oss.driver.api.core.CqlSession
import net.corda.db.core.CloseableDataSource
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.CompressionType
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.StateManagerFactory
import net.corda.libs.statemanager.impl.ScyllaStateManager
import net.corda.libs.statemanager.impl.metrics.MetricsRecorderImpl
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.schema.configuration.StateManagerConfig
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Component(service = [StateManagerFactory::class])
class StateManagerFactoryImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
//    @Reference(service = CompressionService::class)
//    private val compressionService: CompressionService,
) : StateManagerFactory {
    private val lock = ReentrantLock()
    private var dataSource: CloseableDataSource? = null

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

//    // TODO-[CORE-16663]: factory when multiple databases are supported by the Corda platform (only Postgres now).
//    private fun queryProvider(): QueryProvider {
//        return PostgresQueryProvider()
//    }

    override fun create(config: SmartConfig, stateType: StateManagerConfig.StateType, compressionType: CompressionType): StateManager {
        lock.withLock {
//            if (dataSource == null) {
//                logger.info("Initializing Shared State Manager DataSource")
//
//                val stateManagerConfig = config.getConfig(stateType.value)
//
//                val user = stateManagerConfig.getString(StateManagerConfig.Database.JDBC_USER)
//                val pass = stateManagerConfig.getString(StateManagerConfig.Database.JDBC_PASS)
//                val jdbcUrl = stateManagerConfig.getString(StateManagerConfig.Database.JDBC_URL)
//                val jdbcDiver = stateManagerConfig.getString(StateManagerConfig.Database.JDBC_DRIVER)
//                val maxPoolSize = stateManagerConfig.getInt(StateManagerConfig.Database.JDBC_POOL_MAX_SIZE)
//                val minPoolSize = stateManagerConfig.getIntOrDefault(StateManagerConfig.Database.JDBC_POOL_MIN_SIZE, maxPoolSize)
//                val idleTimeout =
//                    stateManagerConfig.getInt(StateManagerConfig.Database.JDBC_POOL_IDLE_TIMEOUT_SECONDS).toLong().run(
//                        Duration::ofSeconds
//                    )
//                val maxLifetime =
//                    stateManagerConfig.getInt(StateManagerConfig.Database.JDBC_POOL_MAX_LIFETIME_SECONDS).toLong().run(
//                        Duration::ofSeconds
//                    )
//                val keepAliveTime =
//                    stateManagerConfig.getInt(StateManagerConfig.Database.JDBC_POOL_KEEP_ALIVE_TIME_SECONDS).toLong().run(
//                        Duration::ofSeconds
//                    )
//                val validationTimeout =
//                    stateManagerConfig.getInt(StateManagerConfig.Database.JDBC_POOL_VALIDATION_TIMEOUT_SECONDS).toLong()
//                        .run(Duration::ofSeconds)
//
//                dataSource = DataSourceFactoryImpl().create(
//                    enablePool = true,
//                    username = user,
//                    password = pass,
//                    jdbcUrl = jdbcUrl,
//                    driverClass = jdbcDiver,
//                    idleTimeout = idleTimeout,
//                    maxLifetime = maxLifetime,
//                    keepaliveTime = keepAliveTime,
//                    minimumPoolSize = minPoolSize,
//                    maximumPoolSize = maxPoolSize,
//                    validationTimeout = validationTimeout
//                )
//            }
//        }
//
//        return StateManagerImpl(
//            lifecycleCoordinatorFactory,
//            dataSource!!,
//            StateRepositoryImpl(queryProvider(), compressionService, compressionType),
//            MetricsRecorderImpl()
//        )

            // NOTE: session can be re-used (but should be closed when finished)

            // this is a bit wasteful, but hacking to create namespace and table on the fly
            CqlSession.builder()
                .build().use {
                    it.execute("""
                        CREATE KEYSPACE IF NOT EXISTS ${stateType.value}
                        WITH replication = {'class': 'SimpleStrategy', 'replication_factor' : 1};
                    """.trimIndent())
                    it.execute("""
                        CREATE TABLE IF NOT EXISTS ${stateType.value}.state (
                             key text,
                             value blob,
                             version int,
                             metadata text,
                             modified_time timestamp,
                             PRIMARY KEY (key)
                         )
                         WITH compression = {'sstable_compression': 'LZ4Compressor'};
                    """.trimIndent())
                }

            logger.info("Creating session for keyspace ${stateType.value}")

            // create new session with keyspace
            val session = CqlSession.builder()
                .withKeyspace(stateType.value)
                .build();

            return ScyllaStateManager(lifecycleCoordinatorFactory, session, MetricsRecorderImpl())
        }
    }
}
