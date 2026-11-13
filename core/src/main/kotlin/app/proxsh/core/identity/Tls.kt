package app.proxsh.core.identity

import app.proxsh.core.util.sha256Hex
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedTrustManager

/**
 * Encrypted connections between two ProxSh devices. Both sides show a certificate.
 *
 * The handshake accepts any certificate on purpose: who the other device is gets decided afterwards
 * from its fingerprint (trusted list, or the Accept prompt), never from a certificate authority.
 * Nothing is sent or saved before that check.
 */
class TlsFactory(identity: Identity) {
    private val context: SSLContext = SSLContext.getInstance("TLS").also { ctx ->
        val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keys.init(identity.keyStore(), Identity.PASSWORD)
        ctx.init(keys.keyManagers, arrayOf(AcceptAnyCertificate), SecureRandom())
    }

    fun server(port: Int): SSLServerSocket {
        val socket = context.serverSocketFactory.createServerSocket(port) as SSLServerSocket
        socket.reuseAddress = true
        socket.needClientAuth = true
        socket.enabledProtocols = strongest(socket.supportedProtocols)
        return socket
    }

    fun connect(host: String, port: Int, timeoutMillis: Int = 4000): SSLSocket {
        val plain = Socket()
        try {
            plain.tcpNoDelay = true
            plain.connect(InetSocketAddress(host, port), timeoutMillis)
            val ssl = context.socketFactory.createSocket(plain, host, port, true) as SSLSocket
            ssl.enabledProtocols = strongest(ssl.supportedProtocols)
            ssl.soTimeout = timeoutMillis
            ssl.startHandshake()
            ssl.soTimeout = 0
            return ssl
        } catch (e: Exception) {
            runCatching { plain.close() }
            throw e
        }
    }

    private fun strongest(supported: Array<String>): Array<String> =
        arrayOf("TLSv1.3", "TLSv1.2").filter { it in supported }.toTypedArray()

    companion object {
        /** The other device's ID, taken from the certificate it showed during the handshake. */
        fun peerFingerprint(socket: SSLSocket): String =
            sha256Hex(socket.session.peerCertificates[0].encoded)
    }

    private object AcceptAnyCertificate : X509ExtendedTrustManager() {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {}
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}
