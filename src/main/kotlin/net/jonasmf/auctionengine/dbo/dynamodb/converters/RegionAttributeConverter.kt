package net.jonasmf.auctionengine.dbo.dynamodb.converters

import net.jonasmf.auctionengine.constant.Region
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

class RegionAttributeConverter : AttributeConverter<Region> {
    override fun transformFrom(input: Region?): AttributeValue? {
        if (input == null) return null
        return AttributeValue
            .builder()
            .s(input.name)
            .build()
    }

    override fun transformTo(input: AttributeValue?): Region? {
        val value = input?.s() ?: return null
        return Region.fromString(value)
    }

    override fun type(): EnhancedType<Region> = EnhancedType.of(Region::class.java)

    override fun attributeValueType(): AttributeValueType = AttributeValueType.S
}
