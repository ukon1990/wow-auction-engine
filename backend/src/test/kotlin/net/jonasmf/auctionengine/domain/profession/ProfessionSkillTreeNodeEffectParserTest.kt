package net.jonasmf.auctionengine.domain.profession

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ProfessionSkillTreeNodeEffectParserTest {
    @Test
    fun `parses gain skill milestone descriptions`() {
        val effect =
            ProfessionSkillTreeNodeEffectParser.parseDescription(
                "Gain +5 Skill when crafting waist armor.",
            )

        assertThat(effect?.skillBonus).isEqualTo(5)
        assertThat(effect?.craftingCategory).isEqualTo("waist armor")
    }

    @Test
    fun `parses compact plus skill descriptions`() {
        val effect = ProfessionSkillTreeNodeEffectParser.parseDescription("+10 Blacksmithing Skill")

        assertThat(effect?.skillBonus).isEqualTo(10)
        assertThat(effect?.craftingCategory).isNull()
    }

    @Test
    fun `returns null for unrelated descriptions`() {
        assertThat(ProfessionSkillTreeNodeEffectParser.parseDescription("Consume 5% less Concentration.")).isNull()
    }
}
