import Foundation

struct Convo { let a: String; let b: String; let lineA: String; let lineB: String }

enum Talk {
    static let convos: [Convo] = [
        Convo(a: "rayan", b: "layan", lineA: "ليان! يلا نرتّب ألعابك مع بعض", lineB: "طيب، بس أنا أختار وين نحطها!"),
        Convo(a: "layan", b: "rayan", lineA: "ريان، شوف! رتّبت سريري 🛏️", lineB: "واو! أحسن من سريري 😅"),
        Convo(a: "rayan", b: "layan", lineA: "مين يخلّص مهامه أول؟", lineB: "أنا! أنا! 🏃‍♀️"),
        Convo(a: "layan", b: "rayan", lineA: "ريان، تلعب معي؟", lineB: "بعد الواجب، وعد ✋"),
        Convo(a: "abdulrahman", b: "ayan", lineA: "أيان يا بطل! تعال", lineB: "عمّو! 🙌"),
        Convo(a: "ayan", b: "abdulrahman", lineA: "عمّو، كورة! ⚽", lineB: "يلا، بس رجّع ألعابك أول"),
        Convo(a: "abdulrahman", b: "ayan", lineA: "مين يفرّش أسنانه؟", lineB: "أنا! 🪥"),
        Convo(a: "abdulrahman", b: "rayan", lineA: "ريان، خلّصت واجباتك؟", lineB: "باقي شوي يا خالي!"),
        Convo(a: "rayan", b: "abdulrahman", lineA: "خالي، نتسابق مين يخلّص أول؟", lineB: "تحدي مقبول! 😎"),
        Convo(a: "layan", b: "abdulrahman", lineA: "خالي، تقرأ لي قصة؟ 📚", lineB: "أكيد، بعد ما ترتّبين سريرك"),
        Convo(a: "abdulrahman", b: "layan", lineA: "ليان، وش رسمتي اليوم؟", lineB: "رسمتك يا خالي! 🎨"),
        Convo(a: "layan", b: "ayan", lineA: "أيان، تعال أعلّمك تفرّش أسنانك", lineB: "أسنان! 🦷"),
        Convo(a: "rayan", b: "ayan", lineA: "أيان، هات خمسة! ✋", lineB: "✋ هيه!"),
        Convo(a: "ayan", b: "layan", lineA: "دبدوب! 🧸", lineB: "هذا دبدوبك يا أيان، خذه")
    ]

    static func solo(_ kid: Kid, _ room: String) -> [String] {
        if kid.toddler {
            switch room {
            case "kitchen": return ["مويه! 💧", "أكل! 🍽️"]
            case "bath": return ["أسنان! 🪥", "فقاعات! 🫧"]
            case "bedroom": return ["نوم! 😴", "ملابس! 👕"]
            default: return ["ألعاب! 🧸", "السلام عليكم 👋"]
            }
        }
        switch room {
        case "kitchen": return ["ريحة الأكل حلوة! 😋", "بساعد في ترتيب السفرة 🍽️", "الزبالة لازم تطلع 🗑️"]
        case "bath": return ["وقت الأسنان! 🪥", "المغسلة لازم تكون نظيفة 🚿"]
        case "bedroom": return ["سريري لازم يترتب 🛏️", "وين شنطتي؟ 🎒", "يلا نخلّص الواجب 📝"]
        default: return ["وين ألعابي؟ 🧸", "يلا تحدي العيلة! 🤝", "الحمد لله على كل شي 🤲"]
        }
    }
}
