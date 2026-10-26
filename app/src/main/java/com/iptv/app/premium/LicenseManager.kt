package com.iptv.app.premium

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.iptv.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Estado da licença exposto para a UI e para a camada de anúncios. */
data class LicenseState(
    val deviceId: String,
    val premium: Boolean,
    /** Chave ativa formatada (`ABCD-1234-…`), quando premium. */
    val key: String? = null,
    /** Validade em epoch segundos; 0 = vitalícia. */
    val expiresAt: Long = 0L,
    /** Benefícios do plano; null = todos (token anterior à lista). */
    val features: Set<String>? = null
) {
    /** Benefício liberado? Único ponto de decisão do Premium (#35). */
    fun has(feature: PremiumFeature): Boolean =
        premium && (features == null || feature.key in features)
}

/** Resultado de [LicenseManager.activate]. */
sealed interface ActivationResult {
    data object Success : ActivationResult
    data object InvalidKey : ActivationResult
    data object BoundToAnotherDevice : ActivationResult
    data object NotConfigured : ActivationResult
    data class Failed(val reason: String) : ActivationResult
}

/**
 * Licença Premium (sem anúncios) vinculada ao aparelho (#29).
 *
 * Fluxo:
 *  1. o usuário compra uma chave de 16 caracteres informando o ID/MAC exibido
 *     em Configurações → Premium;
 *  2. digita a chave; o app envia `{key, deviceId}` ao servidor de licenças;
 *  3. o servidor vincula a chave àquele deviceId na primeira ativação (e
 *     recusa com 409 "Licença já vinculada a outro dispositivo" qualquer
 *     outro aparelho depois disso) e devolve um token assinado
 *     ([LicenseToken]);
 *  4. o token fica no EncryptedSharedPreferences (chave AES-256 no Android
 *     Keystore — não sai do aparelho nem é copiável para outro) e é
 *     revalidado offline a cada abertura: assinatura ECDSA do servidor +
 *     deviceId deste aparelho + validade. Qualquer alteração manual invalida a
 *     licença e o app volta para a versão gratuita.
 *
 * [state] muda na hora da ativação: as telas e o player observam e escondem
 * os anúncios sem reiniciar o app.
 */
@Singleton
class LicenseManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val identity: DeviceIdentity,
    private val http: OkHttpClient
) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val publicKey by lazy { LicenseToken.parsePublicKey(BuildConfig.LICENSE_PUBLIC_KEY) }

    /** Servidor de licenças e chave pública presentes neste build. */
    val isConfigured: Boolean
        get() = BuildConfig.LICENSE_API_URL.isNotBlank() && publicKey != null

    private val _state = MutableStateFlow(LicenseState(deviceId = "", premium = false))
    val state: StateFlow<LicenseState> = _state.asStateFlow()

    @Volatile
    private var loaded = false

    /** Carrega e revalida a licença salva. Idempotente; chamado no boot. */
    suspend fun load() = withContext(Dispatchers.IO) {
        if (loaded) return@withContext
        loaded = true
        _state.value = evaluate()
    }

    fun isPremium(): Boolean = _state.value.premium

    /** Ver [LicenseState.has]. */
    fun has(feature: PremiumFeature): Boolean = _state.value.has(feature)

    /** Liga/desliga na hora em que a licença é ativada ou removida. */
    fun observe(feature: PremiumFeature): Flow<Boolean> =
        state.map { it.has(feature) }.distinctUntilChanged()

    /** Ativa [rawKey] neste aparelho no servidor de licenças. */
    suspend fun activate(rawKey: String): ActivationResult = withContext(Dispatchers.IO) {
        val key = LicenseToken.normalizeKey(rawKey)
        if (key.length != LicenseToken.KEY_LENGTH) return@withContext ActivationResult.InvalidKey
        val pk = publicKey
        if (BuildConfig.LICENSE_API_URL.isBlank() || pk == null) {
            return@withContext ActivationResult.NotConfigured
        }
        val deviceId = deviceId()
        val body = JSONObject().put("key", key).put("deviceId", deviceId).toString()
        val request = Request.Builder()
            .url(BuildConfig.LICENSE_API_URL.trimEnd('/') + "/v1/activate")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        try {
            http.newCall(request).execute().use { resp ->
                when (resp.code) {
                    404 -> return@withContext ActivationResult.InvalidKey
                    409 -> return@withContext ActivationResult.BoundToAnotherDevice
                }
                if (!resp.isSuccessful) return@withContext ActivationResult.Failed("HTTP ${resp.code}")
                val token = JSONObject(resp.body?.string().orEmpty()).optString("token")
                val payload = LicenseToken.verify(token, pk)
                    ?: return@withContext ActivationResult.Failed("token inválido")
                if (!LicenseToken.isValidFor(payload, deviceId, nowSeconds()) || payload.key != key) {
                    return@withContext ActivationResult.Failed("token não corresponde a este aparelho")
                }
                prefs.edit().putString(KEY_TOKEN, token).apply()
                _state.value = evaluate()
                ActivationResult.Success
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha na ativação", e)
            ActivationResult.Failed(e.javaClass.simpleName)
        }
    }

    /** Remove a licença deste aparelho (volta para a versão gratuita). */
    suspend fun deactivateLocally() = withContext(Dispatchers.IO) {
        prefs.edit().remove(KEY_TOKEN).apply()
        _state.value = evaluate()
    }

    private fun deviceId(): String {
        prefs.getString(KEY_DEVICE_ID, null)?.let { return it }
        val computed = identity.compute()
        prefs.edit().putString(KEY_DEVICE_ID, computed).apply()
        return computed
    }

    private fun evaluate(): LicenseState {
        val deviceId = runCatching { deviceId() }.getOrElse { identity.compute() }
        val token = runCatching { prefs.getString(KEY_TOKEN, null) }.getOrNull()
        val pk = publicKey
        if (token.isNullOrBlank() || pk == null) return LicenseState(deviceId, premium = false)
        val payload = LicenseToken.verify(token, pk)
        if (payload == null || !LicenseToken.isValidFor(payload, deviceId, nowSeconds())) {
            // Token adulterado, de outro aparelho ou vencido: descarta.
            Log.w(TAG, "Licença salva inválida — voltando para a versão gratuita")
            prefs.edit().remove(KEY_TOKEN).apply()
            return LicenseState(deviceId, premium = false)
        }
        return LicenseState(
            deviceId = deviceId,
            premium = true,
            key = LicenseToken.formatKey(payload.key),
            expiresAt = payload.expiresAt,
            features = payload.features
        )
    }

    private fun nowSeconds(): Long = System.currentTimeMillis() / 1000

    private companion object {
        const val TAG = "LicenseManager"
        const val FILE_NAME = "tartatv_license"
        const val KEY_TOKEN = "license_token_v1"
        const val KEY_DEVICE_ID = "device_id_v1"
    }
}
