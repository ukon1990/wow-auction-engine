package net.jonasmf.auctionengine.dbo.rds

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

private const val LOCALE_TEXT_COLUMN_LENGTH = 512

@Entity
@Table(
    name = "locale_dbo",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_locale_dbo_source",
            columnNames = ["source_type", "source_key", "source_field"],
        ),
    ],
)
class LocaleDBO(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "source_type", nullable = false)
    val sourceType: String,
    @Column(name = "source_key", nullable = false)
    val sourceKey: String,
    @Column(name = "source_field", nullable = false)
    val sourceField: String,
    @Column(length = LOCALE_TEXT_COLUMN_LENGTH)
    val en_US: String,
    @Column(length = LOCALE_TEXT_COLUMN_LENGTH)
    val es_MX: String,
    @Column(length = LOCALE_TEXT_COLUMN_LENGTH)
    val pt_BR: String,
    @Column(length = LOCALE_TEXT_COLUMN_LENGTH)
    val pt_PT: String?,
    @Column(length = LOCALE_TEXT_COLUMN_LENGTH)
    val de_DE: String,
    @Column(length = LOCALE_TEXT_COLUMN_LENGTH)
    val en_GB: String,
    @Column(length = LOCALE_TEXT_COLUMN_LENGTH)
    val es_ES: String,
    @Column(length = LOCALE_TEXT_COLUMN_LENGTH)
    val fr_FR: String,
    @Column(length = LOCALE_TEXT_COLUMN_LENGTH)
    val it_IT: String,
    @Column(length = LOCALE_TEXT_COLUMN_LENGTH)
    val ru_RU: String,
    @Column(length = LOCALE_TEXT_COLUMN_LENGTH)
    val ko_KR: String,
    @Column(length = LOCALE_TEXT_COLUMN_LENGTH)
    val zh_TW: String,
    @Column(length = LOCALE_TEXT_COLUMN_LENGTH)
    val zh_CN: String,
)
