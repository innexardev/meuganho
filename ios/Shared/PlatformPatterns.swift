import Foundation

/// Palavras da OFERTA de cada plataforma (porte de UberPatterns.kt / NinetyNinePatterns.kt).
/// Sempre minúsculas e SEM acento (comparadas com texto normalizado).
struct OfferPatterns {
    let acceptKeywords: [String]
    let pickupAnchors: [String]
    let tripAnchors: [String]
    let promoKeywords: [String]
    let categories: [String]

    static func of(_ p: Platform) -> OfferPatterns { p == .uber ? uber : ninetyNine }

    static let uber = OfferPatterns(
        acceptKeywords: ["aceitar", "toque para aceitar", "selecionar", "accept"],
        pickupAnchors: ["de distancia", "distancia", "away", "ate o passageiro"],
        tripAnchors: ["viagem de", "viagem:", "de viagem", "trip"],
        promoKeywords: ["ganhe", "promocao", "desafio", "missao", "sequencia", "quest", "garanta"],
        categories: ["uberx", "uber x", "comfort", "black", "flash", "moto", "juntos", "priority", "xl", "envios", "uber pet"]
    )

    static let ninetyNine = OfferPatterns(
        acceptKeywords: ["aceitar", "aceitar corrida", "aceitar por", "toque para aceitar"],
        pickupAnchors: ["embarque", "ate o passageiro", "de voce", "ate a origem"],
        tripAnchors: ["corrida de", "destino", "percurso", "viagem de"],
        promoKeywords: ["ganhe", "promocao", "desafio", "meta", "bonus de", "campanha"],
        categories: ["pop", "99pop", "comfort", "99comfort", "taxi", "99taxi", "top", "negocia", "negocia · dinheiro", "moto", "99moto", "entrega"]
    )
}

/// De qual app é esta tela, só pelo TEXTO (porte de PlatformDetector.kt). Empate ou nada = nil.
enum PlatformDetector {
    private static let uber = [
        "/km aprox", "uberx", "uber x", "comfort", "radar de viagens", "encontro com", "destino de ", "procurando viagens",
        "voce esta online", "voce esta offline", "pesquisar locais", "tendencias de ganhos", "historico de ganhos",
        "coletar pagamento", "aguardando o usuario", "filtro de destino", "nao e possivel ficar offline", "selecionar",
        "pessoa convidada", "viagem longa", "exclusivo", "nenhuma solicitacao",
    ]
    private static let ninetyNine = [
        " corridas", "cheguei no embarque", "iniciar corrida", "finalizar corrida", "chegada prevista", "central de ganhos",
        "corrida aceita", "como foi sua corrida", "avaliar como anonimo", "solicitacoes seguidas", "procurando corridas",
        "ganhos do dia", "corrida em dinheiro", "passageiro a caminho", "aceitar por", "negocia", "99pop", "conta99",
        "embarque distante", "cartao verif", "o passageiro cancelou a corrida", "passageiro confirmando",
        "extrato", "saldo r$", "99multiplica", "compensacao aprovada", "mapa do ",
    ]
    private static let multiplier = Rx(#"\d,\dx"#)

    static func detect(_ lines: [ScreenLine]) -> Platform? {
        let text = lines.map { " " + $0.normalized }.joined(separator: "\n")
        let u = uber.filter { text.contains($0) }.count
        let n = ninetyNine.filter { text.contains($0) }.count
            + (multiplier.contains(in: text) ? 1 : 0)
            + (lines.contains { $0.normalized == "buscando" } ? 1 : 0)
        if u == 0 && n == 0 { return nil }
        return u > n ? .uber : (n > u ? .ninetyNine : nil)
    }
}
