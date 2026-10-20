package com.innexar.meuganho.capture.uber

import com.innexar.meuganho.capture.core.OfferPatterns
import com.innexar.meuganho.capture.core.PlatformPatterns
import com.innexar.meuganho.capture.core.ScreenPatterns

/**
 * ÚNICO lugar com textos da interface do Uber Driver (PT-BR, com fallback em inglês).
 *
 * V1 foi escrita a partir do layout público conhecido do app e PRECISA ser
 * calibrada com telas reais: Diagnóstico > "Gravar próxima tela" gera o texto
 * sanitizado que vira fixture em src/test/resources/fixtures/uber.
 */
object UberPatterns {
    val v1 = PlatformPatterns(
        offer = OfferPatterns(
            // "Selecionar" é o botão dos cartões do Radar de Viagens.
            acceptKeywords = listOf("aceitar", "toque para aceitar", "selecionar", "accept"),
            pickupAnchors = listOf("de distancia", "distancia", "away", "ate o passageiro"),
            tripAnchors = listOf("viagem de", "viagem:", "de viagem", "trip"),
            promoKeywords = listOf("ganhe", "promocao", "desafio", "missao", "sequencia", "quest", "garanta"),
            // Layout real (set/2026): "7 min (2.8 km)" e "43 minutos (20.0 km)" SEM palavra-âncora;
            // a ordem no cartão é busca -> viagem, confirmada pelo "R$x/km aprox." (PlatformRateCheck).
            categories = listOf("uberx", "uber x", "comfort", "black", "flash", "moto", "juntos", "priority", "xl", "envios", "uber pet"),
            priceViewIds = listOf("fare", "price", "earning"),
            pickupViewIds = listOf("pickup"),
            tripViewIds = listOf("trip_eta", "trip_distance", "dropoff"),
        ),
        screens = ScreenPatterns(
            // Real (28/09/2026): "Encontro com ‹nome›" (id key_info_label) = indo buscar.
            toPickup = listOf("encontro com", "buscando", "buscar ", "a caminho do embarque", "picking up"),
            waiting = listOf("aguardando", "esperando", "iniciar viagem", "waiting for", "start trip"),
            // Real (28/09/2026): "Destino de ‹nome›" (id key_info_label) = passageiro a bordo.
            inTrip = listOf("destino de ", // com espaço: "Filtro de destino desativado" NÃO é viagem (real, 30/09/2026)
                 "finalizar viagem", "concluir viagem", "encerrar viagem", "deixando", "dropping off", "complete trip"),
            completed = listOf("coletar pagamento", "viagem concluida", "viagem finalizada", "voce ganhou", "ganhos da viagem", "trip completed"),
            cancelled = listOf("viagem cancelada", "cancelou a viagem", "trip cancelled", "trip canceled"),
            dailyEarnings = listOf("ganhos de hoje", "today's earnings", "hoje r$"),
            // "pagina inicial" = barra de navegação da tela inicial (visto em amostra real de 28/09/2026).
            earningsHistory = listOf("historico de ganhos"),
            idle = listOf("nenhuma solicitacao no momento", "pagina inicial", "alta demanda aqui", "pesquisar locais", "tendencias de ganhos", "voce esta online", "procurando viagens", "encontrando viagens", "voce esta offline", "ficar online", "you're online", "finding trips"),
        ),
    )
}
