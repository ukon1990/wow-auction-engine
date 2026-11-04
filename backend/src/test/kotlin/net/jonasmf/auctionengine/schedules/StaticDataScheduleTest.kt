package net.jonasmf.auctionengine.schedules

import net.jonasmf.auctionengine.profession.ProfessionRecipeSchedule
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import net.jonasmf.auctionengine.config.BlizzardApiProperties
import net.jonasmf.auctionengine.constant.Region
import net.jonasmf.auctionengine.service.BlizzardMediaBackfillService
import net.jonasmf.auctionengine.service.ItemSyncService
import net.jonasmf.auctionengine.profession.ProfessionRecipeSyncGuard
import net.jonasmf.auctionengine.profession.ProfessionRecipeSyncService
import net.jonasmf.auctionengine.service.TsmRegionSyncService
import org.junit.jupiter.api.Test

/** Single-flight guarding is covered by [BackgroundWorkLauncherTest]; this covers the static data toggle. */
class StaticDataScheduleTest {
    private val properties =
        BlizzardApiProperties(
            baseUrl = "https://example.test/",
            tokenUrl = "https://example.test/token",
            clientId = "id",
            clientSecret = "secret",
            regions = listOf(Region.Europe, Region.Taiwan),
        )
    private val launcher = immediateBackgroundWorkLauncher()

    @Test
    fun `scheduled and startup static data syncs skip when disabled for the deployment`() {
        val items = mockk<ItemSyncService>()
        val media = mockk<BlizzardMediaBackfillService>()
        val tsm = mockk<TsmRegionSyncService>()
        val recipes = mockk<ProfessionRecipeSyncService>()

        ItemSchedule(properties, items, launcher, false).run {
            syncItemsOnSchedule()
            syncItemsAfterStartup()
        }
        BlizzardMediaBackfillSchedule(properties, media, launcher, false).run {
            backfillMediaOnSchedule()
            backfillMediaAfterStartup()
        }
        TsmRegionSyncSchedule(properties, tsm, launcher, false).run {
            syncTsmRegionOnSchedule()
            syncTsmRegionAfterStartup()
        }
        ProfessionRecipeSchedule(properties, recipes, mockk<ProfessionRecipeSyncGuard>(), launcher, false).run {
            syncProfessionRecipesOnSchedule()
            syncProfessionRecipesAfterStartup()
        }

        verify(exactly = 0) { items.syncConfiguredStaticDataRegion() }
        verify(exactly = 0) { media.backfillConfiguredStaticDataRegion() }
        verify(exactly = 0) { tsm.syncConfiguredRegions() }
        verify(exactly = 0) { recipes.syncConfiguredStaticDataRegion(any()) }
    }

    @Test
    fun `manual sync runs even when scheduled static data sync is disabled`() {
        val tsm = mockk<TsmRegionSyncService>()
        every { tsm.syncConfiguredRegions() } just runs

        TsmRegionSyncSchedule(properties, tsm, launcher, false).syncTsmRegion()

        verify(exactly = 1) { tsm.syncConfiguredRegions() }
    }
}
