package net.corda.processors.crypto.internal

import net.corda.virtualnode.read.VirtualNodeInfoReadService
import java.util.logging.Logger

data class RotateReport(
    val numberOfTenants: Int,
)

fun rotate(logger: Logger, virtualNodeInfoReadService: VirtualNodeInfoReadService): RotateReport {
    val virtualNodeInfoList = virtualNodeInfoReadService.getAll()
    logger.info("Have ${virtualNodeInfoList.size} nodes to rotate")
    return RotateReport(virtualNodeInfoList.size)
}