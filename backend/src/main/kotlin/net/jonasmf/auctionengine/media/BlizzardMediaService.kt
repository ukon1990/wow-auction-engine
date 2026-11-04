package net.jonasmf.auctionengine.media

import net.jonasmf.auctionengine.service.AmazonS3Service
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.domain.item.Item
import net.jonasmf.auctionengine.domain.item.ItemAppearance
import net.jonasmf.auctionengine.domain.profession.Profession
import net.jonasmf.auctionengine.domain.profession.Recipe
import net.jonasmf.auctionengine.dto.BlizzardMediaAssetDTO
import net.jonasmf.auctionengine.integration.blizzard.BlizzardMediaApiClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.net.URI

enum class BlizzardMediaType(
    val apiType: String,
    val s3Type: String,
) {
    ITEM("item", "item"),
    ITEM_APPEARANCE("item", "item-appearance"),
    RECIPE("recipe", "recipe"),
    PROFESSION("profession", "profession"),
}

data class ResolvedBlizzardMedia(
    val mediaUrl: String,
    val mediaSourceUrl: String,
)

@Service
class BlizzardMediaService(
    private val mediaApiClient: BlizzardMediaApiClient,
    private val amazonS3Service: AmazonS3Service,
) {
    private val log = LoggerFactory.getLogger(BlizzardMediaService::class.java)

    fun resolve(
        region: Region,
        type: BlizzardMediaType,
        id: Int,
        sourceHref: String? = null,
        storageId: Int = id,
    ): ResolvedBlizzardMedia? =
        runCatching {
            val media = mediaApiClient.getMedia(type.apiType, id, region, sourceHref)
            val asset = media.assets.firstOrNull { it.key == "icon" } ?: media.assets.firstOrNull()
            if (asset == null) {
                log.warn("No media assets found for type={} id={} region={}", type, id, region)
                return null
            }
            val extension = extensionFor(asset)
            val key = "media/${type.s3Type}/$storageId.$extension"
            val url =
                if (amazonS3Service.objectExists(region, key)) {
                    amazonS3Service.publicUrl(region, key)
                } else {
                    val downloaded = mediaApiClient.downloadAsset(asset.value)
                    amazonS3Service.uploadBytes(region, key, downloaded.bytes, downloaded.contentType)
                }
            ResolvedBlizzardMedia(
                mediaUrl = url,
                mediaSourceUrl = sourceHref ?: media.links?.self?.href ?: "${type.apiType}/$id",
            )
        }.onFailure { error ->
            log.warn(
                "Skipping media resolution for type={} id={} region={} after failure: {}",
                type,
                id,
                region,
                error.message ?: error::class.simpleName ?: "unknown error",
            )
        }.getOrNull()

    fun resolveItem(
        region: Region,
        item: Item,
    ): Item =
        resolve(region, BlizzardMediaType.ITEM, item.id, item.mediaSourceUrl ?: item.mediaUrl)
            ?.let { item.copy(mediaUrl = it.mediaUrl, mediaSourceUrl = it.mediaSourceUrl) }
            ?: item

    fun resolveItemAppearance(
        region: Region,
        itemAppearance: ItemAppearance,
    ): ItemAppearance =
        resolve(
            region,
            BlizzardMediaType.ITEM_APPEARANCE,
            itemAppearance.itemDisplayInfoId,
            itemAppearance.mediaSourceUrl ?: itemAppearance.mediaUrl,
            itemAppearance.id,
        )?.let { itemAppearance.copy(mediaUrl = it.mediaUrl, mediaSourceUrl = it.mediaSourceUrl) }
            ?: itemAppearance

    fun resolveProfession(
        region: Region,
        profession: Profession,
    ): Profession =
        resolve(region, BlizzardMediaType.PROFESSION, profession.id, profession.mediaSourceUrl ?: profession.mediaUrl)
            ?.let { profession.copy(mediaUrl = it.mediaUrl, mediaSourceUrl = it.mediaSourceUrl) }
            ?: profession

    fun resolveRecipe(
        region: Region,
        recipe: Recipe,
    ): Recipe {
        val mediaUrl = recipe.mediaUrl ?: return recipe
        return resolve(region, BlizzardMediaType.RECIPE, recipe.id, recipe.mediaSourceUrl ?: mediaUrl)
            ?.let { recipe.copy(mediaUrl = it.mediaUrl, mediaSourceUrl = it.mediaSourceUrl) }
            ?: recipe
    }

    private fun extensionFor(asset: BlizzardMediaAssetDTO): String {
        val path = runCatching { URI(asset.value).path }.getOrNull() ?: asset.value
        val extension = path.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return when (extension) {
            "jpg", "jpeg", "png", "webp", "gif" -> extension
            else -> "png"
        }
    }
}
