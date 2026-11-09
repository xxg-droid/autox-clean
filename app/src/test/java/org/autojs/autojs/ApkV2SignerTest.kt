package org.autojs.autojs

import com.android.apksig.ApkVerifier
import org.autojs.autojs.build.ApkSigner
import org.autojs.autojs.build.ApkV2Signer
import org.autojs.autojs.build.apksigner.CertCreator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Verifies the packaging V2 signing fix (build27 item 1):
 * packaged APKs must carry BOTH v1 and v2 signatures, via Google apksig,
 * on both signing paths (user keystore and built-in default key).
 *
 * Runs on the JVM (no Android framework classes involved).
 */
class ApkV2SignerTest {

    // Realistic input: the inrt template APK (already Gradle-signed v1+v2).
    // apksig replaces existing signatures, so signing it again exercises the
    // same code path the packaging feature uses on its unsigned zip.
    private val templateApk =
        File("../inrt/build/outputs/apk/template/release/inrt-template-release.apk")

    private val outDir = File("build/test-outputs/v2-signing").apply { mkdirs() }

    private fun verifyV1AndV2(apk: File, label: String) {
        val result = ApkVerifier.Builder(apk).build().verify()
        println("$label: verified=${result.isVerified} v1=${result.isVerifiedUsingV1Scheme} v2=${result.isVerifiedUsingV2Scheme} v3=${result.isVerifiedUsingV3Scheme}")
        if (!result.isVerified) {
            println("$label errors: ${result.errors}")
        }
        assertTrue("$label: APK must verify", result.isVerified)
        assertTrue("$label: v1 signature missing", result.isVerifiedUsingV1Scheme)
        assertTrue("$label: v2 signature missing", result.isVerifiedUsingV2Scheme)
    }

    @Test
    fun defaultKeyProducesV1AndV2() {
        assertTrue("template APK missing: ${templateApk.absolutePath}", templateApk.exists())
        val out = File(outDir, "default-signed.apk")
        ApkV2Signer.signWithDefaultKey(templateApk, out)
        assertTrue("signed APK missing", out.exists())
        verifyV1AndV2(out, "default-key")
    }

    @Test
    fun keystorePathProducesV1AndV2() {
        assertTrue("template APK missing: ${templateApk.absolutePath}", templateApk.exists())
        // Throwaway keystore, created with the same CertCreator the app uses.
        val ksFile = File(outDir, "test.keystore")
        if (ksFile.exists()) ksFile.delete()
        val dn = CertCreator.DistinguishedNameValues().apply { setCommonName("Test") }
        CertCreator.createKeystoreAndKey(
            ksFile.path, "testpass".toCharArray(), "RSA", 2048,
            "test", "testpass".toCharArray(), "SHA256withRSA", 25, dn
        )
        val out = File(outDir, "keystore-signed.apk")
        // Exact code path ApkBuilder.sign() uses for a user-configured keystore.
        ApkSigner.sign(ksFile.path, "testpass", templateApk, out)
        assertTrue("signed APK missing", out.exists())
        verifyV1AndV2(out, "keystore")
    }

    @Test
    fun defaultCertificateIsTheOriginalTestCert() {
        val cert = ApkV2Signer.defaultCertificate()
        println("default cert subject: ${cert.subjectX500Principal.name}")
        println("default cert serial: ${cert.serialNumber}")
        assertTrue("subject must be the Test cert", cert.subjectX500Principal.name.contains("CN=Test"))
        // The v1 and v2 blocks of a default-signed APK must carry this same cert.
        val out = File(outDir, "default-signed-cert-check.apk")
        ApkV2Signer.signWithDefaultKey(templateApk, out)
        val result = ApkVerifier.Builder(out).build().verify()
        assertTrue(result.isVerified)
        val signers = result.signerCertificates
        assertEquals("exactly one signer", 1, signers.size)
        assertEquals(
            "v1/v2 signer cert must be the embedded Test cert",
            cert, signers[0]
        )
    }
}
