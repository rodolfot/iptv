package com.iptv.app.ui.player

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lista de origem para o zapping (P+/P−, ←/→, ⏭/⏮) quando o canal é aberto
 * de uma tela que não é a categoria do Ao Vivo — hoje, Favoritos (#24).
 *
 * A tela publica a lista imediatamente antes de abrir o player e o
 * PlayerViewModel a consome no load: com fila, o zapping percorre só ela; sem
 * fila, volta ao padrão (canais da mesma categoria). O consumo é único — a
 * fila é descartada no primeiro load, então abrir um canal pelo Ao Vivo
 * depois de ter vindo de Favoritos volta a zapear pela categoria.
 */
@Singleton
class LiveQueue @Inject constructor() {
    @Volatile
    private var pending: List<LiveNav> = emptyList()

    fun publish(items: List<LiveNav>) {
        pending = items
    }

    /** Devolve a fila publicada se ela contém [streamId]; sempre a descarta. */
    fun consume(streamId: Int): List<LiveNav>? {
        val queue = pending
        pending = emptyList()
        return queue.takeIf { list -> list.any { it.streamId == streamId } }
    }
}
