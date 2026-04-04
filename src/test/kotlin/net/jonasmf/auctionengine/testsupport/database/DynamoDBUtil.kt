package net.jonasmf.auctionengine.testsupport.database

import io.awspring.cloud.dynamodb.DynamoDbOperations
import org.springframework.stereotype.Repository

@Repository
class DynamoDBUtil(
    private val dynamoDbOperations: DynamoDbOperations,
) {
    fun <T : Any> clearTable(clazz: Class<T>) {
        val allEntities = dynamoDbOperations.scanAll(clazz)
        allEntities.items().forEach { entity ->
            dynamoDbOperations.delete<T>(entity)
        }
    }
}
