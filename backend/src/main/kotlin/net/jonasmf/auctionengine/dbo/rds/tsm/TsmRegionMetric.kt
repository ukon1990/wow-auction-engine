package net.jonasmf.auctionengine.dbo.rds.tsm

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.constant.TsmSubjectType
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "tsm_region_metric")
@IdClass(TsmRegionMetricId::class)
class TsmRegionMetric(
    @Id
    @Enumerated(EnumType.STRING)
    var region: Region = Region.Europe,
    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type")
    var subjectType: TsmSubjectType = TsmSubjectType.ITEM,
    @Id
    @Column(name = "subject_id")
    var subjectId: Int = 0,
    @Column(name = "sale_rate", nullable = false, precision = 20, scale = 8)
    var saleRate: BigDecimal = BigDecimal.ZERO,
    @Column(name = "sold_per_day", nullable = false, precision = 20, scale = 8)
    var soldPerDay: BigDecimal = BigDecimal.ZERO,
    @Column(name = "market_value")
    var marketValue: Long? = null,
    var historical: Long? = null,
    @Column(name = "avg_sale_price")
    var avgSalePrice: Long? = null,
    @Column(name = "source_updated_at", nullable = false)
    var sourceUpdatedAt: Instant = Instant.EPOCH,
)
