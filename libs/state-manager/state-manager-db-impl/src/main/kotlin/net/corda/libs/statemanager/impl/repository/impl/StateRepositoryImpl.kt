package net.corda.libs.statemanager.impl.repository.impl

import java.sql.Connection
import javax.persistence.EntityManager
import javax.persistence.Query
import net.corda.libs.statemanager.api.IntervalFilter
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.impl.model.v1.StateEntity
import net.corda.libs.statemanager.impl.repository.StateRepository
import net.corda.libs.statemanager.impl.repository.impl.PreparedStatementHelper.extractFailedKeysFromBatchResults

// TODO-[CORE-17733]: batch update and delete.
class StateRepositoryImpl(private val queryProvider: QueryProvider) : StateRepository {

    @Suppress("UNCHECKED_CAST")
    private fun Query.resultListAsStateEntityCollection() = resultList as Collection<StateEntity>

    override fun create(entityManager: EntityManager, state: StateEntity) {
        entityManager
            .createNativeQuery(queryProvider.createState)
            .setParameter(KEY_PARAMETER_NAME, state.key)
            .setParameter(VALUE_PARAMETER_NAME, state.value)
            .setParameter(VERSION_PARAMETER_NAME, state.version)
            .setParameter(METADATA_PARAMETER_NAME, state.metadata)
            .executeUpdate()
    }

    override fun get(entityManager: EntityManager, keys: Collection<String>) =
        entityManager
            .createNativeQuery(queryProvider.findStatesByKey, StateEntity::class.java)
            .setParameter(KEYS_PARAMETER_NAME, keys)
            .resultListAsStateEntityCollection()

    override fun update(connection: Connection, states: List<StateEntity>): StateRepository.StateEntityModificationResponse {
        fun getParameterIndex(currentRow:Int, index: Int) = (currentRow * 4) + index // 4 parameters in the statement
        val updatedKeys = mutableListOf<String>()
        connection.prepareStatement(queryProvider.updateStates(states)).use { stmt ->
            repeat(states.size) {
                stmt.setString(getParameterIndex(it, 1), states[it].key)
                stmt.setBytes(getParameterIndex(it, 2), states[it].value)
                stmt.setString(getParameterIndex(it, 3), states[it].metadata)
                stmt.setInt(getParameterIndex(it, 4), states[it].version)
            }
            stmt.execute()
            val results = stmt.resultSet
            while (results.next()) {
                updatedKeys.add(results.getString(1))
            }
        }
        return StateRepository.StateEntityModificationResponse(
            updatedKeys,
            states.map { it.key }.filterNot { updatedKeys.contains(it) }
        )
    }

    override fun delete(connection: Connection, states: Collection<StateEntity>): Collection<String> {
        return connection.prepareStatement(queryProvider.deleteStatesByKey).use { preparedStatement ->
            for (s in states) {
                preparedStatement.setString(1, s.key)
                preparedStatement.setInt(2, s.version)
                preparedStatement.addBatch()
            }
            val results = preparedStatement.executeBatch()
            extractFailedKeysFromBatchResults(results, states.map { it.key })
        }
    }

    override fun updatedBetween(entityManager: EntityManager, interval: IntervalFilter): Collection<StateEntity> =
        entityManager
            .createNativeQuery(queryProvider.findStatesUpdatedBetween, StateEntity::class.java)
            .setParameter(START_TIMESTAMP_PARAMETER_NAME, interval.start)
            .setParameter(FINISH_TIMESTAMP_PARAMETER_NAME, interval.finish)
            .resultListAsStateEntityCollection()

    override fun filterByAll(entityManager: EntityManager, filters: Collection<MetadataFilter>) =
        entityManager
            .createNativeQuery(queryProvider.findStatesByMetadataMatchingAll(filters), StateEntity::class.java)
            .resultListAsStateEntityCollection()

    override fun filterByAny(entityManager: EntityManager, filters: Collection<MetadataFilter>) =
        entityManager
            .createNativeQuery(queryProvider.findStatesByMetadataMatchingAny(filters), StateEntity::class.java)
            .resultListAsStateEntityCollection()

    override fun filterByUpdatedBetweenAndMetadata(
        entityManager: EntityManager,
        interval: IntervalFilter,
        filter: MetadataFilter
    ) = entityManager
        .createNativeQuery(
            queryProvider.findStatesUpdatedBetweenAndFilteredByMetadataKey(filter),
            StateEntity::class.java
        )
        .setParameter(START_TIMESTAMP_PARAMETER_NAME, interval.start)
        .setParameter(FINISH_TIMESTAMP_PARAMETER_NAME, interval.finish)
        .resultListAsStateEntityCollection()
}
