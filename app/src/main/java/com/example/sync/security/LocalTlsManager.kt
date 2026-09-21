package com.example.sync.security

import okhttp3.OkHttpClient
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.*

/**
 * Local TLS Manager for secure, encrypted peer-to-peer communication over local Wi-Fi.
 *
 * Implements:
 * - HTTPS / TLS v1.2 & v1.3 encryption in transit without requiring public internet CA / domain.
 * - Server certificate SHA-256 fingerprint pinning to prevent Man-in-the-Middle (MITM) attacks.
 * - Dynamic certificate inspection during pairing flow to capture and pin server identity.
 * - Safe timeouts and error handling.
 */
object LocalTlsManager {

    /**
     * Computes the SHA-256 fingerprint of an X509 certificate in uppercase colon-delimited hex.
     * Example: "4A:9B:12:34:...:FE"
     */
    fun computeSha256Fingerprint(cert: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(cert.encoded)
        return hash.joinToString(":") { "%02X".format(it) }
    }

    /**
     * Checks if two certificate fingerprints match safely.
     */
    fun verifyFingerprint(expected: String, actual: String): Boolean {
        if (expected.isBlank() || actual.isBlank()) return false
        return expected.trim().uppercase() == actual.trim().uppercase()
    }

    /**
     * Creates an OkHttpClient builder with TLS configured for certificate fingerprint pinning.
     *
     * @param expectedFingerprint If non-blank, requires the server's certificate fingerprint to match.
     * @param onCertificateCaptured Optional callback invoked when a server certificate is inspected (used during pairing).
     */
    fun createTlsClient(
        expectedFingerprint: String = "",
        onCertificateCaptured: ((String) -> Unit)? = null
    ): OkHttpClient {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                if (chain.isNullOrEmpty()) {
                    throw CertificateException("Server certificate chain is empty.")
                }
                val serverCert = chain[0]
                val fingerprint = computeSha256Fingerprint(serverCert)

                onCertificateCaptured?.invoke(fingerprint)

                if (expectedFingerprint.isNotBlank()) {
                    val cleanExpected = expectedFingerprint.trim().uppercase()
                    val cleanActual = fingerprint.trim().uppercase()
                    if (cleanExpected != cleanActual) {
                        throw CertificateException(
                            "TLS Server Certificate Fingerprint mismatch! " +
                            "Expected: $cleanExpected, Received: $cleanActual. " +
                            "Aborting connection to prevent MITM attack."
                        )
                    }
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }

        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        }

        val hostnameVerifier = HostnameVerifier { _, _ ->
            // In local Wi-Fi, connection authentication is bound by certificate fingerprint pinning,
            // which provides cryptographic authentication of the host.
            true
        }

        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier(hostnameVerifier)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }
}
