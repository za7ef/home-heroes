import SwiftUI
import AVFoundation

struct Target: Identifiable { let id: String; let emoji: String; let name: String; let fx: CGFloat; let fy: CGFloat }

struct DragTask { let kid: Kid; let chore: Chore; let obj: String; let target: Target }

enum RoomItems {
    static func targets(_ room: String) -> [Target] {
        switch room {
        case "bedroom": return [
            Target(id: "basket", emoji: "🧺", name: "سلة الملابس", fx: 0.12, fy: 0.16),
            Target(id: "bed", emoji: "🛏️", name: "السرير", fx: 0.36, fy: 0.14),
            Target(id: "desk", emoji: "🪑", name: "المكتب", fx: 0.62, fy: 0.14),
            Target(id: "wardrobe", emoji: "🚪", name: "الدولاب", fx: 0.88, fy: 0.16)]
        case "kitchen": return [
            Target(id: "sink", emoji: "🚰", name: "المغسلة", fx: 0.14, fy: 0.16),
            Target(id: "cupboard", emoji: "🗄️", name: "الدولاب", fx: 0.5, fy: 0.14),
            Target(id: "bin", emoji: "🗑️", name: "الزبالة", fx: 0.86, fy: 0.18)]
        case "bath": return [
            Target(id: "sink", emoji: "🚰", name: "المغسلة", fx: 0.2, fy: 0.16),
            Target(id: "basket", emoji: "🧺", name: "سلة الملابس", fx: 0.8, fy: 0.18)]
        default: return [
            Target(id: "toybox", emoji: "📦", name: "صندوق الألعاب", fx: 0.14, fy: 0.18),
            Target(id: "shoes", emoji: "👞", name: "رف الجزم", fx: 0.86, fy: 0.18)]
        }
    }

    static func decor(_ room: String) -> [String] {
        switch room {
        case "kitchen": return ["🍎", "🥤"]
        case "bedroom": return ["📕", "🎈"]
        case "bath": return ["🦆", "🧴"]
        default: return ["⚽", "🪀"]
        }
    }

    static func dragFor(_ kid: Kid, _ ch: Chore, _ room: String) -> DragTask? {
        let map: [String: (String, String)] = [
            "👕": ("👕", "basket"), "🧸": ("🧸", "toybox"), "🧺": ("👚", "wardrobe"), "👟": ("👟", "shoes"),
            "🍽️": ("🍽️", "sink"), "🗑️": ("🛍️", "bin"), "🛒": ("🥫", "cupboard"), "🎒": ("🎒", "desk"),
            "🛏️": ("🧣", "bed"), "🪥": ("🪥", "sink"), "🧼": ("🧼", "sink"), "🧹": ("🧦", "basket"), "🚿": ("🧽", "sink"),
        ]
        guard let p = map[ch.emoji], let t = targets(room).first(where: { $0.id == p.1 }) else { return nil }
        return DragTask(kid: kid, chore: ch, obj: p.0, target: t)
    }

    static let spots: [(CGFloat, CGFloat)] = [
        (0.30, 0.62), (0.58, 0.72), (0.80, 0.60), (0.42, 0.86), (0.70, 0.88), (0.18, 0.80), (0.50, 0.55), (0.90, 0.78),
    ]
}

/** النطق العربي من محرك الجهاز */
final class Speaker {
    static let shared = Speaker()
    private let synth = AVSpeechSynthesizer()
    func say(_ s: String) {
        synth.stopSpeaking(at: .immediate)
        let u = AVSpeechUtterance(string: s)
        u.voice = AVSpeechSynthesisVoice(language: "ar-SA")
        u.rate = AVSpeechUtteranceDefaultSpeechRate * 0.9
        synth.speak(u)
    }
}
