package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.generated.model.ProfessionAllocation
import net.jonasmf.auctionengine.generated.model.ProfessionProfileRequest
import net.jonasmf.auctionengine.generated.model.ProfileCharacter
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dto.profession.CharacterProfessionDTO
import net.jonasmf.auctionengine.dto.profession.CharacterProfessionReferenceDTO
import net.jonasmf.auctionengine.dto.profession.CharacterProfessionTierDTO
import net.jonasmf.auctionengine.dto.profession.CharacterProfessionsDTO
import net.jonasmf.auctionengine.integration.blizzard.CharacterProfessionApiClient
import net.jonasmf.auctionengine.repository.rds.ProfileRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class ProfileServiceTest {
    private val repository = mock(ProfileRepository::class.java)
    private val characterProfessionApiClient = mock(CharacterProfessionApiClient::class.java)
    private val service = ProfileService(repository, characterProfessionApiClient)

    @Test
    fun `maps Blizzard character professions to a preview without persistence`() {
        `when`(characterProfessionApiClient.getProfessions(Region.Europe, "draenor", "Owner")).thenReturn(
            CharacterProfessionsDTO(
                primaries =
                    listOf(
                        CharacterProfessionDTO(
                            profession = CharacterProfessionReferenceDTO(164, "Blacksmithing"),
                            tiers =
                                listOf(
                                    CharacterProfessionTierDTO(
                                        skillPoints = 100,
                                        maxSkillPoints = 100,
                                        tier = CharacterProfessionReferenceDTO(2822, "Dragon Isles Blacksmithing"),
                                        knownRecipes = listOf(CharacterProfessionReferenceDTO(367595, "Primal Molten Longsword")),
                                    ),
                                ),
                        ),
                    ),
            ),
        )

        val preview = service.getCharacterProfessionPreview("eu", "Draenor", "Owner")

        assertThat(preview.realmSlug).isEqualTo("draenor")
        assertThat(preview.professions.single().tiers.single().knownRecipes.single().recipeName).isEqualTo("Primal Molten Longsword")
        verify(characterProfessionApiClient).getProfessions(Region.Europe, "draenor", "Owner")
    }

    @Test
    fun `rejects malformed realm slugs before calling Blizzard`() {
        assertThatThrownBy {
            service.getCharacterProfessionPreview("eu", "draenor/other", "Owner")
        }.isInstanceOf(ResponseStatusException::class.java)
            .extracting { (it as ResponseStatusException).statusCode }
            .isEqualTo(HttpStatus.BAD_REQUEST)

        verifyNoInteractions(characterProfessionApiClient)
    }

    @Test
    fun `rejects overlong character lookup values before calling Blizzard`() {
        assertThatThrownBy {
            service.getCharacterProfessionPreview("eu", "a".repeat(256), "Owner")
        }.isInstanceOf(ResponseStatusException::class.java)
            .extracting { (it as ResponseStatusException).statusCode }
            .isEqualTo(HttpStatus.BAD_REQUEST)

        verifyNoInteractions(characterProfessionApiClient)
    }

    @Test
    fun `rejects a tree from another profession before replacing profile`() {
        `when`(repository.findCharacter("user-a", 7)).thenReturn(character())
        `when`(repository.treeBelongsToProfession(99, 164)).thenReturn(false)

        assertThatThrownBy {
            service.putProfile("user-a", 7, 164, ProfessionProfileRequest(99, emptyList()))
        }.isInstanceOf(ResponseStatusException::class.java)
            .extracting { (it as ResponseStatusException).statusCode }
            .isEqualTo(HttpStatus.BAD_REQUEST)

    }

    @Test
    fun `rejects non-positive allocation ranks before replacing profile`() {
        `when`(repository.findCharacter("user-a", 7)).thenReturn(character())
        `when`(repository.treeBelongsToProfession(99, 164)).thenReturn(true)

        assertThatThrownBy {
            service.putProfile("user-a", 7, 164, ProfessionProfileRequest(99, listOf(ProfessionAllocation(4, -1))))
        }.isInstanceOf(ResponseStatusException::class.java)
            .extracting { (it as ResponseStatusException).statusCode }
            .isEqualTo(HttpStatus.BAD_REQUEST)

    }

    @Test
    fun `syncs Blizzard professions for an owned character`() {
        val character = character()
        `when`(repository.findCharacter("user-a", 7)).thenReturn(character)
        `when`(characterProfessionApiClient.getProfessions(Region.Europe, "draenor", "Owner")).thenReturn(
            CharacterProfessionsDTO(primaries = emptyList()),
        )
        `when`(repository.syncBlizzardProfessions(7, emptyList())).thenReturn(emptyList())

        val result = service.syncCharacterFromBlizzard("user-a", 7)

        assertThat(result).isEmpty()
        verify(repository).syncBlizzardProfessions(7, emptyList())
    }

    @Test
    fun `lists known professions for an owned character`() {
        `when`(repository.findCharacter("user-a", 7)).thenReturn(character())
        `when`(repository.listCharacterProfessions("user-a", 7)).thenReturn(emptyList())

        assertThat(service.listCharacterProfessions("user-a", 7)).isEmpty()
    }

    private fun character() = ProfileCharacter(7, "eu", "draenor", "Owner", null)
}
