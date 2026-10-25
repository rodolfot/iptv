package com.iptv.app

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.iptv.app.data.prefs.SettingsStore
import com.iptv.app.diag.CrashLog
import com.iptv.app.notify.Notifications
import com.iptv.app.work.CatalogRefreshWorker
import com.iptv.app.work.ResumeReminderWorker
import com.iptv.app.work.UpdateCheckWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class IptvApp : Application(), Configuration.Provider, coil.ImageLoaderFactory {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var settings: SettingsStore
    @Inject lateinit var license: com.iptv.app.premium.LicenseManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    /**
     * Aplica o locale escolhido ANTES de qualquer Activity inflar — em API < 33
     * o AppCompatDelegate só funciona em AppCompatActivity, e nossa MainActivity
     * é ComponentActivity. Sem este attachBaseContext, mesmo gravando o tag em
     * SharedPreferences o app continuava em português porque ninguém aplicava
     * a Configuration na hierarquia de Context da Application.
     */
    override fun attachBaseContext(base: Context) {
        val localeTag = base.getSharedPreferences(LOCALE_PREFS, MODE_PRIVATE)
            .getString(LOCALE_KEY, null)
        val ctx = applyLocaleToContext(base, localeTag)
        super.attachBaseContext(ctx)
    }

    /**
     * ImageLoader do Coil afinado para listas longas de logos/pôsteres rolando
     * rápido no D-pad (#25):
     *  - no máximo 4 downloads/decodificações em paralelo — rolar 300 canais
     *    disparava centenas de requisições e decodes de uma vez, disputando CPU
     *    com a UI; os que saem da tela são cancelados pelo próprio AsyncImage;
     *  - cache em memória menor (15% do heap) e RGB_565 em aparelhos com pouca
     *    RAM — logos não precisam de canal alfa de 32 bits;
     *  - sem crossfade (cada fade é uma animação por card durante a rolagem);
     *  - ignora `Cache-Control: no-cache` dos servidores de logo dos painéis,
     *    para não baixar a mesma imagem a cada volta da lista.
     */
    override fun newImageLoader(): coil.ImageLoader {
        val lowRam = (getSystemService(ACTIVITY_SERVICE) as? android.app.ActivityManager)
            ?.isLowRamDevice == true
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        val io = Dispatchers.IO.limitedParallelism(4)
        return coil.ImageLoader.Builder(this)
            .memoryCache {
                coil.memory.MemoryCache.Builder(this)
                    .maxSizePercent(if (lowRam) 0.10 else 0.15)
                    .build()
            }
            .diskCache {
                coil.disk.DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build()
            }
            .fetcherDispatcher(io)
            .decoderDispatcher(io)
            .allowRgb565(lowRam)
            .crossfade(false)
            .respectCacheHeaders(false)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        // Forçar dark mode em toda a stack do AppCompat: garante que dialogs
        // do sistema (Toast nativo, picker de teclado) também respeitem o
        // tema do app — caso contrário em TVs/launchers com light theme
        // padrão eles apareciam brancos no meio da nossa UI escura.
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
        )
        // Locale precisa ser aplicado ANTES da Activity inflar a UI — caso
        // contrário as strings já foram resolvidas no idioma anterior e o
        // app continua em português mesmo após restart. Lemos de um
        // SharedPreferences síncrono espelhado do DataStore (escrito ao
        // mesmo tempo que setAppLocale grava no DataStore).
        val localeTag = getSharedPreferences(LOCALE_PREFS, MODE_PRIVATE)
            .getString(LOCALE_KEY, null)
        com.iptv.app.ui.common.LocaleManager.apply(localeTag)
        CrashLog.install(this)
        Notifications.ensureChannels(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            // Revalida a licença Premium offline logo no boot (#29).
            runCatching { license.load() }
            val s = settings.flow.first()
            CatalogRefreshWorker.schedule(this@IptvApp, s.refreshInterval)
            ResumeReminderWorker.schedule(this@IptvApp)
            UpdateCheckWorker.schedule(this@IptvApp)
        }
    }

    companion object {
        const val LOCALE_PREFS = "tartatv_locale"
        const val LOCALE_KEY = "app_locale"

        /**
         * Helper compartilhado entre Application e Activity: dado um Context
         * base, devolve um Context com a Configuration ajustada para o BCP-47
         * em [tag]. Tag em branco/null devolve o Context original (sistema).
         */
        fun applyLocaleToContext(base: Context, tag: String?): Context {
            if (tag.isNullOrBlank()) return base
            val locale = Locale.forLanguageTag(tag)
            Locale.setDefault(locale)
            val config = android.content.res.Configuration(base.resources.configuration)
            config.setLocale(locale)
            return base.createConfigurationContext(config)
        }
    }
}
