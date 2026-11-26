import Foundation

/** مؤشر احتياج من 0 إلى 100 ينقص مع الوقت، وله غرفة تُلبّيه */
struct Meter: Identifiable { let id: String; let emoji: String; let name: String; let perHour: Double; let room: String }

/** أداة تُسحب على الشخصية. spot: مكان استخدامها (الفم، الرأس، اليد) */
struct Tool: Identifiable { var id: String { emoji }; let emoji: String; let name: String; let meter: String; let amount: Double; let spot: String }

/**
 احتياجات الشخصيات (على طريقة ألعاب الحيوان الأليف): الأكل، والنظافة، والنوم، واللعب، والحمام، والصحة.
 أدوات كل غرفة ترفع مؤشرًا واحدًا.
 */
enum Needs {
    static let low = 35.0

    static let meters: [Meter] = [
        Meter(id: "hunger", emoji: "🍎", name: "الأكل", perHour: 14, room: "kitchen"),
        Meter(id: "clean", emoji: "🛁", name: "النظافة", perHour: 9, room: "bath"),
        Meter(id: "sleep", emoji: "😴", name: "النوم", perHour: 7, room: "bedroom"),
        Meter(id: "fun", emoji: "⚽", name: "اللعب", perHour: 18, room: "living"),
        Meter(id: "toilet", emoji: "🚽", name: "الحمام", perHour: 12, room: "bath"),
        Meter(id: "health", emoji: "🤒", name: "الصحة", perHour: 3, room: "bedroom"),
    ]

    static func meter(_ id: String) -> Meter { meters.first { $0.id == id } ?? meters[0] }

    static func tools(_ room: String) -> [Tool] {
        switch room {
        case "kitchen": return [
            Tool(emoji: "🍎", name: "تفاحة", meter: "hunger", amount: 25, spot: "mouth"), Tool(emoji: "🍌", name: "موزة", meter: "hunger", amount: 25, spot: "mouth"),
            Tool(emoji: "🥛", name: "حليب", meter: "hunger", amount: 20, spot: "mouth"), Tool(emoji: "🥪", name: "ساندويتش", meter: "hunger", amount: 35, spot: "mouth")]
        case "bath": return [
            Tool(emoji: "🪥", name: "فرشاة الأسنان", meter: "clean", amount: 25, spot: "mouth"), Tool(emoji: "🧼", name: "الصابون", meter: "clean", amount: 30, spot: "hand"),
            Tool(emoji: "🚿", name: "الدش", meter: "clean", amount: 40, spot: "head"), Tool(emoji: "🚽", name: "الحمام", meter: "toilet", amount: 100, spot: "hand"),
            Tool(emoji: "💊", name: "الدوا", meter: "health", amount: 40, spot: "mouth")]
        case "bedroom": return [
            Tool(emoji: "🌙", name: "النوم", meter: "sleep", amount: 45, spot: "head"), Tool(emoji: "🧸", name: "الدبدوب", meter: "sleep", amount: 20, spot: "hand"),
            Tool(emoji: "📖", name: "قصة", meter: "sleep", amount: 15, spot: "hand"), Tool(emoji: "💊", name: "الدوا", meter: "health", amount: 40, spot: "mouth"),
            Tool(emoji: "🌡️", name: "الحرارة", meter: "health", amount: 10, spot: "mouth")]
        default: return [
            Tool(emoji: "⚽", name: "الكورة", meter: "fun", amount: 30, spot: "hand"), Tool(emoji: "🎈", name: "البالون", meter: "fun", amount: 20, spot: "hand"),
            Tool(emoji: "🧩", name: "اللغز", meter: "fun", amount: 20, spot: "hand"), Tool(emoji: "🚗", name: "السيارة", meter: "fun", amount: 20, spot: "hand")]
        }
    }

    /** أقل احتياج تحت الحد، أو لا شيء */
    static func lowest(_ store: Store, _ kid: String) -> Meter? {
        meters.map { ($0, store.need(kid, $0.id)) }.filter { $0.1 < low }.min { $0.1 < $1.1 }?.0
    }

    static func effect(_ m: String) -> String {
        switch m { case "hunger": return "😋"; case "clean": return "✨"; case "sleep": return "💤"; case "toilet": return "😌"; case "health": return "💪"; default: return "🎉" }
    }

    static func react(_ kid: Kid, _ m: String) -> String {
        if kid.toddler {
            switch m {
            case "hunger": return ["يمي! 😋", "أكل! 🍽️"].randomElement()!
            case "clean": return ["فقاعات! 🫧", "نظيف! ✨"].randomElement()!
            case "sleep": return ["نوم! 😴", "دبدوب! 🧸"].randomElement()!
            case "toilet": return "خلاص! 😌"
            case "health": return "أحسن! 😊"
            default: return ["هييه! 🎉", "لعب! 🙌"].randomElement()!
            }
        }
        return effect(m)
    }

    static func full(_ kid: Kid) -> String { kid.toddler ? "خلاص! 🙅" : "🙅" }

    /** عبارة الاحتياج من مجموعة عباراته، بخلط بلا تكرار محفوظ بين الجلسات */
    static func needLine(_ store: Store, _ kid: Kid, _ m: Meter) -> String {
        let pool = NeedLines.pools[NeedLines.group(kid)]?[m.id] ?? []
        if pool.isEmpty { return m.emoji }
        return pool[store.nextFromPool("\(kid.id)_\(m.id)", pool.count)]
    }

}
