@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.iptv.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage

/**
 * Phone uses the standard Material3 Card with a clickable Surface so taps register.
 * Tablet/TV keep the TV Material3 Card so D-pad focus stays correct on Leanback.
 */
@Composable
private fun TouchableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    content: @Composable () -> Unit
) {
    val dim = rememberTvDim()
    if (dim.formFactor == FormFactor.Phone) {
        androidx.compose.material3.Card(
            onClick = onClick,
            modifier = modifier,
            shape = shape
        ) { content() }
    } else {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = CardDefaults.shape(shape = shape)
        ) { content() }
    }
}

@Composable
fun PosterCard(
    title: String,
    imageUrl: String?,
    locked: Boolean = false,
    fallbackIcon: ImageVector = Icons.Filled.Movie,
    onClick: () -> Unit
) {
    val dim = rememberTvDim()
    val cardModifier = if (dim.formFactor == FormFactor.Phone) {
        Modifier.fillMaxWidth().aspectRatio(2f / 3f)
    } else {
        Modifier.width(dim.PosterCardW).height(dim.PosterCardH)
    }
    TouchableCard(
        onClick = onClick,
        modifier = cardModifier
    ) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            if (imageUrl.isNullOrBlank()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(fallbackIcon, contentDescription = null, modifier = Modifier.size(64.dp))
                }
            } else {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
                )
            }
            if (locked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(40.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xCC000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = "Bloqueado", tint = Color.White)
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color(0xCC000000))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ChannelCard(
    title: String,
    number: Int?,
    logoUrl: String?,
    locked: Boolean = false,
    nowPlaying: String? = null,
    nowProgress: Float? = null,
    onClick: () -> Unit
) {
    val dim = rememberTvDim()
    val channelModifier = if (dim.formFactor == FormFactor.Phone) {
        Modifier.fillMaxWidth().height(dim.ChannelCardH)
    } else {
        Modifier.width(dim.ChannelCardW).height(dim.ChannelCardH)
    }
    TouchableCard(
        onClick = onClick,
        modifier = channelModifier
    ) {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                if (logoUrl.isNullOrBlank()) {
                    Icon(Icons.Filled.LiveTv, contentDescription = null, modifier = Modifier.size(48.dp))
                } else {
                    AsyncImage(
                        model = logoUrl,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize().padding(16.dp)
                    )
                }
                if (locked) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(36.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xCC000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = "Bloqueado", tint = Color.White)
                    }
                }
            }
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Column {
                    if (number != null) {
                        Text(
                            "Canal $number",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!nowPlaying.isNullOrBlank()) {
                        Text(
                            nowPlaying,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (nowProgress != null && nowProgress in 0f..1f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                                    .height(2.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(nowProgress)
                                        .background(MaterialTheme.colorScheme.primary)
                                        .height(2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CategoryCard(
    title: String,
    count: Int?,
    locked: Boolean,
    onClick: () -> Unit
) {
    val dim = rememberTvDim()
    val (w, h, pad) = when (dim.formFactor) {
        FormFactor.Phone -> Triple(170.dp, 96.dp, 12.dp)
        FormFactor.Tablet -> Triple(280.dp, 120.dp, 16.dp)
        FormFactor.Tv -> Triple(360.dp, 140.dp, 20.dp)
    }
    val categoryModifier = if (dim.formFactor == FormFactor.Phone) {
        Modifier.fillMaxWidth().height(h)
    } else {
        Modifier.width(w).height(h)
    }
    TouchableCard(
        onClick = onClick,
        modifier = categoryModifier
    ) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(pad)) {
            Column(modifier = Modifier.align(Alignment.CenterStart)) {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (count != null) {
                    Text("$count itens", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (locked) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = androidx.compose.ui.res.stringResource(com.iptv.app.R.string.a11y_locked),
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
        }
    }
}
