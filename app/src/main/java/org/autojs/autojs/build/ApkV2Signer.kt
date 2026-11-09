package org.autojs.autojs.build

import com.android.apksig.ApkSigner
import java.io.ByteArrayInputStream
import java.io.File
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/**
 * Signs APKs with BOTH v1 (JAR) and v2 (APK Signature Scheme v2) signatures using
 * Google's apksig library (com.android.tools.build:apksig).
 *
 * This replaces the old v1-only signing used by the packaging feature
 * (ApkSigner -> tiny-sign ZipSigner, DefaultSign -> hand-rolled JAR signing).
 * The old code produced v1-only APKs; since the inrt template targets API 33,
 * Android 11+ refuses to install apps targeting API 30+ without a v2 signature
 * ("应用未安装" on the S23).
 *
 * Pure JVM code (no Android framework classes) so it can be exercised by unit
 * tests. v3 signing is intentionally off: the task is v1+v2 only, and v3 needs
 * minSdk 28+ devices to be useful.
 */
object ApkV2Signer {

    // --- Embedded "Test" key/certificate -------------------------------------
    // The SAME pair DefaultSign uses for the built-in default signing path
    // (see DefaultSign.kt: instanceSignature() / writeRSA()). The strings are
    // duplicated here (with java.util.Base64 instead of android.util.Base64)
    // so this object stays pure JVM and unit-testable. Do NOT change them:
    // packaged APKs signed with the default key must keep carrying the same
    // certificate, otherwise reinstalling/updating a previously packaged APK
    // would fail with a signature mismatch.
    private const val DEFAULT_PRIVATE_KEY_B64 =
        "MIIBVgIBADANBgkqhkiG9w0BAQEFAASCAUAwggE8AgEAAkEAoiZSqWnFDHA5sXKoDiUUO9JuL7cm/2dCck5MKumVvv+WfSg0jsovnywsFN0pifmdRSLmOdUkh0d0J+tOnSgtsQIDAQABAkEAihag5u3Qhds9BsViIUmqhZebhr8vUuqZR8cuTo1GnbSoOHIPbAgD3J8TDbC/CVqae8NrgwLp325Pem1Tuof/0QIhAN1hqft1K307bsljgw3iYKopGVZBHRXsjRnNL4edV9QrAiEAu4F+XtS1wohGLz5QtfuMFsQNo4l31mCjt6WpBDmSi5MCIQCB++YijxmJ3mueM5+vd0vqnVcTHghF5y6yB5fwuKHpIQIgInnS1Hjj2prX3MPmby+LOHxfzZvvDtnCAHhTNVWonkUCIQCvV8l+SpL6Vh1nQ/2EKFJo2dbZB3wKG/BEYsFkPFbn9w=="

    // The X.509 certificate of the built-in default ("Test") signing key, as
    // raw DER (base64). It is the certificate embedded inside the PKCS#7 blob
    // DefaultSign writes into CERT.RSA (bytes 56..366 of that blob, 310 bytes).
    // The whole PKCS#7 blob CANNOT be parsed on its own: it is a template whose
    // lengths were computed for the complete structure (490 bytes) but whose
    // final 64-byte signature value is missing — DefaultSign appends the
    // signature separately after writing the blob. So the cert is stored here
    // directly (subject CN=Test, serial 2BA73289).
    private const val DEFAULT_CERT_DER_B64 =
        "MIIBMjCB3aADAgECAgQrpzKJMA0GCSqGSIb3DQEBCwUAMA8xDTALBgNVBAMTBFRlc3QwHhcNMTIwNDIyMDg0NTQ3WhcNMTMwNDIyMDg0NTQ3WjAPMQ0wCwYDVQQDEwRUZXN0MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAKImUqlpxQxwObFyqA4lFDvSbi+3Jv9nQnJOTCrplb7/ln0oNI7KL58sLBTdKYn5nUUi5jnVJIdHdCfrTp0oLbECAwEAAaMhMB8wHQYDVR0OBBYEFFS9sjop1MKQERNbTj8XNW3+Fq04MA0GCSqGSIb3DQEBCwUAA0EAJ4/3mcIasG9rYEmDb4EaOWwTTQqSW0OGpRHj/zC0iFn9p2aQIsfE96EvV9mn30LQIHLTwE3CkGoh9geC+xSpVQ=="

    /** Sign [inputApk] into [outputApk] with v1 AND v2 signatures. */
    @JvmStatic
    fun sign(
        inputApk: File,
        outputApk: File,
        privateKey: PrivateKey,
        certificates: List<X509Certificate>
    ) {
        val signerConfig =
            ApkSigner.SignerConfig.Builder("CERT", privateKey, certificates).build()
        val signer = ApkSigner.Builder(listOf(signerConfig))
            .setInputApk(inputApk)
            .setOutputApk(outputApk)
            .setMinSdkVersion(21)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(false)
            .setOtherSignersSignaturesPreserved(false)
            .build()
        signer.sign()
    }

    /** The private key of the built-in default ("Test") signing key. */
    @JvmStatic
    fun defaultPrivateKey(): PrivateKey {
        val keyBytes = Base64.getDecoder().decode(DEFAULT_PRIVATE_KEY_B64)
        return KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(keyBytes))
    }

    /** The X.509 certificate of the built-in default ("Test") signing key. */
    @JvmStatic
    fun defaultCertificate(): X509Certificate {
        val certBytes = Base64.getDecoder().decode(DEFAULT_CERT_DER_B64)
        val factory = CertificateFactory.getInstance("X.509")
        return factory.generateCertificate(ByteArrayInputStream(certBytes)) as X509Certificate
    }

    /** Sign [inputApk] into [outputApk] with the built-in default key, v1+v2. */
    @JvmStatic
    fun signWithDefaultKey(inputApk: File, outputApk: File) {
        sign(inputApk, outputApk, defaultPrivateKey(), listOf(defaultCertificate()))
    }
}
