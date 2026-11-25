package com.za7ef.homeheroes

/** حوار قصير بين طفلين في نفس الغرفة، حسب أعمارهم وقرابتهم */
data class Convo(val a: String, val b: String, val lineA: String, val lineB: String)

object Talk {
    val convos = listOf(
        // ريان وليان (إخوان)
        Convo("rayan", "layan", "ليان! يلا نرتّب ألعابك مع بعض", "طيب، بس أنا أختار وين نحطها!"),
        Convo("layan", "rayan", "ريان، شوف! رتّبت سريري 🛏️", "واو! أحسن من سريري 😅"),
        Convo("rayan", "layan", "مين يخلّص مهامه أول؟", "أنا! أنا! 🏃‍♀️"),
        Convo("layan", "rayan", "ريان، تلعب معي؟", "بعد الواجب، وعد ✋"),
        // عبدالرحمن وأيان (عمّه)
        Convo("abdulrahman", "ayan", "أيان يا بطل! تعال", "عمّو! 🙌"),
        Convo("ayan", "abdulrahman", "عمّو، كورة! ⚽", "يلا، بس رجّع ألعابك أول"),
        Convo("abdulrahman", "ayan", "مين يفرّش أسنانه؟", "أنا! 🪥"),
        // عبدالرحمن مع ريان وليان (خالهم)
        Convo("abdulrahman", "rayan", "ريان، خلّصت واجباتك؟", "باقي شوي يا خالي!"),
        Convo("rayan", "abdulrahman", "خالي، نتسابق مين يخلّص أول؟", "تحدي مقبول! 😎"),
        Convo("layan", "abdulrahman", "خالي، تقرأ لي قصة؟ 📚", "أكيد، بعد ما ترتّبين سريرك"),
        Convo("abdulrahman", "layan", "ليان، وش رسمتي اليوم؟", "رسمتك يا خالي! 🎨"),
        // مع أيان
        Convo("layan", "ayan", "أيان، تعال أعلّمك تفرّش أسنانك", "أسنان! 🦷"),
        Convo("rayan", "ayan", "أيان، هات خمسة! ✋", "✋ هيه!"),
        Convo("ayan", "layan", "دبدوب! 🧸", "هذا دبدوبك يا أيان، خذه"),
    )

    /** كلام لما يكون الطفل لحاله في الغرفة */
    fun solo(kid: Kid, roomId: String): List<String> {
        if (kid.toddler) return when (roomId) {
            "kitchen" -> listOf("مويه! 💧", "أكل! 🍽️")
            "bath" -> listOf("أسنان! 🪥", "فقاعات! 🫧")
            "bedroom" -> listOf("نوم! 😴", "ملابس! 👕")
            else -> listOf("ألعاب! 🧸", "السلام عليكم 👋")
        }
        return when (roomId) {
            "kitchen" -> listOf("ريحة الأكل حلوة! 😋", "بساعد في ترتيب السفرة 🍽️", "الزبالة لازم تطلع 🗑️")
            "bath" -> listOf("وقت الأسنان! 🪥", "المغسلة لازم تكون نظيفة 🚿")
            "bedroom" -> listOf("سريري لازم يترتب 🛏️", "وين شنطتي؟ 🎒", "يلا نخلّص الواجب 📝")
            else -> listOf("وين ألعابي؟ 🧸", "يلا تحدي العيلة! 🤝", "الحمد لله على كل شي 🤲")
        }
    }
}
