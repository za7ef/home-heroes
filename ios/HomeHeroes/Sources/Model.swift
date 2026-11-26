import SwiftUI

enum TaskState: String { case todo, pending, done }

struct Chore: Codable, Identifiable, Equatable {
    var id: String
    var emoji: String
    var title: String
    var points: Int
    var room: String
}

struct Reward: Codable, Identifiable, Equatable {
    var id: String
    var emoji: String
    var title: String
    var cost: Int
}

struct Request: Codable, Identifiable, Equatable {
    var id: String
    var kidId: String
    var kind: String // chore | reward
    var refId: String
    var title: String
    var points: Int
}

struct Room: Identifiable, Equatable {
    let id: String
    let name: String
    let emoji: String
    let bg: String
}

enum Rooms {
    static let all: [Room] = [
        Room(id: "kitchen", name: "المطبخ", emoji: "🍳", bg: "room_kitchen.jpg"),
        Room(id: "living", name: "غرفة اللعب", emoji: "🧸", bg: "room_living.jpg"),
        Room(id: "bedroom", name: "غرفة النوم", emoji: "🛏️", bg: "room_bedroom.jpg"),
        Room(id: "bath", name: "الحمام", emoji: "🛁", bg: "room_bath.jpg"),
    ]
    static func byId(_ id: String) -> Room { all.first { $0.id == id } ?? all[1] }
}

struct Kid: Identifiable, Equatable {
    let id: String
    let name: String
    let age: String
    let face: String
    let color: Color
    let toddler: Bool
    let autoSpeak: Bool
    let relation: String
}

extension Color {
    init(hex: UInt32) {
        self.init(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255)
    }
}

func image(_ name: String) -> UIImage {
    UIImage(named: name) ?? UIImage()
}

enum Family {
    static let kids: [Kid] = [
        Kid(id: "ayan", name: "أيان", age: "سنتين ونص", face: "face_ayan.png", color: Color(hex: 0x4DABF7), toddler: true, autoSpeak: true, relation: "عمّه عبدالرحمن"),
        Kid(id: "layan", name: "ليان", age: "6 سنين", face: "face_layan.png", color: Color(hex: 0xF783AC), toddler: false, autoSpeak: true, relation: "أخوها ريان، وخالها عبدالرحمن"),
        Kid(id: "rayan", name: "ريان", age: "10 سنين", face: "face_rayan.png", color: Color(hex: 0x51CF66), toddler: false, autoSpeak: false, relation: "أخته ليان، وخاله عبدالرحمن"),
        Kid(id: "abdulrahman", name: "عبدالرحمن", age: "14 سنة", face: "face_abdulrahman.png", color: Color(hex: 0xFF922B), toddler: false, autoSpeak: false, relation: "عم أيان، وخال ريان وليان"),
    ]
    static func byId(_ id: String) -> Kid { kids.first { $0.id == id } ?? kids[0] }

    static func defaultChores(_ kidId: String) -> [Chore] {
        let list: [(String, String, Int, String)]
        switch kidId {
        case "ayan": list = [
            ("🧸", "رجّع ألعابك في الصندوق", 5, "living"), ("🪥", "فرّش أسنانك مع ماما", 5, "bath"),
            ("🧼", "اغسل يدينك", 5, "bath"), ("👕", "حط ملابسك في السلة", 5, "bedroom"),
            ("💧", "اشرب مويتك", 5, "kitchen"), ("🍽️", "كُل أكلك كله", 5, "kitchen"), ("👋", "قول السلام عليكم", 5, "living"),
            ("⚽", "رجّع الكورة للصندوق", 5, "living"),
            ("🧦", "حط جواربك في السلة", 5, "bath"),
            ("🥤", "ودّ كاستك للمغسلة", 5, "kitchen")]
        case "layan": list = [
            ("🛏️", "رتّبي سريرك", 10, "bedroom"), ("🪥", "فرّشي أسنانك الصبح والليل", 10, "bath"),
            ("🧸", "رتّبي ألعابك", 10, "living"), ("👟", "حطّي جزمتك في مكانها", 5, "living"),
            ("🍽️", "ساعدي في ترتيب السفرة", 10, "kitchen"), ("📚", "اقري قصة", 10, "bedroom"), ("🤲", "قولي أذكار الصباح", 10, "living"),
            ("🧦", "حطّي جواربك في السلة", 5, "bath"),
            ("🥤", "ودّي كاستك للمغسلة", 5, "kitchen"),
            ("🧴", "رجّعي الشامبو مكانه", 5, "bath"),
            ("👕", "حطّي ملابسك في السلة", 5, "bedroom")]
        case "rayan": list = [
            ("🛏️", "رتّب سريرك", 10, "bedroom"), ("🪥", "فرّش أسنانك الصبح والليل", 10, "bath"),
            ("🕌", "صلّ الصلوات الخمس", 20, "living"), ("📝", "خلّص واجباتك", 20, "bedroom"),
            ("🎒", "جهّز شنطة المدرسة", 10, "bedroom"), ("🍽️", "شيل الصحون بعد الأكل", 10, "kitchen"),
            ("🗑️", "طلّع الزبالة", 10, "kitchen"), ("🤝", "ساعد ليان ترتّب ألعابها", 15, "living"), ("📖", "اقرأ 15 دقيقة", 10, "bedroom"),
            ("👟", "رتّب جزمتك في الرف", 5, "living"),
            ("🧦", "حط جواربك في السلة", 5, "bath"),
            ("📘", "رجّع كتبك للمكتب", 10, "bedroom"),
            ("🥤", "ودّ الكاسات للمغسلة", 5, "kitchen")]
        default: list = [
            ("🕌", "صلّ الصلوات الخمس", 20, "living"), ("📝", "خلّص واجباتك", 20, "bedroom"),
            ("🧹", "نظّف غرفتك ورتّبها", 15, "bedroom"), ("🧺", "اطوِ ملابسك ورتّبها", 10, "bedroom"),
            ("🗑️", "طلّع الزبالة", 10, "kitchen"), ("🛒", "ساعد في ترتيب المقاضي", 15, "kitchen"),
            ("🤝", "العب مع أيان 15 دقيقة", 15, "living"), ("📖", "اقرأ صفحتين قرآن", 15, "living"),
            ("🚿", "نظّف المغسلة بعد ما تستخدمها", 10, "bath"),
            ("🍽️", "شيل الصحون بعد الأكل", 10, "kitchen"),
            ("👟", "رتّب الجزم في الرف", 10, "living"),
            ("📘", "رجّع كتبك للمكتب", 10, "bedroom"),
            ("⚽", "رجّع ألعاب أيان للصندوق", 10, "living")]
        }
        return list.enumerated().map { i, p in Chore(id: "\(kidId)_\(i)", emoji: p.0, title: p.1, points: p.2, room: p.3) }
    }

    static func startRoom(_ kidId: String) -> String {
        switch kidId { case "ayan": return "living"; case "layan": return "bedroom"; case "rayan": return "kitchen"; default: return "living" }
    }

    static let defaultRewards: [Reward] = [
        Reward(id: "r0", emoji: "⭐", title: "ملصق نجمة", cost: 20),
        Reward(id: "r1", emoji: "🍦", title: "آيس كريم", cost: 50),
        Reward(id: "r2", emoji: "🎮", title: "نص ساعة ألعاب", cost: 60),
        Reward(id: "r3", emoji: "🏞️", title: "طلعة للحديقة", cost: 150),
        Reward(id: "r4", emoji: "🎁", title: "لعبة صغيرة", cost: 200),
    ]
}

/** كل البيانات محفوظة على الجهاز فقط */
final class Store: ObservableObject {
    private let d = UserDefaults.standard
    @Published var tick = 0

    private func changed() { tick += 1 }

    static func today() -> String {
        let f = DateFormatter(); f.locale = Locale(identifier: "en_US_POSIX"); f.dateFormat = "yyyy-MM-dd"
        return f.string(from: Date())
    }

    var pin: String? {
        get { d.string(forKey: "pin") }
        set { d.set(newValue, forKey: "pin"); changed() }
    }
    var skippedVersion: String {
        get { d.string(forKey: "skip_ver") ?? "" }
        set { d.set(newValue, forKey: "skip_ver") }
    }

    func kidRoom(_ k: String) -> String { d.string(forKey: "room_\(k)") ?? Family.startRoom(k) }
    func setKidRoom(_ k: String, _ r: String) { d.set(r, forKey: "room_\(k)"); changed() }

    // الاحتياجات: القيمة المحفوظة ووقت حفظها، وتنقص مع الوقت
    func need(_ k: String, _ m: String) -> Double {
        let meter = Needs.meter(m)
        let v = d.object(forKey: "need_\(k)_\(m)") as? Double ?? 70
        let at = d.double(forKey: "needAt_\(k)_\(m)")
        if at == 0 {
            d.set(v, forKey: "need_\(k)_\(m)"); d.set(Date().timeIntervalSince1970, forKey: "needAt_\(k)_\(m)")
            return v
        }
        let hours = (Date().timeIntervalSince1970 - at) / 3600
        return min(max(v - meter.perHour * hours, 0), 100)
    }

    /** خلط بلا تكرار: يرجع رقم العبارة التالية ويحفظ الباقي، ولما تخلص يعيد الخلط */
    func nextFromPool(_ key: String, _ size: Int) -> Int {
        var left = (d.string(forKey: "pool_\(key)") ?? "").split(separator: ",").compactMap { Int($0) }.filter { $0 < size }
        let last = d.object(forKey: "poolLast_\(key)") as? Int ?? -1
        if left.isEmpty {
            left = Array(0..<size).shuffled()
            if left.count > 1 && left[0] == last { left.append(left.removeFirst()) }
        }
        let i = left.removeFirst()
        d.set(left.map(String.init).joined(separator: ","), forKey: "pool_\(key)")
        d.set(i, forKey: "poolLast_\(key)")
        return i
    }

    func addNeed(_ k: String, _ m: String, _ amount: Double) {
        let v = min(max(need(k, m) + amount, 0), 100)
        d.set(v, forKey: "need_\(k)_\(m)"); d.set(Date().timeIntervalSince1970, forKey: "needAt_\(k)_\(m)")
        changed()
    }

    func points(_ k: String) -> Int { d.integer(forKey: "pts_\(k)") }
    func totalEarned(_ k: String) -> Int { d.integer(forKey: "earned_\(k)") }
    func addPoints(_ k: String, _ delta: Int) {
        d.set(max(0, points(k) + delta), forKey: "pts_\(k)")
        if delta > 0 { d.set(totalEarned(k) + delta, forKey: "earned_\(k)") }
        changed()
    }
    func level(_ k: String) -> Int { totalEarned(k) / 100 + 1 }

    private func load<T: Decodable>(_ key: String) -> T? {
        guard let data = d.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(T.self, from: data)
    }
    private func save<T: Encodable>(_ v: T, _ key: String) {
        d.set(try? JSONEncoder().encode(v), forKey: key); changed()
    }

    func chores(_ k: String) -> [Chore] { load("chores_\(k)") ?? Family.defaultChores(k) }
    func saveChores(_ k: String, _ list: [Chore]) { save(list, "chores_\(k)") }
    func rewards() -> [Reward] { load("rewards") ?? Family.defaultRewards }
    func saveRewards(_ list: [Reward]) { save(list, "rewards") }

    private func dayKey(_ k: String) -> String { "day_\(k)_\(Store.today())" }
    func taskState(_ k: String, _ c: String) -> TaskState {
        let m: [String: String] = load(dayKey(k)) ?? [:]
        return TaskState(rawValue: m[c] ?? "todo") ?? .todo
    }
    func setTaskState(_ k: String, _ c: String, _ s: TaskState) {
        var m: [String: String] = load(dayKey(k)) ?? [:]
        if s == .todo { m.removeValue(forKey: c) } else { m[c] = s.rawValue }
        save(m, dayKey(k))
    }
    func doneToday(_ k: String) -> Int { chores(k).filter { taskState(k, $0.id) == .done }.count }

    func requests() -> [Request] { load("requests") ?? [] }
    func addRequest(_ r: Request) { save(requests() + [r], "requests") }
    func removeRequest(_ id: String) { save(requests().filter { $0.id != id }, "requests") }
    func pruneOld() { save(requests().filter { $0.kind != "chore" || $0.id.hasPrefix(Store.today()) }, "requests") }
    func newRequestId() -> String { "\(Store.today())_\(Int(Date().timeIntervalSince1970 * 1000))" }

    func familyDone() -> Int { Family.kids.reduce(0) { $0 + doneToday($1.id) } }
    func familyTotal() -> Int { Family.kids.reduce(0) { $0 + chores($1.id).count } }
    func familyGoal() -> Int { max(1, familyTotal() * 7 / 10) }
    var bonusGivenToday: Bool { d.string(forKey: "bonus_day") == Store.today() }
    func markBonus() { d.set(Store.today(), forKey: "bonus_day"); changed() }

    /** مكافأة جماعية عند تحقيق هدف العيلة؛ ترجع true لو أُعطيت الآن */
    func checkFamilyBonus() -> Bool {
        if bonusGivenToday { return false }
        if familyDone() >= familyGoal() {
            Family.kids.forEach { addPoints($0.id, 10) }
            markBonus()
            return true
        }
        return false
    }

    func resetAll() {
        for k in Family.kids {
            d.removeObject(forKey: "pts_\(k.id)")
            d.removeObject(forKey: "earned_\(k.id)")
        }
        d.removeObject(forKey: "requests")
        changed()
    }
}
