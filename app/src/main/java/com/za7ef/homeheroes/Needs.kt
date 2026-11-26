package com.za7ef.homeheroes

/**
 * احتياجات الشخصيات (على طريقة ألعاب الحيوان الأليف): الأكل، والنظافة، والنوم، واللعب، والحمام، والصحة.
 * كل مؤشر من 0 إلى 100 وينقص مع الوقت، وأدوات كل غرفة ترفع مؤشرًا واحدًا.
 */
data class Meter(val id: String, val emoji: String, val name: String, val perHour: Float, val room: String)

/** أداة تُسحب على الشخصية. spot: مكان استخدامها (الفم، الرأس، اليد) */
data class Tool(val emoji: String, val name: String, val meter: String, val amount: Float, val spot: String)

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
            Tool("🍎", "تفاحة", "hunger", 25f, "mouth"), Tool("🍌", "موزة", "hunger", 25f, "mouth"),
            Tool("🥛", "حليب", "hunger", 20f, "mouth"), Tool("🥪", "ساندويتش", "hunger", 35f, "mouth"),
        )
        "bath" -> listOf(
            Tool("🪥", "فرشاة الأسنان", "clean", 25f, "mouth"), Tool("🧼", "الصابون", "clean", 30f, "hand"),
            Tool("🚿", "الدش", "clean", 40f, "head"), Tool("🚽", "الحمام", "toilet", 100f, "hand"),
            Tool("💊", "الدوا", "health", 40f, "mouth"),
        )
        "bedroom" -> listOf(
            Tool("🌙", "النوم", "sleep", 45f, "head"), Tool("🧸", "الدبدوب", "sleep", 20f, "hand"),
            Tool("📖", "قصة", "sleep", 15f, "hand"), Tool("💊", "الدوا", "health", 40f, "mouth"),
            Tool("🌡️", "الحرارة", "health", 10f, "mouth"),
        )
        else -> listOf(
            Tool("⚽", "الكورة", "fun", 30f, "hand"), Tool("🎈", "البالون", "fun", 20f, "hand"),
            Tool("🧩", "اللغز", "fun", 20f, "hand"), Tool("🚗", "السيارة", "fun", 20f, "hand"),
        )
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
