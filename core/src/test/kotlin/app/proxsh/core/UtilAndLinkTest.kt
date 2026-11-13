package app.proxsh.core

import app.proxsh.core.link.LinkCandidate
import app.proxsh.core.link.LinkChooser
import app.proxsh.core.link.LinkKind
import app.proxsh.core.link.LinkProber
import app.proxsh.core.link.LinkScore
import app.proxsh.core.trust.CheckCode
import app.proxsh.core.util.FileNames
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UtilAndLinkTest {
    @Test
    fun `file names cannot escape the folder`() {
        assertEquals("evil.dll", FileNames.sanitize("../../Windows/system32/evil.dll"))
        assertEquals("boot.ini", FileNames.sanitize("..\\..\\boot.ini"))
        assertEquals("file", FileNames.sanitize(".."))
        assertEquals("file", FileNames.sanitize(""))
        assertEquals("a_b_c_d_e_f_g_h.txt", FileNames.sanitize("a<b>c:d\"e|f?g*h.txt"))
        assertEquals("_CON.txt", FileNames.sanitize("CON.txt"))
        assertEquals("ఫోటో 2026.jpg", FileNames.sanitize("ఫోటో 2026.jpg"))
        val long = FileNames.sanitize("x".repeat(500) + ".pdf")
        assertEquals(200, long.length)
        assertTrue(long.endsWith(".pdf"))
    }

    @Test
    fun `existing files are never overwritten`() {
        val dir = Files.createTempDirectory("proxsh-names").toFile()
        assertEquals("a.jpg", FileNames.uniqueIn(dir, "a.jpg").name)
        dir.resolve("a.jpg").writeText("x")
        assertEquals("a (1).jpg", FileNames.uniqueIn(dir, "a.jpg").name)
        dir.resolve("a (1).jpg").writeText("x")
        assertEquals("a (2).jpg", FileNames.uniqueIn(dir, "a.jpg").name)
        dir.deleteRecursively()
    }

    @Test
    fun `check code is the same on both devices and differs per pair`() {
        val a = "a".repeat(64)
        val b = "b".repeat(64)
        val c = "c".repeat(64)
        assertEquals(CheckCode.of(a, b), CheckCode.of(b, a))
        assertEquals(4, CheckCode.of(a, b).length)
        assertNotEquals(CheckCode.of(a, b), CheckCode.of(a, c))
    }

    private fun candidate(kind: LinkKind, metered: Boolean = false) = LinkCandidate(kind, kind.name, 1, metered)

    private fun chooser(speeds: Map<LinkKind, Double?>, delays: Map<LinkKind, Long> = emptyMap()) = LinkChooser(
        LinkProber { c ->
            delays[c.kind]?.let { delay(it) }
            speeds[c.kind]?.let { LinkScore(c, 5.0, it) }
        },
        probeTimeoutMillis = 300,
    )

    @Test
    fun `fastest measured link wins, including the internet`() = runBlocking {
        val c = chooser(mapOf(LinkKind.LAN to 8e6, LinkKind.INTERNET to 20e6))
        val best = c.best(listOf(candidate(LinkKind.LAN), candidate(LinkKind.INTERNET)))
        assertEquals(LinkKind.INTERNET, best?.candidate?.kind)
    }

    @Test
    fun `local network wins when it measures faster`() = runBlocking {
        val c = chooser(mapOf(LinkKind.LAN to 30e6, LinkKind.INTERNET to 3e6))
        val best = c.best(listOf(candidate(LinkKind.INTERNET), candidate(LinkKind.LAN)))
        assertEquals(LinkKind.LAN, best?.candidate?.kind)
    }

    @Test
    fun `a link that cannot be reached or is too slow to answer is skipped`() = runBlocking {
        val c = chooser(
            mapOf(LinkKind.LAN to null, LinkKind.HOTSPOT to 9e6, LinkKind.INTERNET to 50e6),
            delays = mapOf(LinkKind.INTERNET to 2000),
        )
        val best = c.best(listOf(candidate(LinkKind.LAN), candidate(LinkKind.HOTSPOT), candidate(LinkKind.INTERNET)))
        assertEquals(LinkKind.HOTSPOT, best?.candidate?.kind)
    }

    @Test
    fun `metered links are skipped unless allowed`() = runBlocking {
        val c = chooser(mapOf(LinkKind.LAN to 5e6, LinkKind.INTERNET to 40e6))
        val list = listOf(candidate(LinkKind.LAN), candidate(LinkKind.INTERNET, metered = true))
        assertEquals(LinkKind.LAN, c.best(list, allowMetered = false)?.candidate?.kind)
        assertEquals(LinkKind.INTERNET, c.best(list, allowMetered = true)?.candidate?.kind)
        assertNull(c.best(listOf(candidate(LinkKind.INTERNET, metered = true)), allowMetered = false))
    }

    @Test
    fun `switching needs a clear win, not noise`() {
        val c = chooser(emptyMap())
        val lan = LinkScore(candidate(LinkKind.LAN), 2.0, 10e6)
        val slightlyFaster = LinkScore(candidate(LinkKind.INTERNET), 2.0, 11e6)
        val muchFaster = LinkScore(candidate(LinkKind.INTERNET), 2.0, 20e6)
        assertFalse(c.shouldSwitch(lan, slightlyFaster))
        assertTrue(c.shouldSwitch(lan, muchFaster))
        assertFalse(c.shouldSwitch(lan, lan))
    }
}
