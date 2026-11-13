package app.proxsh.core.trust

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/** A device the user chose to accept from every time. */
@Serializable
data class TrustedDevice(
    val fingerprint: String,
    val name: String,
    val addedAt: Long,
    val lastSeen: Long = addedAt,
    val autoAccept: Boolean = true,
)

/** Remembers trusted devices. Pass a file to keep them between runs, or null for memory only. */
class TrustStore(private val file: File? = null) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val state = MutableStateFlow(load())

    val devices: StateFlow<List<TrustedDevice>> get() = state

    fun get(fingerprint: String): TrustedDevice? = state.value.firstOrNull { it.fingerprint == fingerprint }

    @Synchronized
    fun add(fingerprint: String, name: String, autoAccept: Boolean = true) {
        val now = System.currentTimeMillis()
        val rest = state.value.filterNot { it.fingerprint == fingerprint }
        state.value = rest + TrustedDevice(fingerprint, name, addedAt = now, autoAccept = autoAccept)
        save()
    }

    @Synchronized
    fun touch(fingerprint: String) {
        val now = System.currentTimeMillis()
        state.value = state.value.map { if (it.fingerprint == fingerprint) it.copy(lastSeen = now) else it }
        save()
    }

    @Synchronized
    fun revoke(fingerprint: String) {
        state.value = state.value.filterNot { it.fingerprint == fingerprint }
        save()
    }

    private fun load(): List<TrustedDevice> {
        val f = file ?: return emptyList()
        if (!f.exists()) return emptyList()
        return runCatching { json.decodeFromString(ListSerializer(TrustedDevice.serializer()), f.readText()) }
            .getOrDefault(emptyList())
    }

    private fun save() {
        val f = file ?: return
        f.parentFile?.mkdirs()
        val tmp = File(f.path + ".tmp")
        tmp.writeText(json.encodeToString(ListSerializer(TrustedDevice.serializer()), state.value))
        if (!tmp.renameTo(f)) {
            f.delete()
            tmp.renameTo(f)
        }
    }
}

/**
 * A 4-digit code both screens show for the same pair of devices, so the user can see that the phone
 * asking to send is the phone in their hand and not a stranger with the same name.
 */
object CheckCode {
    fun of(fingerprintA: String, fingerprintB: String): String {
        val (first, second) = listOf(fingerprintA, fingerprintB).sorted()
        val digest = MessageDigest.getInstance("SHA-256").digest("$first|$second".toByteArray())
        val number = ((digest[0].toInt() and 0xff) shl 8 or (digest[1].toInt() and 0xff)) % 10000
        return number.toString().padStart(4, '0')
    }
}
