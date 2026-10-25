package com.iptv.app.ui.common

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager

/**
 * Lembra qual item de uma lista/grade tinha o foco para devolvê-lo quando o
 * D-pad entra de novo no painel (#27).
 *
 * Sem isso, ao voltar da gaveta de categorias para a grade de canais o Compose
 * escolhia o item geometricamente mais próximo da gaveta — geralmente um canal
 * aleatório da primeira coluna, ou o primeiro da lista — e o usuário perdia o
 * lugar. Regras ao entrar no painel:
 *  1. o último item focado, se ainda estiver na tela;
 *  2. senão, o item indicado pelo `fallback` (ex.: o primeiro visível no topo);
 *  3. senão, o comportamento padrão do Compose.
 *
 * Só itens compostos (visíveis) são candidatos: um FocusRequester de item que a
 * lista virtualizada já descartou não pode receber foco.
 */
@Stable
class FocusMemory {
    private val requesters = HashMap<Any, FocusRequester>()
    private val attached = HashSet<Any>()
    var lastFocusedKey: Any? = null
        private set

    /** Fallback registrado pelo contêiner ([restoreFocusFrom]) — permite que
     *  outro painel (ex.: a gaveta) mande o foco para cá com as mesmas regras. */
    internal var fallbackKeyProvider: (() -> Any?)? = null

    fun requesterFor(key: Any): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    internal fun attach(key: Any) {
        attached += key
    }

    internal fun detach(key: Any) {
        attached -= key
        requesters.remove(key)
    }

    internal fun onFocused(key: Any) {
        lastFocusedKey = key
    }

    /** Alvo do foco ao entrar no painel, seguindo as regras da classe. */
    fun enterTarget(fallbackKey: Any? = fallbackKeyProvider?.invoke()): FocusRequester {
        lastFocusedKey?.takeIf { it in attached }?.let { return requesterFor(it) }
        fallbackKey?.takeIf { it in attached }?.let { return requesterFor(it) }
        return FocusRequester.Default
    }
}

/** [FocusMemory] que zera quando [resetKey] muda (ex.: troca de categoria). */
@Composable
fun rememberFocusMemory(resetKey: Any? = Unit): FocusMemory = remember(resetKey) { FocusMemory() }

/** Registra o item [key] na [memory]. Use em cada item da lista/grade. */
@Composable
fun Modifier.focusMemoryItem(memory: FocusMemory, key: Any): Modifier {
    DisposableEffect(memory, key) {
        memory.attach(key)
        onDispose { memory.detach(key) }
    }
    return this
        .focusRequester(memory.requesterFor(key))
        .onFocusChanged { if (it.isFocused) memory.onFocused(key) }
}

/**
 * Aplica no contêiner da lista/grade: quando o foco entra vindo de fora,
 * vai para o alvo de [FocusMemory.enterTarget]. [fallbackKey] é avaliado no
 * momento da entrada (ex.: chave do primeiro item visível).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.restoreFocusFrom(
    memory: FocusMemory,
    fallbackKey: () -> Any?,
    /** Para onde vai o foco ao sair pela esquerda (ex.: a gaveta recolhida,
     *  cujos itens não ficam geometricamente à esquerda da grade e, por isso,
     *  a busca de foco padrão nunca os encontraria). */
    onExitLeft: (() -> FocusRequester)? = null,
    /** → sempre leva o foco para este destino (ex.: da gaveta para a grade,
     *  em vez do item que estiver geometricamente à direita). */
    onExitRight: (() -> FocusRequester)? = null
): Modifier {
    val focusManager = LocalFocusManager.current
    SideEffect { memory.fallbackKeyProvider = fallbackKey }
    val rightModifier = if (onExitRight == null) Modifier else Modifier.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown || event.key != Key.DirectionRight) return@onPreviewKeyEvent false
        val target = onExitRight()
        if (target == FocusRequester.Default) return@onPreviewKeyEvent false
        runCatching { target.requestFocus() }.isSuccess
    }
    val exitModifier = if (onExitLeft == null) Modifier else Modifier.onPreviewKeyEvent { event ->
        // ← dentro do painel: tenta o movimento normal; só quando não há
        // para onde ir (primeira coluna) o foco vai para o destino de saída.
        // (O `exit` de focusProperties não dispara sem candidato geométrico.)
        if (event.type != KeyEventType.KeyDown || event.key != Key.DirectionLeft) return@onPreviewKeyEvent false
        if (focusManager.moveFocus(FocusDirection.Left)) return@onPreviewKeyEvent true
        runCatching { onExitLeft().requestFocus() }
        true
    }
    return this
        .then(exitModifier)
        .then(rightModifier)
        .focusProperties { enter = { memory.enterTarget(fallbackKey()) } }
        .focusGroup()
}
