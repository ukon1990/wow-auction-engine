package net.jonasmf.auctionengine.schedules

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

internal fun immediateBackgroundWorkLauncher(): BackgroundWorkLauncher =
    BackgroundWorkLauncher(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
