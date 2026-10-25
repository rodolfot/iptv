package com.iptv.app.premium

import com.iptv.app.TestApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tokens assinados pelo servidor de referência (server/license-worker, WebCrypto
 * → assinatura P1363) com uma chave gerada só para este teste.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [33])
class LicenseTokenTest {

    private val publicKey = LicenseToken.parsePublicKey(
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAErQjJvrKQRDHesTs8oHehCbC8lzcdNZgSZ6F4gsY/reFi4zWJ2f2IJO0nufH7y+ixaq2i0bsZpHIX+dsAmU7xzA=="
    )!!

    private val lifetime =
        "eyJ2IjoxLCJrIjoiQUJDRDEyMzRFRkdINTY3OCIsImQiOiJBQTpCQjpDQzpERDpFRTpGRiIsImlhdCI6MTc5MDAwMDAwMCwiZXhwIjowfQ" +
            ".wu_vwUXPYJM4yFFiMlfZyavqhT4qPcYEzU22OMni4h1pFgwY2zguLq4aYZ575mTVLP2zli8Oj_6vYCxH3mFLDQ"

    private val expiring =
        "eyJ2IjoxLCJrIjoiQUJDRDEyMzRFRkdINTY3OCIsImQiOiJBQTpCQjpDQzpERDpFRTpGRiIsImlhdCI6MTc5MDAwMDAwMCwiZXhwIjoxNzkwMDAwMTAwfQ" +
            ".finlmo3EZlElooSwnAvd5ztL0yXpwsxTivGE91Ks5_NjMPzeYTs6X0QC0vEkFPRQKHWsT9cjbCQPT5hE_29Gjw"

    @Test
    fun validTokenFromServerVerifies() {
        val payload = LicenseToken.verify(lifetime, publicKey)
        assertNotNull(payload)
        assertEquals("ABCD1234EFGH5678", payload!!.key)
        assertEquals("AA:BB:CC:DD:EE:FF", payload.deviceId)
        assertTrue(LicenseToken.isValidFor(payload, "aa:bb:cc:dd:ee:ff", nowSeconds = 1_800_000_000))
    }

    @Test
    fun tokenIsBoundToTheDevice() {
        val payload = LicenseToken.verify(lifetime, publicKey)!!
        assertFalse(LicenseToken.isValidFor(payload, "11:22:33:44:55:66", nowSeconds = 1_800_000_000))
    }

    @Test
    fun tamperedPayloadIsRejected() {
        // Troca o deviceId no payload mantendo a assinatura original.
        val forgedPayload = android.util.Base64.encodeToString(
            """{"v":1,"k":"ABCD1234EFGH5678","d":"11:22:33:44:55:66","iat":1790000000,"exp":0}""".toByteArray(),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
        )
        val forged = forgedPayload + "." + lifetime.substringAfter('.')
        assertNull(LicenseToken.verify(forged, publicKey))
        assertNull(LicenseToken.verify("lixo", publicKey))
    }

    @Test
    fun expiredTokenIsInvalid() {
        val payload = LicenseToken.verify(expiring, publicKey)!!
        assertTrue(LicenseToken.isValidFor(payload, "AA:BB:CC:DD:EE:FF", nowSeconds = 1_790_000_050))
        assertFalse(LicenseToken.isValidFor(payload, "AA:BB:CC:DD:EE:FF", nowSeconds = 1_790_000_200))
    }

    @Test
    fun keyFormatting() {
        assertEquals("ABCD1234EFGH5678", LicenseToken.normalizeKey("abcd-1234 efgh-5678"))
        assertTrue(LicenseToken.isWellFormedKey("abcd-1234-efgh-5678"))
        assertFalse(LicenseToken.isWellFormedKey("abcd-1234"))
        assertEquals("ABCD-1234-EFGH-5678", LicenseToken.formatKey("abcd1234efgh5678"))
    }
}
