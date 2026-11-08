package com.iptv.app.premium

import androidx.annotation.StringRes
import com.iptv.app.R

/**
 * Benefícios do Premium (#35). O gratuito mantém tudo o que o app já tinha;
 * o Premium tira os anúncios e libera os recursos abaixo.
 *
 * O token de licença pode trazer a lista de benefícios do plano (`f`, com as
 * [key]s); token sem `f` (emitido antes da 4.0.2) vale como Premium completo.
 * Toda verificação passa por [LicenseManager.has] — nada de `if (premium)`
 * espalhado pelas telas.
 *
 * @property available já existe nesta versão; os demais aparecem na tela
 *  "Seja Premium" como "em breve" e ganham o bloqueio quando forem lançados.
 * @property inAllPlans vale para qualquer Premium, mesmo com a lista `f` do
 *  token — benefício criado depois dos planos já vendidos.
 */
enum class PremiumFeature(
    val key: String,
    @StringRes val label: Int,
    val available: Boolean,
    val inAllPlans: Boolean = false
) {
    NO_ADS("noads", R.string.premium_benefit_noads, true),
    UNLIMITED_PROFILES("profiles", R.string.premium_benefit_profiles, true),
    WEB_PLAYER("web", R.string.premium_benefit_web, true),
    /** Tamanho da interface de 85% a 150% (gratuito: automático ou 85%). */
    UI_SCALE("uiscale", R.string.premium_benefit_uiscale, true, inAllPlans = true),
    CATCHUP("catchup", R.string.premium_benefit_catchup, false),
    SUBTITLES("subtitles", R.string.premium_benefit_subtitles, false),
    MULTIVIEW("multiview", R.string.premium_benefit_multiview, false),
    PVR("pvr", R.string.premium_benefit_pvr, false),
    SYNC("sync", R.string.premium_benefit_sync, false);

    companion object {
        /** Perfis (cada um com a sua lista) na versão gratuita. */
        const val FREE_PROFILE_LIMIT = 2

        /** Tamanhos da interface na versão gratuita (0 = automático). */
        val FREE_UI_SCALES = setOf(0, 85)

        fun fromKey(key: String): PremiumFeature? = entries.firstOrNull { it.key == key }
    }
}
