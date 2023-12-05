package net.corda.crypto.merkle.impl

import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.extensions.merkle.MerkleTreeHashDigestProvider
import net.corda.v5.crypto.merkle.IndexedMerkleLeaf
import net.corda.v5.crypto.merkle.MerkleProof
import net.corda.v5.crypto.merkle.MerkleProof.LeveledHash
import net.corda.v5.crypto.merkle.MerkleProofType
import kotlin.math.max

class LoadedMerkleProofTree(
    // index to data (index stored in the database)
    // I'll need the nonce of the indexed merkle leaf, so i might as well store this in the database as an indexed merkle leaf
//    private val loadedLeaves: List<Pair<Int, ByteArray>>,
    private val loadedLeaves: List<IndexedMerkleLeaf>,
    private val loadedHashes: List<LeveledHash>,
    private val treeSize: Int,
    private val digest: MerkleTreeHashDigestProvider
) {

//    init {
//        require(leaves.isNotEmpty()) { "Merkle tree must have at least one item" }
//    }

    // index to hash
//    private val leafHashes: Map<Int, SecureHash> by lazy(LazyThreadSafetyMode.PUBLICATION) {
//        loadedLeaves.associate { (index, bytes) ->
//            val nonce = digest.leafNonce(index)
//            index to digest.leafHash(index, nonce, bytes)
//        }
//    }
    private val leafHashes: Map<Int, SecureHash> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        loadedLeaves.associate { leaf ->
            leaf.index to digest.leafHash(leaf.index, leaf.nonce, leaf.leafData)
        }
    }

    private val loadedLeavesMap: Map<Int, IndexedMerkleLeaf> = loadedLeaves.associateBy { leaf -> leaf.index }

    private val loadedHashesMap: Map<Pair<Int, Int>, SecureHash> = loadedHashes.associate { leveledHash ->
        (leveledHash.level to leveledHash.index) to leveledHash.hash
    }

    private val loadedHashesPerLevel: Map<Int, List<LeveledHash>> = loadedHashes.groupBy { hash -> hash.level }

    private val loadedHashesMaxIndexPerLevel: Map<Int, Int> = loadedHashesPerLevel
        .mapValues { (_, hashes) -> hashes.maxOf { hash -> hash.index } }

    /**
     * We calculate the tree's elements here from starting with the lowest level and progressing level by level towards
     * the root element.
     *
     * hashes contain the current level what we are process
     * nodeHashes contains the next level what we are calculating now
     * hashSet will be the result
     *
     * If any level has odd number of elements, the last one just gets lifted to the next level without
     * more hashing.
     */
    val nodeHashes: List<Map<Int, SecureHash>> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        var depthCounter = depth
        val hashSet = mutableListOf<Map<Int, SecureHash>>()
        // normal leaf hashes in the order they would be in a normal tree/proof
        var hashes = leafHashes
        hashSet += hashes
        var numberOfHashesForCurrentLevel = max(hashes.keys.max(), (loadedHashesMaxIndexPerLevel[depthCounter - 1] ?: 0)) + 1
        while (numberOfHashesForCurrentLevel > 1) {
            --depthCounter
            val nodeHashes = mutableMapOf<Int, SecureHash>()
            var i = 0
            var j = 0
            while (i < numberOfHashesForCurrentLevel) {
                if (i <= numberOfHashesForCurrentLevel - 2) {
                    val first = loadedHashesMap[depthCounter to i] ?: hashes[i]
                    val second = loadedHashesMap[depthCounter to i + 1] ?: hashes[i + 1]
                    if (first != null && second != null) {
                        nodeHashes[j] = digest.nodeHash(depthCounter, first, second)
                    }
                    j++
                }
                i += 2
            }
            if ((numberOfHashesForCurrentLevel and 1) == 1) { // Non-paired last elements of odd lists, just get lifted one level upper.
                nodeHashes[j] = hashes[hashes.keys.max()]!!
            }
            hashes = nodeHashes
            hashSet += hashes

            // check that we either have hashes to calculate or have loaded hashes on the next level
            numberOfHashesForCurrentLevel = hashes.size + (loadedHashesPerLevel[depthCounter - 1]?.size ?: 0)
        }
        require(depthCounter == 0) { "Sanity check root is at depth 0" }

        // put the loaded hashes into the output hashset
        val result = mutableListOf<Map<Int, SecureHash>>()
        var hashSetIndex = 0
        depthCounter = depth
        while (depthCounter >= 0) {
            --depthCounter
            numberOfHashesForCurrentLevel = max(hashSet[hashSetIndex].keys.max(), (loadedHashesMaxIndexPerLevel[depthCounter] ?: 0)) + 1
            var i = 0
            val toAdd = mutableMapOf<Int, SecureHash>()
            while (i < numberOfHashesForCurrentLevel) {
                loadedHashesMap[depthCounter to i]?.let { toAdd[i] = it }
                    ?: hashSet[hashSetIndex][i]?.let { toAdd[i] = it }
                i++
            }
            result += toAdd
            hashSetIndex++
        }
        result
    }

    private val depth: Int = MerkleTreeImpl.treeDepth(treeSize)

    private val _root: SecureHash by lazy(LazyThreadSafetyMode.PUBLICATION) {
        nodeHashes.last().values.single()
    }

    /**
     * createAuditProof creates the proof for a set of leaf indices.
     * Similarly to the Merkle tree building it walks through the tree from the leaves towards the root.
     * The proof contains
     * - the data what we are proving with their positions and nonce. (leaves)
     * - enough hashes of the other elements to be able to reconstruct the tree.
     * - the size of the tree.
     *
     * The extra hashes' order is quite important to fill the gaps between the subject elements.
     *
     * We'll need to calculate the node's hashes on the routes from the subject elements
     * towards the tree's root element in the verification process.
     * We'll mark the indices of these route elements in inPath for the level what we are processing.
     * Through the processing of a level we'll add a set of hashes to the proof, and we'll calculate the next
     * level's in route elements (next iteration's inPath) in the newInPath variable.
     *
     * When we process a level of the tree, we pair the elements, and we'll have these cases:
     * - Both elements of the pair are in route, which means they'll be calculated in the verification, so
     *   their parent on the next level will be in route as well, and we do not need to add their hashes.
     * - None of the elements of the pair are in route, which means their parent won't be either, also
     *   we do not need to add their hashes, since their parent hash will be enough to cover them.
     * - Only one of the elements are in route, which means their parent will be in route, and also we need to
     *   add the other element's hash to the proof.
     *
     * If a level has odd number of elements, then the last element is essentially the both or none case.
     * So we do not need to add its hash, and its parent node will be on the route if and only if it was on route.
     */
    @Suppress("NestedBlockDepth", "ComplexMethod")
    fun createAuditProof(leafIndices: List<Int>): MerkleProof {
        require(leafIndices.isNotEmpty()) { "Proof requires at least one leaf" }
        val loadedLeafIndexes = loadedLeavesMap.keys
        require(loadedLeafIndexes.containsAll(leafIndices)) { "Leaf indices out of bounds (have not stored a requested index)" }
        require(leafIndices.toSet().size == leafIndices.size) { "Duplications are not allowed." }

        var level = 0
        var inPath = List(treeSize) { it in leafIndices }
        val outputHashes = mutableListOf<SecureHash>()
        while (inPath.size > 1) {
            val newInPath = mutableListOf<Boolean>()            // This will contain the next
            // level's in route element's
            for (i in inPath.indices step 2) {
                if (i <= inPath.size - 2) {                     // We still have a pair to process.
                    newInPath += inPath[i] || inPath[i + 1]     // If any are in route, then their parent will be too
                    if (!inPath[i] && inPath[i + 1]) {          // We need to add a hash for the "Only one" cases.
                        nodeHashes[level][i]?.let { outputHashes += it }
                    } else if (inPath[i] && !inPath[i + 1]) {
                        nodeHashes[level][i + 1]?.let { outputHashes += it }
                    }
                }
            }
            if ((inPath.size and 1) == 1) {                     // If the level has odd number of elements,
                // the last one is still to be processed.
                newInPath += inPath.last()
            }
            inPath = newInPath
            ++level
        }
        require(level == MerkleTreeImpl.treeDepth(treeSize)) { "Sanity check calc" }
        return MerkleProofImpl(
            MerkleProofType.AUDIT,
            treeSize,
            leafIndices.sorted().map {
                loadedLeavesMap[it]!!.let { leaf ->
                    IndexedMerkleLeafImpl(it, leaf.nonce, leaf.leafData.copyOf())
                }
            },
            outputHashes
        )
    }
}
