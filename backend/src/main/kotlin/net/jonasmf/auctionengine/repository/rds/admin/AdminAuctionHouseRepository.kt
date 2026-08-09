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
        """
        WITH ah AS (
            SElECT
                a,
                (SELECT COUNT(*) FROM AuctionHouse) AS total_items
            FROM AuctionHouse a
            LIMIT :pageSize
            OFFSET :offset
        )
        SELECT
            ah.total_items AS page_total_items,
            ah.id AS auction_house_id,
            ah.connectedId AS auction_house_connected_id,
            ah.autoUpdate AS auction_house_auto_update,
            ah.region AS auction_house_region,

            ah.lowestDelay AS auction_house_lowest_delay,
            ah.avgDelay AS auction_house_avg_delay,
            ah.highestDelay AS auction_house_highest_delay,

            ah.lastHistoryDeleteEvent AS auction_house_last_auction_price_delete_event,
            ah.lastHistoryDeleteEvent AS auction_house_last_history_delete_event,
            ah.lastHistoryDeleteEventDaily AS auction_house_last_history_delete_event_daily,

            ah.lastModified AS auction_house_updated_at,
            ah.nextUpdate AS auction_house_next_update_at,

            r.id AS realm_id,
            r.slug AS realm_slug,
            r.name AS realm_name,
            r.locale AS realm_locale,
            r.category AS realm_category,
            r.gameBuild AS realm_game_build,
            r.timezone AS realm_timezone
        FROM ah
            LEFT JOIN ConnectedRealm cr ON cr.auctionHouse = ah
            LEFT JOIN cr.realms r
        ORDER BY :orderBy
    """,
    )
    fun findAllWithQuery(
        offset: Int,
        pageSize: Int,
        orderBy: String,
    ): List<AdminAuctinHouseRow>
}
