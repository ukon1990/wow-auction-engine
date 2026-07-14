package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperTalentNode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NormalizedProfessionTreeRelationshipsTest {
    @Test
    fun `unnamed structural hubs remain visible parents for their children`() {
        val root = node(1, "Foundations")
        val hub = node(2, null, listOf(1), maxRanks = 30)
        val child = node(3, "Armorsmithing", listOf(2))

        assertThat(resolvedVisibleParentNodeIds(child.nodeId, listOf(root, hub, child)))
            .containsExactly(2)
    }

    @Test
    fun `unnamed structural parents resolve to their nearest visible ancestors`() {
        val root = node(1, "Foundations")
        val structural = node(2, null, listOf(1))
        val child = node(3, "Armorsmithing", listOf(2))

        assertThat(resolvedVisibleParentNodeIds(child.nodeId, listOf(root, structural, child)))
            .containsExactly(1)
    }

    @Test
    fun `cycles between unnamed structural nodes terminate safely`() {
        val first = node(1, null, listOf(2))
        val second = node(2, null, listOf(1))

        assertThat(resolvedVisibleParentNodeIds(first.nodeId, listOf(first, second))).isEmpty()
    }

    private fun node(
        id: Int,
        name: String?,
        parents: List<Int> = emptyList(),
        maxRanks: Int = 1,
    ) = NormalizedAuctionHelperTalentNode(
        nodeId = id,
        name = name,
        maxRanks = maxRanks,
        parentNodeIds = parents,
        propertyEntries = emptyList(),
    )
}
