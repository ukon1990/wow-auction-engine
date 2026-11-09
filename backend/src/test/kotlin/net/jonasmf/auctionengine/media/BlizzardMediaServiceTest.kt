package net.jonasmf.auctionengine.media

import net.jonasmf.auctionengine.integration.aws.AmazonS3Service
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.media.BlizzardMediaAssetDTO
import net.jonasmf.auctionengine.media.BlizzardMediaDTO
import net.jonasmf.auctionengine.integration.blizzard.dto.Link
import net.jonasmf.auctionengine.integration.blizzard.dto.Links
import net.jonasmf.auctionengine.integration.blizzard.BlizzardMediaApiClient
import net.jonasmf.auctionengine.integration.blizzard.DownloadedMediaAsset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BlizzardMediaServiceTest {
    private val mediaApiClient = mockk<BlizzardMediaApiClient>()
    private val amazonS3Service = mockk<AmazonS3Service>()
    private val service = BlizzardMediaService(mediaApiClient, amazonS3Service)

    @Test
    fun `resolve chooses icon asset and uploads missing object`() {
        val media =
            BlizzardMediaDTO(
                links = Links(Link("https://eu.api.blizzard.com/data/wow/media/item/19019")),
                assets =
                    listOf(
                        BlizzardMediaAssetDTO("fallback", "https://render.example.test/fallback.jpg"),
                        BlizzardMediaAssetDTO("icon", "https://render.example.test/icon.png"),
                    ),
            )

        every { mediaApiClient.getMedia("item", 19019, Region.Europe, null) } returns media
        every { amazonS3Service.objectExists(Region.Europe, "media/item/19019.png") } returns false
        every { mediaApiClient.downloadAsset("https://render.example.test/icon.png") } returns
            DownloadedMediaAsset(byteArrayOf(1, 2, 3), "image/png")
        every {
            amazonS3Service.uploadBytes(Region.Europe, "media/item/19019.png", byteArrayOf(1, 2, 3), "image/png")
        } returns "https://wah-data-eu.s3.eu-north-1.amazonaws.com/media/item/19019.png"

        val resolved = service.resolve(Region.Europe, BlizzardMediaType.ITEM, 19019)

        assertEquals("https://wah-data-eu.s3.eu-north-1.amazonaws.com/media/item/19019.png", resolved?.mediaUrl)
        assertEquals("https://eu.api.blizzard.com/data/wow/media/item/19019", resolved?.mediaSourceUrl)
        verify(exactly = 1) { mediaApiClient.downloadAsset("https://render.example.test/icon.png") }
    }

    @Test
    fun `resolve falls back to first asset and skips upload when object exists`() {
        val media =
            BlizzardMediaDTO(
                links = Links(Link("https://eu.api.blizzard.com/data/wow/media/recipe/42363")),
                assets = listOf(BlizzardMediaAssetDTO("main", "https://render.example.test/recipe.jpg")),
            )

        every { mediaApiClient.getMedia("recipe", 42363, Region.Europe, null) } returns media
        every { amazonS3Service.objectExists(Region.Europe, "media/recipe/42363.jpg") } returns true
        every { amazonS3Service.publicUrl(Region.Europe, "media/recipe/42363.jpg") } returns
            "https://wah-data-eu.s3.eu-north-1.amazonaws.com/media/recipe/42363.jpg"

        val resolved = service.resolve(Region.Europe, BlizzardMediaType.RECIPE, 42363)

        assertEquals("https://wah-data-eu.s3.eu-north-1.amazonaws.com/media/recipe/42363.jpg", resolved?.mediaUrl)
        verify(exactly = 0) { mediaApiClient.downloadAsset(any()) }
    }

    @Test
    fun `resolve uploads when database already has S3 url but object is missing`() {
        val media =
            BlizzardMediaDTO(
                links = Links(Link("https://eu.api.blizzard.com/data/wow/media/item/19019")),
                assets = listOf(BlizzardMediaAssetDTO("icon", "https://render.example.test/icon.png")),
            )

        every {
            mediaApiClient.getMedia(
                "item",
                19019,
                Region.Europe,
                "https://eu.api.blizzard.com/data/wow/media/item/19019",
            )
        } returns media
        every { amazonS3Service.objectExists(Region.Europe, "media/item/19019.png") } returns false
        every { mediaApiClient.downloadAsset("https://render.example.test/icon.png") } returns
            DownloadedMediaAsset(byteArrayOf(1, 2, 3), "image/png")
        every {
            amazonS3Service.uploadBytes(Region.Europe, "media/item/19019.png", byteArrayOf(1, 2, 3), "image/png")
        } returns "https://wah-data-eu.s3.eu-north-1.amazonaws.com/media/item/19019.png"

        val resolved =
            service.resolve(
                region = Region.Europe,
                type = BlizzardMediaType.ITEM,
                id = 19019,
                sourceHref = "https://eu.api.blizzard.com/data/wow/media/item/19019",
            )

        assertEquals("https://wah-data-eu.s3.eu-north-1.amazonaws.com/media/item/19019.png", resolved?.mediaUrl)
        verify(exactly = 1) { mediaApiClient.downloadAsset("https://render.example.test/icon.png") }
        verify(exactly = 1) {
            amazonS3Service.uploadBytes(Region.Europe, "media/item/19019.png", byteArrayOf(1, 2, 3), "image/png")
        }
    }

    @Test
    fun `resolve returns null when media has no assets`() {
        every { mediaApiClient.getMedia("profession", 164, Region.Europe, null) } returns
            BlizzardMediaDTO(assets = emptyList())

        assertNull(service.resolve(Region.Europe, BlizzardMediaType.PROFESSION, 164))
    }

    @Test
    fun `resolve can use a separate upstream lookup id and S3 storage id`() {
        val media =
            BlizzardMediaDTO(
                links = Links(Link("https://eu.api.blizzard.com/data/wow/media/item/3555131")),
                assets = listOf(BlizzardMediaAssetDTO("icon", "https://render.example.test/appearance.png")),
            )

        every { mediaApiClient.getMedia("item", 3555131, Region.Europe, null) } returns media
        every { amazonS3Service.objectExists(Region.Europe, "media/item-appearance/42762.png") } returns true
        every { amazonS3Service.publicUrl(Region.Europe, "media/item-appearance/42762.png") } returns
            "https://wah-data-eu.s3.eu-north-1.amazonaws.com/media/item-appearance/42762.png"

        val resolved =
            service.resolve(
                region = Region.Europe,
                type = BlizzardMediaType.ITEM_APPEARANCE,
                id = 3555131,
                storageId = 42762,
            )

        assertEquals(
            "https://wah-data-eu.s3.eu-north-1.amazonaws.com/media/item-appearance/42762.png",
            resolved?.mediaUrl,
        )
    }
}
