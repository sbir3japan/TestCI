package net.corda.virtualnode.read.impl

import net.corda.crypto.core.ShortHash
import net.corda.data.identity.HoldingIdentity
import net.corda.data.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.toCorda
import java.util.concurrent.ConcurrentHashMap

/**
 * Map of [HoldingIdentity] to [VirtualNodeInfo] AVRO data objects
 *
 * We use the [toCorda()] methods to convert the Avro objects to Corda ones.
 *
 * This class is nothing more than two maps with different keys.
 */
internal class VirtualNodeInfoMap {
    private val virtualNodeInfoByHoldingIdentity = ConcurrentHashMap<HoldingIdentity, VirtualNodeInfo>()
    private val virtualNodeInfoById = ConcurrentHashMap<ShortHash, VirtualNodeInfo>()

    /** Class to be used as a key for putting items. */
    data class Key(val holdingIdentity: HoldingIdentity, val holdingIdShortHash: ShortHash)

    /** Get everything as Corda objects NOT Avro objects */
    @Synchronized
    fun getAllAsCordaObjects(): Map<net.corda.virtualnode.HoldingIdentity, net.corda.virtualnode.VirtualNodeInfo> =
        virtualNodeInfoByHoldingIdentity.asIterable().associate { (key, value) -> key.toCorda() to value.toCorda() }

    /** Put (store/merge) the incoming map.  May throw [IllegalArgumentException] */
    @Synchronized
    fun putAll(incoming: Map<Key, VirtualNodeInfo>) = incoming.forEach { (key, value) -> put(key, value) }

    /** Putting a null value removes the [VirtualNodeInfo] from the maps. May throw [IllegalArgumentException] */
    @Synchronized
    fun put(key: Key, value: VirtualNodeInfo?) {
        if (value == null) {
            remove(key)
        } else {
            validatePut(key, value)
            virtualNodeInfoById[key.holdingIdShortHash] = value
            virtualNodeInfoByHoldingIdentity[key.holdingIdentity] = value
        }
    }

    private fun validatePut(key: Key, value: VirtualNodeInfo) {
        // The following are checks that "should never occur in production", i.e.
        // that the holding identity 'key' matches the holding identity in the 'value'.
        // Whoever posts (HoldingIdentity, VirtualNodeInfo) on to Kakfa, should have used:
        // (virtualNodeInfo.holdingIdentity, virtualNodeInfo).
        require(key.holdingIdentity == value.holdingIdentity) {
            "Trying to add a VirtualNodeInfo with a mismatched HoldingIdentity: ($key , $value)"
        }
        val existingHoldingIdentity = virtualNodeInfoById[key.holdingIdShortHash]?.holdingIdentity
        require(existingHoldingIdentity == null || existingHoldingIdentity == value.holdingIdentity) {
            "Cannot put different VirtualNodeInfo for same short hash value: (${key.holdingIdShortHash}, $key , $value)"
        }
    }

    /** Get list of [VirtualNodeInfo] for all virtual nodes. Returns an empty list if no virtual nodes are onboarded. */
    @Synchronized
    fun getAll(): List<VirtualNodeInfo> = virtualNodeInfoById.values.toList()

    /** Get a [VirtualNodeInfo] by [HoldingIdentity], `null` if not found. */
    @Synchronized
    fun get(holdingIdentity: HoldingIdentity): VirtualNodeInfo? = virtualNodeInfoByHoldingIdentity[holdingIdentity]

    /** Get a [VirtualNodeInfo] by short hash ([net.corda.virtualnode.HoldingIdentity.shortHash]), `null` if not found */
    @Synchronized
    fun getById(holdingIdShortHash: ShortHash): VirtualNodeInfo? = virtualNodeInfoById[holdingIdShortHash]

    /** Remove the [VirtualNodeInfo] from this collection and return it. */
    @Synchronized
    fun remove(key: Key): VirtualNodeInfo? {
        virtualNodeInfoById.remove(key.holdingIdShortHash)
        return virtualNodeInfoByHoldingIdentity.remove(key.holdingIdentity)
    }

    /** Clear all the content */
    @Synchronized
    fun clear() {
        virtualNodeInfoById.clear()
        virtualNodeInfoByHoldingIdentity.clear()
    }
}
