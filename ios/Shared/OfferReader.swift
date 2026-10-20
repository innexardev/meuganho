import Foundation

/// Uma oferta lida e analisada, pronta para a notificação.
struct ReadOffer {
    let offer: RideOffer
    let status: DataStatus
    let reasons: [String]
    let analysis: OfferAnalysis
}

/// Resultado de uma leitura de tela.
struct ScreenRead {
    let platform: Platform?
    let isOffer: Bool
    let offers: [ReadOffer]
    let lines: [ScreenLine]
}

/// Texto da tela inteira (OCR do iPhone) -> ofertas analisadas. Função pura: testável sem iPhone.
enum OfferReader {

    static func read(rawLines: [String], at: Date = Date(), settings: Settings) -> ScreenRead {
        // Mesma ordem do Android: tira a nossa notificação, depois corrige o "RS" do OCR.
        let lines = withoutOwnNotification(rawLines).map(TextPatterns.fixOcr).map(ScreenLine.init)
        guard let platform = PlatformDetector.detect(lines) else { return ScreenRead(platform: nil, isOffer: false, offers: [], lines: lines) }
        guard OfferExtractor.isOffer(lines, OfferPatterns.of(platform)) else {
            return ScreenRead(platform: platform, isOffer: false, offers: [], lines: lines)
        }
        let offers = OfferExtractor.parseOffers(lines, platform: platform, at: at).map { o -> ReadOffer in
            let d = OfferAssessment.assess(o)
            let a = OfferAnalysis.analyze(o, reliable: d.status == .autoAccepted, cost: settings.costModel, thresholds: settings.thresholds)
            return ReadOffer(offer: o, status: d.status, reasons: d.reasons, analysis: a)
        }
        return ScreenRead(platform: platform, isOffer: true, offers: offers.filter { $0.status != .invalid }, lines: lines)
    }

    /// A transmissão grava a tela INTEIRA, inclusive a nossa notificação ("99 · R$ 6,10 …" + "R$ 6,52/km R$92/h").
    /// Ela some antes da leitura (real, Android 01/10/2026: o OCR lia a notificação como se fosse da 99).
    static func withoutOwnNotification(_ lines: [String]) -> [String] {
        let title = Rx(#"^(★.*)?(99|uber)\W{0,3}R\$\s?\d+,\d{2}"#, .caseInsensitive)
        let metric = Rx(#"R\$\s?\d+(,\d+)?/(h|min|mi)|/km\s+R\$|R\$\s\d+,\d{2}/km"#)
        var out: [String] = []
        var skipping = false
        for l in lines {
            let t = l.trimmingCharacters(in: .whitespaces)
            if title.contains(in: t) { skipping = true; continue }
            if skipping && metric.contains(in: t) { continue }
            skipping = false
            out.append(l)
        }
        return out
    }
}
