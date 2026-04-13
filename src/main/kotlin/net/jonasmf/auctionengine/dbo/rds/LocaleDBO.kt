package net.jonasmf.auctionengine.dbo.rds

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

private const val LOCALE_TEXT_COLUMN_DEFINITION = "TEXT"

@Entity
@Table(
    name = "locale",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_locale_source",
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
    @Column(columnDefinition = LOCALE_TEXT_COLUMN_DEFINITION)
    val en_US: String,
    @Column(columnDefinition = LOCALE_TEXT_COLUMN_DEFINITION)
    val es_MX: String,
    @Column(columnDefinition = LOCALE_TEXT_COLUMN_DEFINITION)
    val pt_BR: String,
    @Column(columnDefinition = LOCALE_TEXT_COLUMN_DEFINITION)
    val pt_PT: String?,
    @Column(columnDefinition = LOCALE_TEXT_COLUMN_DEFINITION)
    val de_DE: String,
    @Column(columnDefinition = LOCALE_TEXT_COLUMN_DEFINITION)
    val en_GB: String,
    @Column(columnDefinition = LOCALE_TEXT_COLUMN_DEFINITION)
    val es_ES: String,
    @Column(columnDefinition = LOCALE_TEXT_COLUMN_DEFINITION)
    val fr_FR: String,
    @Column(columnDefinition = LOCALE_TEXT_COLUMN_DEFINITION)
    val it_IT: String,
    @Column(columnDefinition = LOCALE_TEXT_COLUMN_DEFINITION)
    val ru_RU: String,
    @Column(columnDefinition = LOCALE_TEXT_COLUMN_DEFINITION)
    val ko_KR: String,
    @Column(columnDefinition = LOCALE_TEXT_COLUMN_DEFINITION)
    val zh_TW: String,
    @Column(columnDefinition = LOCALE_TEXT_COLUMN_DEFINITION)
    val zh_CN: String,
)
