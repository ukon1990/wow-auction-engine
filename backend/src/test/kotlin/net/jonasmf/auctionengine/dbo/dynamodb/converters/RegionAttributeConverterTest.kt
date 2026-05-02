package net.jonasmf.auctionengine.dbo.dynamodb.converters

import net.jonasmf.auctionengine.constant.Region
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

class RegionAttributeConverterTest {
    private val converter = RegionAttributeConverter()

    @Test
    fun `reads legacy region code values`() {
        assertEquals(Region.NorthAmerica, converter.transformTo(AttributeValue.fromS("us")))
        assertEquals(Region.Europe, converter.transformTo(AttributeValue.fromS("eu")))
        assertEquals(Region.Korea, converter.transformTo(AttributeValue.fromS("kr")))
        assertEquals(Region.Taiwan, converter.transformTo(AttributeValue.fromS("tw")))
    }

    @Test
    fun `reads enum name values`() {
        assertEquals(Region.NorthAmerica, converter.transformTo(AttributeValue.fromS("NorthAmerica")))
        assertEquals(Region.Europe, converter.transformTo(AttributeValue.fromS("Europe")))
    }

    @Test
    fun `writes canonical enum names`() {
        assertEquals("NorthAmerica", converter.transformFrom(Region.NorthAmerica)?.s())
        assertEquals("Europe", converter.transformFrom(Region.Europe)?.s())
    }
}
