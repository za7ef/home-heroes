import SwiftUI
import AVFoundation

/** مكان تُوضع فيه الأغراض. الإحداثيات نسبة من صورة الغرفة نفسها حتى يقع فوق الأثاث المرسوم.
 prop = true يعني أن المكان غير مرسوم في الصورة فيُرسم كغرض على الأرض. */
struct Target: Identifiable { let id: String; let emoji: String; let name: String; let fx: CGFloat; let fy: CGFloat; var prop = false }

struct DragTask { let kid: Kid; let chore: Chore; let obj: String; let target: Target }

/** وصف صورة الغرفة: مقاسها، وبداية الأرضية فيها، ولون الجدار لملء أي فراغ أعلاها */
struct RoomArt { let iw: CGFloat; let ih: CGFloat; let floorTop: CGFloat; let wall: Color; let targets: [Target] }

enum RoomItems {
    static func art(_ room: String) -> RoomArt {
        switch room {
        case "bedroom": return RoomArt(iw: 1280, ih: 714, floorTop: 0.74, wall: Color(hex: 0xF2B33D), targets: [
            Target(id: "bed", emoji: "🛏️", name: "السرير", fx: 0.12, fy: 0.60),
            Target(id: "desk", emoji: "🪑", name: "المكتب", fx: 0.38, fy: 0.50),
            Target(id: "wardrobe", emoji: "🚪", name: "الدولاب", fx: 0.58, fy: 0.52),
            Target(id: "basket", emoji: "🧺", name: "سلة الملابس", fx: 0.71, fy: 0.62)])
        case "kitchen": return RoomArt(iw: 1367, ih: 784, floorTop: 0.85, wall: Color(hex: 0xF6B73C), targets: [
            Target(id: "sink", emoji: "🚰", name: "المغسلة", fx: 0.64, fy: 0.50),
            Target(id: "cupboard", emoji: "🗄️", name: "الدولاب", fx: 0.80, fy: 0.66),
            Target(id: "bin", emoji: "🗑️", name: "الزبالة", fx: 0.93, fy: 0.90, prop: true)])
        case "bath": return RoomArt(iw: 1280, ih: 714, floorTop: 0.80, wall: Color(hex: 0xF9D27A), targets: [
            Target(id: "sink", emoji: "🚰", name: "المغسلة", fx: 0.84, fy: 0.52),
            Target(id: "basket", emoji: "🧺", name: "سلة الملابس", fx: 0.40, fy: 0.90, prop: true)])
        default: return RoomArt(iw: 1280, ih: 484, floorTop: 0.66, wall: Color(hex: 0xF2C46B), targets: [
            Target(id: "toybox", emoji: "📦", name: "صندوق الألعاب", fx: 0.16, fy: 0.80),
            Target(id: "shoes", emoji: "👞", name: "رف الجزم", fx: 0.87, fy: 0.69)])
        }
    }

    static func targets(_ room: String) -> [Target] { art(room).targets }

    static func decor(_ room: String) -> [String] {
        switch room {
        case "kitchen": return ["🍎", "🍌", "🥄", "🧃"]
        case "bedroom": return ["📕", "🎈", "🧩", "🖍️"]
        case "bath": return ["🦆", "🧽", "🪮", "🫧"]
        default: return ["🪀", "🚗", "🎲", "🪁"]
        }
    }

    static func dragFor(_ kid: Kid, _ ch: Chore, _ room: String) -> DragTask? {
        let map: [String: (String, String)] = [
            "👕": ("👕", "basket"), "🧸": ("🧸", "toybox"), "🧺": ("👚", "wardrobe"), "👟": ("👟", "shoes"),
            "🍽️": ("🍽️", "sink"), "🗑️": ("🛍️", "bin"), "🛒": ("🥫", "cupboard"), "🎒": ("🎒", "desk"),
            "🛏️": ("🧣", "bed"), "🪥": ("🪥", "sink"), "🧼": ("🧼", "sink"), "🧹": ("🧦", "basket"), "🚿": ("🧽", "sink"),
            "🧦": ("🧦", "basket"), "⚽": ("⚽", "toybox"), "🥤": ("🥤", "sink"), "🧴": ("🧴", "sink"), "📘": ("📘", "desk"),
        ]
        guard let p = map[ch.emoji], let t = targets(room).first(where: { $0.id == p.1 }) else { return nil }
        return DragTask(kid: kid, chore: ch, obj: p.0, target: t)
    }

    /** أماكن ظهور الأغراض: نسبة أفقية من العرض، ونسبة من عمق الأرضية */
    static let spots: [(CGFloat, CGFloat)] = [
        (0.30, 0.45), (0.55, 0.70), (0.78, 0.40), (0.42, 0.85), (0.66, 0.88), (0.20, 0.75), (0.50, 0.30), (0.88, 0.70),
        (0.12, 0.40), (0.36, 0.62), (0.60, 0.50), (0.82, 0.92), (0.26, 0.95), (0.72, 0.62),
    ]
}

/**
 النطق: يشغّل الصوت المسجّل مسبقًا للعبارة الثابتة إن وُجد (بصوت المتحدث نفسه أو الراوي)،
 وإلا ينطقها محرك الجهاز العربي. البحث بنص العبارة بعد حذف الرموز والإيموجي.
 */
final class Speaker {
    static let shared = Speaker()
    private var player: AVAudioPlayer?
    private var byKey: [String: [String: String]] = [:]

    private init() {
        if let url = Bundle.main.url(forResource: "voices", withExtension: "json"),
           let data = try? Data(contentsOf: url),
           let arr = try? JSONSerialization.jsonObject(with: data) as? [[String: String]] {
            for o in arr {
                guard let f = o["f"], let sp = o["s"], let t = o["t"] else { continue }
                byKey[Speaker.key(t), default: [:]][sp] = f
            }
        }
    }

    /** يُبقي الحروف والأرقام فقط */
    static func key(_ s: String) -> String {
        String(s.unicodeScalars.filter { CharacterSet.alphanumerics.contains($0) }.map(Character.init))
    }

    private func playRecorded(_ s: String, _ who: String?) -> Bool {
        guard let m = byKey[Speaker.key(s)] else { return false }
        guard let file = (who.flatMap { m[$0] }) ?? m["narrator"] ?? m.values.first else { return false }
        let name = (file as NSString).deletingPathExtension
        guard let url = Bundle.main.url(forResource: name, withExtension: "m4a"),
              let p = try? AVAudioPlayer(contentsOf: url) else { return false }
        player?.stop()
        player = p
        p.play()
        return true
    }

    /** يشغّل المقطع المسجّل فقط؛ العبارة غير المسجّلة تظهر نصًا بلا صوت */
    func say(_ s: String, as who: String? = nil) {
        if playRecorded(s, who) { return }
        player?.stop()
    }
}
