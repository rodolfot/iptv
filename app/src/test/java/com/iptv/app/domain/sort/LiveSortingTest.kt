package com.iptv.app.domain.sort

import com.iptv.app.domain.model.LiveChannel
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveSortingTest {

    private fun ch(id: Int, num: Int?, name: String) = LiveChannel(
        id = id,
        num = num,
        name = name,
        logoUrl = null,
        categoryId = "1",
        epgChannelId = null,
        addedTimestamp = 0L
    )

    private val channels = listOf(
        ch(id = 9001, num = null, name = "Sem número B"),
        ch(id = 12, num = 3, name = "Canal 3"),
        ch(id = 5, num = 1, name = "Canal 1"),
        ch(id = 9000, num = null, name = "Sem número A"),
        ch(id = 7, num = 2, name = "Canal 2")
    )

    @Test
    fun `ID crescente segue o número do canal e deixa os sem número no fim`() {
        // Antes os sem número caíam no streamId (9000+) — aqui continuam no
        // fim, mas em ordem alfabética e nunca no meio da numeração.
        val names = channels.sorted(SortOption.ID_ASC).map { it.name }
        assertEquals(
            listOf("Canal 1", "Canal 2", "Canal 3", "Sem número A", "Sem número B"),
            names
        )
    }

    @Test
    fun `ID decrescente inverte só os numerados`() {
        val names = channels.sorted(SortOption.ID_DESC).map { it.name }
        assertEquals(
            listOf("Canal 3", "Canal 2", "Canal 1", "Sem número A", "Sem número B"),
            names
        )
    }

    @Test
    fun `número repetido desempata pelo nome`() {
        val tied = listOf(ch(1, 4, "Zeta"), ch(2, 4, "Alfa"))
        assertEquals(listOf("Alfa", "Zeta"), tied.sorted(SortOption.ID_ASC).map { it.name })
    }
}
