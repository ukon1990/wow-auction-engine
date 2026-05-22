package net.jonasmf.auctionengine.mapper.admin

import aws.sdk.kotlin.services.cognitoidentityprovider.model.UserType
import aws.smithy.kotlin.runtime.time.toJvmInstant
import net.jonasmf.auctionengine.generated.model.User
import java.time.OffsetDateTime
import java.time.ZoneOffset

fun UserType.toUser(): User =
    User(
        sub = getAttributeByKey(UserAttributeName.Sub),
        username = username,
        email = getAttributeByKey(UserAttributeName.Email),
        emailVerified =
            getAttributeByKey(UserAttributeName.EmailVerified)?.equals("true"),
        status = userStatus?.value,
        created = OffsetDateTime.ofInstant(userCreateDate?.toJvmInstant(), ZoneOffset.UTC),
        lastModified = OffsetDateTime.ofInstant(userLastModifiedDate?.toJvmInstant(), ZoneOffset.UTC),
    )

// It is here, as it's the only context I imagine that I will use it
enum class UserAttributeName(
    val attributeName: String,
) {
    Email("email"),
    EmailVerified("email_verified"),
    Sub("sub"),
}

fun UserType.getAttributeByKey(key: UserAttributeName): String? =
    attributes
        ?.find {
            it.name == key.attributeName
        }?.value
