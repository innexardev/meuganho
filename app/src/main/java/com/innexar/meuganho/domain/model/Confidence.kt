package com.innexar.meuganho.domain.model

/**
 * Níveis discretos em vez de um float: "0.73" não diz o que está incerto nem
 * pode ser testado com clareza. A ordem importa (LOW < MEDIUM < HIGH) porque a
 * confiança de uma oferta é o MÍNIMO dos campos críticos.
 */
enum class Confidence(val label: String) { LOW("baixa"), MEDIUM("média"), HIGH("alta");

    companion object {
        fun min(values: Iterable<Confidence>): Confidence = values.minOrNull() ?: LOW
    }
}

/** De onde veio o valor. Estruturas (id de view, âncora) valem mais que regex solta. */
enum class FieldSource { NODE_ID, TEXT_ANCHOR, REGEX, NOTIFICATION, DERIVED, USER, MISSING }

/** Canal pelo qual a tela chegou até nós. */
enum class CaptureChannel { ACCESSIBILITY, NOTIFICATION, OCR }

/**
 * Um valor capturado que sabe de onde veio e por que confiamos nele.
 */
data class Captured<T>(
    val value: T?,
    val source: FieldSource,
    val confidence: Confidence,
    val reasons: List<String> = emptyList(),
) {
    val isPresent: Boolean get() = value != null

    companion object {
        fun <T> missing(reason: String): Captured<T> =
            Captured(null, FieldSource.MISSING, Confidence.LOW, listOf(reason))
    }
}

fun <T> Captured<T>.cappedAt(max: Confidence): Captured<T> =
    if (confidence > max) copy(confidence = max) else this
