package net.jonasmf.auctionengine.config

import net.jonasmf.auctionengine.constant.Region
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration

class RuntimeConfigurationPropertiesTest {
    private val contextRunner =
        ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration::class.java)
            .withPropertyValues(
                "blizzard.base-url=https://api.blizzard.test/data/wow/",
                "blizzard.token-url=https://oauth.blizzard.test/token",
                "blizzard.client-id=id",
                "blizzard.client-secret=secret",
                "wae.s3.buckets.europe.name=wah-data-eu",
                "wae.s3.buckets.europe.bucket-region=eu-west-1",
                "wae.s3.buckets.northamerica.name=wah-data-us",
                "wae.s3.buckets.northamerica.bucket-region=us-west-1",
                "wae.s3.buckets.korea.name=wah-data-as",
                "wae.s3.buckets.korea.bucket-region=ap-northeast-2",
                "wae.s3.buckets.taiwan.name=wah-data-as",
                "wae.s3.buckets.taiwan.bucket-region=ap-northeast-2",
            )

    @Test
    fun `binds multiple blizzard regions from comma separated property`() {
        contextRunner
            .withPropertyValues("blizzard.regions=Korea, Taiwan")
            .run { context ->
                val properties = context.getBean(BlizzardApiProperties::class.java)

                assertEquals(listOf(Region.Korea, Region.Taiwan), properties.configuredRegions)
                assertEquals(Region.Korea, properties.primaryRegion)
                assertEquals(Region.Europe, properties.staticDataRegion)
            }
    }

    @Test
    fun `binds static data region independently of configured regions`() {
        contextRunner
            .withPropertyValues(
                "blizzard.regions=Korea, Taiwan",
                "blizzard.static-data-region=Europe",
            ).run { context ->
                val properties = context.getBean(BlizzardApiProperties::class.java)

                assertEquals(listOf(Region.Korea, Region.Taiwan), properties.configuredRegions)
                assertEquals(Region.Europe, properties.staticDataRegion)
            }
    }

    @Test
    fun `falls back to singular blizzard region property`() {
        contextRunner
            .withPropertyValues("blizzard.region=Europe")
            .run { context ->
                val properties = context.getBean(BlizzardApiProperties::class.java)

                assertEquals(listOf(Region.Europe), properties.configuredRegions)
            }
    }

    @Test
    fun `binds shared asia bucket metadata`() {
        contextRunner
            .withPropertyValues("blizzard.region=Europe")
            .run { context ->
                val properties = context.getBean(WaeS3Properties::class.java)

                assertEquals("wah-data-us", properties.bucketFor(Region.NorthAmerica).name)
                assertEquals("us-west-1", properties.bucketFor(Region.NorthAmerica).bucketRegion)
                assertEquals("wah-data-as", properties.bucketFor(Region.Korea).name)
                assertEquals("wah-data-as", properties.bucketFor(Region.Taiwan).name)
            }
    }

    @Configuration
    @EnableConfigurationProperties(
        value = [
            BlizzardApiProperties::class,
            WaeS3Properties::class,
        ],
    )
    class TestConfiguration
}
