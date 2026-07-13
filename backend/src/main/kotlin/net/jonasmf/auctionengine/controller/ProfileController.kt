package net.jonasmf.auctionengine.controller

import net.jonasmf.auctionengine.generated.api.ProfileApi
import net.jonasmf.auctionengine.generated.model.ProfessionProfile
import net.jonasmf.auctionengine.generated.model.ProfessionProfileRequest
import net.jonasmf.auctionengine.generated.model.ProfessionSkillTree
import net.jonasmf.auctionengine.generated.model.CharacterProfessionPreview
import net.jonasmf.auctionengine.generated.model.ProfileCharacter
import net.jonasmf.auctionengine.generated.model.ProfileCharacterRequest
import net.jonasmf.auctionengine.service.ProfileService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.RestController

@RestController
class ProfileController(
    private val profileService: ProfileService,
) : ProfileApi {
    override suspend fun getCharacterProfessionPreview(
        region: String,
        realmSlug: String,
        characterName: String,
    ): ResponseEntity<CharacterProfessionPreview> =
        ResponseEntity.ok(profileService.getCharacterProfessionPreview(region, realmSlug, characterName))

    override suspend fun listProfileCharacters(): ResponseEntity<List<ProfileCharacter>> = ResponseEntity.ok(profileService.listCharacters(subject()))

    override suspend fun createProfileCharacter(body: ProfileCharacterRequest): ResponseEntity<ProfileCharacter> = ResponseEntity.status(HttpStatus.CREATED).body(profileService.createCharacter(subject(), body))

    override suspend fun deleteProfileCharacter(characterId: Long): ResponseEntity<Unit> {
        profileService.deleteCharacter(subject(), characterId)
        return ResponseEntity.noContent().build()
    }

    override suspend fun listProfessionTrees(expansionId: Int, professionId: Int): ResponseEntity<List<ProfessionSkillTree>> = ResponseEntity.ok(profileService.listTrees(expansionId, professionId))

    override suspend fun getProfessionProfile(characterId: Long, professionId: Int): ResponseEntity<ProfessionProfile> = ResponseEntity.ok(profileService.getProfile(subject(), characterId, professionId))

    override suspend fun putProfessionProfile(characterId: Long, professionId: Int, body: ProfessionProfileRequest): ResponseEntity<ProfessionProfile> = ResponseEntity.ok(profileService.putProfile(subject(), characterId, professionId, body))

    override suspend fun deleteProfessionProfile(characterId: Long, professionId: Int): ResponseEntity<Unit> {
        profileService.deleteProfile(subject(), characterId, professionId)
        return ResponseEntity.noContent().build()
    }
}

private fun subject(): String = (SecurityContextHolder.getContext().authentication as? JwtAuthenticationToken)?.token?.subject ?: throw IllegalStateException("Authenticated JWT subject is required")
