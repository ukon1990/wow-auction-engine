package net.jonasmf.auctionengine.integration.blizzard

import net.jonasmf.auctionengine.domain.profession.Profession
import net.jonasmf.auctionengine.domain.profession.SkillTier
import net.jonasmf.auctionengine.dto.profession.ProfessionDTO
import net.jonasmf.auctionengine.dto.profession.ProfessionIndexDTO
import net.jonasmf.auctionengine.dto.profession.SkillTierDTO
import net.jonasmf.auctionengine.mapper.toDomain
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.bodyToMono
import java.time.Duration
import java.time.Instant

private const val PROFESSION_API_RETRY_ATTEMPTS = 3L
private val PROFESSION_API_RETRY_BACKOFF: Duration = Duration.ofSeconds(2)

const val PROFESSION_BASE_PATH = "/data/wow/profession"
const val PROFESSION_INDEX_PATH = "$PROFESSION_BASE_PATH/index"

@Component
class ProfessionApiClient(
    private val blizzardApiSupport: BlizzardApiSupport,
) {
    private val logger: Logger = LoggerFactory.getLogger(ProfessionApiClient::class.java)

    fun getAll(): List<Profession> = getAll(blizzardApiSupport.defaultRegion())

    fun getAll(region: net.jonasmf.auctionengine.constant.Region): List<Profession> {
        val uri =
            blizzardApiSupport.buildRegionalUri(
                region = region,
                path = PROFESSION_INDEX_PATH,
                namespace = blizzardApiSupport.staticNamespaceForRegion(region).value,
            )
        val index =
            blizzardApiSupport
                .webClient()
                .get()
                .uri(uri)
                .retrieve()
                .bodyToMono<ProfessionIndexDTO>()
                .onErrorMap { error ->
                    BlizzardApiClientException.from(
                        error = error,
                        operation = "fetch profession index",
                        url = uri,
                        timeout = PROFESSION_API_RETRY_BACKOFF,
                    )
                }.retryTransientBlizzardFailures(
                    maxRetries = PROFESSION_API_RETRY_ATTEMPTS,
                    backoff = PROFESSION_API_RETRY_BACKOFF,
                ).doOnError { error ->
                    logger.logBlizzardHttpFailure(error)
                }.block()!!

        return index.professions.mapNotNull { profession ->
            runCatching { getById(profession.id, region) }
                .onFailure { error ->
                    logger.warn(
                        "Skipping profession {} for region {} after fetch failure: {}",
                        profession.id,
                        region,
                        error.message ?: error::class.simpleName ?: "unknown error",
                    )
                }.getOrNull()
        }
    }

    fun getSkillTier(
        professionId: Int,
        skillTierId: Int,
    ): SkillTier = getSkillTier(professionId, skillTierId, blizzardApiSupport.defaultRegion())

    fun getSkillTier(
        professionId: Int,
        skillTierId: Int,
        region: net.jonasmf.auctionengine.constant.Region,
    ): SkillTier {
        val uri =
            blizzardApiSupport.buildRegionalUri(
                region = region,
                path = "$PROFESSION_BASE_PATH/$professionId/skill-tier/$skillTierId",
                namespace = blizzardApiSupport.staticNamespaceForRegion(region).value,
            )
        val skillTier =
            blizzardApiSupport
                .webClient()
                .get()
                .uri(uri)
                .retrieve()
                .bodyToMono<SkillTierDTO>()
                .onErrorMap { error ->
                    BlizzardApiClientException.from(
                        error = error,
                        operation = "fetch profession skill tier",
                        url = uri,
                        timeout = PROFESSION_API_RETRY_BACKOFF,
                    )
                }.retryTransientBlizzardFailures(
                    maxRetries = PROFESSION_API_RETRY_ATTEMPTS,
                    backoff = PROFESSION_API_RETRY_BACKOFF,
                ).doOnError { error ->
                    logger.logBlizzardHttpFailure(error)
                }.block()!!
        return skillTier.toDomain()
    }

    fun getById(id: Int): Profession = getById(id, blizzardApiSupport.defaultRegion())

    fun getById(
        id: Int,
        region: net.jonasmf.auctionengine.constant.Region,
    ): Profession {
        val uri =
            blizzardApiSupport.buildRegionalUri(
                region = region,
                path = "$PROFESSION_BASE_PATH/$id",
                namespace = blizzardApiSupport.staticNamespaceForRegion(region).value,
            )
        val response =
            blizzardApiSupport
                .webClient()
                .get()
                .uri(uri)
                .retrieve()
                .toEntity(ProfessionDTO::class.java)
                .onErrorMap { error ->
                    BlizzardApiClientException.from(
                        error = error,
                        operation = "fetch profession",
                        url = uri,
                        timeout = PROFESSION_API_RETRY_BACKOFF,
                    )
                }.retryTransientBlizzardFailures(
                    maxRetries = PROFESSION_API_RETRY_ATTEMPTS,
                    backoff = PROFESSION_API_RETRY_BACKOFF,
                ).doOnError { error ->
                    logger.logBlizzardHttpFailure(error)
                }.block()!!
        val profession = requireNotNull(response.body) { "Profession body missing for $uri" }
        val skillTiers =
            profession.skillTiers.mapNotNull { tier ->
                runCatching { getSkillTier(profession.id, tier.id, region) }
                    .onFailure { error ->
                        logger.warn(
                            "Skipping skill tier {} for profession {} region {} after fetch failure: {}",
                            tier.id,
                            profession.id,
                            region,
                            error.message ?: error::class.simpleName ?: "unknown error",
                        )
                    }.getOrNull()
            }
        return profession.toDomain(
            skillTiers = skillTiers,
            lastModified = response.headers.lastModified.takeIf { it > 0 }?.let(Instant::ofEpochMilli),
        )
    }
}
