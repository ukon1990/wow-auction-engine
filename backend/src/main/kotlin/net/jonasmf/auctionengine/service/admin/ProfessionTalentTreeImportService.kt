package net.jonasmf.auctionengine.service.admin

import com.fasterxml.jackson.databind.JsonNode
import net.jonasmf.auctionengine.repository.rds.ProfessionTalentTreeImportRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProfessionTalentTreeImportService(
    private val repository: ProfessionTalentTreeImportRepository,
) {
    @Transactional
    fun import(payload: JsonNode): Int {
        require(payload.intOrNull("formatVersion") == 1) { "Unsupported professions_talents formatVersion; expected 1" }
        require(payload.textOrNull("sourceVersion") != null) { "sourceVersion is required" }
        return repository.replace(payload)
    }
}

private fun JsonNode.intOrNull(name: String): Int? = get(name)?.takeIf { it.isInt }?.intValue()
private fun JsonNode.textOrNull(name: String): String? = get(name)?.takeIf { it.isTextual }?.textValue()
