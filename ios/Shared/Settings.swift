import Foundation

/// Configurações compartilhadas entre o app e a extensão de Transmissão (App Group).
/// Se o App Group não estiver disponível (conta gratuita), cai no UserDefaults padrão e a extensão usa os valores padrão.
struct Settings {
    static let appGroup = "group.com.innexar.meuganho"

    var fuelPriceCentsPerLiter = 619
    var kmPerLiter = 10.0
    /// Manutenção + pneus + depreciação + outros, em centavos por km.
    var otherCostCentsPerKm = 20.0
    var thresholds = RatingThresholds()
    /// Envia o texto (anonimizado) das telas de oferta para calibrar a leitura.
    var sendSamples = true
    /// Quanto tempo a notificação da oferta fica na tela.
    var notificationSeconds = 7.0

    var costModel: CostModel {
        CostModel(fuelPriceCentsPerLiter: fuelPriceCentsPerLiter, kmPerLiter: kmPerLiter,
                  maintenanceCentsPerKm: otherCostCentsPerKm, depreciationCentsPerKm: 0, otherCentsPerKm: 0)
    }

    static var store: UserDefaults { UserDefaults(suiteName: appGroup) ?? .standard }

    static func load() -> Settings {
        let d = store
        var s = Settings()
        if d.object(forKey: "fuel") != nil { s.fuelPriceCentsPerLiter = d.integer(forKey: "fuel") }
        if d.object(forKey: "kmpl") != nil { s.kmPerLiter = d.double(forKey: "kmpl") }
        if d.object(forKey: "other") != nil { s.otherCostCentsPerKm = d.double(forKey: "other") }
        if d.object(forKey: "exKm") != nil { s.thresholds.excellentCentsPerKm = d.integer(forKey: "exKm") }
        if d.object(forKey: "exH") != nil { s.thresholds.excellentCentsPerHour = d.integer(forKey: "exH") }
        if d.object(forKey: "okKm") != nil { s.thresholds.acceptableCentsPerKm = d.integer(forKey: "okKm") }
        if d.object(forKey: "okH") != nil { s.thresholds.acceptableCentsPerHour = d.integer(forKey: "okH") }
        if d.object(forKey: "samples") != nil { s.sendSamples = d.bool(forKey: "samples") }
        return s
    }

    func save() {
        let d = Settings.store
        d.set(fuelPriceCentsPerLiter, forKey: "fuel")
        d.set(kmPerLiter, forKey: "kmpl")
        d.set(otherCostCentsPerKm, forKey: "other")
        d.set(thresholds.excellentCentsPerKm, forKey: "exKm")
        d.set(thresholds.excellentCentsPerHour, forKey: "exH")
        d.set(thresholds.acceptableCentsPerKm, forKey: "okKm")
        d.set(thresholds.acceptableCentsPerHour, forKey: "okH")
        d.set(sendSamples, forKey: "samples")
    }

    /// Identificador anônimo desta instalação (para as amostras de calibração).
    static var installId: String {
        let d = store
        if let id = d.string(forKey: "installId") { return id }
        let id = UUID().uuidString.lowercased()
        d.set(id, forKey: "installId")
        return id
    }
}

/// Últimas ofertas lidas (a extensão grava, o app mostra). Só funciona com App Group.
enum RecentOffers {
    struct Item: Codable { let at: Date; let platform: String; let title: String; let body: String }

    static func add(_ item: Item) {
        var list = all()
        list.insert(item, at: 0)
        if let data = try? JSONEncoder().encode(Array(list.prefix(30))) { Settings.store.set(data, forKey: "recent") }
    }

    static func all() -> [Item] {
        guard let data = Settings.store.data(forKey: "recent") else { return [] }
        return (try? JSONDecoder().decode([Item].self, from: data)) ?? []
    }
}
