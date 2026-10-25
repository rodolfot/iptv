package com.iptv.app.premium

import android.util.Base64
import org.json.JSONObject
import java.math.BigInteger
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Conteúdo assinado pelo servidor de licenças ao ativar uma chave.
 *
 * @property key chave de ativação (16 caracteres, sem hífens).
 * @property deviceId identificador do aparelho ao qual a chave foi vinculada.
 * @property expiresAt epoch em segundos; 0 = vitalícia.
 */
data class LicensePayload(
    val key: String,
    val deviceId: String,
    val issuedAt: Long,
    val expiresAt: Long
)

/**
 * Token de licença no formato `base64url(payload JSON).base64url(assinatura)`.
 *
 * A assinatura é ECDSA P-256 / SHA-256 feita pelo servidor com a chave
 * privada; o app só tem a chave pública (BuildConfig.LICENSE_PUBLIC_KEY). Isso
 * permite validar a licença offline, a cada abertura, sem confiar em nada que
 * esteja gravado no aparelho: editar o arquivo de preferências (trocar o
 * deviceId, a validade, ou colar o token de outro aparelho) invalida a
 * assinatura ou o vínculo, e o app volta para a versão gratuita.
 *
 * Aceita a assinatura em DER (Java/OpenSSL) ou em P1363 (r‖s, 64 bytes —
 * formato do WebCrypto usado pelo servidor de referência em Cloudflare Workers).
 */
object LicenseToken {

    fun parsePublicKey(base64Spki: String): PublicKey? = runCatching {
        val der = Base64.decode(base64Spki.trim(), Base64.DEFAULT)
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(der))
    }.getOrNull()

    /** Payload do token se a assinatura confere com [publicKey]; senão null. */
    fun verify(token: String, publicKey: PublicKey): LicensePayload? = runCatching {
        val parts = token.trim().split('.')
        if (parts.size != 2) return null
        val payloadBytes = b64url(parts[0])
        var sig = b64url(parts[1])
        if (sig.size == 64) sig = p1363ToDer(sig)
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey)
        verifier.update(parts[0].toByteArray(Charsets.US_ASCII))
        if (!verifier.verify(sig)) return null
        val json = JSONObject(String(payloadBytes, Charsets.UTF_8))
        if (json.optInt("v", 1) != 1) return null
        LicensePayload(
            key = json.getString("k"),
            deviceId = json.getString("d"),
            issuedAt = json.optLong("iat", 0L),
            expiresAt = json.optLong("exp", 0L)
        )
    }.getOrNull()

    /** True quando o payload vale para [deviceId] em [nowSeconds]. */
    fun isValidFor(payload: LicensePayload, deviceId: String, nowSeconds: Long): Boolean =
        payload.deviceId.equals(deviceId, ignoreCase = true) &&
            (payload.expiresAt == 0L || payload.expiresAt > nowSeconds)

    /** Normaliza a chave digitada: só letras/números, maiúsculas. */
    fun normalizeKey(input: String): String = input.filter { it.isLetterOrDigit() }.uppercase()

    /** Chave válida no formato: 16 caracteres alfanuméricos. */
    fun isWellFormedKey(input: String): Boolean = normalizeKey(input).length == KEY_LENGTH

    /** `ABCD1234EFGH5678` → `ABCD-1234-EFGH-5678`. */
    fun formatKey(input: String): String = normalizeKey(input).chunked(4).joinToString("-")

    const val KEY_LENGTH = 16

    // android.util.Base64: java.util.Base64 só existe a partir da API 26 (minSdk é 23).
    private fun b64url(value: String): ByteArray =
        Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    /** r‖s (32+32 bytes) → SEQUENCE { INTEGER r, INTEGER s } em DER. */
    private fun p1363ToDer(sig: ByteArray): ByteArray {
        fun derInt(bytes: ByteArray): ByteArray {
            val v = BigInteger(1, bytes).toByteArray() // já com 0x00 se o bit alto estiver ligado
            return byteArrayOf(0x02, v.size.toByte()) + v
        }
        val r = derInt(sig.copyOfRange(0, 32))
        val s = derInt(sig.copyOfRange(32, 64))
        val body = r + s
        return byteArrayOf(0x30, body.size.toByte()) + body
    }
}
