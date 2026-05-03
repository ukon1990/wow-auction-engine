package net.jonasmf.auctionengine.controller

import net.jonasmf.auctionengine.generated.api.RealmApi
import net.jonasmf.auctionengine.generated.model.Realm
import net.jonasmf.auctionengine.generated.model.RealmDetail
import net.jonasmf.auctionengine.service.RealmQueryService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class RealmController(
    private val realmQueryService: RealmQueryService,
) : RealmApi {
    override fun listRealms(): ResponseEntity<List<Realm>> = ResponseEntity.ok(realmQueryService.listAllRealms())

    override fun getRealm(
        region: String,
        slug: String,
    ): ResponseEntity<RealmDetail> {
        val detail = realmQueryService.getRealmDetail(region, slug) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(detail)
    }
}
