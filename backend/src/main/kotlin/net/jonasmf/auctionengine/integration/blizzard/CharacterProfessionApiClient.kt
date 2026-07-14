package net.jonasmf.auctionengine.integration.blizzard

import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.dto.profession.CharacterProfessionsDTO
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.bodyToMono
import java.net.URI
import java.time.Duration

private const val CHARACTER_PROFESSION_API_RETRY_ATTEMPTS = 3L
private val CHARACTER_PROFESSION_API_RETRY_BACKOFF: Duration = Duration.ofSeconds(2)

const val CHARACTER_PROFESSIONS_BASE_PATH = "/profile/wow/character"
private const val CHARACTER_PROFESSIONS_DIAGNOSTIC_PATH = "$CHARACTER_PROFESSIONS_BASE_PATH/{realmSlug}/{characterName}/professions"

@Component
class CharacterProfessionApiClient(
    private val blizzardApiSupport: BlizzardApiSupport,
) {
    private val logger: Logger = LoggerFactory.getLogger(CharacterProfessionApiClient::class.java)

    fun getProfessions(
        region: Region,
        realmSlug: String,
        characterName: String,
    ): CharacterProfessionsDTO {
        val uri =
            blizzardApiSupport.buildRegionalUri(
                region = region,
                path =
                    "$CHARACTER_PROFESSIONS_BASE_PATH/" +
                        "$realmSlug/$characterName/professions",
                namespace = blizzardApiSupport.profileNamespaceForRegion(region).value,
            )
        val diagnosticUrl =
            blizzardApiSupport.buildRegionalUri(
                region = region,
                path = CHARACTER_PROFESSIONS_DIAGNOSTIC_PATH,
                namespace = blizzardApiSupport.profileNamespaceForRegion(region).value,
            )
        return requireNotNull(
            blizzardApiSupport
                .webClient()
                .get()
                .uri(URI.create(uri))
                .retrieve()
                .bodyToMono<CharacterProfessionsDTO>()
                .onErrorMap { error ->
                    BlizzardApiClientException.from(
                        error = error,
                        operation = "fetch character professions",
                        url = diagnosticUrl,
                        timeout = CHARACTER_PROFESSION_API_RETRY_BACKOFF,
                    )
                }.retryTransientBlizzardFailures(
                    maxRetries = CHARACTER_PROFESSION_API_RETRY_ATTEMPTS,
                    backoff = CHARACTER_PROFESSION_API_RETRY_BACKOFF,
                ).doOnError { error ->
                    logger.logBlizzardHttpFailure(error)
                }.block(),
        ) { "Character professions body missing for $diagnosticUrl" }
    }
}
