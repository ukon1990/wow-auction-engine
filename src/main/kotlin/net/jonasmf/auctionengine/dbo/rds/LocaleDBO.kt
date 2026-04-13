package net.jonasmf.auctionengine.dbo.rds

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "locale_dbo")
class LocaleDBO(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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
