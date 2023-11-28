package net.corda.libs.statemanager.impl.repository.impl

import net.corda.db.schema.DbSchema.STATE_MANAGER_TABLE
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.Operation
import net.corda.libs.statemanager.impl.model.v1.StateEntity.Companion.KEY_COLUMN
import net.corda.libs.statemanager.impl.model.v1.StateEntity.Companion.METADATA_COLUMN
import net.corda.libs.statemanager.impl.model.v1.StateEntity.Companion.MODIFIED_TIME_COLUMN
import net.corda.libs.statemanager.impl.model.v1.StateEntity.Companion.VALUE_COLUMN
import net.corda.libs.statemanager.impl.model.v1.StateEntity.Companion.VERSION_COLUMN

class PostgresQueryProvider : AbstractQueryProvider() {

    override val createState: String
        get() = """
            INSERT INTO $STATE_MANAGER_TABLE
            VALUES (?, ?, ?, CAST(? as JSONB), CURRENT_TIMESTAMP AT TIME ZONE 'UTC')
        """.trimIndent()

    override fun updateStates(size: Int): String = """
            UPDATE $STATE_MANAGER_TABLE AS s 
            SET 
                $KEY_COLUMN = temp.key, 
                $VALUE_COLUMN = temp.value, 
                $VERSION_COLUMN = s.$VERSION_COLUMN + 1, 
                $METADATA_COLUMN = CAST(temp.metadata as JSONB), 
                $MODIFIED_TIME_COLUMN = CURRENT_TIMESTAMP AT TIME ZONE 'UTC'
            FROM
            (
                VALUES ${List(size) { "(?, ?, ?, ?)" }.joinToString(",")}
            ) AS temp(key, value, metadata, version)
            WHERE temp.key = s.$KEY_COLUMN AND temp.version = s.$VERSION_COLUMN
            RETURNING s.$KEY_COLUMN
    """.trimIndent()

    override fun findStatesByMetadataMatchingAll(filters: Collection<MetadataFilter>) =
        """
            SELECT s.$KEY_COLUMN, s.$VALUE_COLUMN, s.$METADATA_COLUMN, s.$VERSION_COLUMN, s.$MODIFIED_TIME_COLUMN 
            FROM $STATE_MANAGER_TABLE s
            WHERE ${metadataKeyFilters(filters).joinToString(" AND ")}
        """.trimIndent()

    override fun findStatesByMetadataMatchingAny(filters: Collection<MetadataFilter>) =
        """
            SELECT s.$KEY_COLUMN, s.$VALUE_COLUMN, s.$METADATA_COLUMN, s.$VERSION_COLUMN, s.$MODIFIED_TIME_COLUMN 
            FROM $STATE_MANAGER_TABLE s
            WHERE ${metadataKeyFilters(filters).joinToString(" OR ")}
        """.trimIndent()

    override fun findStatesUpdatedBetweenAndFilteredByMetadataKey(filter: MetadataFilter): String {
        return """
            SELECT s.$KEY_COLUMN, s.$VALUE_COLUMN, s.$METADATA_COLUMN, s.$VERSION_COLUMN, s.$MODIFIED_TIME_COLUMN
            FROM $STATE_MANAGER_TABLE s
            WHERE (${metadataKeyFilter(filter)}) AND (${updatedBetweenFilter()})
        """.trimIndent()
    }

    fun metadataKeyFilters(filters: Collection<MetadataFilter>) =
        filters.map { "(${metadataKeyFilter(it)})" }

    fun metadataKeyFilter(filter: MetadataFilter) =
        "(s.$METADATA_COLUMN->>'${filter.key}')::${filter.value.toNativeType()} ${filter.operation.toNativeOperator()} '${filter.value}'"

    private fun Any.toNativeType() = when (this) {
        is String -> "text"
        is Number -> "numeric"
        is Boolean -> "boolean"
        else -> throw IllegalArgumentException("Unsupported Type: ${this::class.java.simpleName}")
    }

    private fun Operation.toNativeOperator() = when (this) {
        Operation.Equals -> "="
        Operation.NotEquals -> "<>"
        Operation.LesserThan -> "<"
        Operation.GreaterThan -> ">"
    }
}
