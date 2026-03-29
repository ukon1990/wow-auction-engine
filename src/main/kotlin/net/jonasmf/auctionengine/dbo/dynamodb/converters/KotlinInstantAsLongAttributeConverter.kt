package net.jonasmf.auctionengine.dbo.dynamodb.converters

import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import java.time.Instant

class KotlinInstantAsLongAttributeConverter : AttributeConverter<Instant> {
    override fun transformFrom(input: Instant?): AttributeValue? {
        if (input == null) return null
        return AttributeValue
            .builder()
            .n(input.toEpochMilli().toString())
            .build()
    }

    override fun transformTo(input: AttributeValue?): Instant? {
        if (input == null) return null
        return Instant.ofEpochMilli(input.n()?.toLong() ?: 0L)
    }

    override fun type(): EnhancedType<Instant?> = EnhancedType.of(Instant::class.java)

    override fun attributeValueType(): AttributeValueType = AttributeValueType.N
}

fun Instant.toKotlin(): kotlin.time.Instant = kotlin.time.Instant.fromEpochMilliseconds(toEpochMilli())
