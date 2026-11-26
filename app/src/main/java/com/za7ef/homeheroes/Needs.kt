package com.za7ef.homeheroes

/**
 * احتياجات الشخصيات (على طريقة ألعاب الحيوان الأليف): الأكل، والنظافة، والنوم، واللعب، والحمام، والصحة.
 * كل مؤشر من 0 إلى 100 وينقص مع الوقت، وأدوات كل غرفة ترفع مؤشرًا واحدًا.
 */
data class Meter(val id: String, val emoji: String, val name: String, val perHour: Float, val room: String)

/**
 * أداة يمسكها الطفل بإصبعه ويحرّكها على الشخصية. spot: مكان استخدامها (الفم، الرأس، اليد/الجسم).
 * kind: نوع الحركة والأنيميشن الخاص بها (أكل، شرب، تفريش، صابون، دش، ...).
 */
data class Tool(val emoji: String, val name: String, val meter: String, val amount: Float, val spot: String, val kind: String)

/**
 * طريقة استخدام كل نوع: rub = مسافة التحريك (dp) اللازمة لإنهاء الاستخدام بالتحريك فقط،
 * hold = عدد الثواني اللازمة لو بقيت الأداة ثابتة على مكانها. التقدم = مجموع الاثنين.
 * face: تعبير الوجه أثناء الاستخدام (teeth أسنان، chew مضغ، open فم مفتوح، sleep عيون مغمضة).
 */
data class ToolUse(val rub: Float, val hold: Float, val face: String?)

object Needs {
    const val LOW = 35f

    val meters = listOf(
        Meter("hunger", "🍎", "الأكل", 14f, "kitchen"),
        Meter("clean", "🛁", "النظافة", 9f, "bath"),
        Meter("sleep", "😴", "النوم", 7f, "bedroom"),
        Meter("fun", "⚽", "اللعب", 18f, "living"),
        Meter("toilet", "🚽", "الحمام", 12f, "bath"),
        Meter("health", "🤒", "الصحة", 3f, "bedroom"),
    )

    fun meter(id: String): Meter = meters.first { it.id == id }

    fun tools(roomId: String): List<Tool> = when (roomId) {
        "kitchen" -> listOf(
            Tool("🍎", "تفاحة", "hunger", 25f, "mouth", "eat"), Tool("🍌", "موزة", "hunger", 25f, "mouth", "eat"),
            Tool("🥛", "حليب", "hunger", 20f, "mouth", "drink"), Tool("🥪", "ساندويتش", "hunger", 35f, "mouth", "eat"),
        )
        "bath" -> listOf(
            Tool("🪥", "فرشاة الأسنان", "clean", 25f, "mouth", "brush"), Tool("🧼", "الصابون", "clean", 30f, "hand", "scrub"),
            Tool("🚿", "الدش", "clean", 40f, "head", "shower"), Tool("🚽", "الحمام", "toilet", 100f, "hand", "toilet"),
            Tool("💊", "الدوا", "health", 40f, "mouth", "pill"),
        )
        "bedroom" -> listOf(
            Tool("🌙", "النوم", "sleep", 45f, "head", "sleep"), Tool("🧸", "الدبدوب", "sleep", 20f, "hand", "hug"),
            Tool("📖", "قصة", "sleep", 15f, "hand", "story"), Tool("💊", "الدوا", "health", 40f, "mouth", "pill"),
            Tool("🌡️", "الحرارة", "health", 10f, "mouth", "thermo"),
        )
        else -> listOf(
            Tool("⚽", "الكورة", "fun", 30f, "hand", "ball"), Tool("🎈", "البالون", "fun", 20f, "hand", "balloon"),
            Tool("🧩", "اللغز", "fun", 20f, "hand", "puzzle"), Tool("🚗", "السيارة", "fun", 20f, "hand", "car"),
        )
    }

    fun use(kind: String): ToolUse = when (kind) {
        "eat" -> ToolUse(520f, 6f, "chew")
        "drink" -> ToolUse(2400f, 2.5f, "open")
        "brush" -> ToolUse(720f, 40f, "teeth")
        "scrub" -> ToolUse(800f, 40f, null)
        "shower" -> ToolUse(2600f, 3f, "sleep")
        "toilet" -> ToolUse(4000f, 2.5f, null)
        "pill" -> ToolUse(4000f, 1.2f, "open")
        "thermo" -> ToolUse(4000f, 2.5f, "open")
        "sleep" -> ToolUse(2600f, 3f, "sleep")
        "hug" -> ToolUse(1600f, 2.5f, null)
        "story" -> ToolUse(3000f, 3f, null)
        else -> ToolUse(720f, 40f, null) // ball, balloon, puzzle, car: باللعب والتحريك
    }

    /** أقل احتياج تحت الحد، أو لا شيء */
    fun lowest(store: Store, kidId: String): Meter? =
        meters.map { it to store.need(kidId, it.id) }.filter { it.second < LOW }.minByOrNull { it.second }?.first

    fun effect(meter: String): String = when (meter) {
        "hunger" -> "😋"; "clean" -> "✨"; "sleep" -> "💤"; "toilet" -> "😌"; "health" -> "💪"; else -> "🎉"
    }

    fun react(kid: Kid, meter: String): String = if (kid.toddler) when (meter) {
        "hunger" -> listOf("يمي! 😋", "أكل! 🍽️").random()
        "clean" -> listOf("فقاعات! 🫧", "نظيف! ✨").random()
        "sleep" -> listOf("نوم! 😴", "دبدوب! 🧸").random()
        "toilet" -> "خلاص! 😌"
        "health" -> "أحسن! 😊"
        else -> listOf("هييه! 🎉", "لعب! 🙌").random()
    } else effect(meter)

    fun full(kid: Kid): String = if (kid.toddler) "خلاص! 🙅" else "🙅"

    /** عبارة الاحتياج من مجموعة عباراته، بخلط بلا تكرار: لا تتكرر العبارة قبل استخدام البقية (محفوظ بين الجلسات) */
    fun needLine(store: Store, kid: Kid, m: Meter): String {
        val pool = NeedLines.pools[NeedLines.group(kid)]?.get(m.id).orEmpty()
        if (pool.isEmpty()) return m.emoji // الشخصيات الكبيرة: أيقونة فقط حتى تُسجّل عباراتها
        return pool[store.nextFromPool("${kid.id}_${m.id}", pool.size)]
    }

}
