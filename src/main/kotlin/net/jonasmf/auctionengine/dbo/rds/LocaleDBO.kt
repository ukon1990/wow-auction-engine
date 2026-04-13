package net.jonasmf.auctionengine.dbo.rds

import jakarta.persistence.Entity
import jakarta.persistence.Id

@Entity
class LocaleDBO(
    @Id
    val id: Long? = null,
    val en_US: String,
    val es_MX: String,
    val pt_BR: String,
    val pt_PT: String?,
    val de_DE: String,
    val en_GB: String,
    val es_ES: String,
    val fr_FR: String,
    val it_IT: String,
    val ru_RU: String,
    val ko_KR: String,
    val zh_TW: String,
    val zh_CN: String,
)
