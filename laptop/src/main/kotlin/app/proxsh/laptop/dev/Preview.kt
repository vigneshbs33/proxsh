package app.proxsh.laptop.dev

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import app.proxsh.core.NodeConfig
import app.proxsh.core.ProxshNode
import app.proxsh.core.ReceiveMode
import app.proxsh.core.transfer.OutgoingItem
import app.proxsh.laptop.AppModel
import app.proxsh.laptop.Settings
import app.proxsh.laptop.ui.HomeScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.util.Random

/**
 * Developer tool: runs a laptop node and a simulated phone node on this machine, drives a real
 * transfer between them, and renders the window offscreen to PNG files. Nothing on screen is captured.
 *
 *   gradle :laptop:preview
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) = runBlocking {
    val out = File(args.firstOrNull() ?: "build/previews").apply { mkdirs() }
    val work = File(out, "work").apply { deleteRecursively(); mkdirs() }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val discoveryPort = 54319

    val laptop = ProxshNode(
        NodeConfig(File(work, "laptop/data"), File(work, "laptop/received"), "SUNDAY", "laptop", tcpPort = 0, discoveryPort = discoveryPort),
        scope,
    )
    laptop.start()
    val model = AppModel(
        laptop,
        CoroutineScope(SupervisorJob() + Dispatchers.Main),
        Settings("SUNDAY", File(work, "laptop/received").absolutePath),
        saveSettings = {},
        needAttention = { _, _ -> },
    )

    val scene = ImageComposeScene(1080, 740, Density(1f)) {
        HomeScreen(model, pickFiles = { emptyList() }, chooseFolder = { null })
    }
    var clock = 0L
    fun shot(name: String) {
        // Entrance animations start on the frame after a thing first appears, so draw one frame to
        // compose it, one to start its animation, and a last one well after it has finished.
        scene.render(clock)
        clock += 16_000_000L
        scene.render(clock)
        clock += 3_000_000_000L
        val image = scene.render(clock)
        File(out, "$name.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        clock += 1_000_000L
        println("wrote $name.png")
    }

    delay(500)
    shot("1-empty")

    val phone = ProxshNode(
        NodeConfig(File(work, "phone/data"), File(work, "phone/received"), "Vignesh's Pixel", "phone", tcpPort = 0, discoveryPort = discoveryPort),
        scope,
    )
    phone.start()
    withTimeout(20_000) { while (laptop.peers.value.none { it.id == phone.identity.fingerprint }) delay(100) }
    delay(300)
    shot("2-device-found")

    fun sample(name: String, size: Int): File {
        val f = File(work, name)
        val bytes = ByteArray(size)
        Random(size.toLong()).nextBytes(bytes)
        f.writeBytes(bytes)
        return f
    }

    val laptopPeerSeenByPhone = withTimeout(20_000) {
        while (phone.peers.value.none { it.id == laptop.identity.fingerprint }) delay(100)
        phone.peers.value.first { it.id == laptop.identity.fingerprint }
    }

    // A stranger asks to send: the accept dialog.
    val firstItems = listOf(
        OutgoingItem.file(sample("IMG-2045 from WhatsApp.jpg", 2_400_000)),
        OutgoingItem.file(sample("Lecture notes.pdf", 5_100_000)),
    )
    val firstSend = scope.async { phone.send(laptopPeerSeenByPhone, firstItems) }
    withTimeout(10_000) { while (model.incoming == null) delay(50) }
    delay(300)
    shot("3-accept-request")

    model.answer(accept = true, always = true)
    firstSend.await()
    delay(300)

    // More activity: text from the phone, a file from the laptop, and one transfer caught mid-way.
    phone.send(laptopPeerSeenByPhone, listOf(OutgoingItem.Text("Meeting at 4 in lab 3 — తెలుగు ✓")))
    phone.trustStore.add(laptop.identity.fingerprint, "SUNDAY")
    val peer = laptop.peers.value.first { it.id == phone.identity.fingerprint }
    model.selectedId = peer.id
    model.sendFiles(peer, listOf(sample("report_final.docx", 800_000)))
    delay(1200)

    val big = sample("Project demo video.mp4", 700_000_000)
    model.sendFiles(peer, listOf(big))
    withTimeout(15_000) { while (model.live.values.none { it.totalBytes > 0 && it.doneBytes in 1 until it.totalBytes }) delay(10) }
    shot("4-in-progress")
    withTimeout(60_000) { while (model.live.isNotEmpty()) delay(100) }
    delay(300)
    shot("5-activity")

    model.showSettings = true
    delay(200)
    shot("6-settings")
    model.showSettings = false
    laptop.setReceiveMode(ReceiveMode.TRUSTED_ONLY)

    scene.close()
    phone.close()
    laptop.close()
    println("done")
    System.exit(0)
}
