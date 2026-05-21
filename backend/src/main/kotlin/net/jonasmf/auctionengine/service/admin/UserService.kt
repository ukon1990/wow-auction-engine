package net.jonasmf.auctionengine.service.admin

import aws.sdk.kotlin.services.cognitoidentityprovider.CognitoIdentityProviderClient
import aws.sdk.kotlin.services.cognitoidentityprovider.model.ListUsersRequest
import net.jonasmf.auctionengine.generated.model.Users
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class UserService(
    @Value("\${aws.cognito.identity-provider.url}")
    private val cognitoPoolId: String,
) {
    suspend fun getUsers(): List<Users> {
        val request =
            ListUsersRequest {
                this.userPoolId = userPoolId
            }
        CognitoIdentityProviderClient.fromEnvironment { region = "us-east-1" }.use { cognitoClient ->
            val response = cognitoClient.listUsers(request)
            response.users?.forEach { user ->
                println("The user name is ${user.username}")
            }
        }
        return emptyList<Users>()
    }
}
