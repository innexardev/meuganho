import ReplayKit
import Vision
import CoreMedia

/// Extensão de Transmissão de tela: recebe a TELA INTEIRA enquanto o motorista usa a Uber ou a 99,
/// lê o texto (Vision, no próprio iPhone, nada de imagem sai do aparelho) e notifica a análise da oferta.
///
/// Limite do iOS: ~50 MB de memória para esta extensão. Por isso: no máximo 1 leitura a cada 0,7 s,
/// só quando a imagem mudou (assinatura de 16×16 da luminância), e uma leitura por vez.
final class SampleHandler: RPBroadcastSampleHandler {
    private let queue = DispatchQueue(label: "meuganho.ocr", qos: .userInitiated)
    private var busy = false
    private var lastRunAt = Date.distantPast
    private var lastSignature: [UInt8] = []
    private var seen: [String: Date] = [:]          // oferta já notificada (mesma oferta em vários quadros)
    private var settings = Settings.load()

    private static let minInterval: TimeInterval = 0.7
    private static let dedupWindow: TimeInterval = 90

    override func broadcastStarted(withSetupInfo setupInfo: [String: NSObject]?) {
        settings = Settings.load()
        OfferNotifier.requestPermission()
    }

    override func processSampleBuffer(_ sampleBuffer: CMSampleBuffer, with sampleBufferType: RPSampleBufferType) {
        guard sampleBufferType == .video, !busy, Date().timeIntervalSince(lastRunAt) >= Self.minInterval,
              let pixels = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        let signature = Self.signature(pixels)
        if Self.similar(signature, lastSignature) { return }
        lastSignature = signature
        lastRunAt = Date()
        busy = true
        let orientation = Self.orientation(sampleBuffer)
        queue.async { [weak self] in
            self?.recognize(pixels, orientation)
            self?.busy = false
        }
    }

    private func recognize(_ pixels: CVPixelBuffer, _ orientation: CGImagePropertyOrientation) {
        let request = VNRecognizeTextRequest()
        request.recognitionLevel = .accurate
        request.recognitionLanguages = ["pt-BR"]
        request.usesLanguageCorrection = false // números e "R$" não podem ser "corrigidos"
        let handler = VNImageRequestHandler(cvPixelBuffer: pixels, orientation: orientation, options: [:])
        guard (try? handler.perform([request])) != nil, let results = request.results else { return }
        let lines = Self.readingOrder(results)
        guard !lines.isEmpty else { return }

        let read = OfferReader.read(rawLines: lines, settings: settings)
        guard read.isOffer else { return }
        if read.offers.isEmpty {
            SampleUploader.shared.record(read, reason: "OFFER_UNPARSED")
            return
        }
        let now = Date()
        seen = seen.filter { now.timeIntervalSince($0.value) < Self.dedupWindow }
        let fresh = read.offers.filter { seen[$0.offer.fingerprint] == nil }
        guard !fresh.isEmpty else { return }
        fresh.forEach { seen[$0.offer.fingerprint] = now }
        OfferNotifier.show(read.offers, settings: settings)
        for r in fresh {
            func j(_ v: Int?) -> Any { if let v { return v } else { return NSNull() } }
            SampleUploader.shared.record(read, reason: "OFFER", parse: [
                "value": j(r.offer.offeredCents.value), "pickupM": j(r.offer.pickupMeters.value),
                "pickupS": j(r.offer.pickupSeconds.value), "tripM": j(r.offer.tripMeters.value),
                "tripS": j(r.offer.tripSeconds.value), "confidence": r.offer.criticalConfidence.name,
                "status": r.status.rawValue, "reasons": r.reasons.joined(separator: " | "), "cards": read.offers.count,
            ])
        }
    }

    // MARK: - Ordem de leitura

    /// Linhas de cima para baixo (no Vision o y cresce para cima); na mesma altura, da esquerda para a direita.
    static func readingOrder(_ obs: [VNRecognizedTextObservation]) -> [String] {
        obs.compactMap { o -> (CGRect, String)? in
            guard let t = o.topCandidates(1).first?.string, !t.isEmpty else { return nil }
            return (o.boundingBox, t)
        }
        .sorted { a, b in
            let sameRow = abs(a.0.midY - b.0.midY) < min(a.0.height, b.0.height) * 0.5
            return sameRow ? a.0.minX < b.0.minX : a.0.midY > b.0.midY
        }
        .map(\.1)
    }

    // MARK: - Mudou a imagem?

    /// 16×16 amostras do plano de luminância (Y): custa quase nada e evita OCR com a tela parada.
    static func signature(_ pb: CVPixelBuffer) -> [UInt8] {
        CVPixelBufferLockBaseAddress(pb, .readOnly)
        defer { CVPixelBufferUnlockBaseAddress(pb, .readOnly) }
        let planar = CVPixelBufferIsPlanar(pb)
        guard let base = planar ? CVPixelBufferGetBaseAddressOfPlane(pb, 0) : CVPixelBufferGetBaseAddress(pb) else { return [] }
        let w = planar ? CVPixelBufferGetWidthOfPlane(pb, 0) : CVPixelBufferGetWidth(pb)
        let h = planar ? CVPixelBufferGetHeightOfPlane(pb, 0) : CVPixelBufferGetHeight(pb)
        let row = planar ? CVPixelBufferGetBytesPerRowOfPlane(pb, 0) : CVPixelBufferGetBytesPerRow(pb)
        let bpp = planar ? 1 : 4
        let p = base.assumingMemoryBound(to: UInt8.self)
        var out: [UInt8] = []
        out.reserveCapacity(256)
        for gy in 0..<16 {
            for gx in 0..<16 {
                let x = (gx * 2 + 1) * w / 32, y = (gy * 2 + 1) * h / 32
                out.append(p[y * row + x * bpp + (planar ? 0 : 1)])
            }
        }
        return out
    }

    static func similar(_ a: [UInt8], _ b: [UInt8]) -> Bool {
        guard a.count == b.count, !a.isEmpty else { return false }
        let changed = zip(a, b).filter { abs(Int($0) - Int($1)) > 24 }.count
        return changed <= 2
    }

    static func orientation(_ sb: CMSampleBuffer) -> CGImagePropertyOrientation {
        guard let n = CMGetAttachment(sb, key: RPVideoSampleOrientationKey as CFString, attachmentModeOut: nil) as? NSNumber,
              let o = CGImagePropertyOrientation(rawValue: n.uint32Value) else { return .up }
        return o
    }
}
