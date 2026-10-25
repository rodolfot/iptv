package com.iptv.app.car

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detecta se o veículo está em movimento pela velocidade do GPS da central
 * multimídia (#23) — usado pela regra de segurança "só áudio em movimento".
 *
 * Centrais Android de painel (como a NAVPRO do HR-V) não são Android
 * Automotive: não há CarUxRestrictions nem sinal de câmbio/freio de mão
 * acessível a apps, e o CANBUS não expõe velocidade. O GPS interno é a fonte
 * confiável que sobra. Com histerese para não piscar no semáforo:
 *  - em movimento assim que uma leitura passa de [MOVING_SPEED_MS] (10 km/h);
 *  - parado só depois de [STOP_CONFIRM_MS] seguidos abaixo de
 *    [STOPPED_SPEED_MS] (5 km/h).
 * Sem permissão de localização, emite sempre `false` (não bloqueia nada).
 */
@Singleton
class DrivingMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // checado em hasPermission()
    fun isMoving(): Flow<Boolean> {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (manager == null || !hasPermission() ||
            !manager.allProviders.contains(LocationManager.GPS_PROVIDER)
        ) return flowOf(false)
        return callbackFlow {
            var moving = false
            var slowSince = 0L
            trySend(false)
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (!location.hasSpeed()) return
                    val speed = location.speed
                    val now = SystemClock.elapsedRealtime()
                    if (speed >= MOVING_SPEED_MS) {
                        moving = true
                        slowSince = 0L
                    } else if (moving && speed < STOPPED_SPEED_MS) {
                        if (slowSince == 0L) slowSince = now
                        if (now - slowSince >= STOP_CONFIRM_MS) moving = false
                    }
                    trySend(moving)
                }

                @Deprecated("API antiga; obrigatório abaixo da API 29")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) = Unit
            }
            runCatching {
                manager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, UPDATE_INTERVAL_MS, 0f, listener, Looper.getMainLooper()
                )
            }
            awaitClose { runCatching { manager.removeUpdates(listener) } }
        }.distinctUntilChanged()
    }

    private companion object {
        const val MOVING_SPEED_MS = 2.78f   // 10 km/h
        const val STOPPED_SPEED_MS = 1.39f  // 5 km/h
        const val STOP_CONFIRM_MS = 8_000L
        const val UPDATE_INTERVAL_MS = 2_000L
    }
}
