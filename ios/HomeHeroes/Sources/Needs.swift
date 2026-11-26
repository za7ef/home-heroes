import Foundation

/** مؤشر احتياج من 0 إلى 100 ينقص مع الوقت، وله غرفة تُلبّيه */
struct Meter: Identifiable { let id: String; let emoji: String; let name: String; let perHour: Double; let room: String }

/**
 أداة يمسكها الطفل بإصبعه ويحرّكها على الشخصية. spot: مكان استخدامها (الفم، الرأس، اليد/الجسم).
 kind: نوع الحركة والأنيميشن الخاص بها.
 */
struct Tool: Identifiable { var id: String { emoji }; let emoji: String; let name: String; let meter: String; let amount: Double; let spot: String; let kind: String }

/** rub = مسافة التحريك (نقطة) اللازمة للإنهاء، hold = ثواني الثبات اللازمة، face = تعبير الوجه أثناء الاستخدام */
struct ToolUse { let rub: Double; let hold: Double; let face: String? }

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
            Tool(emoji: "🍎", name: "تفاحة", meter: "hunger", amount: 25, spot: "mouth", kind: "eat"), Tool(emoji: "🍌", name: "موزة", meter: "hunger", amount: 25, spot: "mouth", kind: "eat"),
            Tool(emoji: "🥛", name: "حليب", meter: "hunger", amount: 20, spot: "mouth", kind: "drink"), Tool(emoji: "🥪", name: "ساندويتش", meter: "hunger", amount: 35, spot: "mouth", kind: "eat")]
        case "bath": return [
            Tool(emoji: "🪥", name: "فرشاة الأسنان", meter: "clean", amount: 25, spot: "mouth", kind: "brush"), Tool(emoji: "🧼", name: "الصابون", meter: "clean", amount: 30, spot: "hand", kind: "scrub"),
            Tool(emoji: "🚿", name: "الدش", meter: "clean", amount: 40, spot: "head", kind: "shower"), Tool(emoji: "🚽", name: "الحمام", meter: "toilet", amount: 100, spot: "hand", kind: "toilet"),
            Tool(emoji: "💊", name: "الدوا", meter: "health", amount: 40, spot: "mouth", kind: "pill")]
        case "bedroom": return [
            Tool(emoji: "🌙", name: "النوم", meter: "sleep", amount: 45, spot: "head", kind: "sleep"), Tool(emoji: "🧸", name: "الدبدوب", meter: "sleep", amount: 20, spot: "hand", kind: "hug"),
            Tool(emoji: "📖", name: "قصة", meter: "sleep", amount: 15, spot: "hand", kind: "story"), Tool(emoji: "💊", name: "الدوا", meter: "health", amount: 40, spot: "mouth", kind: "pill"),
            Tool(emoji: "🌡️", name: "الحرارة", meter: "health", amount: 10, spot: "mouth", kind: "thermo")]
        default: return [
            Tool(emoji: "⚽", name: "الكورة", meter: "fun", amount: 30, spot: "hand", kind: "ball"), Tool(emoji: "🎈", name: "البالون", meter: "fun", amount: 20, spot: "hand", kind: "balloon"),
            Tool(emoji: "🧩", name: "اللغز", meter: "fun", amount: 20, spot: "hand", kind: "puzzle"), Tool(emoji: "🚗", name: "السيارة", meter: "fun", amount: 20, spot: "hand", kind: "car")]
        }
    }

    static func use(_ kind: String) -> ToolUse {
        switch kind {
        case "eat": return ToolUse(rub: 520, hold: 6, face: "chew")
        case "drink": return ToolUse(rub: 2400, hold: 2.5, face: "open")
        case "brush": return ToolUse(rub: 720, hold: 40, face: "teeth")
        case "scrub": return ToolUse(rub: 800, hold: 40, face: nil)
        case "shower": return ToolUse(rub: 2600, hold: 3, face: "sleep")
        case "toilet": return ToolUse(rub: 4000, hold: 2.5, face: nil)
        case "pill": return ToolUse(rub: 4000, hold: 1.2, face: "open")
        case "thermo": return ToolUse(rub: 4000, hold: 2.5, face: "open")
        case "sleep": return ToolUse(rub: 2600, hold: 3, face: "sleep")
        case "hug": return ToolUse(rub: 1600, hold: 2.5, face: nil)
        case "story": return ToolUse(rub: 3000, hold: 3, face: nil)
        default: return ToolUse(rub: 720, hold: 40, face: nil)
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
