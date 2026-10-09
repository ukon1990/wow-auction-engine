package net.jonasmf.auctionengine.testsupport

import aws.sdk.kotlin.services.s3.model.Object
import net.jonasmf.auctionengine.config.JsonMappers
import java.nio.file.Files
import java.nio.file.Path

private val mapper = JsonMappers.storage

fun <BodyType>writeJsonToDisk(fileName: String, body: BodyType): Path? {
    val path = Files.createTempFile(fileName, ".json")
    Files.writeString(path, mapper.writeValueAsString(body))
    return path
}
