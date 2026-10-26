package com.iptv.app.premium

import android.util.Base64
import com.iptv.app.TestApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/** Benefícios do plano no token (`f`) e a regra única [LicenseState.has] (#35). */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [33])
class PremiumFeatureTest {

    private val keys = KeyPairGenerator.getInstance("EC")
        .apply { initialize(ECGenParameterSpec("secp256r1")) }
        .generateKeyPair()

    private fun token(payloadJson: String): String {
        val flags = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        val body = Base64.encodeToString(payloadJson.toByteArray(), flags)
        val sig = Signature.getInstance("SHA256withECDSA").run {
            initSign(keys.private)
            update(body.toByteArray(Charsets.US_ASCII))
            sign() // DER — o app aceita DER ou P1363
        }
        return body + "." + Base64.encodeToString(sig, flags)
    }

    @Test
    fun `token com f traz so os beneficios do plano`() {
        val payload = LicenseToken.verify(
            token("""{"v":1,"k":"ABCD1234EFGH5678","d":"AA:BB:CC:DD:EE:FF","iat":1,"exp":0,"f":["noads","web"]}"""),
            keys.public
        )!!
        assertEquals(setOf("noads", "web"), payload.features)
        val state = LicenseState("AA:BB:CC:DD:EE:FF", premium = true, features = payload.features)
        assertTrue(state.has(PremiumFeature.NO_ADS))
        assertTrue(state.has(PremiumFeature.WEB_PLAYER))
        assertFalse(state.has(PremiumFeature.UNLIMITED_PROFILES))
    }

    @Test
    fun `token antigo sem f vale como premium completo`() {
        val payload = LicenseToken.verify(
            token("""{"v":1,"k":"ABCD1234EFGH5678","d":"AA:BB:CC:DD:EE:FF","iat":1,"exp":0}"""),
            keys.public
        )!!
        assertNull(payload.features)
        val state = LicenseState("AA:BB:CC:DD:EE:FF", premium = true, features = null)
        PremiumFeature.entries.forEach { assertTrue(it.name, state.has(it)) }
    }

    @Test
    fun `gratuito nao tem nenhum beneficio mesmo com lista`() {
        val state = LicenseState("AA:BB:CC:DD:EE:FF", premium = false, features = setOf("noads"))
        PremiumFeature.entries.forEach { assertFalse(it.name, state.has(it)) }
    }

    @Test
    fun `beneficio adulterado no token invalida a assinatura`() {
        val good = token("""{"v":1,"k":"ABCD1234EFGH5678","d":"AA:BB:CC:DD:EE:FF","iat":1,"exp":0,"f":["noads"]}""")
        val forgedBody = Base64.encodeToString(
            """{"v":1,"k":"ABCD1234EFGH5678","d":"AA:BB:CC:DD:EE:FF","iat":1,"exp":0,"f":["noads","pvr"]}""".toByteArray(),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        val forged = forgedBody + "." + good.substringAfter('.')
        assertNull(LicenseToken.verify(forged, keys.public))
    }
}
