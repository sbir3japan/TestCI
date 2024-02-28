package net.corda.libs.statemanager.impl.tests

import net.corda.libs.statemanager.api.CompressionType
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.Operation
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.metadata
import net.corda.libs.statemanager.impl.factory.StateManagerFactoryImpl
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.schema.configuration.StateManagerConfig
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.UUID

class Scylla {
    private val lifecycleCoordinator = mock<LifecycleCoordinator>()
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), any()) }.doReturn(lifecycleCoordinator)
    }

    private val stateManager by lazy {
        val smf = StateManagerFactoryImpl(lifecycleCoordinatorFactory)
        smf.create(mock(), StateManagerConfig.StateType.FLOW_CHECKPOINT, CompressionType.NONE)
    }

    @Test
    fun createSm() {
        val smf = StateManagerFactoryImpl(lifecycleCoordinatorFactory)
        smf.create(mock(), StateManagerConfig.StateType.FLOW_CHECKPOINT, CompressionType.NONE)
    }

    @Test
    fun testCreateGetUpdateDelete() {
        val key1 = "sk-${UUID.randomUUID()}"
        val state1 = State(key1, key1.plus("_state").toByteArray())
        val state15 = State(key1, key1.plus("_state_changed").toByteArray())
        val key2 = "sk-${UUID.randomUUID()}"
        val state2 = State(key2, key2.plus("_state").toByteArray())
        val state25 = State(key2, key2.plus("_state_changed").toByteArray())

        assertThat(stateManager.create(listOf(state1, state2))).isEmpty()
        assertThat(stateManager.create(listOf(state15))).containsOnly(state15.key)

        val retrievedStates = stateManager.get(listOf(key1, key2))
        assertSoftly {
            it.assertThat(retrievedStates.size).isEqualTo(2)

            it.assertThat(retrievedStates[key1]!!.value).isEqualTo(state1.value)
            it.assertThat(retrievedStates[key2]!!.value).isEqualTo(state2.value)
        }

        // simulate version conflict
        val state25_copy = state25.copy(version = state25.version + 1)

        val update = stateManager.update(listOf(state15, state25_copy))
        assertThat(update).containsOnlyKeys(state25.key)
        val retrievedUpdatedStates = stateManager.get(listOf(key1, key2))
        assertSoftly {
            it.assertThat(retrievedUpdatedStates.size).isEqualTo(2)

            it.assertThat(retrievedUpdatedStates[key1]!!.value).isEqualTo(state15.value)
            it.assertThat(retrievedUpdatedStates[key2]!!.value).isEqualTo(state2.value)
        }

        val deletes = stateManager.delete(listOf(retrievedUpdatedStates[key1]!!, state25_copy))
        assertThat(deletes).containsOnlyKeys(state25.key)
        val retrievedDeletedStates = stateManager.get(listOf(key1, key2))
        assertSoftly {
            it.assertThat(retrievedDeletedStates.size).isEqualTo(1)
            it.assertThat(retrievedDeletedStates).doesNotContainKey(key1)
        }
    }

    @Test
    fun `can filter states using multiple conjunctive comparisons on metadata values`() {
        val count = 20
        val states = (0..count).map {
            State(
                "sk-${UUID.randomUUID()}",
                "sv-${UUID.randomUUID()}".toByteArray(),
                metadata = metadata(
                    "number" to it,
                    "boolean" to (it % 2 == 0),
                    "string" to "random_$it"
                )
            )
        }
        stateManager.create(states)

        assertThat(
            stateManager.findByMetadataMatchingAll(
                listOf(
                    MetadataFilter("number", Operation.GreaterThan, 5),
                    MetadataFilter("number", Operation.LesserThan, 7),
                    MetadataFilter("boolean", Operation.Equals, true),
                    MetadataFilter("string", Operation.Equals, "random_6"),
                )
            )
        ).hasSize(1)

        assertThat(
            stateManager.findByMetadataMatchingAll(
                listOf(
                    MetadataFilter("number", Operation.GreaterThan, 5),
                    MetadataFilter("number", Operation.LesserThan, 7),
                    MetadataFilter("boolean", Operation.Equals, true),
                    MetadataFilter("string", Operation.Equals, "non_existing_value"),
                )
            )
        ).isEmpty()

        assertThat(
            stateManager.findByMetadataMatchingAll(
                listOf(
                    MetadataFilter("number", Operation.GreaterThan, 0),
                    MetadataFilter("boolean", Operation.Equals, true),
                )
            )
        ).hasSize(count / 2)

        assertThat(
            stateManager.findByMetadataMatchingAll(
                listOf(
                    MetadataFilter("number", Operation.NotEquals, 0),
                    MetadataFilter("string", Operation.Equals, "non_existing_key"),
                )
            )
        ).isEmpty()
    }
}