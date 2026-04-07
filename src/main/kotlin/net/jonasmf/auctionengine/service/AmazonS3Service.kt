package net.jonasmf.auctionengine.service

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.smithy.kotlin.runtime.content.asByteStream
import aws.smithy.kotlin.runtime.content.writeToFile
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.runBlocking
import net.jonasmf.auctionengine.config.AmazonS3ClientFactory
import net.jonasmf.auctionengine.config.WaeS3Properties
import net.jonasmf.auctionengine.constant.Region
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.zip.GZIPOutputStream

@Service
class AmazonS3Service(
    private val amazonS3: S3Client,
    private val amazonS3ClientFactory: AmazonS3ClientFactory,
    private val s3Properties: WaeS3Properties,
    @Value("\${spring.cloud.aws.s3.endpoint:}")
    private val s3Endpoint: String,
) {
    private val logger: Logger = LoggerFactory.getLogger(AmazonS3Service::class.java)

    /**
     * Serializes an object to JSON, writes it to a gzip file, and uploads the file to S3.
     *
     * @param data Object to serialize and upload.
     * @param path Path where the gzip file will be stored temporarily.
     */
    private fun serializeCompressAndUpload(
        path: String,
        data: Any,
    ): File {
        val mapper = jacksonObjectMapper() // ObjectMapper for JSON serialization
        val filePath = Paths.get("/tmp/$path.gz")
        // Ensure directories for the file path exist
        Files.createDirectories(filePath.parent)

        // Serialize data to JSON and compress to GZIP
        GZIPOutputStream(
            Files.newOutputStream(
                filePath,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
            ),
        ).use { gzipOutputStream ->
            val jsonData = mapper.writeValueAsBytes(data)
            gzipOutputStream.write(jsonData)
        }

        // Upload the compressed file to S3
        return filePath.toFile()
    }

    fun uploadFile(
        region: Region,
        path: String,
        data: Any,
    ): String? {
        val file = serializeCompressAndUpload(path, data)
        val fileName = "engine/$path.gz"
        val bucketConfig = s3Properties.bucketFor(region)
        val bucketName = bucketConfig.name
        val s3Client = clientFor(bucketConfig.bucketRegion)
        runBlocking {
            s3Client.putObject(
                PutObjectRequest {
                    bucket = bucketName
                    key = fileName
                    body = file.asByteStream()
                },
            )
        }
        val url =
            if (s3Endpoint.isBlank()) {
                "https://$bucketName.s3.${bucketConfig.bucketRegion}.amazonaws.com/$fileName"
            } else {
                "${s3Endpoint.trimEnd('/')}/$bucketName/$fileName"
            }
        logger.info("Uploaded file to $url")
        return url
    }

    fun getFile(
        region: Region,
        path: String,
    ): Path =
        runBlocking {
            val bucketConfig = s3Properties.bucketFor(region)
            clientFor(bucketConfig.bucketRegion).getObject(
                GetObjectRequest {
                    bucket = bucketConfig.name
                    key = path
                },
            ) { response ->
                val file = Paths.get("/tmp/$path")
                Files.createDirectories(file.parent)
                val body = checkNotNull(response.body) { "S3 object body was empty for key $path" }
                body.writeToFile(file)
                file
            }
        }

    private fun clientFor(region: String): S3Client =
        if (s3Endpoint.isBlank()) {
            amazonS3ClientFactory.create(region)
        } else {
            amazonS3
        }
}
