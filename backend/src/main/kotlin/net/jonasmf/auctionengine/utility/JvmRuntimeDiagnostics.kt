package net.jonasmf.auctionengine.utility

object JvmRuntimeDiagnostics {
    fun snapshot(): String {
        val runtime = Runtime.getRuntime()
        val megabyte = 1024L * 1024L
        val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / megabyte
        val totalMb = runtime.totalMemory() / megabyte
        val maxMb = runtime.maxMemory() / megabyte
        val freeMb = runtime.freeMemory() / megabyte
        return "jvmMemoryMb{used=$usedMb,total=$totalMb,free=$freeMb,max=$maxMb}"
    }
}
