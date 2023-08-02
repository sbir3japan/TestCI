package net.corda.processors.db.internal.reconcile.db

import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepositoryImpl
import net.corda.reconciliation.VersionedRecord
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import java.util.stream.Stream

/**
 * Gets and converts the database entity classes to 'Corda' classes
 */
@Suppress("warnings")
val getAllVirtualNodesDBVersionedRecords
        : (ReconciliationContext) -> Stream<VersionedRecord<HoldingIdentity, VirtualNodeInfo>> =
    { context ->
        val em = context.getOrCreateEntityManager()

        VirtualNodeRepositoryImpl()
            .findAll(em)
            .map { entity ->
                object : VersionedRecord<HoldingIdentity, VirtualNodeInfo> {
                    override val version = entity.version
                    override val isDeleted = entity.isDeleted
                    override val key = entity.holdingIdentity
                    override val value = entity
                } as VersionedRecord<HoldingIdentity, VirtualNodeInfo>
            }.onClose {
                context.close()
            }
    }
