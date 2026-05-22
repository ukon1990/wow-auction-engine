package net.jonasmf.auctionengine.mapper.admin

import aws.sdk.kotlin.services.cognitoidentityprovider.model.AttributeType
import aws.sdk.kotlin.services.cognitoidentityprovider.model.UserStatusType
import aws.sdk.kotlin.services.cognitoidentityprovider.model.UserType
import aws.smithy.kotlin.runtime.time.Instant
import net.jonasmf.auctionengine.generated.model.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ToUserTest {
    @Test
    fun `maps UserType to Users`() {
        val userType =
            UserType {
                attributes =
                    listOf(
                        AttributeType {
                            name = "sub"
                            value = "user-sub"
                        },
                        AttributeType {
                            name = "email"
                            value = "user@example.com"
                        },
                        AttributeType {
                            name = "email_verified"
                            value = "true"
                        },
                    )
                username = "username"
                userStatus = UserStatusType.Confirmed
                userCreateDate = Instant.fromIso8601("2026-05-21T10:15:30Z")
                userLastModifiedDate = Instant.fromIso8601("2026-05-22T12:30:45Z")
            }

        val user = userType.toUser()

        assertEquals(
            User(
                sub = "user-sub",
                username = "username",
                email = "user@example.com",
                emailVerified = true,
                status = "CONFIRMED",
                created = OffsetDateTime.of(2026, 5, 21, 10, 15, 30, 0, ZoneOffset.UTC),
                lastModified = OffsetDateTime.of(2026, 5, 22, 12, 30, 45, 0, ZoneOffset.UTC),
            ),
            user,
        )
    }
}
