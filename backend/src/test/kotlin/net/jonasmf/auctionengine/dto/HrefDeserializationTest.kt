package net.jonasmf.auctionengine.dto

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import net.jonasmf.auctionengine.dto.realm.ConnectedRealmIndex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HrefDeserializationTest {
    private val mapper = jacksonObjectMapper()

    @Test
    fun `deserializes connected realm index href`() {
        val json =
            """
            {
              "connected_realms": [
                { "href": "https://example.com/connected-realm/1" }
              ]
            }
            """.trimIndent()

        val result: ConnectedRealmIndex = mapper.readValue(json)

        assertEquals("https://example.com/connected-realm/1", result.connectedRealms.first().href)
    }
}
