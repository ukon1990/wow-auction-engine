package net.jonasmf.auctionengine.controller

import net.jonasmf.auctionengine.config.SecurityConfig
import net.jonasmf.auctionengine.generated.model.ProfessionProfile
import net.jonasmf.auctionengine.generated.model.CharacterProfessionPreview
import net.jonasmf.auctionengine.generated.model.ProfileCharacter
import net.jonasmf.auctionengine.service.ProfileService
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.context.annotation.Import
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(controllers = [ProfileController::class])
@ImportAutoConfiguration(
    ServletWebSecurityAutoConfiguration::class,
    SecurityFilterAutoConfiguration::class,
)
@Import(SecurityConfig::class)
@TestPropertySource(
    properties = [
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.example.test",
    ],
)
class ProfileControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var profileService: ProfileService

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Test
    fun `profile endpoints reject anonymous callers`() {
        mockMvc.get("/profile/characters").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `returns caller-owned characters`() {
        `when`(profileService.listCharacters(anyString())).thenReturn(
            listOf(ProfileCharacter(7, "eu", "draenor", "Owner", null)),
        )

        val result = mockMvc.get("/profile/characters") { with(jwt().jwt { it.subject("user-a") }) }.andExpect { request { asyncStarted() } }.andReturn()

        mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk)
    }

    @Test
    fun `returns a Blizzard character profession preview for an authenticated caller`() {
        `when`(profileService.getCharacterProfessionPreview(anyString(), anyString(), anyString())).thenReturn(
            CharacterProfessionPreview(CharacterProfessionPreview.Region.EU, "draenor", "Owner", emptyList()),
        )

        val result =
            mockMvc
                .get("/profile/characters/professions-preview?region=eu&realmSlug=draenor&characterName=Owner") {
                    with(jwt().jwt { it.subject("user-a") })
                }.andExpect { request { asyncStarted() } }
                .andReturn()

        mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk)
    }

    @Test
    fun `returns empty allocation when caller has no saved profile`() {
        `when`(profileService.getProfile(anyString(), anyLong(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(
            ProfessionProfile(7, 164, emptyList()),
        )

        val result = mockMvc.get("/profile/characters/7/professions/164") { with(jwt().jwt { it.subject("user-a") }) }.andExpect { request { asyncStarted() } }.andReturn()

        mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk)
    }
}
