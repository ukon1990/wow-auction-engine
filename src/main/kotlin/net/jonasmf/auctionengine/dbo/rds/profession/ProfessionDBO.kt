package net.jonasmf.auctionengine.dbo.rds.profession

import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import net.jonasmf.auctionengine.dbo.rds.LocaleDBO

@Entity
@Table(name = "profession_category")
class ProfessionCategoryDBO(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val internalId: Long? = null,
    @OneToOne(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    val name: LocaleDBO,
    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "profession_category_id")
    val recipes: MutableList<RecipeDBO> = mutableListOf(),
)

@Entity
@Table(name = "skill_tier")
class SkillTierDBO(
    @Id
    val id: Int,
    @OneToOne(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    val name: LocaleDBO,
    val minimumSkillLevel: Int,
    val maximumSkillLevel: Int,
    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "skill_tier_id")
    val categories: MutableList<ProfessionCategoryDBO> = mutableListOf(),
)

@Entity
@Table(name = "profession")
class ProfessionDBO(
    @Id
    val id: Int,
    @OneToOne(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    val name: LocaleDBO,
    @OneToOne(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    val description: LocaleDBO,
    val mediaUrl: String,
    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "profession_id")
    val skillTiers: MutableList<SkillTierDBO> = mutableListOf(),
)
