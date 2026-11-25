package com.za7ef.homeheroes

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** حالة المهمة لليوم الحالي */
enum class TaskState { TODO, PENDING, DONE }

data class Chore(val id: String, var emoji: String, var title: String, var points: Int, var room: String = "living")

data class Room(val id: String, val name: String, val emoji: String, val bg: Int)

object Rooms {
    val all = listOf(
        Room("kitchen", "المطبخ", "🍳", R.drawable.room_kitchen),
        Room("living", "الصالة", "🛋️", R.drawable.room_living),
        Room("bedroom", "غرفة النوم", "🛏️", R.drawable.room_bedroom),
        Room("bath", "الحمام", "🛁", R.drawable.room_bath),
    )

    fun byId(id: String): Room = all.firstOrNull { it.id == id } ?: all[1]
}
data class Reward(val id: String, var emoji: String, var title: String, var cost: Int)
data class Request(val id: String, val kidId: String, val kind: String, val refId: String, val title: String, val points: Int)

data class Kid(
    val id: String,
    val name: String,
    val age: String,
    val imageRes: Int,
    val bodyRes: Int,
    val color: Int,
    /** وضع الصغار: صور وصوت فقط بدون الاعتماد على القراءة */
    val toddler: Boolean,
    /** نطق تلقائي لاسم المهمة عند لمسها */
    val autoSpeak: Boolean,
    val relation: String,
)

object Family {
    val kids = listOf(
        Kid("ayan", "أيان", "سنتين ونص", R.drawable.face_ayan, R.drawable.body_ayan, 0xFF4DABF7.toInt(), true, true, "عمّه عبدالرحمن"),
        Kid("layan", "ليان", "6 سنين", R.drawable.face_layan, R.drawable.body_layan, 0xFFF783AC.toInt(), false, true, "أخوها ريان، وخالها عبدالرحمن"),
        Kid("rayan", "ريان", "10 سنين", R.drawable.face_rayan, R.drawable.body_rayan, 0xFF51CF66.toInt(), false, false, "أخته ليان، وخاله عبدالرحمن"),
        Kid("abdulrahman", "عبدالرحمن", "14 سنة", R.drawable.face_abdulrahman, R.drawable.body_abdulrahman, 0xFFFF922B.toInt(), false, false, "عم أيان، وخال ريان وليان"),
    )

    fun byId(id: String): Kid = kids.first { it.id == id }

    fun defaultChores(kidId: String): List<Chore> {
        // (إيموجي، المهمة، النقاط، الغرفة)
        val list: List<List<Any>> = when (kidId) {
            "ayan" -> listOf(
                listOf("🧸", "رجّع ألعابك في الصندوق", 5, "living"),
                listOf("🪥", "فرّش أسنانك مع ماما", 5, "bath"),
                listOf("🧼", "اغسل يدينك", 5, "bath"),
                listOf("👕", "حط ملابسك في السلة", 5, "bedroom"),
                listOf("💧", "اشرب مويتك", 5, "kitchen"),
                listOf("🍽️", "كُل أكلك كله", 5, "kitchen"),
                listOf("👋", "قول السلام عليكم", 5, "living"),
            )
            "layan" -> listOf(
                listOf("🛏️", "رتّبي سريرك", 10, "bedroom"),
                listOf("🪥", "فرّشي أسنانك الصبح والليل", 10, "bath"),
                listOf("🧸", "رتّبي ألعابك", 10, "living"),
                listOf("👟", "حطّي جزمتك في مكانها", 5, "living"),
                listOf("🍽️", "ساعدي في ترتيب السفرة", 10, "kitchen"),
                listOf("📚", "اقري قصة", 10, "bedroom"),
                listOf("🤲", "قولي أذكار الصباح", 10, "living"),
            )
            "rayan" -> listOf(
                listOf("🛏️", "رتّب سريرك", 10, "bedroom"),
                listOf("🪥", "فرّش أسنانك الصبح والليل", 10, "bath"),
                listOf("🕌", "صلّ الصلوات الخمس", 20, "living"),
                listOf("📝", "خلّص واجباتك", 20, "bedroom"),
                listOf("🎒", "جهّز شنطة المدرسة", 10, "bedroom"),
                listOf("🍽️", "شيل الصحون بعد الأكل", 10, "kitchen"),
                listOf("🗑️", "طلّع الزبالة", 10, "kitchen"),
                listOf("🤝", "ساعد ليان ترتّب ألعابها", 15, "living"),
                listOf("📖", "اقرأ 15 دقيقة", 10, "bedroom"),
            )
            else -> listOf(
                listOf("🕌", "صلّ الصلوات الخمس", 20, "living"),
                listOf("📝", "خلّص واجباتك", 20, "bedroom"),
                listOf("🧹", "نظّف غرفتك ورتّبها", 15, "bedroom"),
                listOf("🧺", "اطوِ ملابسك ورتّبها", 10, "bedroom"),
                listOf("🗑️", "طلّع الزبالة", 10, "kitchen"),
                listOf("🛒", "ساعد في ترتيب المقاضي", 15, "kitchen"),
                listOf("🤝", "العب مع أيان 15 دقيقة", 15, "living"),
                listOf("📖", "اقرأ صفحتين قرآن", 15, "living"),
                listOf("🚿", "نظّف المغسلة بعد ما تستخدمها", 10, "bath"),
            )
        }
        return list.mapIndexed { i, p -> Chore("${kidId}_$i", p[0] as String, p[1] as String, p[2] as Int, p[3] as String) }
    }

    /** الغرفة اللي يبدأ فيها كل طفل */
    fun startRoom(kidId: String): String = when (kidId) {
        "ayan" -> "living"
        "layan" -> "bedroom"
        "rayan" -> "kitchen"
        else -> "living"
    }

    fun defaultRewards(): List<Reward> = listOf(
        Reward("r0", "⭐", "ملصق نجمة", 20),
        Reward("r1", "🍦", "آيس كريم", 50),
        Reward("r2", "🎮", "نص ساعة ألعاب", 60),
        Reward("r3", "🏞️", "طلعة للحديقة", 150),
        Reward("r4", "🎁", "لعبة صغيرة", 200),
    )
}

class Store(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("home_heroes", Context.MODE_PRIVATE)

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    // ---------- PIN ----------
    var pin: String?
        get() = prefs.getString("pin", null)
        set(v) { prefs.edit().putString("pin", v).apply() }

    var githubToken: String
        get() = prefs.getString("gh_token", "") ?: ""
        set(v) { prefs.edit().putString("gh_token", v.trim()).apply() }

    var skippedVersion: String
        get() = prefs.getString("skip_ver", "") ?: ""
        set(v) { prefs.edit().putString("skip_ver", v).apply() }

    // ---------- مكان كل طفل في البيت ----------
    fun kidRoom(kidId: String): String = prefs.getString("room_$kidId", null) ?: Family.startRoom(kidId)
    fun setKidRoom(kidId: String, roomId: String) { prefs.edit().putString("room_$kidId", roomId).apply() }

    // ---------- Points ----------
    fun points(kidId: String): Int = prefs.getInt("pts_$kidId", 0)
    fun totalEarned(kidId: String): Int = prefs.getInt("earned_$kidId", 0)

    fun addPoints(kidId: String, delta: Int) {
        val e = prefs.edit()
        e.putInt("pts_$kidId", maxOf(0, points(kidId) + delta))
        if (delta > 0) e.putInt("earned_$kidId", totalEarned(kidId) + delta)
        e.apply()
    }

    /** المستوى يرتفع كل 100 نقطة مكتسبة */
    fun level(kidId: String): Int = totalEarned(kidId) / 100 + 1

    // ---------- Chores ----------
    fun chores(kidId: String): MutableList<Chore> {
        val raw = prefs.getString("chores_$kidId", null) ?: return Family.defaultChores(kidId).toMutableList()
        val arr = JSONArray(raw)
        val out = mutableListOf<Chore>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(Chore(o.getString("id"), o.getString("e"), o.getString("t"), o.getInt("p"), o.optString("r", "living")))
        }
        return out
    }

    fun saveChores(kidId: String, list: List<Chore>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("e", it.emoji).put("t", it.title).put("p", it.points).put("r", it.room)) }
        prefs.edit().putString("chores_$kidId", arr.toString()).apply()
    }

    // ---------- Rewards ----------
    fun rewards(): MutableList<Reward> {
        val raw = prefs.getString("rewards", null) ?: return Family.defaultRewards().toMutableList()
        val arr = JSONArray(raw)
        val out = mutableListOf<Reward>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(Reward(o.getString("id"), o.getString("e"), o.getString("t"), o.getInt("c")))
        }
        return out
    }

    fun saveRewards(list: List<Reward>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("e", it.emoji).put("t", it.title).put("c", it.cost)) }
        prefs.edit().putString("rewards", arr.toString()).apply()
    }

    // ---------- Daily task state ----------
    private fun dayKey(kidId: String) = "day_${kidId}_${today()}"

    fun taskState(kidId: String, choreId: String): TaskState {
        val o = JSONObject(prefs.getString(dayKey(kidId), "{}") ?: "{}")
        return when (o.optString(choreId, "")) {
            "p" -> TaskState.PENDING
            "d" -> TaskState.DONE
            else -> TaskState.TODO
        }
    }

    fun setTaskState(kidId: String, choreId: String, s: TaskState) {
        val o = JSONObject(prefs.getString(dayKey(kidId), "{}") ?: "{}")
        when (s) {
            TaskState.TODO -> o.remove(choreId)
            TaskState.PENDING -> o.put(choreId, "p")
            TaskState.DONE -> o.put(choreId, "d")
        }
        prefs.edit().putString(dayKey(kidId), o.toString()).apply()
    }

    fun doneToday(kidId: String): Int = chores(kidId).count { taskState(kidId, it.id) == TaskState.DONE }

    // ---------- Approval queue ----------
    fun requests(): MutableList<Request> {
        val arr = JSONArray(prefs.getString("requests", "[]") ?: "[]")
        val out = mutableListOf<Request>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(Request(o.getString("id"), o.getString("k"), o.getString("kind"), o.getString("ref"), o.getString("t"), o.getInt("p")))
        }
        return out
    }

    private fun saveRequests(list: List<Request>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("k", it.kidId).put("kind", it.kind).put("ref", it.refId).put("t", it.title).put("p", it.points))
        }
        prefs.edit().putString("requests", arr.toString()).apply()
    }

    fun addRequest(r: Request) { saveRequests(requests() + r) }

    fun removeRequest(id: String) { saveRequests(requests().filter { it.id != id }) }

    /** يحذف طلبات المهام من أيام سابقة لأن حالتها اليومية انتهت */
    fun pruneOldChoreRequests() {
        val t = today()
        saveRequests(requests().filter { it.kind != "chore" || it.id.startsWith(t) })
    }

    fun newRequestId(): String = today() + "_" + System.currentTimeMillis()

    // ---------- Stars history (for the family challenge) ----------
    fun familyDoneToday(): Int = Family.kids.sumOf { doneToday(it.id) }
    fun familyTotalToday(): Int = Family.kids.sumOf { chores(it.id).size }

    fun bonusGivenToday(): Boolean = prefs.getString("bonus_day", "") == today()
    fun markBonusGiven() { prefs.edit().putString("bonus_day", today()).apply() }
}
