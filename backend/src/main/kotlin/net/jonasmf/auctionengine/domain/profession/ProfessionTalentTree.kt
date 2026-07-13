package net.jonasmf.auctionengine.domain.profession

data class ProfessionTalentNode(
    val externalNodeId: Int,
    val name: String,
    val maxRanks: Int,
    val entries: List<ProfessionTalentEntry>,
)

data class ProfessionTalentEntry(
    val externalEntryId: Int,
    val name: String,
    val rankLimit: Int,
)
