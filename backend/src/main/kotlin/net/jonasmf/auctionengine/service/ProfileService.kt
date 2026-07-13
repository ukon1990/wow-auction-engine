package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.generated.model.ProfessionProfile
import net.jonasmf.auctionengine.generated.model.ProfessionProfileRequest
import net.jonasmf.auctionengine.generated.model.ProfessionSkillTree
import net.jonasmf.auctionengine.generated.model.ProfileCharacter
import net.jonasmf.auctionengine.generated.model.ProfileCharacterRequest
import net.jonasmf.auctionengine.repository.rds.ProfileRepository
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class ProfileService(
    private val profileRepository: ProfileRepository,
) {
    fun listCharacters(subject: String): List<ProfileCharacter> = profileRepository.listCharacters(subject)

    fun createCharacter(subject: String, request: ProfileCharacterRequest): ProfileCharacter {
        if (request.region.isBlank() || request.realmName.isBlank() || request.characterName.isBlank()) badRequest("region, realmName, and characterName are required")
        return try { profileRepository.createCharacter(subject, request) } catch (_: DuplicateKeyException) { throw ResponseStatusException(HttpStatus.CONFLICT, "Character already exists for this profile") }
    }

    fun deleteCharacter(subject: String, characterId: Long) {
        if (!profileRepository.deleteCharacter(subject, characterId)) notFound("Character not found")
    }

    fun listTrees(expansionId: Int, professionId: Int): List<ProfessionSkillTree> = profileRepository.listTrees(expansionId, professionId)

    fun getProfile(subject: String, characterId: Long, professionId: Int): ProfessionProfile {
        requireCharacter(subject, characterId)
        return profileRepository.getProfile(subject, characterId, professionId) ?: ProfessionProfile(characterId, professionId, emptyList())
    }

    fun putProfile(subject: String, characterId: Long, professionId: Int, request: ProfessionProfileRequest): ProfessionProfile {
        requireCharacter(subject, characterId)
        validateAllocations(professionId, request)
        return profileRepository.replaceProfile(subject, characterId, professionId, request.treeId, request.skillLevel, request.allocations)
    }

    fun deleteProfile(subject: String, characterId: Long, professionId: Int) {
        requireCharacter(subject, characterId)
        if (!profileRepository.deleteProfile(subject, characterId, professionId)) notFound("Profession profile not found")
    }

    private fun requireCharacter(subject: String, characterId: Long) {
        if (profileRepository.findCharacter(subject, characterId) == null) notFound("Character not found")
    }

    private fun validateAllocations(
        professionId: Int,
        request: ProfessionProfileRequest,
    ) {
        if (!profileRepository.treeBelongsToProfession(request.treeId, professionId)) {
            badRequest("Profession skill tree ${request.treeId} does not belong to profession $professionId")
        }
        if (request.allocations.map { it.entryId }.toSet().size != request.allocations.size) badRequest("Allocations must not contain duplicate entryId values")
        if (request.allocations.any { it.rank <= 0 }) badRequest("Allocation ranks must be at least 1")
        val rules = profileRepository.allocationRules(request.treeId).associateBy { it.entryId }
        if (rules.isEmpty()) badRequest("Profession skill tree not found")
        val allocations = request.allocations.associate { it.entryId to it.rank }
        request.allocations.forEach { allocation ->
            val rule = rules[allocation.entryId] ?: badRequest("Allocation entry ${allocation.entryId} does not belong to tree ${request.treeId}")
            if (allocation.rank > rule.rankLimit) badRequest("Allocation rank for entry ${allocation.entryId} exceeds its rank limit")
        }
        val rankByNode = rules.values.groupBy { it.nodeId }.mapValues { (_, entries) -> entries.sumOf { allocations[it.entryId] ?: 0 } }
        rules.values.forEach { rule ->
            val allocatedRanks = rankByNode[rule.nodeId] ?: 0
            if (allocatedRanks > rule.maxRanks) badRequest("Allocated ranks exceed max ranks for node ${rule.nodeId}")
            if (allocatedRanks > 0 && allocatedRanks < rule.requiredRank) {
                badRequest("Node ${rule.nodeId} requires at least ${rule.requiredRank} total allocated ranks")
            }
        }
        profileRepository.parentRules(request.treeId).forEach { prerequisite ->
            if ((rankByNode[prerequisite.nodeId] ?: 0) > 0 && (rankByNode[prerequisite.parentNodeId] ?: 0) < prerequisite.requiredParentRanks) badRequest("Node ${prerequisite.nodeId} requires ${prerequisite.requiredParentRanks} ranks in node ${prerequisite.parentNodeId}")
        }
    }
}

private fun badRequest(detail: String): Nothing = throw ResponseStatusException(HttpStatus.BAD_REQUEST, detail)
private fun notFound(detail: String): Nothing = throw ResponseStatusException(HttpStatus.NOT_FOUND, detail)
