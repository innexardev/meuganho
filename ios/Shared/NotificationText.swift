import Foundation

/// Texto da notificação, igual ao do Android (OfferNotificationText): bolinha por métrica pelos limites pessoais.
struct OfferNotificationText {
    let title: String
    let metrics: String
    let details: String

    static func of(_ r: ReadOffer, thresholds t: RatingThresholds, isBest: Bool, extra: Int) -> OfferNotificationText {
        let a = r.analysis
        let uncertain = a.rating == .uncertain
        let verdict = uncertain ? "? INCERTO" : "\(a.rating.symbol) \(a.rating.rawValue)"
        let title = (isBest ? "★ MELHOR · " : "") + "\(r.offer.platform.label) · \(Fmt.money(a.valueCents)) · \(verdict)"
        func dot(_ v: Int, _ ok: Int, _ ex: Int) -> String { uncertain ? "⚪" : (v >= ex ? "🟢" : (v >= ok ? "🟡" : "🔴")) }
        let metrics = [
            a.centsPerKm.map { "\(dot($0, t.acceptableCentsPerKm, t.excellentCentsPerKm)) \(Fmt.money($0))/km" },
            a.centsPerHour.map { "\(dot($0, t.acceptableCentsPerHour, t.excellentCentsPerHour)) \(Fmt.moneyShort($0))/h" },
            a.centsPerMinute.map { "\(dot($0, t.acceptableCentsPerHour / 60, t.excellentCentsPerHour / 60)) \(Fmt.money($0))/min" },
        ].compactMap { $0 }.joined(separator: "   ")
        let details = [
            a.totalMeters.map(Fmt.km),
            a.totalSeconds.map(Fmt.duration),
            a.profitCents.map { p in "lucro \(Fmt.money(p))" + (a.netCentsPerHour.map { " (\(Fmt.moneyShort($0))/h)" } ?? "") },
            extra > 0 ? "+\(extra) outra(s) na tela" : nil,
        ].compactMap { $0 }.joined(separator: " · ")
        return OfferNotificationText(title: title, metrics: metrics, details: details)
    }
}
