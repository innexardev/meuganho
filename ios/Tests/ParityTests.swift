import XCTest

/// O iPhone tem que ler as MESMAS telas reais com os MESMOS números do Android.
/// Tests/Fixtures e expected.json são gerados no servidor pelo IphoneParityExportTest (Android).
final class ParityTests: XCTestCase {
    private struct Expected: Decodable {
        struct Offer: Decodable { let cents: Int?; let pickupM: Int?; let pickupS: Int?; let tripM: Int?; let tripS: Int?; let status: String; let confidence: String }
        let file: String
        let platform: String?
        let isOffer: Bool
        let offers: [Offer]
    }

    private func fixture(_ name: String) throws -> URL {
        let bundle = Bundle(for: Self.self)
        let base = (name as NSString).deletingPathExtension, ext = (name as NSString).pathExtension
        return try XCTUnwrap(bundle.url(forResource: base, withExtension: ext), "fixture \(name) ausente")
    }

    func testMesmasTelasMesmosNumerosDoAndroid() throws {
        let expected = try JSONDecoder().decode([Expected].self, from: Data(contentsOf: fixture("expected.json")))
        XCTAssertGreaterThan(expected.count, 20)
        var checked = 0
        for e in expected {
            let lines = try String(contentsOf: fixture(e.file), encoding: .utf8).split(separator: "\n").map(String.init)
            let read = OfferReader.read(rawLines: lines, settings: Settings())
            XCTAssertEqual(read.platform?.rawValue, e.platform, "\(e.file): plataforma")
            XCTAssertEqual(read.isOffer, e.isOffer, "\(e.file): é oferta?")
            XCTAssertEqual(read.offers.count, e.offers.count, "\(e.file): nº de ofertas")
            for (got, want) in zip(read.offers, e.offers) {
                XCTAssertEqual(got.offer.offeredCents.value, want.cents, "\(e.file): valor")
                XCTAssertEqual(got.offer.pickupMeters.value, want.pickupM, "\(e.file): km busca")
                XCTAssertEqual(got.offer.pickupSeconds.value, want.pickupS, "\(e.file): tempo busca")
                XCTAssertEqual(got.offer.tripMeters.value, want.tripM, "\(e.file): km viagem")
                XCTAssertEqual(got.offer.tripSeconds.value, want.tripS, "\(e.file): tempo viagem")
                XCTAssertEqual(got.status.rawValue, want.status, "\(e.file): status")
                XCTAssertEqual(got.offer.criticalConfidence.name, want.confidence, "\(e.file): confiança")
                checked += 1
            }
        }
        XCTAssertGreaterThan(checked, 10)
    }

    func testCalculoIgualAoAndroid() {
        // Uber real: R$ 35,93, 2,8 + 20,0 km, 7 + 43 min. O Uber mostra R$1,58/km.
        let lines = ["UberX", "Exclusivo", "R$ 35,93", "R$1,58/km aprox.", "4,83 (436)", "7 min (2.8 km)", "Rua A, 1", "43 minutos (20.0 km)", "Rua B, 2", "Aceitar"]
        let r = OfferReader.read(rawLines: lines, settings: Settings()).offers.first!
        XCTAssertEqual(r.status, .autoAccepted)
        XCTAssertEqual(r.analysis.centsPerKm, 158)
        XCTAssertEqual(r.analysis.centsPerHour, 4312)        // 3593 em 50 min
        XCTAssertEqual(r.analysis.centsPerMinute, 72)
        XCTAssertEqual(r.analysis.rating, .poor)             // 1,58 < 1,60 aceitável
        let text = OfferNotificationText.of(r, thresholds: RatingThresholds(), isBest: false, extra: 0)
        XCTAssertEqual(text.title, "Uber · R$ 35,93 · ▼ RUIM")
        XCTAssertTrue(text.metrics.contains("R$ 1,58/km"))
    }

    func testNotificacaoDoProprioMeuGanhoNaoViraOferta() {
        let lines = ["99 · R$ 6,10 · ▼ RUIM 07:30", "R$ 6,52/km R$92/h", "R$ 1,53/min", "Cheguei no embarque"]
        XCTAssertEqual(OfferReader.withoutOwnNotification(lines), ["Cheguei no embarque"])
    }

    func testOcrRsSoComCentavos() {
        XCTAssertEqual(TextPatterns.fixOcr("RS1,93/km"), "R$1,93/km")
        XCTAssertEqual(TextPatterns.fixOcr("RS2395"), "RS2395")
    }

    func testFormatos() {
        XCTAssertEqual(Fmt.money(123456), "R$ 1.234,56")
        XCTAssertEqual(Fmt.moneyShort(4312), "R$43")
        XCTAssertEqual(Fmt.km(22800), "22,8 km")
        XCTAssertEqual(Fmt.duration(3000), "50 min")
        XCTAssertEqual(Fmt.duration(3900), "1h05")
    }
}
