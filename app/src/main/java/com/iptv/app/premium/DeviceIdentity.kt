package com.iptv.app.premium

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.NetworkInterface
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Identificador do aparelho usado para vincular a licença Premium (#29),
 * exibido no formato de endereço MAC (`AA:BB:CC:DD:EE:FF`) na tela de
 * ativação — é o que lojas de IPTV pedem ao vender a ativação.
 *
 * Ordem de tentativa:
 *  1. MAC real da Ethernet (`eth0`) ou do Wi-Fi (`wlan0`). Disponível na
 *     maioria dos TV boxes até o Android 10; a partir do Android 11 o sistema
 *     esconde o MAC de apps comuns (devolve null).
 *  2. Derivado do `Settings.Secure.ANDROID_ID` (único por aparelho + chave
 *     de assinatura do app, e estável entre reinstalações desde o Android 8)
 *     via SHA-256, formatado como MAC "localmente administrado" (2º dígito
 *     2/6/A/E) para não colidir com um MAC de fabricante.
 *
 * O resultado é calculado uma vez e guardado pelo [LicenseManager] no
 * armazenamento criptografado: se o sistema passar a esconder o MAC depois
 * de uma atualização, o aparelho não "muda de identidade" e não perde a
 * licença.
 */
@Singleton
class DeviceIdentity @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun compute(): String = hardwareMac() ?: derivedId()

    private fun hardwareMac(): String? = runCatching {
        val preferred = listOf("eth0", "wlan0")
        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        preferred.firstNotNullOfOrNull { name ->
            interfaces.firstOrNull { it.name == name }?.hardwareAddress?.takeIf(::isRealMac)
        }?.let(::formatMac)
    }.getOrNull()

    @SuppressLint("HardwareIds")
    private fun derivedId(): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            .orEmpty()
            .ifBlank { "unknown" }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$androidId:${context.packageName}".toByteArray(Charsets.UTF_8))
        val bytes = digest.copyOfRange(0, 6)
        // Bit "localmente administrado" ligado e bit multicast desligado.
        bytes[0] = ((bytes[0].toInt() and 0xFC) or 0x02).toByte()
        return formatMac(bytes)
    }

    companion object {
        /** MACs que o Android devolve quando esconde o endereço real. */
        private val PLACEHOLDERS = setOf("02:00:00:00:00:00", "00:00:00:00:00:00")

        fun isRealMac(bytes: ByteArray): Boolean =
            bytes.size == 6 && formatMac(bytes) !in PLACEHOLDERS

        fun formatMac(bytes: ByteArray): String =
            bytes.joinToString(":") { "%02X".format(it.toInt() and 0xFF) }
    }
}
