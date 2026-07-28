package com.iptv.app.tv

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.tvprovider.media.tv.PreviewChannel
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import com.iptv.app.R
import com.iptv.app.data.db.LiveHistoryDao
import com.iptv.app.data.db.MovieProgressDao
import com.iptv.app.data.prefs.CurrentProfile
import com.iptv.app.ui.player.PlayerArgs
import com.iptv.app.ui.player.PlayerKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Publica um "canal" da tela inicial da Android TV (a linha de recomendações
 * do launcher) com os canais recentes e os filmes em andamento. Cada card é um
 * deep link `tartatv://play?...` que abre o app já no player.
 *
 * Requer API 26 (TvProvider preview programs) e só faz algo em aparelhos que
 * têm o launcher Leanback — em telefones a chamada simplesmente não encontra o
 * provider e é ignorada silenciosamente.
 */
@Singleton
class HomeChannelPublisher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val liveHistoryDao: LiveHistoryDao,
    private val movieProgressDao: MovieProgressDao,
    private val currentProfile: CurrentProfile
) {
    private val prefs by lazy {
        context.getSharedPreferences("tv_home_channel", Context.MODE_PRIVATE)
    }

    suspend fun publish() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching { publishInternal() }
    }

    // MainActivity.onCreate dispara publish() via lifecycleScope.launch, que usa
    // Dispatchers.Main.immediate — sem este withContext, ensureChannel() e o loop
    // de deletePreviewProgram() abaixo (chamadas síncronas de ContentResolver/Binder
    // para o TvProvider do sistema) rodariam na thread principal antes de qualquer
    // suspensão. Se o launcher/TvProvider ainda estiver acordando de standby (TV
    // desligada e religada, processo do app recriado do zero), essa chamada trava
    // a UI thread antes do primeiro frame: tela preta sem ANR (não há input
    // pendente pro watchdog do sistema cronometrar), só resolve com "forçar parada".
    // PreviewProgram.Builder's setters are flagged RestrictedApi by lint even
    // though this is exactly the intended usage documented in Android's own
    // TV Recommendations Channel guide — androidx.tvprovider's own
    // @RestrictTo annotations are overly broad here.
    @Suppress("RestrictedApi")
    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun publishInternal() = withContext(Dispatchers.IO) {
        val helper = PreviewChannelHelper(context)
        val channelId = ensureChannel(helper)

        // Limpa os programas publicados na rodada anterior (não há API direta
        // para listar por canal, então rastreamos os IDs em prefs).
        prefs.getString(KEY_PROGRAM_IDS, null)
            ?.split(',')
            ?.mapNotNull { it.toLongOrNull() }
            ?.forEach { runCatching { helper.deletePreviewProgram(it) } }

        val pid = currentProfile.id()
        val newIds = ArrayList<Long>()

        liveHistoryDao.observeRecent(pid, limit = 10).first().forEach { ch ->
            val args = PlayerArgs(
                kind = PlayerKind.LIVE,
                streamId = ch.channelId,
                title = ch.name,
                containerExtension = null,
                categoryId = ch.categoryId
            )
            val program = PreviewProgram.Builder()
                .setChannelId(channelId)
                .setType(TvContractCompat.PreviewPrograms.TYPE_CHANNEL)
                .setTitle(ch.name)
                .also { b -> ch.logoUrl?.let { b.setPosterArtUri(Uri.parse(it)) } }
                .setIntentUri(Uri.parse(PlayerArgs.toDeepLink(args)))
                .build()
            runCatching { helper.publishPreviewProgram(program) }.getOrNull()?.let { newIds.add(it) }
        }

        movieProgressDao.observeInProgress(pid, limit = 10).first().forEach { mv ->
            val args = PlayerArgs(
                kind = PlayerKind.MOVIE,
                streamId = mv.movieId,
                title = mv.title,
                containerExtension = mv.containerExtension,
                posterUrl = mv.posterUrl,
                categoryId = mv.categoryId
            )
            val program = PreviewProgram.Builder()
                .setChannelId(channelId)
                .setType(TvContractCompat.PreviewPrograms.TYPE_MOVIE)
                .setTitle(mv.title)
                .also { b -> mv.posterUrl?.let { b.setPosterArtUri(Uri.parse(it)) } }
                .also { b ->
                    if (mv.durationMs > 0) {
                        b.setDurationMillis(mv.durationMs.toInt())
                        b.setLastPlaybackPositionMillis(mv.positionMs.toInt())
                    }
                }
                .setIntentUri(Uri.parse(PlayerArgs.toDeepLink(args)))
                .build()
            runCatching { helper.publishPreviewProgram(program) }.getOrNull()?.let { newIds.add(it) }
        }

        prefs.edit().putString(KEY_PROGRAM_IDS, newIds.joinToString(",")).apply()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun ensureChannel(helper: PreviewChannelHelper): Long {
        val existing = prefs.getLong(KEY_CHANNEL_ID, -1L)
        if (existing > 0 && runCatching { helper.getPreviewChannel(existing) }.getOrNull() != null) {
            return existing
        }
        val logo = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.app_banner)
            ?.let { drawableToBitmap(it) }
        val builder = PreviewChannel.Builder()
            .setDisplayName(context.getString(R.string.app_name))
            .setAppLinkIntentUri(Uri.parse("tartatv://home"))
        if (logo != null) builder.setLogo(logo)
        val id = helper.publishDefaultChannel(builder.build())
        // Torna o canal visível na home por padrão (launcher pode ignorar).
        runCatching { TvContractCompat.requestChannelBrowsable(context, id) }
        prefs.edit().putLong(KEY_CHANNEL_ID, id).apply()
        return id
    }

    private fun drawableToBitmap(d: android.graphics.drawable.Drawable): android.graphics.Bitmap {
        if (d is android.graphics.drawable.BitmapDrawable && d.bitmap != null) return d.bitmap
        val w = d.intrinsicWidth.coerceAtLeast(1)
        val h = d.intrinsicHeight.coerceAtLeast(1)
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        d.setBounds(0, 0, canvas.width, canvas.height)
        d.draw(canvas)
        return bmp
    }

    private companion object {
        const val KEY_CHANNEL_ID = "channel_id"
        const val KEY_PROGRAM_IDS = "program_ids"
    }
}
