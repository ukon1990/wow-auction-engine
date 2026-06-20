package net.jonasmf.auctionengine.testsupport

import aws.sdk.kotlin.services.s3.model.Object
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.nio.file.Path

private val mapper = jacksonObjectMapper()

fun <BodyType>writeJsonToDisk(fileName: String, body: BodyType): Path? {
    val path = Files.createTempFile(fileName, ".json")
    Files.writeString(path, mapper.writeValueAsString(body))
    return path
}
