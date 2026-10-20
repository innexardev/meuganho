package com.innexar.meuganho.capture

import com.innexar.meuganho.capture.core.OcrCleanup
import com.innexar.meuganho.capture.core.ParserRegistry
import com.innexar.meuganho.capture.core.PlatformDetector
import com.innexar.meuganho.capture.core.ScreenLine
import com.innexar.meuganho.capture.core.ScreenSnapshot
import com.innexar.meuganho.capture.core.ScreenType
import com.innexar.meuganho.capture.core.TextPatterns
import com.innexar.meuganho.domain.model.CaptureChannel
import com.innexar.meuganho.domain.model.DataStatus
import com.innexar.meuganho.domain.validation.OfferAssessment
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Gabarito do iPhone: todas as telas reais (fixtures) passam pelo leitor do Android em "modo iPhone"
 * (só o texto, como o OCR da Transmissão de tela entrega) e o resultado vai para ios/Tests/Fixtures.
 * O teste do iPhone (ios/Tests/ParityTests.swift) lê as MESMAS telas e precisa chegar aos MESMOS números.
 */
class IphoneParityExportTest {
    @Test fun `exporta telas e resultado esperado para o iPhone`() {
        val src = File("src/test/resources/fixtures")
        val out = File("../ios/Tests/Fixtures").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val entries = mutableListOf<String>()
        var offers = 0
        for (dir in listOf("uber", "ninetynine")) {
            File(src, dir).listFiles { f -> f.extension == "txt" }!!.sortedBy { it.name }.forEach { f ->
                val texts = f.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
                    .map { it.replace(Regex("""\s*@(id|g):\S+"""), "").trim() }.filter { it.isNotEmpty() }
                val name = "${dir}_${f.nameWithoutExtension}.txt"
                File(out, name).writeText(texts.joinToString("\n") + "\n")
                // Mesmo caminho do coordenador para leitura por imagem: notificação própria fora, "RS" corrigido.
                val lines = OcrCleanup.withoutOwnNotification(texts.map { ScreenLine(it) }).map { ScreenLine(TextPatterns.fixOcr(it.text)) }
                val platform = PlatformDetector.detect(lines)
                val snap = ScreenSnapshot(ScreenSnapshot.WHOLE_SCREEN, 0, 0, lines, CaptureChannel.OCR)
                val isOffer = platform != null && ParserRegistry.forPlatform(platform).classify(snap) == ScreenType.OFFER
                val parsed = if (isOffer) ParserRegistry.forPlatform(platform!!).parseOffers(snap)
                    .map { it to OfferAssessment.assess(it, null).status }.filter { it.second != DataStatus.INVALID } else emptyList()
                offers += parsed.size
                entries += buildString {
                    append("{\"file\":\"$name\",\"platform\":${platform?.let { "\"${it.name}\"" } ?: "null"},\"isOffer\":$isOffer,\"offers\":[")
                    append(parsed.joinToString(",") { (o, st) ->
                        "{\"cents\":${o.offeredCents.value},\"pickupM\":${o.pickupMeters.value},\"pickupS\":${o.pickupSeconds.value}," +
                            "\"tripM\":${o.tripMeters.value},\"tripS\":${o.tripSeconds.value},\"status\":\"${st.name}\",\"confidence\":\"${o.criticalConfidence.name}\"}"
                    })
                    append("]}")
                }
            }
        }
        File(out, "expected.json").writeText("[\n" + entries.joinToString(",\n") + "\n]\n")
        println("iPhone: ${entries.size} telas, $offers ofertas no gabarito")
        assertTrue(offers > 10)
    }
}
