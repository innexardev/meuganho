import Foundation

/// Porte de AnchoredOfferExtractor.kt + OfferBlocks.kt + KeywordPlatformParser.parseOffers + PlatformRateCheck.kt.
/// No iPhone tudo chega por OCR: sem ids de view nem cartões clicáveis, só o texto.
enum OfferExtractor {

    /// A tela é uma oferta? (mesmas regras do Android para leitura por imagem)
    static func isOffer(_ lines: [ScreenLine], _ p: OfferPatterns) -> Bool {
        let hasMoney = lines.contains { TextPatterns.moneyIn($0.text).contains { !$0.isAddition } }
        if hasMoney && lines.contains(where: { TextPatterns.containsAny($0.normalized, p.acceptKeywords) }) { return true }
        // Botão fora da tela: preço isolado + R$/km do app + tempo/distância.
        return lines.contains { TextPatterns.isStandalonePrice($0.text) }
            && lines.contains { TextPatterns.platformRatePerKm($0.text) != nil }
            && lines.contains { TextPatterns.hasTimeAndDistance($0.normalized) }
    }

    static func parseOffers(_ lines: [ScreenLine], platform: Platform, at: Date) -> [RideOffer] {
        let p = OfferPatterns.of(platform)
        let (blocks, textual) = split(lines)
        return blocks.compactMap { block in
            let x = extract(block, p)
            guard x.price.isPresent else { return nil }
            // Leitura por imagem: no máximo MEDIUM; a prova do R$/km do app pode subir o preço para HIGH.
            var price = x.price.capped(at: .medium)
            if textual { price = price.capped(at: .medium) }
            let offer = RideOffer(
                platform: platform, detectedAt: at,
                offeredCents: price,
                pickupMeters: x.pickupMeters.capped(at: .medium), pickupSeconds: x.pickupSeconds.capped(at: .medium),
                tripMeters: x.tripMeters.capped(at: .medium), tripSeconds: x.tripSeconds.capped(at: .medium),
                surgeCents: x.surge, category: x.category, warnings: x.warnings
            )
            return rateCheck(offer, shown: x.shownCentsPerKm)
        }
    }

    // MARK: - Cartões (Radar com várias ofertas)

    /// Fatias que começam em cada preço isolado, se cada fatia tiver tempo/distância; senão, a tela toda.
    private static func split(_ lines: [ScreenLine]) -> ([[ScreenLine]], Bool) {
        let idx = lines.indices.filter { TextPatterns.isStandalonePrice(lines[$0].text) }
        if idx.count >= 2 {
            let slices = idx.enumerated().map { k, start in Array(lines[start..<(k + 1 < idx.count ? idx[k + 1] : lines.count)]) }
            if slices.allSatisfy({ $0.contains { TextPatterns.hasTimeAndDistance($0.normalized) } }) { return (slices, true) }
        }
        return ([lines], false)
    }

    // MARK: - Extração por âncoras

    private struct Extracted {
        var price: Captured<Int>
        var surge: Int?
        var pickupMeters: Captured<Int>
        var pickupSeconds: Captured<Int>
        var tripMeters: Captured<Int>
        var tripSeconds: Captured<Int>
        var category: String?
        var warnings: [String]
        var shownCentsPerKm: Int?
    }

    private struct TimeDistance {
        var seconds: Int?
        var meters: Int?
        var confidence: Confidence
        var source: FieldSource
    }

    private static func extract(_ lines: [ScreenLine], _ p: OfferPatterns) -> Extracted {
        var warnings: [String] = []
        let shownRate = lines.lazy.compactMap { TextPatterns.platformRatePerKm($0.text) }.first
        // Promoções e o "R$1,58/km aprox." nunca são candidatos a preço.
        let keep = lines.map { !TextPatterns.containsAny($0.normalized, p.promoKeywords) && TextPatterns.platformRatePerKm($0.text) == nil }
        let usable = lines.indices.filter { keep[$0] }.map { lines[$0] }
        // Igual ao Android: qualquer linha descartada com dinheiro (inclusive o "R$/km aprox.").
        let promoIgnored = lines.indices.contains { !keep[$0] && !TextPatterns.moneyIn(lines[$0].text).isEmpty }
        let price = extractPrice(usable, p, &warnings)
        if promoIgnored && price.confidence != .high { warnings.append("Valor de promoção/meta na mesma tela") }
        let surge = usable.flatMap { TextPatterns.moneyIn($0.text) }.first { $0.isAddition }?.cents

        func isAnchor(_ l: ScreenLine, _ anchors: [String]) -> Bool {
            TextPatterns.containsAny(l.normalized, anchors) && !TextPatterns.containsAny(l.normalized, p.acceptKeywords)
        }
        let pickupIdx = usable.firstIndex { isAnchor($0, p.pickupAnchors) }
        let tripIdx = usable.indices.first { $0 != pickupIdx && isAnchor(usable[$0], p.tripAnchors) }

        var pickup = pickupIdx.flatMap { pairAround(usable, $0) }
        var trip = tripIdx.flatMap { pairAround(usable, $0) }

        if pickup == nil || trip == nil {
            let loose = usable.compactMap { pairIn($0.normalized) }
            if trip == nil && pickup == nil && loose.count >= 2 {
                // Dois pares: o TOTAL é certo mesmo se a ordem estivesse trocada (MEDIUM). Mais de dois: ambíguo (LOW).
                let level: Confidence = loose.count == 2 ? .medium : .low
                pickup = TimeDistance(seconds: loose[0].seconds, meters: loose[0].meters, confidence: level, source: .regex)
                trip = TimeDistance(seconds: loose[1].seconds, meters: loose[1].meters, confidence: level, source: .regex)
                if level == .low { warnings.append("Busca/viagem identificadas pela ordem, sem âncora") }
            } else if trip == nil, let found = pickup {
                trip = loose.first { $0.seconds != found.seconds || $0.meters != found.meters }
                    .map { TimeDistance(seconds: $0.seconds, meters: $0.meters, confidence: .low, source: .regex) }
            } else if pickup == nil, let found = trip {
                pickup = loose.first { $0.seconds != found.seconds || $0.meters != found.meters }
                    .map { TimeDistance(seconds: $0.seconds, meters: $0.meters, confidence: .low, source: .regex) }
            }
        }

        let category = usable.lazy.compactMap { l in
            p.categories.first { l.normalized == $0 || l.normalized.hasPrefix($0 + " ") }.map { _ in l.text.trimmingCharacters(in: .whitespaces) }
        }.first

        return Extracted(
            price: price, surge: surge,
            pickupMeters: meters(pickup, "km até passageiro"), pickupSeconds: seconds(pickup, "tempo até passageiro"),
            tripMeters: meters(trip, "km da viagem"), tripSeconds: seconds(trip, "tempo da viagem"),
            category: category, warnings: warnings, shownCentsPerKm: shownRate
        )
    }

    private static func extractPrice(_ lines: [ScreenLine], _ p: OfferPatterns, _ warnings: inout [String]) -> Captured<Int> {
        // "Aceitar por R$13,07" (99 Negocia): o botão diz exatamente o valor.
        if let l = lines.first(where: { TextPatterns.containsAny($0.normalized, p.acceptKeywords) }) {
            let m = TextPatterns.moneyIn(l.text).filter { !$0.isAddition }
            if m.count == 1 { return Captured(value: m[0].cents, source: .textAnchor, confidence: .high, reasons: ["valor do botão de aceite"]) }
        }
        if let l = lines.first(where: { TextPatterns.isPriceRange($0.text) }), let low = TextPatterns.moneyIn(l.text).first {
            warnings.append("Preço em faixa; usado o menor valor")
            return Captured(value: low.cents, source: .regex, confidence: .low, reasons: ["faixa de preço"])
        }
        var standalone: [Int] = []
        for l in lines where TextPatterns.isStandalonePrice(l.text) {
            let c = TextPatterns.moneyIn(l.text)[0].cents
            if !standalone.contains(c) { standalone.append(c) }
        }
        if standalone.count == 1 { return Captured(value: standalone[0], source: .textAnchor, confidence: .high) }
        if standalone.count > 1 {
            return Captured(value: standalone[0], source: .textAnchor, confidence: .low, reasons: ["vários valores isolados na tela"])
        }
        var embedded: [Int] = []
        for m in lines.flatMap({ TextPatterns.moneyIn($0.text) }) where !m.isAddition && !embedded.contains(m.cents) { embedded.append(m.cents) }
        switch embedded.count {
        case 0: return .missing("valor não encontrado")
        case 1: return Captured(value: embedded[0], source: .regex, confidence: .medium, reasons: ["valor dentro de texto"])
        default: return Captured(value: embedded[0], source: .regex, confidence: .low, reasons: ["vários valores no texto"])
        }
    }

    private static func pairIn(_ normalized: String) -> TimeDistance? {
        guard let s = TextPatterns.durationSeconds(normalized), let m = TextPatterns.distanceMeters(normalized) else { return nil }
        return TimeDistance(seconds: s, meters: m, confidence: .high, source: .textAnchor)
    }

    private static func pairAround(_ lines: [ScreenLine], _ i: Int) -> TimeDistance? {
        if let pair = pairIn(lines[i].normalized) { return pair }
        let window = [i - 1, i, i + 1].filter { lines.indices.contains($0) }.map { lines[$0] }
        let s = window.lazy.compactMap { TextPatterns.durationSeconds($0.normalized) }.first
        let m = window.lazy.compactMap { TextPatterns.distanceMeters($0.normalized) }.first
        if s == nil && m == nil { return nil }
        return TimeDistance(seconds: s, meters: m, confidence: .medium, source: .textAnchor)
    }

    private static func meters(_ t: TimeDistance?, _ label: String) -> Captured<Int> {
        guard let t, let m = t.meters else { return .missing("\(label) não encontrado") }
        return Captured(value: m, source: t.source, confidence: t.confidence)
    }

    private static func seconds(_ t: TimeDistance?, _ label: String) -> Captured<Int> {
        guard let t, let s = t.seconds else { return .missing("\(label) não encontrado") }
        return Captured(value: s, source: t.source, confidence: t.confidence)
    }

    // MARK: - Prova com o R$/km do próprio app

    /// O Uber mostra "R$1,58/km aprox." sobre o km TOTAL. Se bate com valor ÷ nossos km, a leitura está certa.
    private static func rateCheck(_ offer: RideOffer, shown: Int?) -> RideOffer {
        guard let shown, shown > 0, let value = offer.offeredCents.value, let meters = offer.totalMeters, meters > 0 else { return offer }
        let computed = Double(value) * 1000 / Double(meters)
        var o = offer
        guard abs(computed - Double(shown)) <= max(2.0, Double(shown) * 0.03) else {
            o.warnings.append("R$/km calculado (\(Int(computed))) difere do exibido pelo app (\(shown))")
            return o
        }
        let reason = "confere com o R$/km exibido pelo app"
        o.offeredCents = o.offeredCents.atLeast(.high, reason: reason)
        o.pickupMeters = o.pickupMeters.atLeast(.medium, reason: reason)
        o.tripMeters = o.tripMeters.atLeast(.medium, reason: reason)
        o.pickupSeconds = o.pickupSeconds.atLeast(.medium, reason: reason)
        o.tripSeconds = o.tripSeconds.atLeast(.medium, reason: reason)
        o.warnings = o.warnings.filter { !$0.contains("pela ordem") }
        o.confirmedByPlatform = true
        return o
    }
}
