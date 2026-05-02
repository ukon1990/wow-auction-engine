package net.jonasmf.auctionengine.service

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.sdk.kotlin.services.s3.model.PutObjectResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import net.jonasmf.auctionengine.config.AmazonS3ClientFactory
import net.jonasmf.auctionengine.config.BucketConfig
import net.jonasmf.auctionengine.config.WaeS3Properties
import net.jonasmf.auctionengine.constant.Region
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AmazonS3ServiceTest {
    @Test
    fun `uploadFile uses the configured bucket region when building public urls`() {
        val defaultClient = mockk<S3Client>(relaxed = true)
        val regionalClient = mockk<S3Client>()
        val clientFactory = mockk<AmazonS3ClientFactory>()
        val s3Properties =
            WaeS3Properties(
                buckets =
                    mapOf(
                        "northamerica" to BucketConfig("wah-data-us", "us-west-1"),
                        "europe" to BucketConfig("wah-data-eu", "eu-west-1"),
                        "korea" to BucketConfig("wah-data-as", "ap-northeast-2"),
                        "taiwan" to BucketConfig("wah-data-as", "ap-northeast-2"),
                    ),
            )
        val requestSlot = mutableListOf<PutObjectRequest>()
        val service =
            AmazonS3Service(
                amazonS3 = defaultClient,
                amazonS3ClientFactory = clientFactory,
                s3Properties = s3Properties,
                s3Endpoint = "",
            )

        every { clientFactory.create("us-west-1") } returns regionalClient
        coEvery { regionalClient.putObject(any()) } answers {
            requestSlot += firstArg<PutObjectRequest>()
            mockk<PutObjectResponse>(relaxed = true)
        }

        val url = service.uploadFile(Region.NorthAmerica, "auctions/northamerica/1/test.json", mapOf("ok" to true))

        assertEquals("https://wah-data-us.s3.us-west-1.amazonaws.com/engine/auctions/northamerica/1/test.json.gz", url)
        assertEquals("wah-data-us", requestSlot.single().bucket)
        coVerify(exactly = 1) { regionalClient.putObject(any()) }
    }
}
