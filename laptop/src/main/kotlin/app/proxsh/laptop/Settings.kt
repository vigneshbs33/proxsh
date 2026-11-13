package app.proxsh.laptop

import app.proxsh.core.ReceiveMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.InetAddress

@Serializable
data class Settings(
    val deviceName: String,
    val receiveDir: String,
    val mode: ReceiveMode = ReceiveMode.ASK,
)

object SettingsStore {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    /** Where ProxSh keeps its identity and settings. Set PROXSH_DATA_DIR to put it somewhere else. */
    fun dataDir(): File {
        System.getenv("PROXSH_DATA_DIR")?.takeIf { it.isNotBlank() }?.let { return File(it) }
        val appData = System.getenv("APPDATA")
        return if (appData != null) File(appData, "ProxSh") else File(System.getProperty("user.home"), ".proxsh")
    }

    fun defaultReceiveDir(): File {
        System.getenv("PROXSH_RECEIVE_DIR")?.takeIf { it.isNotBlank() }?.let { return File(it) }
        return File(System.getProperty("user.home"), "Downloads/ProxSh")
    }

    fun load(dataDir: File): Settings {
        val file = File(dataDir, "settings.json")
        val saved = runCatching { json.decodeFromString(Settings.serializer(), file.readText()) }.getOrNull()
        val name = saved?.deviceName ?: runCatching { InetAddress.getLocalHost().hostName }.getOrNull().orEmpty().ifBlank { "My laptop" }
        val receive = System.getenv("PROXSH_RECEIVE_DIR")?.takeIf { it.isNotBlank() } ?: saved?.receiveDir ?: defaultReceiveDir().absolutePath
        return Settings(name, receive, saved?.mode ?: ReceiveMode.ASK)
    }

    fun save(dataDir: File, settings: Settings) {
        dataDir.mkdirs()
        File(dataDir, "settings.json").writeText(json.encodeToString(Settings.serializer(), settings))
    }
}
