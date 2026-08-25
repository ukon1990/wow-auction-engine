package net.jonasmf.auctionengine.repository.rds.admin

import jakarta.persistence.OrderBy
import net.jonasmf.auctionengine.dbo.rds.admin.AdminAuctinHouseRow
import net.jonasmf.auctionengine.dbo.rds.realm.AuctionHouse
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface AdminAuctionHouseRepository : JpaRepository<AuctionHouse, Int> {
    @Query(
        """
        FROM AuctionHouse a
        LEFT JOIN ConnectedRealm
        LEFT JOIN Realm
        WHERE a.id = :id
    """,
    )
    fun getByid(id: Int): AuctionHouse?

    @Query(
        nativeQuery = true,
        value =
            """
        WITH ah AS (
            SElECT
                a.*,
                (SELECT COUNT(*) FROM auction_house) AS total_items
            FROM auction_house a
            LIMIT :pageSize
            OFFSET :offset
        )
        SELECT
            ah.total_items AS page_total_items,
            ah.id AS auction_house_id,
            ah.connected_id AS auction_house_connected_id,
            ah.auto_update AS auction_house_auto_update,
            ah.region AS auction_house_region,

            ah.lowest_delay AS auction_house_lowest_delay,
            ah.avg_delay AS auction_house_avg_delay,
            ah.highest_delay AS auction_house_highest_delay,

            ah.last_auction_price_delete_event AS auction_house_last_auction_price_delete_event,
            ah.last_history_delete_event AS auction_house_last_history_delete_event,
            ah.last_history_delete_event_daily AS auction_house_last_history_delete_event_daily,

            ah.last_modified AS auction_house_last_modified,
            ah.next_update AS auction_house_next_update,

            r.id AS realm_id,
            r.slug AS realm_slug,
            r.name AS realm_name,
            r.locale AS realm_locale,
            r.category AS realm_category,
            r.game_build AS realm_game_build,
            r.timezone AS realm_timezone
        FROM ah
            JOIN connected_realm cr ON cr.id = ah.connected_id
            JOIN connected_realm_realms crr ON crr.connected_realm_id = cr.id
            JOIN realm r ON r.id = crr.realms_id
        ORDER BY :orderBy
    """,
    )
    fun findAllWithQuery(
        offset: Int,
        pageSize: Int,
        orderBy: String,
    ): List<AdminAuctinHouseRow>
}
