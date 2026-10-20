package com.innexar.meuganho.platform.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import com.innexar.meuganho.capture.core.ScreenLine

/** Radiografia da leitura: prova se a árvore foi cortada pelos limites ou veio vazia do app. */
data class TreeStats(
    val visited: Int,
    val maxDepth: Int,
    val truncatedByNodes: Boolean,
    val truncatedByDepth: Boolean,
    val invisibleSkipped: Int,
    val topClasses: String,
) {
    override fun toString() =
        "nodes=$visited depth=$maxDepth cutNodes=$truncatedByNodes cutDepth=$truncatedByDepth hidden=$invisibleSkipped classes=$topClasses"
}

class TreeRead(val lines: List<ScreenLine>, val stats: TreeStats)

/**
 * Converte a árvore de acessibilidade em linhas de texto na ordem de leitura.
 *
 * Travessia iterativa (sem recursão) e com limites. Os limites foram ampliados na
 * 0.1.4: apps com árvores muito profundas (componentes aninhados, como a 99)
 * tinham o cartão da oferta cortado com os antigos 30 níveis / 400 nós.
 * Só nós visíveis ao usuário entram: o Uber mantém cartões antigos escondidos na
 * árvore, e lê-los geraria "ofertas fantasmas".
 */
object NodeTreeReader {

    private const val MAX_NODES = 1500
    private const val MAX_DEPTH = 100

    private class Item(val node: AccessibilityNodeInfo, val depth: Int, val group: Int)

    /**
     * [groupOffset] mantém os grupos únicos quando várias janelas do mesmo app são lidas.
     * Um nó clicável COM filhos abre um grupo novo (cartão); botões-folha herdam o do pai.
     */
    fun read(root: AccessibilityNodeInfo, groupOffset: Int = 0): TreeRead {
        val lines = ArrayList<ScreenLine>(64)
        val stack = ArrayDeque<Item>()
        val classes = HashMap<String, Int>()
        stack.addLast(Item(root, 0, -1))
        var visited = 0
        var maxDepth = 0
        var cutDepth = false
        var hidden = 0
        while (stack.isNotEmpty() && visited < MAX_NODES) {
            val item = stack.removeLast()
            val node = item.node
            visited++
            maxDepth = maxOf(maxDepth, item.depth)
            node.className?.toString()?.substringAfterLast('.')?.let { classes[it] = (classes[it] ?: 0) + 1 }
            if (!node.isVisibleToUser) {
                hidden++
                continue
            }
            val group = if (node.isClickable && node.childCount > 0) groupOffset + visited else item.group
            val text = node.text?.toString()?.trim().orEmpty()
                .ifEmpty { node.contentDescription?.toString()?.trim().orEmpty() }
            if (text.isNotEmpty()) {
                lines += ScreenLine(text, node.viewIdResourceName?.substringAfter(":id/"), group)
            }
            if (item.depth < MAX_DEPTH) {
                // Empilha de trás para frente para visitar os filhos na ordem natural.
                for (i in node.childCount - 1 downTo 0) {
                    node.getChild(i)?.let { stack.addLast(Item(it, item.depth + 1, group)) }
                }
            } else if (node.childCount > 0) {
                cutDepth = true
            }
        }
        val stats = TreeStats(
            visited = visited,
            maxDepth = maxDepth,
            truncatedByNodes = stack.isNotEmpty(),
            truncatedByDepth = cutDepth,
            invisibleSkipped = hidden,
            topClasses = classes.entries.sortedByDescending { it.value }.take(5).joinToString(",") { "${it.key}:${it.value}" },
        )
        return TreeRead(lines, stats)
    }
}
