package net.jonasmf.auctionengine.repository.rds

import net.jonasmf.auctionengine.generated.model.NormalizedAuctionHelperTalentNode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TalentParentInferenceTest {
    @Test
    fun `infers three-leaf branch hubs from export order when parents are missing`() {
        val nodes =
            listOf(
                node(107757, "Nature's Novelties"),
                node(107758, "Worldsoul Wards"),
                node(107759, "Azerothian Arms"),
                node(107760, "Haranir Heightening"),
                node(107761, "Trollish Tools"),
                node(107762, "Berserker Brawn"),
                node(107763, "Zul'Aman Zeal"),
                node(107764, "Amani Augments"),
                node(107765, "Quel'Thalas Quality"),
                node(107766, "Eversong Empowerments"),
                node(107767, "Silvermoon's Spellpower"),
                node(107768, "Thalassian Talents"),
                node(107769, "Elevating Equipment"),
            )

        val parents = inferParentNodeIdsFromExportOrder(nodes)

        assertThat(parents[107757]).containsExactly(107760)
        assertThat(parents[107758]).containsExactly(107760)
        assertThat(parents[107759]).containsExactly(107760)
        assertThat(parents[107760]).containsExactly(107769)
        assertThat(parents[107761]).containsExactly(107764)
        assertThat(parents[107768]).containsExactly(107769)
    }

    @Test
    fun `keeps explicit parent relationships when present`() {
        val nodes =
            listOf(
                node(104229, "Craftsmithing"),
                node(104230, "Concentration", listOf(104231)),
                node(104231, null, listOf(104229)),
            )

        assertThat(resolveTalentParentNodeIds(nodes))
            .containsEntry(104230, listOf(104231))
            .containsEntry(104231, listOf(104229))
    }

    private fun node(
        id: Int,
        name: String?,
        parents: List<Int> = emptyList(),
    ) = NormalizedAuctionHelperTalentNode(
        nodeId = id,
        name = name,
        parentNodeIds = parents,
        propertyEntries = emptyList(),
    )
}
