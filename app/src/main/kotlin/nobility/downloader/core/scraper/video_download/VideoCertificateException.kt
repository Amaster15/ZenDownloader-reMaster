package nobility.downloader.core.scraper.video_download

import java.net.Socket
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.*
import java.util.Date
import javax.net.ssl.*

/** Explicit local opt-in for the one expired certificate observed on this video host. */
object VideoCertificateException {
    private val numberedHost = Regex("u[0-9]+\\.cizgifilmlerizle\\.com")
    private fun allowedHost(host: String?): Boolean {
        val normalized = host?.lowercase() ?: return false
        return normalized == "neptun.cizgifilmlerizle.com" || numberedHost.matches(normalized)
    }
    const val FINGERPRINT = "f5274b5bda93700d896d53c44922ac97f760c667331973cec14a2cc82ff7e614"

    fun configure(connection: HttpsURLConnection) {
        if (System.getenv("ZEN_ALLOW_EXPIRED_VIDEO_CERTIFICATE") == "1" &&
            allowedHost(connection.url.host)) {
            connection.sslSocketFactory = context.socketFactory
        }
        // Never replace the hostname verifier or the JVM-wide SSL defaults.
    }

    private fun defaultManager(): X509ExtendedTrustManager {
        val factory = TrustManagerFactory.getInstance("PKIX")
        factory.init(null as KeyStore?)
        return factory.trustManagers.filterIsInstance<X509ExtendedTrustManager>().single()
    }

    private val context by lazy {
        SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(ExpiredLeafManager(defaultManager())), null)
        }
    }

    private class ExpiredLeafManager(val normal: X509ExtendedTrustManager) : X509ExtendedTrustManager() {
        private fun fallback(chain: Array<X509Certificate>, host: String?, failure: CertificateException): X509ExtendedTrustManager {
            if (!allowedHost(host) || chain.isEmpty() ||
                generateSequence<Throwable>(failure) { it.cause }.none { it is CertificateExpiredException }) throw failure
            val leaf = chain.first()
            val digest = MessageDigest.getInstance("SHA-256").digest(leaf.encoded)
                .joinToString("") { "%02x".format(it) }
            if (digest != FINGERPRINT || !Date().after(leaf.notAfter)) throw failure
            // Only the pinned leaf may be expired. Intermediates still need to be valid today.
            chain.drop(1).forEach { it.checkValidity() }
            val parameters = PKIXBuilderParameters(normal.acceptedIssuers.map { TrustAnchor(it, null) }.toSet(), X509CertSelector()).apply {
                date = Date(leaf.notAfter.time - 1000)
                isRevocationEnabled = java.lang.Boolean.getBoolean("com.sun.net.ssl.checkRevocation")
            }
            return TrustManagerFactory.getInstance("PKIX").apply {
                init(CertPathTrustManagerParameters(parameters))
            }.trustManagers.filterIsInstance<X509ExtendedTrustManager>().single()
        }

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket) {
            try { normal.checkServerTrusted(chain, authType, socket) }
            catch (e: CertificateException) {
                fallback(chain, (socket as? SSLSocket)?.handshakeSession?.peerHost, e)
                    .checkServerTrusted(chain, authType, socket)
            }
        }
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine) {
            try { normal.checkServerTrusted(chain, authType, engine) }
            catch (e: CertificateException) { fallback(chain, engine.peerHost, e).checkServerTrusted(chain, authType, engine) }
        }
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = normal.checkServerTrusted(chain, authType)
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = normal.checkClientTrusted(chain, authType)
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket) = normal.checkClientTrusted(chain, authType, socket)
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine) = normal.checkClientTrusted(chain, authType, engine)
        override fun getAcceptedIssuers(): Array<X509Certificate> = normal.acceptedIssuers
    }
}
