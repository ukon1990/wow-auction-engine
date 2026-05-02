package net.jonasmf.auctionengine.config

import net.jonasmf.auctionengine.constant.Region
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding

@ConfigurationProperties(prefix = "wae.s3")
data class WaeS3Properties
    @ConstructorBinding
    constructor(
        val buckets: Map<String, BucketConfig>,
    ) {
        fun bucketFor(region: Region): BucketConfig =
            buckets[region.name.lowercase()]
                ?: error("Missing S3 bucket configuration for Blizzard region ${region.name}")

        fun supportedBucketNames(): List<String> = buckets.values.map { it.name }.distinct()
    }

data class BucketConfig
    @ConstructorBinding
    constructor(
        val name: String,
        val bucketRegion: String,
    )
