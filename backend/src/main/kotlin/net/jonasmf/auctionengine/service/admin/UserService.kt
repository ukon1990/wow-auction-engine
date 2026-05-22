package net.jonasmf.auctionengine.service.admin

import aws.sdk.kotlin.services.cognitoidentityprovider.CognitoIdentityProviderClient
import aws.sdk.kotlin.services.cognitoidentityprovider.model.ListUsersRequest
import net.jonasmf.auctionengine.generated.model.User
import net.jonasmf.auctionengine.mapper.admin.toUser
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class UserService(
    // @Value("\${spring.cloud.aws.cognito.pool_id}")
    // We can derive the pool ID from here
    @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private val issuerUri: String,
    private val cognitoRegion: String = "eu-north-1",
) {
    private val logger = LoggerFactory.getLogger(UserService::class.java)

    fun getCognitoPoolId(): String? {
        if (issuerUri == null) return null
        val parts = issuerUri.split("amazonaws.com/")
        val poolId = parts[parts.size - 1] // The last part is the pool id
        return poolId ?: null
    }

    suspend fun getUsers(): List<User> {
        val poolId = getCognitoPoolId()
        val request =
            ListUsersRequest {
                this.userPoolId = poolId
            }
        var users: List<User> = mutableListOf<User>()

        logger.info("Fetching users for pool $poolId...")
        CognitoIdentityProviderClient.fromEnvironment { region = cognitoRegion }.use { cognitoClient ->
            val response = cognitoClient.listUsers(request)
            users =
                response.users?.map {
                    it.toUser()
                }!!
        }
        return users
    }
}
