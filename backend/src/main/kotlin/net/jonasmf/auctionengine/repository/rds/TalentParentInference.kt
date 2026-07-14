package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperTalentNode

internal fun resolveTalentParentNodeIds(
    nodes: List<NormalizedAuctionHelperTalentNode>,
): Map<Int, List<Int>> {
    if (nodes.any { !it.parentNodeIds.isNullOrEmpty() }) {
        return nodes
            .filter { !it.parentNodeIds.isNullOrEmpty() }
            .associate { it.nodeId to it.parentNodeIds.orEmpty() }
    }
    return inferParentNodeIdsFromExportOrder(nodes)
}

internal fun inferParentNodeIdsFromExportOrder(
    nodes: List<NormalizedAuctionHelperTalentNode>,
): Map<Int, List<Int>> {
    val named =
        nodes
            .mapIndexed { index, node -> index to node }
            .filter { (_, node) -> !node.name.isNullOrBlank() }
    if (named.size < 2) return emptyMap()

    val result = linkedMapOf<Int, List<Int>>()
    val root = named.last().second
    var cursor = named.size - 2
    val hubs = mutableListOf<NormalizedAuctionHelperTalentNode>()

    while (cursor >= 0) {
        val hub = named[cursor].second
        hubs.add(0, hub)
        val leafStart = maxOf(0, cursor - 3)
        for (index in leafStart until cursor) {
            result[named[index].second.nodeId] = listOf(hub.nodeId)
        }
        cursor -= 4
    }

    hubs.forEach { hub ->
        result[hub.nodeId] = listOf(root.nodeId)
    }

    return result
}

internal fun nodesWithResolvedParents(
    nodes: List<NormalizedAuctionHelperTalentNode>,
): List<NormalizedAuctionHelperTalentNode> {
    val parentNodeIds = resolveTalentParentNodeIds(nodes)
    if (parentNodeIds.isEmpty()) return nodes
    return nodes.map { node ->
        val parents = parentNodeIds[node.nodeId]
        if (parents.isNullOrEmpty()) {
            node
        } else {
            node.copy(parentNodeIds = parents)
        }
    }
}
