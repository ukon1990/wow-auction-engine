package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.config.S3IntegrationTestBase
import net.jonasmf.auctionengine.constant.Region
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.nio.file.Files
import java.util.zip.GZIPInputStream

class AmazonS3ServiceIntegrationTest : S3IntegrationTestBase() {
    @Autowired
    lateinit var amazonS3Service: AmazonS3Service

    @Test
    fun `should upload and download file against floci s3`() {
        val region = Region.Europe
        val path = "integration-tests/europe/test-auction-file"
        val payload = mapOf("status" to "ok", "count" to 2)

        val url = amazonS3Service.uploadFile(region, path, payload)

        assertEquals(
            "http://${flociContainer.host}:${flociContainer.getMappedPort(4566)}/wah-data-eu/engine/$path.gz",
            url,
        )

        val downloaded = amazonS3Service.getFile(region, "engine/$path.gz")

        assertTrue(Files.exists(downloaded))
        GZIPInputStream(Files.newInputStream(downloaded)).bufferedReader().use { reader ->
            val json = reader.readText()
            assertTrue(json.contains("\"status\":\"ok\""))
            assertTrue(json.contains("\"count\":2"))
        }
    }
}
