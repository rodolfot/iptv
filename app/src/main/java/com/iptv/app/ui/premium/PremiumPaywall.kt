package com.iptv.app.ui.premium

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.iptv.app.BuildConfig
import com.iptv.app.R
import com.iptv.app.premium.LicenseManager
import com.iptv.app.premium.PremiumFeature
import com.iptv.app.ui.settings.PremiumSettingsContent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PremiumGateViewModel @Inject constructor(
    val license: LicenseManager
) : ViewModel()

/** Site da loja: o servidor de licenças é `<site>/api/license`. */
fun portalBaseUrl(): String =
    BuildConfig.LICENSE_API_URL.substringBefore("/api/").trimEnd('/').ifBlank { DEFAULT_PORTAL }

/** Página de compra já com o ID deste aparelho preenchido. */
fun premiumBuyUrl(deviceId: String): String =
    "${portalBaseUrl()}/comprar" + if (deviceId.isNotBlank()) "?device=${deviceId.uppercase()}" else ""

private const val DEFAULT_PORTAL = "https://tv.muchila.com.br"

/** Benefício liberado neste aparelho (reage à ativação na hora). */
@Composable
fun rememberHasPremium(feature: PremiumFeature, vm: PremiumGateViewModel = hiltViewModel()): Boolean {
    val state by vm.license.state.collectAsState()
    LaunchedEffect(Unit) { vm.license.load() }
    return state.has(feature)
}

/**
 * Tela "Seja Premium" (#35): aparece ao tocar num recurso com cadeado.
 * Mostra por que travou, a lista de benefícios (os ainda não lançados como
 * "em breve"), o QR code da loja com o ID do aparelho e o campo da chave —
 * ativou, o diálogo fecha sozinho e o recurso libera sem reiniciar.
 */
@Composable
fun PremiumPaywallDialog(
    feature: PremiumFeature?,
    onDismiss: () -> Unit,
    vm: PremiumGateViewModel = hiltViewModel()
) {
    val state by vm.license.state.collectAsState()
    LaunchedEffect(state.premium) { if (feature != null && state.has(feature)) onDismiss() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.premium_paywall_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                feature?.let {
                    Text(
                        stringResource(R.string.premium_paywall_reason, stringResource(it.label)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(
                        modifier = Modifier.widthIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PremiumBenefitsList()
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val url = premiumBuyUrl(state.deviceId)
                        PremiumQrCode(url, size = 150.dp)
                        Text(
                            stringResource(R.string.premium_paywall_scan),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            portalBaseUrl().removePrefix("https://") + "/comprar",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                Text(
                    stringResource(R.string.premium_paywall_have_key),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
                PremiumSettingsContent(showOffer = false)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.premium_paywall_later)) }
        }
    )
}

/** ✓ para o que já existe, relógio para "em breve". */
@Composable
fun PremiumBenefitsList(included: (PremiumFeature) -> Boolean = { true }) {
    PremiumFeature.entries.filter(included).forEach { f ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                if (f.available) Icons.Filled.Check else Icons.Filled.Schedule,
                contentDescription = null,
                tint = if (f.available) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Text(
                if (f.available) stringResource(f.label)
                else stringResource(R.string.premium_benefit_soon, stringResource(f.label)),
                style = MaterialTheme.typography.bodySmall,
                color = if (f.available) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** QR code em fundo branco (leitura confiável mesmo em TV com brilho alto). */
@Composable
fun PremiumQrCode(url: String, size: Dp, modifier: Modifier = Modifier) {
    val bitmap = remember(url) { qrBitmap(url) } ?: return
    Image(
        bitmap = bitmap,
        contentDescription = url,
        filterQuality = FilterQuality.None,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .padding(6.dp)
    )
}

private fun qrBitmap(text: String, px: Int = 256): ImageBitmap? = runCatching {
    val matrix = QRCodeWriter().encode(
        text,
        BarcodeFormat.QR_CODE,
        px,
        px,
        mapOf(EncodeHintType.MARGIN to 0)
    )
    val pixels = IntArray(px * px) { i ->
        if (matrix[i % px, i / px]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    Bitmap.createBitmap(pixels, px, px, Bitmap.Config.ARGB_8888).asImageBitmap()
}.getOrNull()
