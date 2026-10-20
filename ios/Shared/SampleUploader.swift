import Foundation

/// Porte de SnapshotSanitizer.kt: nomes e endereços nunca saem do celular. Só ficam números,
/// palavras de interface conhecidas e palavras em minúsculas; o resto vira "‹texto›".
enum Sanitizer {
    static let mask = "‹texto›"
    static let freeText = "‹texto livre›"
    private static let categories: Set<String> = ["uberx", "comfort", "black", "flash", "moto", "pop", "top"]
    private static let vocabulary: Set<String> = {
        let ui = ["chegada", "prevista", "finalizar", "iniciar", "cheguei", "embarque", "aguarde", "ganhos", "central",
            "r$", "km", "m", "min", "h", "de", "a", "o", "e", "da", "do", "em", "para", "com", "ate", "no", "na",
            "aceitar", "recusar", "confirmar", "cancelar", "concluir", "chegar", "voltar", "fechar", "ok", "hoje", "semana",
            "online", "offline", "viagem", "viagens", "corrida", "corridas", "passageiro", "passageira", "destino", "desembarque",
            "parada", "paradas", "pagamento", "dinheiro", "cartao", "pix", "tarifa", "dinamico", "dinamica", "promocao", "avaliar",
            "avaliacao", "avalie", "navegar", "mapa", "pausar", "conta", "carteira", "mensagem", "mensagens", "ligar", "chat", "rota",
            "taxa", "distancia", "tempo", "total", "valor", "voce", "recebeu", "ganhou", "buscar", "buscando", "aguardando",
            "esperando", "deixando", "procurando", "cancelada", "cancelado", "concluida", "finalizada", "detalhes", "resumo",
            "incluido", "estimado", "estimativa", "preco", "bonus", "selecionar", "exclusivo", "radar", "aprox", "minutos"]
        let platform = (OfferPatterns.uber.acceptKeywords + OfferPatterns.uber.pickupAnchors + OfferPatterns.uber.tripAnchors
            + OfferPatterns.ninetyNine.acceptKeywords + OfferPatterns.ninetyNine.pickupAnchors + OfferPatterns.ninetyNine.tripAnchors)
            .flatMap { $0.split(separator: " ").map(String.init) }
        return Set(ui + platform)
    }()

    static func line(_ text: String) -> String {
        let tokens = text.split(whereSeparator: { $0.isWhitespace }).map(String.init)
        let trim = CharacterSet(charactersIn: "(),.:;!?·•\"")
        let bare = tokens.map { TextPatterns.normalize($0).trimmingCharacters(in: trim) }
        let unknown = bare.filter { $0.contains(where: \.isLetter) && !vocabulary.contains($0) }.count
        if tokens.count >= 6 && unknown * 2 > tokens.count { return freeText }
        var out: [String] = []
        for (i, token) in tokens.enumerated() {
            let b = bare[i]
            let keep = b.isEmpty || b.contains(where: \.isNumber) || !b.contains(where: \.isLetter) || vocabulary.contains(b)
                || !token.contains(where: \.isUppercase)
                || (token.count <= 3 && token.filter(\.isLetter).allSatisfy(\.isUppercase))
                || categories.contains(b)
                || (tokens.count == 1 && (5...14).contains(b.count) && (b.hasSuffix("ar") || b.hasSuffix("er") || b.hasSuffix("ir")))
            let piece = keep ? token : mask
            if !(piece == mask && out.last == mask) { out.append(piece) }
        }
        return out.joined(separator: " ")
    }
}

/// Envia ao servidor (innexar.com.br/meuganho) o texto ANONIMIZADO das telas de oferta lidas no iPhone,
/// no mesmo formato das amostras do Android: é assim que a leitura do iPhone é calibrada.
final class SampleUploader {
    static let shared = SampleUploader()
    private let queue = DispatchQueue(label: "meuganho.samples")
    private var pending: [[String: Any]] = []
    private var lastSentAt = Date.distantPast
    private var lastSampleAt: [String: Date] = [:]

    /// [reason]: OFFER (oferta lida), OFFER_UNPARSED (parece oferta e não leu), UNKNOWN (tela sem app reconhecido).
    func record(_ read: ScreenRead, reason: String, parse: [String: Any] = [:]) {
        guard Settings.load().sendSamples, !Secrets.uploadKey.isEmpty else { return }
        let key = "\(read.platform?.rawValue ?? "?")|\(reason)"
        let now = Date()
        // No máximo 1 amostra a cada 3 s por tipo (a transmissão manda vários quadros por segundo).
        if let last = lastSampleAt[key], now.timeIntervalSince(last) < 3 { return }
        lastSampleAt[key] = now
        let text = read.lines.map { Sanitizer.line($0.text) }.joined(separator: "\n")
        let parseJson = (try? JSONSerialization.data(withJSONObject: parse)).flatMap { String(data: $0, encoding: .utf8) } ?? "{}"
        queue.async {
            self.pending.append([
                "capturedAt": Int(now.timeIntervalSince1970 * 1000),
                "platform": read.platform?.rawValue ?? "UNKNOWN",
                "screenType": read.isOffer ? "OFFER" : "UNKNOWN",
                "reason": reason + "_IOS",
                "parserVersion": "ios-1",
                "sourceAppVersion": 0,
                "text": text,
                "parse": parseJson,
            ])
            if self.pending.count >= 10 || now.timeIntervalSince(self.lastSentAt) > 60 { self.flush() }
        }
    }

    private func flush() {
        guard !pending.isEmpty, let url = URL(string: "https://innexar.com.br/meuganho/api/samples") else { return }
        let batch = pending
        pending = []
        lastSentAt = Date()
        let body: [String: Any] = [
            "installId": Settings.installId, "appVersionName": Secrets.appVersion, "appVersionCode": 1,
            "device": "iPhone", "ios": ProcessInfo.processInfo.operatingSystemVersionString, "samples": batch,
        ]
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue(Secrets.uploadKey, forHTTPHeaderField: "X-MeuGanho-Key")
        req.httpBody = try? JSONSerialization.data(withJSONObject: body)
        URLSession.shared.dataTask(with: req) { _, resp, _ in
            if (resp as? HTTPURLResponse)?.statusCode != 200 {
                self.queue.async { self.pending.insert(contentsOf: batch, at: 0); self.pending = Array(self.pending.prefix(50)) }
            }
        }.resume()
    }
}
