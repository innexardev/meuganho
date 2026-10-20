import Foundation

// Porte fiel do Android (com.innexar.meuganho.domain.model / capture.core). Mesmos nomes, mesmas regras:
// uma correção num lado deve ir para o outro, e os testes usam as MESMAS telas reais (Tests/Fixtures).

enum Platform: String, CaseIterable, Codable {
    case uber = "UBER"
    case ninetyNine = "NINETY_NINE"

    var label: String { self == .uber ? "Uber" : "99" }
}

/// Níveis discretos (LOW < MEDIUM < HIGH). A confiança de uma oferta é o MÍNIMO dos campos críticos.
enum Confidence: Int, Comparable, Codable {
    case low = 0, medium = 1, high = 2

    static func < (a: Confidence, b: Confidence) -> Bool { a.rawValue < b.rawValue }
    var name: String { ["LOW", "MEDIUM", "HIGH"][rawValue] }
}

enum FieldSource { case nodeId, textAnchor, regex, missing }

/// Um valor capturado que sabe de onde veio e por que confiamos nele.
struct Captured<T> {
    var value: T?
    var source: FieldSource
    var confidence: Confidence
    var reasons: [String] = []

    var isPresent: Bool { value != nil }

    static func missing(_ reason: String) -> Captured<T> {
        Captured(value: nil, source: .missing, confidence: .low, reasons: [reason])
    }

    func capped(at max: Confidence) -> Captured<T> {
        var c = self
        if confidence > max { c.confidence = max }
        return c
    }

    func atLeast(_ level: Confidence, reason: String) -> Captured<T> {
        guard value != nil, confidence < level else { return self }
        var c = self
        c.confidence = level
        c.reasons = reasons.filter { !$0.contains("não encontrado") } + [reason]
        return c
    }
}

/// Uma linha de texto da tela, na ordem de leitura (de cima para baixo).
struct ScreenLine {
    let text: String
    let normalized: String

    init(_ text: String) {
        self.text = text
        self.normalized = TextPatterns.normalize(text)
    }
}

/// Oferta normalizada: Uber e 99 viram este modelo; o cálculo só conhece ele.
struct RideOffer {
    var platform: Platform
    var detectedAt: Date
    var offeredCents: Captured<Int>
    var pickupMeters: Captured<Int>
    var pickupSeconds: Captured<Int>
    var tripMeters: Captured<Int>
    var tripSeconds: Captured<Int>
    var surgeCents: Int?
    var category: String?
    var warnings: [String] = []
    /// Os números bateram com o R$/km exibido pelo próprio app (prova independente).
    var confirmedByPlatform = false

    var totalMeters: Int? { tripMeters.value.map { $0 + (pickupMeters.value ?? 0) } }
    var totalSeconds: Int? { tripSeconds.value.map { $0 + (pickupSeconds.value ?? 0) } }

    var criticalFields: [(String, Confidence, [String])] {
        [("valor", offeredCents.confidence, offeredCents.reasons),
         ("km até passageiro", pickupMeters.confidence, pickupMeters.reasons),
         ("tempo até passageiro", pickupSeconds.confidence, pickupSeconds.reasons),
         ("km da viagem", tripMeters.confidence, tripMeters.reasons),
         ("tempo da viagem", tripSeconds.confidence, tripSeconds.reasons)]
    }

    var criticalConfidence: Confidence { criticalFields.map(\.1).min() ?? .low }

    /// Mesma oferta lida em vários quadros da transmissão = uma notificação só.
    var fingerprint: String {
        [platform.rawValue, offeredCents.value.map(String.init) ?? "-",
         pickupMeters.value.map { String($0 / 100) } ?? "-",
         tripMeters.value.map { String($0 / 100) } ?? "-",
         tripSeconds.value.map { String($0 / 60) } ?? "-"].joined(separator: "|")
    }
}

enum DataStatus: String { case autoAccepted = "AUTO_ACCEPTED", needsReview = "NEEDS_REVIEW", invalid = "INVALID" }

enum OfferRating: String {
    case excellent = "EXCELENTE", acceptable = "ACEITÁVEL", poor = "RUIM", uncertain = "INCERTO"

    var symbol: String {
        switch self { case .excellent: "▲"; case .acceptable: "●"; case .poor: "▼"; case .uncertain: "?" }
    }
}
