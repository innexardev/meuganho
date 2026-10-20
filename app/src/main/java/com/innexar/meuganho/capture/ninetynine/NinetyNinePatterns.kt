package com.innexar.meuganho.capture.ninetynine

import com.innexar.meuganho.capture.core.OfferPatterns
import com.innexar.meuganho.capture.core.PlatformPatterns
import com.innexar.meuganho.capture.core.ScreenPatterns

/**
 * ÚNICO lugar com textos da interface do 99 Motorista.
 * Mesmas regras de calibração de [com.innexar.meuganho.capture.uber.UberPatterns].
 */
object NinetyNinePatterns {
    val v1 = PlatformPatterns(
        offer = OfferPatterns(
            // Layout real (print 28/09/2026): "R$12,10", "R$1,25/km", "8 min (2,6 km)" sem âncora,
            // "R$3,30 por embarque distante" (já incluso), Negocia: "Aceitar por R$13,07" + contrapropostas.
            acceptKeywords = listOf("aceitar", "aceitar corrida", "aceitar por", "toque para aceitar"),
            pickupAnchors = listOf("embarque", "ate o passageiro", "de voce", "ate a origem"),
            tripAnchors = listOf("corrida de", "destino", "percurso", "viagem de"),
            promoKeywords = listOf("ganhe", "promocao", "desafio", "meta", "bonus de", "campanha"),
            categories = listOf("pop", "99pop", "comfort", "99comfort", "taxi", "99taxi", "top", "negocia", "negocia · dinheiro", "moto", "99moto", "entrega"),
            priceViewIds = listOf("price", "fare", "valor"),
            pickupViewIds = listOf("pickup", "embarque"),
            tripViewIds = listOf("trip", "destino"),
        ),
        screens = ScreenPatterns(
            toPickup = listOf("corrida aceita", "a caminho do embarque", "indo ao embarque", "buscar passageiro", "cheguei"),
            waiting = listOf("aguardando passageiro", "esperando passageiro", "iniciar corrida"),
            inTrip = listOf("corrida em dinheiro", "finalizar corrida", "encerrar corrida", "a caminho do destino", "em corrida"),
            completed = listOf("como foi sua corrida", "avaliar como anonimo", "corrida finalizada", "corrida concluida", "voce recebeu", "valor da corrida"),
            cancelled = listOf("corrida cancelada", "cancelou a corrida"),
            dailyEarnings = listOf("ganhos de hoje", "ganhos do dia", "hoje r$"),
            idle = listOf("procurando corridas", "voce esta online", "voce esta offline", "ficar online", "buscando corridas"),
        ),
    )
}
