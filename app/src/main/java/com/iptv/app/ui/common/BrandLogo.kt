package com.iptv.app.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Identidade visual 4.0 (#30): o "T" em gradiente turquesa → azul → violeta
 * dentro de um anel luminoso, sobre azul-marinho, com "Tarta TV" em branco.
 * As mesmas formas (em coordenadas 0–100) geram os PNGs do ícone, do banner
 * e da splash do sistema em `branding/generate-assets.py`.
 */
object Brand {
    val Navy = Color(0xFF0B121C)
    val Teal = Color(0xFF34D1B6)
    val Sky = Color(0xFF5BC3F5)
    val Violet = Color(0xFFA67BEB)
    const val WORDMARK = "Tarta TV"
}

private fun Path.polygon(scale: Float, ox: Float, oy: Float, vararg pts: Float) {
    moveTo(ox + pts[0] * scale, oy + pts[1] * scale)
    var i = 2
    while (i < pts.size) {
        lineTo(ox + pts[i] * scale, oy + pts[i + 1] * scale)
        i += 2
    }
    close()
}

/** Desenha a marca "T" ocupando um quadrado de [side] px com origem em [topLeft]. */
fun DrawScope.drawTartaMark(topLeft: Offset, side: Float) {
    val k = side / 100f
    val ox = topLeft.x
    val oy = topLeft.y
    // Travessa: paralelogramo inclinado, mais grosso à esquerda.
    val bar = Path().apply { polygon(k, ox, oy, 15f, 4f, 96f, 4f, 87f, 15f, 6f, 24f) }
    // Haste: afina para baixo e termina arredondada.
    val stem = Path().apply {
        moveTo(ox + 39f * k, oy + 18f * k)
        lineTo(ox + 62f * k, oy + 14f * k)
        lineTo(ox + 59f * k, oy + 82f * k)
        cubicTo(ox + 58f * k, oy + 96f * k, ox + 41f * k, oy + 97f * k, ox + 40f * k, oy + 86f * k)
        close()
    }
    val round = Stroke(width = 3.5f * k, join = StrokeJoin.Round, cap = StrokeCap.Round)
    val barBrush = Brush.horizontalGradient(
        listOf(Brand.Teal, Brand.Sky),
        startX = ox + 6f * k,
        endX = ox + 96f * k
    )
    val stemBrush = Brush.verticalGradient(
        listOf(Brand.Sky, Color(0xFF7FA2F0), Brand.Violet),
        startY = oy + 14f * k,
        endY = oy + 96f * k
    )
    drawPath(bar, barBrush)
    drawPath(bar, barBrush, style = round)
    drawPath(stem, stemBrush)
    drawPath(stem, stemBrush, style = round)
    // Dobra: sombra suave onde a haste sai da travessa.
    drawPath(
        Path().apply { polygon(k, ox, oy, 39f, 18f, 62f, 14f, 61.5f, 24f, 39.5f, 27f) },
        Brush.verticalGradient(
            listOf(Color.Black.copy(alpha = 0.18f), Color.Transparent),
            startY = oy + 14f * k,
            endY = oy + 27f * k
        )
    )
}

/** Marca estática (sem anel). */
@Composable
fun TartaLogoMark(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        drawTartaMark(Offset.Zero, this.size.minDimension)
    }
}

/** Animações desligadas nas opções do Android ("Remover animações"). */
@Composable
private fun animationsDisabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }
}

/**
 * Logo do menu superior (#37): a marca dentro do anel, girando devagar — bem
 * mais lento que o [BrandLoader] para não parecer carregamento. Só o Canvas
 * redesenha (o ângulo é lido na fase de desenho), sem recompor o menu. Com
 * "Remover animações" ligado no sistema, fica parado.
 */
@Composable
fun AnimatedTartaLogo(size: Dp, modifier: Modifier = Modifier) {
    val still = animationsDisabled()
    val transition = rememberInfiniteTransition(label = "top-logo")
    val rotation = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "top-logo-rotation"
    )
    val hue = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Reverse),
        label = "top-logo-hue"
    )
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val ringColor = lerp(Brand.Sky, Brand.Violet, if (still) 0f else hue.value)
            val strokePx = this.size.minDimension * 0.06f
            val inset = strokePx
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            drawCircle(
                color = ringColor.copy(alpha = 0.2f),
                radius = arcSize.minDimension / 2,
                style = Stroke(width = strokePx * 0.6f)
            )
            rotate(if (still) 0f else rotation.value) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(Color.Transparent, ringColor, Color.White)),
                    startAngle = 0f,
                    sweepAngle = 300f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }
        TartaLogoMark(size = size * 0.52f, modifier = Modifier.padding(bottom = size * 0.02f))
    }
}

/**
 * Marca + anel luminoso girando, com a cor oscilando entre turquesa e violeta
 * (como na vinheta da nova identidade). Usado na splash, no carregamento do
 * catálogo e enquanto o canal conecta no player.
 */
@Composable
fun BrandLoader(
    modifier: Modifier = Modifier,
    size: Dp = 132.dp,
    showWordmark: Boolean = true
) {
    val transition = rememberInfiniteTransition(label = "brand-ring")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "ring-rotation"
    )
    val hue by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Reverse),
        label = "ring-hue"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "ring-pulse"
    )
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val ringColor = lerp(Brand.Sky, Brand.Violet, hue)
                val strokePx = this.size.minDimension * 0.022f
                val inset = strokePx * 3
                val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
                // Trilho tênue completo.
                drawCircle(
                    color = ringColor.copy(alpha = 0.18f),
                    radius = arcSize.minDimension / 2,
                    style = Stroke(width = strokePx * 0.6f)
                )
                rotate(rotation) {
                    // Brilho difuso por baixo do arco.
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(Color.Transparent, ringColor.copy(alpha = 0.35f * pulse), Color.Transparent)
                        ),
                        startAngle = 0f,
                        sweepAngle = 300f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = strokePx * 4f, cap = StrokeCap.Round)
                    )
                    drawArc(
                        brush = Brush.sweepGradient(listOf(Color.Transparent, ringColor, Color.White)),
                        startAngle = 0f,
                        sweepAngle = 300f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                }
            }
            TartaLogoMark(size = size * 0.46f, modifier = Modifier.padding(bottom = size * 0.02f))
        }
        if (showWordmark) {
            Text(
                Brand.WORDMARK,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.2f).coerceIn(18f, 34f).sp,
                style = MaterialTheme.typography.headlineSmall
            )
        }
    }
}
