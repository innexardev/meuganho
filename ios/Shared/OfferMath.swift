import Foundation

/// Porte de OfferValidator.kt + StatusResolver.kt + OfferAssessment.kt (sem a faixa pessoal: o iPhone não guarda histórico).
enum OfferAssessment {
    struct Decision { let status: DataStatus; let reasons: [String] }

    static func assess(_ o: RideOffer) -> Decision {
        var hard: [String] = []
        var soft: [String] = []
        if let v = o.offeredCents.value {
            if v <= 0 { hard.append("Valor zero ou negativo") }
            else if v < 300 || v > 50_000 { soft.append("Valor fora da faixa esperada") }
        } else {
            hard.append("Valor da oferta não encontrado")
        }
        let trip = o.tripMeters.value, tripTime = o.tripSeconds.value, pickup = o.pickupMeters.value
        if (trip ?? 0) < 0 || (pickup ?? 0) < 0 || (tripTime ?? 0) < 0 || (o.pickupSeconds.value ?? 0) < 0 { hard.append("Distância ou tempo negativo") }
        if trip == 0 || tripTime == 0 { soft.append("Distância ou tempo da viagem zerado") }
        if let trip, trip > 150_000 { soft.append("Viagem acima de 150 km") }
        if let pickup, pickup > 25_000 { soft.append("Busca acima de 25 km") }
        if let s = speedIssue(trip, tripTime, "viagem") { soft.append(s) }
        if let s = speedIssue(pickup, o.pickupSeconds.value, "busca") { soft.append(s) }
        if let v = o.offeredCents.value, v > 0, let total = o.totalMeters, total > 0, let perKm = Rates.centsPerKm(v, total),
           perKm < 50 || perKm > 1_000 {
            soft.append("R$/km fora do possível")
        }
        soft += o.warnings

        let fieldReasons = o.criticalFields.flatMap { name, _, reasons in reasons.map { "\(name): \($0)" } }
        let status: DataStatus = !hard.isEmpty ? .invalid : (o.criticalConfidence == .low || !soft.isEmpty ? .needsReview : .autoAccepted)
        var seen = Set<String>()
        return Decision(status: status, reasons: (fieldReasons + hard + soft).filter { seen.insert($0).inserted })
    }

    private static func speedIssue(_ meters: Int?, _ seconds: Int?, _ label: String) -> String? {
        guard let meters, let seconds, seconds > 0, meters >= 1_000 else { return nil }
        let kmh = Double(meters) / 1000 / (Double(seconds) / 3600)
        return (kmh < 4 || kmh > 100) ? "Velocidade implícita da \(label) improvável (\(Int(kmh)) km/h)" : nil
    }
}

/// Taxas em centavos, arredondadas como o Uber (R$ 1,58/km, não 1,57).
enum Rates {
    static func centsPerKm(_ cents: Int, _ meters: Int) -> Int? { meters <= 0 ? nil : floorDiv(cents * 1000 + meters / 2, meters) }
    static func centsPerHour(_ cents: Int, _ seconds: Int) -> Int? { seconds <= 0 ? nil : floorDiv(cents * 3600 + seconds / 2, seconds) }
    private static func floorDiv(_ a: Int, _ b: Int) -> Int { Int((Double(a) / Double(b)).rounded(.down)) }
}

/// Custo do carro por km (porte de CostModel.kt).
struct CostModel {
    var fuelPriceCentsPerLiter: Int
    var kmPerLiter: Double
    var maintenanceCentsPerKm: Double
    var depreciationCentsPerKm: Double
    var otherCentsPerKm: Double

    var fuelCentsPerKm: Double { kmPerLiter > 0 ? Double(fuelPriceCentsPerLiter) / kmPerLiter : 0 }
    var fixedCentsPerKm: Double { maintenanceCentsPerKm + depreciationCentsPerKm + otherCentsPerKm }
    func fuelCostCents(_ meters: Int) -> Int { Int((Double(meters) / 1000 * fuelCentsPerKm).rounded()) }
    func operationalCostCents(_ meters: Int) -> Int { Int((Double(meters) / 1000 * (fuelCentsPerKm + fixedCentsPerKm)).rounded()) }
}

/// Limites pessoais: para subir de nível a oferta precisa dos DOIS (R$/km E R$/h).
struct RatingThresholds {
    var excellentCentsPerKm = 200
    var excellentCentsPerHour = 4_500
    var acceptableCentsPerKm = 160
    var acceptableCentsPerHour = 3_500

    func rate(_ perKm: Int?, _ perHour: Int?) -> OfferRating {
        guard let perKm, let perHour else { return .uncertain }
        if perKm >= excellentCentsPerKm && perHour >= excellentCentsPerHour { return .excellent }
        if perKm >= acceptableCentsPerKm && perHour >= acceptableCentsPerHour { return .acceptable }
        return .poor
    }
}

/// Análise instantânea da oferta (porte de OfferAnalyzer.kt). O km total inclui a busca: não é pago, mas gasta.
struct OfferAnalysis {
    let valueCents: Int
    let totalMeters: Int?
    let totalSeconds: Int?
    let centsPerKm: Int?
    let centsPerHour: Int?
    let centsPerMinute: Int?
    let fuelCostCents: Int?
    let operationalCostCents: Int?
    let netCentsPerHour: Int?
    let rating: OfferRating

    /// Lucro: com custos do carro, o operacional; senão, só descontando combustível.
    var profitCents: Int? {
        if let op = operationalCostCents { return valueCents - op }
        return fuelCostCents.map { valueCents - $0 }
    }

    static func analyze(_ o: RideOffer, reliable: Bool, cost: CostModel, thresholds: RatingThresholds) -> OfferAnalysis {
        let value = o.offeredCents.value ?? 0
        let meters = o.totalMeters, seconds = o.totalSeconds
        let perKm = meters.flatMap { Rates.centsPerKm(value, $0) }
        let perHour = seconds.flatMap { Rates.centsPerHour(value, $0) }
        let op = meters.map { cost.operationalCostCents($0) }
        return OfferAnalysis(
            valueCents: value, totalMeters: meters, totalSeconds: seconds,
            centsPerKm: perKm, centsPerHour: perHour,
            centsPerMinute: seconds.flatMap { $0 > 0 ? (value * 60 + $0 / 2) / $0 : nil },
            fuelCostCents: meters.map { cost.fuelCostCents($0) },
            operationalCostCents: op,
            netCentsPerHour: op.flatMap { op in seconds.flatMap { Rates.centsPerHour(value - op, $0) } },
            rating: reliable ? thresholds.rate(perKm, perHour) : .uncertain
        )
    }
}

/// Formatos iguais aos do Android ("R$ 1.234,56", "R$184", "2,8 km", "28 min", "1h05").
enum Fmt {
    static func money(_ cents: Int) -> String {
        "\(cents < 0 ? "-" : "")R$ \(group(abs(cents) / 100)),\(String(format: "%02d", abs(cents) % 100))"
    }
    static func moneyShort(_ cents: Int) -> String { "\(cents < 0 ? "-" : "")R$\(group(Int((Double(abs(cents)) / 100).rounded())))" }
    static func km(_ meters: Int) -> String { String(format: "%.1f km", Double(meters) / 1000).replacingOccurrences(of: ".", with: ",") }
    static func duration(_ seconds: Int) -> String {
        let m = (seconds + 30) / 60
        return m < 60 ? "\(m) min" : "\(m / 60)h\(String(format: "%02d", m % 60))"
    }
    private static func group(_ v: Int) -> String {
        let s = String(v)
        return stride(from: s.count, to: 0, by: -3).reversed().map { end -> String in
            let start = max(0, end - 3)
            return String(s[s.index(s.startIndex, offsetBy: start)..<s.index(s.startIndex, offsetBy: end)])
        }.joined(separator: ".")
    }
}
