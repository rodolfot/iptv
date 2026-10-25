package com.iptv.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Splash com a identidade 4.0 (#30): marca "T" no anel luminoso e
 * "Tarta TV". Mostrado enquanto o RootViewModel ainda não emitiu o primeiro
 * estado do DataStore — em TVs lentas isso leva ~1-2s e antes mostrava uma
 * tela preta.
 */
@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandBackground),
        contentAlignment = Alignment.Center
    ) {
        BrandLoader()
    }
}

/** Fundo azul-marinho com um leve halo no centro, como na vinheta. */
val BrandBackground: Brush = Brush.radialGradient(
    listOf(Color(0xFF16222F), Brand.Navy)
)
