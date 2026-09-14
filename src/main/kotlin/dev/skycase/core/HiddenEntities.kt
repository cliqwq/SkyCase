package dev.skycase.core
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Task 13: UUIDs of ItemEntities whose rendering is being skipped client-side while a Dragon-fight
 * reveal is gathering/pending -- consulted by EntityRenderDispatcherMixin (render thread) and written
 * by ChatTrigger's TickEvent handler (also render thread, but ConcurrentHashMap.newKeySet() keeps the
 * cross-thread invariant explicit and costs nothing here). Purely a render-side skip: nothing here
 * touches packets or pickup.
 */
object HiddenEntities {
    val hidden: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    fun clear() = hidden.clear()
}
