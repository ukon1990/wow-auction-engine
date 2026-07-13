package net.jonasmf.auctionengine.service

import net.jonasmf.auctionengine.generated.model.ProfessionAllocation
import net.jonasmf.auctionengine.generated.model.ProfessionProfileRequest
import net.jonasmf.auctionengine.generated.model.ProfileCharacter
import net.jonasmf.auctionengine.repository.rds.ProfileRepository
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class ProfileServiceTest {
    private val repository = mock(ProfileRepository::class.java)
    private val service = ProfileService(repository)

    @Test
    fun `rejects a tree from another profession before replacing profile`() {
        `when`(repository.findCharacter("user-a", 7)).thenReturn(character())
        `when`(repository.treeBelongsToProfession(99, 164)).thenReturn(false)

        assertThatThrownBy {
            service.putProfile("user-a", 7, 164, ProfessionProfileRequest(99, emptyList()))
        }.isInstanceOf(ResponseStatusException::class.java)
            .extracting { (it as ResponseStatusException).statusCode }
            .isEqualTo(HttpStatus.BAD_REQUEST)

        verify(repository, never()).replaceProfile(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList())
    }

    @Test
    fun `rejects non-positive allocation ranks before replacing profile`() {
        `when`(repository.findCharacter("user-a", 7)).thenReturn(character())
        `when`(repository.treeBelongsToProfession(99, 164)).thenReturn(true)

        assertThatThrownBy {
            service.putProfile("user-a", 7, 164, ProfessionProfileRequest(99, listOf(ProfessionAllocation(4, -1))))
        }.isInstanceOf(ResponseStatusException::class.java)
            .extracting { (it as ResponseStatusException).statusCode }
            .isEqualTo(HttpStatus.BAD_REQUEST)

        verify(repository, never()).replaceProfile(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList())
    }

    private fun character() = ProfileCharacter(7, "eu", "draenor", "Owner")
}
