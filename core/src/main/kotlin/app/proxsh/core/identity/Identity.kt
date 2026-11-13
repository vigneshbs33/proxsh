package app.proxsh.core.identity

import app.proxsh.core.util.sha256Hex
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

/**
 * This device's identity: its own key pair and a self-signed certificate, made once on first run.
 * The fingerprint (SHA-256 of the certificate) is the device's ID. Nobody else can claim it.
 * There is no account and no server involved.
 */
class Identity private constructor(
    private val privateKey: PrivateKey,
    val certificate: X509Certificate,
) {
    val fingerprint: String = sha256Hex(certificate.encoded)

    internal fun keyStore(): KeyStore {
        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        ks.setKeyEntry("proxsh", privateKey, PASSWORD, arrayOf(certificate))
        return ks
    }

    fun saveTo(file: File) {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.outputStream().use { keyStore().store(it, PASSWORD) }
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "Could not save identity" }
        }
    }

    companion object {
        // The key file is protected by the operating system's file permissions; this value only
        // satisfies the PKCS12 format.
        internal val PASSWORD = "proxsh".toCharArray()

        fun generate(): Identity {
            val generator = KeyPairGenerator.getInstance("EC")
            generator.initialize(ECGenParameterSpec("secp256r1"))
            val pair = generator.generateKeyPair()
            val subject = X500Name("CN=ProxSh")
            val now = Instant.now()
            val builder = JcaX509v3CertificateBuilder(
                subject,
                BigInteger(64, SecureRandom()),
                Date.from(now.minus(1, ChronoUnit.DAYS)),
                Date.from(now.plus(20L * 365, ChronoUnit.DAYS)),
                subject,
                pair.public,
            )
            val signer = JcaContentSignerBuilder("SHA256withECDSA").build(pair.private)
            val cert = JcaX509CertificateConverter().getCertificate(builder.build(signer))
            return Identity(pair.private, cert)
        }

        fun load(file: File): Identity {
            val ks = KeyStore.getInstance("PKCS12")
            file.inputStream().use { ks.load(it, PASSWORD) }
            val key = ks.getKey("proxsh", PASSWORD) as PrivateKey
            val cert = ks.getCertificate("proxsh") as X509Certificate
            return Identity(key, cert)
        }

        fun loadOrCreate(file: File): Identity {
            if (file.exists()) {
                runCatching { return load(file) }
            }
            return generate().also { it.saveTo(file) }
        }
    }
}
