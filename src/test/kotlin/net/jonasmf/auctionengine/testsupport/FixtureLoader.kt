package net.jonasmf.auctionengine.testsupport

import java.nio.charset.StandardCharsets

fun loadFixture(
    anchor: Any,
    path: String,
): String =
    anchor::class.java
        .getResourceAsStream(path)
        ?.use { inputStream -> String(inputStream.readAllBytes(), StandardCharsets.UTF_8) }
        ?: error("Missing test resource: $path")
