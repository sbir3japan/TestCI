package net.corda.libs.statemanager.impl.repository.impl

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.libs.statemanager.api.IntervalFilter
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.impl.model.v1.resultSetAsStateCollection
import net.corda.libs.statemanager.impl.repository.StateRepository
import java.sql.Connection
import java.sql.Timestamp

class StateRepositoryImpl(private val queryProvider: QueryProvider) : StateRepository {

    private companion object {
        private const val CREATE_RESULT_COLUMN_INDEX = 1
        private val objectMapper = ObjectMapper()
    }

    override fun create(connection: Connection, states: Collection<State>): Collection<String> {
        if (states.isEmpty()) return emptySet()
        return connection.prepareStatement(queryProvider.createStates(states.size)).use { statement ->
            val statesOrdered = states.toList()
            statesOrdered.forEach { state ->
                statement.setString(1, state.key)
                statement.setBytes(2, state.value)
                statement.setInt(3, state.version)
                statement.setString(4, objectMapper.writeValueAsString(state.metadata))
                statement.addBatch()
            }

            statement.executeBatch().zip(statesOrdered).mapNotNull {(count, state) ->
                if (count > 0) {
                    state.key
                } else {
                    null
                }
            }.toList()
        }
//        return states.chunked(10).map { subStates ->
//            connection.prepareStatement(queryProvider.createStates(subStates.size)).use { statement ->
//                val indices = generateSequence(1) { it + 1 }.iterator()
//                subStates.forEach { state ->
//                    statement.setString(indices.next(), state.key)
//                    statement.setBytes(indices.next(), state.value)
//                    statement.setInt(indices.next(), state.version)
//                    statement.setString(indices.next(), objectMapper.writeValueAsString(state.metadata))
//                }
//                statement.execute()
//                val results = statement.resultSet
//                sequence<String> {
//                    while (results.next()) {
//                        yield(results.getString(CREATE_RESULT_COLUMN_INDEX))
//                    }
//                }.toList()
//            }
//        }.flatten()
    }

    override fun get(connection: Connection, keys: Collection<String>): Collection<State> {
        if (keys.isEmpty()) return emptySet()
        return connection.prepareStatement(queryProvider.findStatesByKey(keys.size)).use {
            keys.forEachIndexed { index, key ->
                it.setString(index + 1, key)
            }

            it.executeQuery().resultSetAsStateCollection(objectMapper)
        }
    }

    override fun update(connection: Connection, states: Collection<State>): StateRepository.StateUpdateSummary {
        if (states.isEmpty()) return StateRepository.StateUpdateSummary(emptyList(), emptyList())
        return connection.prepareStatement(queryProvider.updateStates(states.size)).use { statement ->
            val statesOrdered = states.toList()
            statesOrdered.forEach { state ->
                statement.setBytes(1, state.value)
                statement.setString(2, objectMapper.writeValueAsString(state.metadata))
                statement.setString(3, state.key)
                statement.setInt(4, state.version)
                statement.addBatch()
            }
            val (succeeded, failed) = statement.executeBatch().zip(statesOrdered)
                .partition { (count, _) -> count > 0 }
            StateRepository.StateUpdateSummary(succeeded.map { it.second.key }, failed.map { it.second.key} )
        }
//        val updatedKeys = mutableListOf<String>()
//        states.chunked(1).forEach { subStates ->
//            val indices = generateSequence(1) { it + 1 }.iterator()
//            connection.prepareStatement(queryProvider.updateStates(subStates.size)).use { stmt ->
//                subStates.forEach { state ->
//                    stmt.setString(indices.next(), state.key)
//                    stmt.setBytes(indices.next(), state.value)
//                    stmt.setString(indices.next(), objectMapper.writeValueAsString(state.metadata))
//                    stmt.setInt(indices.next(), state.version)
//                }
//                stmt.execute()
//                val results = stmt.resultSet
//                while (results.next()) {
//                    updatedKeys.add(results.getString(1))
//                }
//            }
//        }
//        return StateRepository.StateUpdateSummary(
//            updatedKeys,
//            states.map { it.key }.filterNot { updatedKeys.contains(it) }
//        )
    }

    override fun delete(connection: Connection, states: Collection<State>): Collection<String> {
        if (states.isEmpty()) return emptySet()
        return states.chunked(10).map { subStates ->
            connection.prepareStatement(queryProvider.deleteStatesByKey).use { statement ->
                // The actual state order doesn't matter, but we must ensure that the states are iterated over in the same
                // order when examining the result as when the statements were generated.
                val statesOrdered = subStates.toList()
                statesOrdered.forEach { state ->
                    statement.setString(1, state.key)
                    statement.setInt(2, state.version)
                    statement.addBatch()
                }
                // For the delete case, it's safe to return anything other than a row update count of 1 as failed. The state
                // manager must check any returned failed deletes regardless to verify that the call did not request
                // removal of a state that never existed.
                statement.executeBatch().zip(statesOrdered).mapNotNull { (count, state) ->
                    if (count <= 0) {
                        state.key
                    } else {
                        null
                    }
                }.toList()
            }
        }.flatten()
    }

    override fun updatedBetween(connection: Connection, interval: IntervalFilter): Collection<State> =
        connection.prepareStatement(queryProvider.findStatesUpdatedBetween).use {
            it.setTimestamp(1, Timestamp.from(interval.start))
            it.setTimestamp(2, Timestamp.from(interval.finish))
            it.executeQuery().resultSetAsStateCollection(objectMapper)
        }

    override fun filterByAll(connection: Connection, filters: Collection<MetadataFilter>) =
        connection.prepareStatement(queryProvider.findStatesByMetadataMatchingAll(filters)).use {
            it.executeQuery().resultSetAsStateCollection(objectMapper)
        }

    override fun filterByAny(connection: Connection, filters: Collection<MetadataFilter>) =
        connection.prepareStatement(queryProvider.findStatesByMetadataMatchingAny(filters)).use {
            it.executeQuery().resultSetAsStateCollection(objectMapper)
        }

    override fun filterByUpdatedBetweenWithMetadataMatchingAll(
        connection: Connection,
        interval: IntervalFilter,
        filters: Collection<MetadataFilter>
    ) = connection.prepareStatement(queryProvider.findStatesUpdatedBetweenWithMetadataMatchingAll(filters)).use {
        it.setTimestamp(1, Timestamp.from(interval.start))
        it.setTimestamp(2, Timestamp.from(interval.finish))
        it.executeQuery().resultSetAsStateCollection(objectMapper)
    }

    override fun filterByUpdatedBetweenWithMetadataMatchingAny(
        connection: Connection,
        interval: IntervalFilter,
        filters: Collection<MetadataFilter>
    ) = connection.prepareStatement(queryProvider.findStatesUpdatedBetweenWithMetadataMatchingAny(filters)).use {
        it.setTimestamp(1, Timestamp.from(interval.start))
        it.setTimestamp(2, Timestamp.from(interval.finish))
        it.executeQuery().resultSetAsStateCollection(objectMapper)
    }
}
