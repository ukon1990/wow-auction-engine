package net.jonasmf.auctionengine.service

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

class ConnectedRealmUpdateHistoryServiceTest {
    @Test
    fun `setUpdateToCompleted is transactional because it executes a modifying query`() {
        val method =
            ConnectedRealmUpdateHistoryService::class.java.getMethod(
                "setUpdateToCompleted",
                Int::class.javaPrimitiveType,
                ZonedDateTime::class.java,
            )

        val transactional = AnnotatedElementUtils.findMergedAnnotation(method, Transactional::class.java)

        assertNotNull(transactional)
    }
}
