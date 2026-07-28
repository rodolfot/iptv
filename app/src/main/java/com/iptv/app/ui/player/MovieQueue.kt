package com.iptv.app.ui.player

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fila de filmes da categoria/filtro atual, na MESMA ordem que o usuário vê na
 * listagem (após busca local, filtros avançados e ordenação). A tela de filmes
 * publica a fila aqui imediatamente antes de abrir o player; o PlayerViewModel a
 * consome para habilitar o botão "Próximo" e saltar para o próximo filme sem
 * voltar para a lista.
 *
 * Singleton em memória de propósito: a fila reflete a sessão de navegação atual.
 * Se o processo for recriado direto no player (sem passar pela lista) a fila
 * fica vazia e o botão Próximo simplesmente não aparece — sem regressão.
 */
@Singleton
class MovieQueue @Inject constructor() {
    @Volatile
    var items: List<PlayerArgs> = emptyList()

    fun indexOf(streamId: Int): Int = items.indexOfFirst { it.streamId == streamId }
}
