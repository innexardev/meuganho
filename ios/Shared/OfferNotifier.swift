import Foundation
import UserNotifications

/// Notificação local com a análise. Uma por plataforma (a nova substitui a anterior); some sozinha depois de N segundos.
enum OfferNotifier {
    static func requestPermission(_ done: @escaping (Bool) -> Void = { _ in }) {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { ok, _ in done(ok) }
    }

    static func show(_ offers: [ReadOffer], settings: Settings) {
        guard let best = offers.max(by: { ($0.analysis.centsPerHour ?? 0) < ($1.analysis.centsPerHour ?? 0) }) else { return }
        for r in offers {
            let text = OfferNotificationText.of(r, thresholds: settings.thresholds, isBest: offers.count > 1 && r.offer.fingerprint == best.offer.fingerprint,
                                                extra: offers.count - 1)
            let content = UNMutableNotificationContent()
            content.title = text.title
            content.body = "\(text.metrics)\n\(text.details)"
            content.interruptionLevel = .timeSensitive
            let id = "offer-\(r.offer.platform.rawValue)-\(r.offer.fingerprint)"
            UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: id, content: content, trigger: nil))
            RecentOffers.add(.init(at: Date(), platform: r.offer.platform.label, title: text.title, body: content.body))
            DispatchQueue.main.asyncAfter(deadline: .now() + settings.notificationSeconds) {
                UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: [id])
            }
        }
    }
}
