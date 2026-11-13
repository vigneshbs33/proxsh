package app.proxsh.core.link

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

enum class LinkKind(val label: String) {
    LAN("Wi-Fi"),
    HOTSPOT("Hotspot"),
    INTERNET("Internet"),
    BLUETOOTH("Bluetooth"),
}

/** One way to reach another device: an address, a port, and what kind of link it is. */
data class LinkCandidate(
    val kind: LinkKind,
    val host: String,
    val port: Int,
    /** Mobile data and similar. Skipped for big transfers unless the user allows it. */
    val metered: Boolean = false,
)

/** What a speed test of one link found. */
data class LinkScore(
    val candidate: LinkCandidate,
    val rttMillis: Double,
    val bytesPerSecond: Double,
)

fun interface LinkProber {
    /** Tests the link and returns its speed, or null if it could not be reached. */
    suspend fun probe(candidate: LinkCandidate): LinkScore?
}

/**
 * Picks the fastest way to reach a device by measuring each link, not by a fixed order.
 * The internet wins when it measures faster, and loses when the local network is faster.
 */
class LinkChooser(
    private val prober: LinkProber,
    private val probeTimeoutMillis: Long = 2500,
    /** A new link must be this much faster than the current one before it is worth switching. */
    private val switchMargin: Double = 1.25,
) {
    suspend fun best(candidates: List<LinkCandidate>, allowMetered: Boolean = true): LinkScore? {
        val usable = candidates.filter { allowMetered || !it.metered }
        if (usable.isEmpty()) return null
        if (usable.size == 1) return LinkScore(usable[0], 0.0, 0.0)
        val scores = coroutineScope {
            usable.map { candidate ->
                async { withTimeoutOrNull(probeTimeoutMillis) { runCatching { prober.probe(candidate) }.getOrNull() } }
            }.awaitAll()
        }.filterNotNull()
        return scores.maxByOrNull { it.bytesPerSecond }
    }

    fun shouldSwitch(current: LinkScore?, best: LinkScore?): Boolean {
        if (best == null) return false
        if (current == null) return true
        if (best.candidate == current.candidate) return false
        return best.bytesPerSecond > current.bytesPerSecond * switchMargin
    }
}
