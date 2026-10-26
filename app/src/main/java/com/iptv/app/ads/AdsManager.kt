package com.iptv.app.ads

import android.os.SystemClock
import android.util.Log
import com.iptv.app.BuildConfig
import com.iptv.app.premium.LicenseManager
import com.iptv.app.premium.PremiumFeature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Onde um banner pode aparecer. */
enum class AdPlacement(val key: String) { HOME("home"), DETAIL("detail") }

/** Banner discreto da versão gratuita (imagem + texto opcional). */
data class AdBannerSpec(
    val placement: AdPlacement,
    val imageUrl: String,
    val text: String?,
    val clickUrl: String?,
    /** Anúncio da casa (#34): promove o Premium quando não há anunciante. */
    val house: Boolean = false
)

/** O que toca antes/depois do conteúdo. */
sealed interface AdBreakItem {
    /** Anúncio em vídeo de um anunciante (tag VAST). */
    data class Video(val ad: VastAd) : AdBreakItem

    /** Cartão "Assista sem anúncios" com o QR da loja — anúncio da casa (#34). */
    data object HousePremium : AdBreakItem
}

/**
 * Configuração de anúncios servida em `ADS_CONFIG_URL` (JSON). Exemplo em
 * `server/ads.example.json`. Tudo opcional: sem pre-roll configurado, nada
 * toca antes do vídeo; sem banners, nada aparece nas telas.
 */
data class AdsConfig(
    val prerollVastUrl: String?,
    val postrollVastUrl: String?,
    /** Intervalo mínimo entre dois anúncios em vídeo (frequency cap). */
    val minIntervalMs: Long,
    /** Tempo máximo para o anúncio responder e começar a tocar. */
    val timeoutMs: Long,
    val banners: List<AdBannerSpec>,
    /** Anúncios da casa quando falta anunciante (padrão ligado). */
    val houseAds: Boolean = true
) {
    companion object {
        private const val DEFAULT_INTERVAL_MS = 15 * 60_000L
        val NONE = AdsConfig(null, null, DEFAULT_INTERVAL_MS, 4_000L, emptyList())

        fun fromJson(raw: String): AdsConfig {
            val json = JSONObject(raw)
            fun vast(name: String): String? = json.optJSONObject(name)
                ?.takeIf { it.optBoolean("enabled", true) }
                ?.optString("vastUrl")
                ?.takeIf { it.isNotBlank() }
            val banners = buildList {
                val arr = json.optJSONArray("banners") ?: return@buildList
                for (i in 0 until arr.length()) {
                    val b = arr.optJSONObject(i) ?: continue
                    val placement = AdPlacement.values().firstOrNull { it.key == b.optString("placement") } ?: continue
                    val image = b.optString("imageUrl").takeIf { it.isNotBlank() } ?: continue
                    add(
                        AdBannerSpec(
                            placement = placement,
                            imageUrl = image,
                            text = b.optString("text").takeIf { it.isNotBlank() },
                            clickUrl = b.optString("clickUrl").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
            return AdsConfig(
                prerollVastUrl = vast("preroll"),
                postrollVastUrl = vast("postroll"),
                minIntervalMs = json.optLong("minIntervalMinutes", 15) * 60_000L,
                timeoutMs = json.optLong("timeoutMs", 4_000L).coerceIn(1_000L, 10_000L),
                banners = banners,
                houseAds = json.optJSONObject("house")?.optBoolean("enabled", true) ?: true
            )
        }
    }
}

/**
 * Anúncios da versão gratuita (#29): banners nas telas e vídeo antes
 * (pre-roll) e depois (post-roll) do conteúdo, via tags VAST padrão de
 * mercado — qualquer ad server (Google Ad Manager, SpringServe, Revive…)
 * entrega VAST.
 *
 * Regras:
 *  - Premium ([LicenseManager]) nunca vê anúncio — e o estado é observado,
 *    então ativar a licença esconde os banners na hora;
 *  - falha, VAST vazio ou demora acima de `timeoutMs` = sem anúncio; o
 *    conteúdo abre normalmente;
 *  - no máximo um anúncio em vídeo a cada `minIntervalMinutes` (o zapping
 *    entre canais não dispara pre-roll a cada troca);
 *  - sem anunciante (nenhuma tag/banner, ou a tag falhou), a versão gratuita
 *    mostra os anúncios da casa promovendo o Premium (#34) — desligável pelo
 *    servidor (`house.enabled`) e só em builds com loja (licença configurada).
 */
@Singleton
class AdsManager @Inject constructor(
    private val http: OkHttpClient,
    private val license: LicenseManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val config = MutableStateFlow(AdsConfig.NONE)
    private val loadLock = Mutex()
    private var loadedAt = 0L

    @Volatile
    private var lastVideoAdAt = 0L

    /** Banner da posição, ou null (premium, sem configuração ou sem banner). */
    fun banner(placement: AdPlacement): Flow<AdBannerSpec?> {
        scope.launch { ensureConfig() }
        return combine(config, license.state) { cfg, lic ->
            when {
                lic.has(PremiumFeature.NO_ADS) -> null
                else -> cfg.banners.firstOrNull { it.placement == placement }
                    ?: if (houseAdsEnabled(cfg)) AdBannerSpec(placement, "", null, null, house = true) else null
            }
        }
    }

    /** Pre-roll: anúncio do anunciante ou, sem ele, o cartão do Premium. */
    suspend fun preroll(): AdBreakItem? = videoBreak(houseFallback = true) { it.prerollVastUrl }

    /** Post-roll: só anunciante (o cartão da casa no fim do filme cansaria). */
    suspend fun postroll(): AdBreakItem? = videoBreak(houseFallback = false) { it.postrollVastUrl }

    private fun houseAdsEnabled(cfg: AdsConfig) = cfg.houseAds && license.isConfigured

    /** Registra o anúncio exibido (frequency cap). */
    fun markShown() {
        lastVideoAdAt = SystemClock.elapsedRealtime()
    }

    /** Dispara URLs de rastreamento (impressão, quartis, skip…) sem bloquear. */
    fun ping(urls: List<String>) {
        if (urls.isEmpty()) return
        scope.launch {
            urls.forEach { url ->
                runCatching { http.newCall(Request.Builder().url(url).build()).execute().close() }
            }
        }
    }

    private suspend fun videoBreak(houseFallback: Boolean, pick: (AdsConfig) -> String?): AdBreakItem? {
        license.load()
        if (license.has(PremiumFeature.NO_ADS)) return null
        val cfg = ensureConfig()
        val since = SystemClock.elapsedRealtime() - lastVideoAdAt
        if (lastVideoAdAt > 0 && since < cfg.minIntervalMs) return null
        pick(cfg)?.let { tag ->
            withTimeoutOrNull(cfg.timeoutMs) { resolveVast(tag) }?.let { return AdBreakItem.Video(it) }
        }
        return if (houseFallback && houseAdsEnabled(cfg)) AdBreakItem.HousePremium else null
    }

    /** Segue Wrappers (até 4 níveis) acumulando impressões/rastreamento. */
    private suspend fun resolveVast(url: String): VastAd? = withContext(Dispatchers.IO) {
        var next: String? = url
        val extraImpressions = mutableListOf<String>()
        val extraTracking = mutableMapOf<String, MutableList<String>>()
        repeat(MAX_WRAPPER_DEPTH) {
            val current = next ?: return@withContext null
            val xml = runCatching { fetch(current) }.getOrNull() ?: return@withContext null
            when (val doc = runCatching { VastParser.parse(xml) }.getOrDefault(VastDocument.Empty)) {
                is VastDocument.InLine -> {
                    val ad = doc.ad
                    val tracking = ad.tracking.mapValues { it.value.toMutableList() }.toMutableMap()
                    extraTracking.forEach { (k, v) -> tracking.getOrPut(k) { mutableListOf() } += v }
                    return@withContext ad.copy(impressions = ad.impressions + extraImpressions, tracking = tracking)
                }
                is VastDocument.Wrapper -> {
                    extraImpressions += doc.impressions
                    doc.tracking.forEach { (k, v) -> extraTracking.getOrPut(k) { mutableListOf() } += v }
                    next = doc.nextUrl
                }
                VastDocument.Empty -> return@withContext null
            }
        }
        null
    }

    private suspend fun ensureConfig(): AdsConfig = loadLock.withLock {
        val url = BuildConfig.ADS_CONFIG_URL
        if (url.isBlank()) return config.value
        val fresh = SystemClock.elapsedRealtime() - loadedAt < CONFIG_TTL_MS && loadedAt > 0
        if (fresh) return config.value
        val loaded = withContext(Dispatchers.IO) {
            runCatching { AdsConfig.fromJson(fetch(url)) }
                .onFailure { Log.w(TAG, "Falha ao carregar configuração de anúncios", it) }
                .getOrNull()
        }
        loadedAt = SystemClock.elapsedRealtime()
        if (loaded != null) config.value = loaded
        config.value
    }

    private fun fetch(url: String): String =
        http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            resp.body?.string().orEmpty()
        }

    private companion object {
        const val TAG = "AdsManager"
        const val MAX_WRAPPER_DEPTH = 4
        const val CONFIG_TTL_MS = 6 * 60 * 60 * 1000L
    }
}
