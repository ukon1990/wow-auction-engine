package net.jonasmf.auctionengine.config

import io.micrometer.context.ContextRegistry
import io.micrometer.context.ThreadLocalAccessor
import io.micrometer.context.integration.Slf4jThreadLocalAccessor
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.springframework.context.annotation.Configuration
import reactor.core.publisher.Hooks

@Configuration
class MdcContextPropagationConfig {
    private val registry = ContextRegistry.getInstance()
    private var previousAccessor: ThreadLocalAccessor<*>? = null
    private var automaticPropagationWasEnabled = false

    @PostConstruct
    fun registerMdcPropagation() {
        previousAccessor = registry.threadLocalAccessors.firstOrNull { it.key() == Slf4jThreadLocalAccessor.KEY }
        automaticPropagationWasEnabled = Hooks.isAutomaticContextPropagationEnabled()
        registry.registerThreadLocalAccessor(Slf4jThreadLocalAccessor())
        Hooks.enableAutomaticContextPropagation()
    }

    @PreDestroy
    fun restoreContextPropagation() {
        registry.removeThreadLocalAccessor(Slf4jThreadLocalAccessor.KEY)
        previousAccessor?.let(registry::registerThreadLocalAccessor)
        if (!automaticPropagationWasEnabled) {
            Hooks.disableAutomaticContextPropagation()
        }
    }
}
