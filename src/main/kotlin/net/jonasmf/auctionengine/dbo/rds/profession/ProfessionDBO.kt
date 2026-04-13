package net.jonasmf.auctionengine.dbo.rds.profession

import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.OneToOne
import net.jonasmf.auctionengine.dbo.rds.LocaleDBO
import net.jonasmf.auctionengine.domain.profession.SkillTier

@Entity
class ProfessionCategory(
    val id: Int,
    @OneToOne(cascade = [CascadeType.ALL])
    val name: LocaleDBO,
    val recipes: RecipeDBO,
)

@Entity
class SkillTierDBO(
    @Id
    val id: Long,
    @OneToOne(cascade = [CascadeType.ALL])
    val name: LocaleDBO,
    val minimumSkillLevel: Int,
    val maximumSkillLevel: Int,
)

@Entity
data class ProfessionDBO(
    val id: Int,
    @OneToOne(cascade = [CascadeType.ALL])
    val name: LocaleDBO,
    @OneToOne(cascade = [CascadeType.ALL])
    val description: LocaleDBO,
    val mediaUrl: String,
    @OneToOne(cascade = [CascadeType.ALL])
    val skillTiers: MutableList<SkillTier>,
)
