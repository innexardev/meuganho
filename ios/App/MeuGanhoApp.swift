import SwiftUI
import ReplayKit
import UserNotifications

@main
struct MeuGanhoApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    var body: some Scene {
        WindowGroup { ContentView() }
    }
}

/// Mostra a notificação também com o app aberto (botão de teste).
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
                                withCompletionHandler completion: @escaping (UNNotificationPresentationOptions) -> Void) {
        completion([.banner, .sound])
    }
}

/// Botão do sistema que inicia a Transmissão de tela já apontando para a nossa extensão.
struct BroadcastButton: UIViewRepresentable {
    func makeUIView(context: Context) -> RPSystemBroadcastPickerView {
        let v = RPSystemBroadcastPickerView(frame: CGRect(x: 0, y: 0, width: 64, height: 64))
        v.preferredExtension = (Bundle.main.bundleIdentifier ?? "") + ".broadcast"
        v.showsMicrophoneButton = false
        return v
    }
    func updateUIView(_ uiView: RPSystemBroadcastPickerView, context: Context) {}
}

struct ContentView: View {
    @State private var s = Settings.load()
    @State private var recent = RecentOffers.all()
    @State private var permission = "verificando…"
    private let timer = Timer.publish(every: 3, on: .main, in: .common).autoconnect()

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    HStack(spacing: 16) {
                        BroadcastButton().frame(width: 56, height: 56)
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Ler ofertas").font(.headline)
                            Text("Toque no botão e escolha “MeuGanho” → Iniciar transmissão. Depois abra a Uber ou a 99.")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    Text("Notificações: \(permission)").font(.caption)
                    Button("Testar notificação com uma oferta de exemplo") { testNotification() }
                } footer: {
                    Text("A tela é lida no próprio iPhone; nenhuma imagem sai do aparelho. No Foco “Direção”, permita o MeuGanho para ver as ofertas dirigindo.")
                }

                Section("Custos do carro") {
                    numberRow("Combustível (R$/litro)", value: Binding(
                        get: { Double(s.fuelPriceCentsPerLiter) / 100 }, set: { s.fuelPriceCentsPerLiter = Int(($0 * 100).rounded()) }))
                    numberRow("Consumo (km/litro)", value: $s.kmPerLiter)
                    numberRow("Manutenção e outros (R$/km)", value: Binding(
                        get: { s.otherCostCentsPerKm / 100 }, set: { s.otherCostCentsPerKm = $0 * 100 }))
                }

                Section("Minhas metas") {
                    numberRow("R$/km aceitável", value: centsBinding(\.acceptableCentsPerKm))
                    numberRow("R$/km excelente", value: centsBinding(\.excellentCentsPerKm))
                    numberRow("R$/hora aceitável", value: centsBinding(\.acceptableCentsPerHour))
                    numberRow("R$/hora excelente", value: centsBinding(\.excellentCentsPerHour))
                }

                Section {
                    Toggle("Enviar telas de oferta (anônimas) para melhorar a leitura", isOn: $s.sendSamples)
                }

                Section("Últimas ofertas lidas") {
                    if recent.isEmpty { Text("Nenhuma ainda.").foregroundStyle(.secondary) }
                    ForEach(Array(recent.prefix(15).enumerated()), id: \.offset) { _, item in
                        VStack(alignment: .leading, spacing: 2) {
                            Text(item.title).font(.subheadline.bold())
                            Text(item.body).font(.caption)
                            Text(item.at.formatted(date: .omitted, time: .shortened)).font(.caption2).foregroundStyle(.secondary)
                        }
                    }
                }
            }
            .navigationTitle("MeuGanho")
            .onChange(of: s.fuelPriceCentsPerLiter) { _ in s.save() }
            .onChange(of: s.kmPerLiter) { _ in s.save() }
            .onChange(of: s.otherCostCentsPerKm) { _ in s.save() }
            .onChange(of: s.sendSamples) { _ in s.save() }
            .onDisappear { s.save() }
            .onReceive(timer) { _ in recent = RecentOffers.all() }
            .task {
                OfferNotifier.requestPermission { ok in DispatchQueue.main.async { permission = ok ? "permitidas" : "BLOQUEADAS (Ajustes > MeuGanho)" } }
            }
        }
    }

    private func centsBinding(_ kp: WritableKeyPath<RatingThresholds, Int>) -> Binding<Double> {
        Binding(get: { Double(s.thresholds[keyPath: kp]) / 100 },
                set: { s.thresholds[keyPath: kp] = Int(($0 * 100).rounded()); s.save() })
    }

    private func numberRow(_ label: String, value: Binding<Double>) -> some View {
        HStack {
            Text(label)
            Spacer()
            TextField("", value: value, format: .number.precision(.fractionLength(0...2)))
                .keyboardType(.decimalPad).multilineTextAlignment(.trailing).frame(width: 90)
        }
    }

    /// Oferta real do Uber (print de 28/09/2026) passando pelo mesmo leitor da extensão.
    private func testNotification() {
        s.save()
        let sample = ["UberX", "Exclusivo", "R$ 35,93", "R$1,58/km aprox.", "4,83 (436)", "7 min (2.8 km)",
                      "Rua Exemplo, 100", "43 minutos (20.0 km)", "Av. Exemplo, 200", "Aceitar"]
        let read = OfferReader.read(rawLines: sample, settings: s)
        OfferNotifier.show(read.offers, settings: s)
        recent = RecentOffers.all()
    }
}
