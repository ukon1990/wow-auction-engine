package net.jonasmf.auctionengine.config

import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.auth.awscredentials.CredentialsProvider
import aws.smithy.kotlin.runtime.collections.Attributes

internal fun staticCredentialsProvider(
    accessKeyId: String,
    secretAccessKey: String,
): CredentialsProvider =
    object : CredentialsProvider {
        override suspend fun resolve(attributes: Attributes): Credentials =
            Credentials(
                accessKeyId = accessKeyId,
                secretAccessKey = secretAccessKey,
            )
    }
