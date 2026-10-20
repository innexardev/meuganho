import Foundation

struct MoneyMatch {
    let cents: Int
    let isAddition: Bool
    let range: Range<String.Index>
}

/// Pequeno invólucro de NSRegularExpression (funciona no iOS 15+, sem a sintaxe Regex do iOS 16).
struct Rx {
    let re: NSRegularExpression
    init(_ pattern: String, _ options: NSRegularExpression.Options = []) {
        re = try! NSRegularExpression(pattern: pattern, options: options)
    }

    func first(in s: String) -> [String?]? {
        guard let m = re.firstMatch(in: s, range: NSRange(s.startIndex..., in: s)) else { return nil }
        return groups(m, s)
    }

    func all(in s: String) -> [(groups: [String?], range: Range<String.Index>)] {
        re.matches(in: s, range: NSRange(s.startIndex..., in: s)).compactMap { m in
            guard let r = Range(m.range, in: s) else { return nil }
            return (groups(m, s), r)
        }
    }

    func matches(_ s: String) -> Bool {
        guard let m = re.firstMatch(in: s, range: NSRange(s.startIndex..., in: s)) else { return false }
        return m.range == NSRange(s.startIndex..., in: s)
    }

    func contains(in s: String) -> Bool { re.firstMatch(in: s, range: NSRange(s.startIndex..., in: s)) != nil }

    func replace(in s: String, with template: String) -> String {
        re.stringByReplacingMatches(in: s, range: NSRange(s.startIndex..., in: s), withTemplate: template)
    }

    private func groups(_ m: NSTextCheckingResult, _ s: String) -> [String?] {
        (0..<m.numberOfRanges).map { i in Range(m.range(at: i), in: s).map { String(s[$0]) } }
    }
}

/// Porte de TextPatterns.kt: dinheiro, distância e tempo no formato brasileiro.
enum TextPatterns {
    private static let money = Rx(#"(\+\s*)?R\$\s*(\d{1,3}(?:\.\d{3})+|\d+)(?:,(\d{2}))?"#)
    private static let distance = Rx(#"(\d+(?:[.,]\d+)?)\s*(km|m)(?![a-z])"#)
    private static let hourMinutes = Rx(#"(\d+)\s*h(?:oras?|r)?\s*(?:e\s*)?(\d{1,2})\s*(?:min)?(?![a-z0-9])"#)
    private static let minutes = Rx(#"(\d+)\s*min"#)
    private static let hoursOnly = Rx(#"(\d+)\s*h(?:oras?|r)?(?![a-z0-9])"#)
    private static let platformRate = Rx(#"R\$\s*(\d+)(?:,(\d{2}))?\s*/\s*km"#)
    private static let range = Rx(#"R\$\s*[\d.,]+\s*(?:-|–|a|até)\s*R\$"#)
    private static let bidi = Rx("[\u{200E}\u{200F}\u{2066}-\u{2069}]")
    private static let spaces = Rx(#"\s+"#)
    // OCR lê "R$" como "RS"/"R5"/"RŞ". Só com centavos: "RS2395" (vírgula perdida) continua ilegível.
    private static let ocrCurrency = Rx(#"\bR[S5Ş]\s?(?=\d{1,3}(?:\.\d{3})*,\d{2})"#)

    static func fixOcr(_ text: String) -> String { ocrCurrency.replace(in: text, with: "R\\$") }

    /// Minúsculas, sem acentos, espaços normalizados.
    static func normalize(_ text: String) -> String {
        var s = bidi.replace(in: text.replacingOccurrences(of: "\u{00A0}", with: " "), with: "")
        s = s.lowercased().folding(options: .diacriticInsensitive, locale: Locale(identifier: "pt_BR"))
        return spaces.replace(in: s, with: " ").trimmingCharacters(in: .whitespaces)
    }

    static func moneyIn(_ text: String) -> [MoneyMatch] {
        let clean = text.replacingOccurrences(of: "\u{00A0}", with: " ")
        return money.all(in: clean).map { g, r in
            let reais = Int((g[2] ?? "0").replacingOccurrences(of: ".", with: "")) ?? 0
            let cents = Int(g[3] ?? "0") ?? 0
            return MoneyMatch(cents: reais * 100 + cents, isAddition: g[1] != nil, range: r)
        }
    }

    /// "R$1,58/km aprox." exibido pelo próprio app -> 158.
    static func platformRatePerKm(_ text: String) -> Int? {
        guard let g = platformRate.first(in: text.replacingOccurrences(of: "\u{00A0}", with: " ")) else { return nil }
        return (Int(g[1] ?? "0") ?? 0) * 100 + (Int(g[2] ?? "0") ?? 0)
    }

    static func hasTimeAndDistance(_ normalized: String) -> Bool {
        durationSeconds(normalized) != nil && distanceMeters(normalized) != nil
    }

    static func isPriceRange(_ text: String) -> Bool { range.contains(in: text.replacingOccurrences(of: "\u{00A0}", with: " ")) }

    /// Linha que é "só" um preço, ex.: "R$ 28,50".
    static func isStandalonePrice(_ text: String) -> Bool {
        let clean = text.replacingOccurrences(of: "\u{00A0}", with: " ").trimmingCharacters(in: .whitespaces)
        let m = moneyIn(clean)
        guard m.count == 1, !m[0].isAddition else { return false }
        var rest = clean
        rest.removeSubrange(m[0].range)
        return rest.trimmingCharacters(in: .whitespaces).isEmpty
    }

    static func distanceMeters(_ normalized: String) -> Int? {
        guard let g = distance.first(in: normalized), let n = parseDecimal(g[1] ?? "") else { return nil }
        return g[2] == "km" ? Int(n * 1000) : Int(n)
    }

    static func durationSeconds(_ normalized: String) -> Int? {
        if let g = hourMinutes.first(in: normalized) { return (Int(g[1]!) ?? 0) * 3600 + (Int(g[2]!) ?? 0) * 60 }
        if let g = minutes.first(in: normalized) { return (Int(g[1]!) ?? 0) * 60 }
        if let g = hoursOnly.first(in: normalized) { return (Int(g[1]!) ?? 0) * 3600 }
        return nil
    }

    static func containsAny(_ normalized: String, _ keywords: [String]) -> Bool { keywords.contains { normalized.contains($0) } }

    /// "1.2" (inglês) e "1,2" (português) são decimais; "1.200" é milhar.
    private static func parseDecimal(_ raw: String) -> Double? {
        if let sep = raw.firstIndex(where: { $0 == "." || $0 == "," }),
           raw[sep] == ".", raw.distance(from: sep, to: raw.endIndex) - 1 == 3 {
            return Double(raw.replacingOccurrences(of: ".", with: ""))
        }
        return Double(raw.replacingOccurrences(of: ",", with: "."))
    }
}
