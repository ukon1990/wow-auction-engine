package net.jonasmf.auctionengine.service

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.PutObjectRequest
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.utility.getBucketName
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.zip.GZIPOutputStream

@Service
class AmazonS3Service(
    private var amazonS3: AmazonS3,
) {
    private val LOG: Logger = LoggerFactory.getLogger(AmazonS3Service::class.java)

    /**
     * Serializes an object to JSON, writes it to a gzip file, and uploads the file to S3.
     *
     * @param data Object to serialize and upload.
     * @param filePath Path where the gzip file will be stored temporarily.
     * @param region The region to determine the S3 bucket.
     * @param s3Key The S3 key under which to store the object.
     */
    private fun serializeCompressAndUpload(
        region: Region,
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
        val file = serializeCompressAndUpload(region, path, data)
        val fileName = "engine/$path.gz"
        val result = amazonS3.putObject(PutObjectRequest(getBucketName(region), fileName, file))
        val url = amazonS3.getUrl(getBucketName(region), fileName)
        if (result != null) {
            LOG.info("Uploaded file to $url")
            return url.toString()
        }
        LOG.error("Failed to upload file - $path")
        return null
    }

    fun getFile(
        region: Region,
        path: String,
    ): Path {
        amazonS3.getObject(getBucketName(region), path).objectContent.use { input ->
            val file = Paths.get("/tmp/$path")
            Files.createDirectories(file.parent)
            Files.copy(input, file)
            return file
        }
    }
}
