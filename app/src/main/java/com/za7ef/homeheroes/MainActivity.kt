package com.za7ef.homeheroes

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var store: Store
    private val main = Handler(Looper.getMainLooper())

    /** شاشة الرجوع عند ضغط زر الرجوع */
    private var back: (() -> Unit)? = null

    private val bg = 0xFFFFF4D6.toInt()
    private val orange = 0xFFE8590C.toInt()
    private val green = 0xFF2F9E44.toInt()
    private val gray = 0xFF868E96.toInt()
    private val blue = 0xFF1C7ED6.toInt()
    private val red = 0xFFE03131.toInt()

    private var sound: Sound? = null
    private var voice: Voice? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Settings.init(this)
        store = Store(this)
        store.pruneOldChoreRequests()
        sound = Sound(this).also { Sound.current = it }
        voice = Voice(this)
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7)
        }
        showHome()
        checkForUpdate(manual = false)
    }

    override fun onResume() {
        super.onResume()
        sound?.startMusic()
        if (overlay != null && charViews.isNotEmpty()) startTalk()
    }

    override fun onPause() {
        stopTalk()
        sound?.pauseMusic()
        super.onPause()
    }

    override fun onDestroy() {
        stopTalk()
        sound?.release()
        voice?.stop()
        if (Sound.current === sound) Sound.current = null
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val b = back
        if (b != null) b() else super.onBackPressed()
    }

    // ---------------- صوت ----------------

    /** يشغّل الصوت المسجّل للعبارة (بصوت المتحدث who) فقط؛ العبارة غير المسجّلة تظهر نصًا بلا صوت */
    private fun speak(s: String, who: String? = null) {
        if (voice?.play(s, who) == true) return
        voice?.stop()
    }

    private fun cheerSound() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
            tg.startTone(ToneGenerator.TONE_PROP_ACK, 300)
            main.postDelayed({ tg.release() }, 600)
        } catch (_: Exception) {
        }
    }

    // ---------------- هيكل الشاشة ----------------

    private fun screen(title: String, onBack: (() -> Unit)?, build: (LinearLayout) -> Unit) {
        stopTalk()
        overlay = null
        roomScene = null
        charViews.clear()
        back = onBack
        val root = vbox().apply { setBackgroundColor(bg); fitsSystemWindows = true }
        val bar = hbox().apply { setPadding(dp(16), dp(12), dp(16), dp(12)) }
        if (onBack != null) {
            bar.addView(button("→ رجوع", gray, 18f) { onBack() })
        }
        val t = text(title, 26f, orange, true).apply { gravity = Gravity.CENTER }
        bar.addView(t, lp(0, WRAP, 1f))
        root.addView(bar, lp(MATCH, WRAP))
        val scroll = ScrollView(this)
        val content = vbox().apply { setPadding(dp(16), dp(4), dp(16), dp(24)) }
        scroll.addView(content)
        root.addView(scroll, lp(MATCH, 0, 1f))
        build(content)
        setContentView(root)
    }

    private fun avatar(kid: Kid, sizeDp: Int): ImageView = ImageView(this).apply {
        setImageResource(kid.imageRes)
        scaleType = ImageView.ScaleType.FIT_CENTER
        background = rounded(Color.WHITE, dp(sizeDp / 2).toFloat(), dp(4), kid.color)
        setPadding(dp(6), dp(6), dp(6), dp(6))
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

    private fun columns(): Int = if (resources.configuration.screenWidthDp >= 900) 4 else 2

    // ---------------- البيت (الشاشة الرئيسية) ----------------

    private var selectedKid: String? = null
    private val charViews = mutableMapOf<String, View>()
    private var overlay: FrameLayout? = null
    private var talkLoop: Runnable? = null
    private var talkBusyUntil = 0L
    private var talkGen = 0
    private var houseRoomViews = mutableMapOf<String, FrameLayout>()

    private fun stopTalk() {
        talkLoop?.let { main.removeCallbacks(it) }
        talkLoop = null
        talkGen++
        talkBusyUntil = 0L
    }

    /** يبني شاشة كاملة بدون تمرير، مع طبقة علوية للفقاعات والحركة */
    private fun stage(onBack: (() -> Unit)?, build: (FrameLayout) -> Unit) {
        stopTalk()
        back = onBack
        charViews.clear()
        roomScene = null
        val frame = FrameLayout(this).apply {
            setBackgroundColor(bg)
            // يبقى المحتوى داخل المنطقة الآمنة (بعيد عن النوتش وأشرطة النظام)
            fitsSystemWindows = true
        }
        build(frame)
        val ov = FrameLayout(this)
        frame.addView(ov, FrameLayout.LayoutParams(MATCH, MATCH))
        overlay = ov
        setContentView(frame)
    }

    private fun showHome() {
        checkFamilyBonus()
        stage(null) { frame ->
            val root = vbox()
            frame.addView(root, FrameLayout.LayoutParams(MATCH, MATCH))

            // الشريط العلوي: العنوان ونقاط كل طفل
            val top = hbox().apply { setPadding(dp(12), dp(8), dp(12), dp(8)) }
            top.addView(text("🏠 أبطال البيت", 24f, orange, true))
            val chips = hbox().apply { gravity = Gravity.CENTER }
            for (kid in Family.kids) {
                val chip = hbox().apply {
                    background = rounded(Color.WHITE, dp(30).toFloat(), dp(3), kid.color)
                    setPadding(dp(4), dp(2), dp(12), dp(2))
                    setOnClickListener { showKid(kid) }
                }
                chip.addView(avatar(kid, 44))
                chip.addView(text(" ⭐${store.points(kid.id)}", 18f, kid.color, true))
                chips.addView(chip, lp(WRAP, WRAP, margin = dp(4)))
            }
            top.addView(chips, lp(0, WRAP, 1f))
            val pending = store.requests().size
            top.addView(button("⚙️", 0xFF5C5F66.toInt(), 18f) { showSettings { showHome() } }, lp(WRAP, WRAP, margin = dp(4)))
            top.addView(button(if (pending > 0) "🔒 $pending" else "🔒", 0xFF5C5F66.toInt(), 18f) { parentGate() })
            root.addView(top, lp(MATCH, WRAP))

            // البيت: أربع غرف
            houseRoomViews.clear()
            val house = vbox().apply { setPadding(dp(6), 0, dp(6), 0) }
            for (row in Rooms.all.chunked(2)) {
                val r = hbox().apply { gravity = Gravity.FILL }
                for (room in row) {
                    val rv = houseRoom(room)
                    houseRoomViews[room.id] = rv
                    r.addView(rv, LinearLayout.LayoutParams(0, MATCH, 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
                }
                house.addView(r, lp(MATCH, 0, 1f))
            }
            root.addView(house, lp(MATCH, 0, 1f))

            // شريط تحدي العيلة
            val done = store.familyDoneToday()
            val goal = familyGoal(store.familyTotalToday())
            val bar = hbox().apply {
                background = rounded(0xFFFFE8CC.toInt(), dp(20).toFloat())
                setPadding(dp(14), dp(6), dp(14), dp(6))
            }
            bar.addView(text("🤝 تحدي العيلة", 18f, orange, true))
            bar.addView(progress(minOf(done, goal), goal, orange), lp(0, dp(16), 1f, dp(8)))
            bar.addView(text(if (store.bonusGivenToday()) "🎉 فزتوا!" else "$done / $goal", 18f, 0xFF495057.toInt(), true))
            root.addView(bar, lp(MATCH, WRAP, margin = dp(8)))
        }
        selectedKid = null
        startTalk()
    }

    private fun todoCount(roomId: String, kidId: String?): Int {
        val kids = if (kidId != null) listOf(kidId) else Family.kids.map { it.id }
        return kids.sumOf { k -> store.chores(k).count { it.room == roomId && store.taskState(k, it.id) == TaskState.TODO } }
    }

    private fun houseRoom(room: Room): FrameLayout {
        val fr = FrameLayout(this).apply {
            background = rounded(Color.WHITE, dp(18).toFloat(), dp(4), 0xFF8D5524.toInt())
            setPadding(dp(4), dp(4), dp(4), dp(4))
            clipToPadding = true
        }
        fr.addView(ImageView(this).apply {
            setImageResource(room.bg)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }, FrameLayout.LayoutParams(MATCH, MATCH))

        val n = todoCount(room.id, selectedKid)
        val label = text("${room.emoji} ${room.name}" + if (n > 0) "  ⭐$n" else "", 17f, Color.WHITE, true).apply {
            background = rounded(0xCC5C3D2E.toInt(), dp(14).toFloat())
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }
        fr.addView(label, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.END).apply { setMargins(dp(8), dp(8), dp(8), dp(8)) })

        val floor = hbox().apply { gravity = Gravity.CENTER or Gravity.BOTTOM }
        for (kid in Family.kids.filter { store.kidRoom(it.id) == room.id }) {
            val v = character(kid, 0.8f)
            charViews[kid.id] = v
            floor.addView(v)
        }
        fr.addView(floor, FrameLayout.LayoutParams(MATCH, MATCH).apply { bottomMargin = dp(6) })

        fr.setOnClickListener {
            val sel = selectedKid
            if (sel != null && store.kidRoom(sel) != room.id) moveKid(Family.byId(sel), room) { showHome() }
            else {
                selectedKid = null
                showRoom(room)
            }
        }
        return fr
    }

    /** الشخصية: صورة الجسم، وحجمها حسب العمر */
    private fun character(kid: Kid, scale: Float): CharacterView {
        val h = when (kid.id) {
            "ayan" -> 95
            "layan" -> 120
            "rayan" -> 135
            else -> 150
        }
        val px = (dp(h) * scale).toInt()
        return CharacterView(this, kid).apply {
            layoutParams = LinearLayout.LayoutParams((px * 0.62f).toInt(), px).apply { setMargins(dp(2), 0, dp(2), 0) }
            setOnClickListener { onCharacterTap(kid, this) }
            setOnLongClickListener { showKid(kid); true }
        }
    }

    private fun onCharacterTap(kid: Kid, v: View) {
        if (selectedKid == kid.id) {
            selectedKid = null
            showRoom(Rooms.byId(store.kidRoom(kid.id)))
            return
        }
        selectedKid = kid.id
        charViews.values.forEach { it.animate().scaleX(1f).scaleY(1f).setDuration(120).start() }
        v.animate().scaleX(1.15f).scaleY(1.15f).setDuration(150).start()
        speak(kid.name)
        bubble(v, if (kid.toddler) "وين؟ 🙂" else "وين أروح؟ اختار غرفة!")
        // تحديث عدادات المهام على الغرف لهذا الطفل
        for (room in Rooms.all) {
            val fr = houseRoomViews[room.id] ?: continue
            val label = fr.getChildAt(1) as? TextView ?: continue
            val n = todoCount(room.id, kid.id)
            label.text = "${room.emoji} ${room.name}" + if (n > 0) "  ⭐$n" else ""
        }
    }

    /** يمشي الطفل من غرفته للغرفة الجديدة بحركة مشي بسيطة */
    private fun moveKid(kid: Kid, to: Room, then: () -> Unit) {
        val ov = overlay ?: return then()
        val from = charViews[kid.id]
        val target = houseRoomViews[to.id]
        store.setKidRoom(kid.id, to.id)
        speak(if (kid.toddler) "يلا!" else "رايح ${to.name}", kid.id)
        if (from == null || target == null) return then()
        val o = IntArray(2); ov.getLocationInWindow(o)
        val a = IntArray(2); from.getLocationInWindow(a)
        val b = IntArray(2); target.getLocationInWindow(b)
        val walker = CharacterView(this, kid).apply { walking = true }
        ov.addView(walker, FrameLayout.LayoutParams(from.width, from.height))
        walker.x = (a[0] - o[0]).toFloat()
        walker.y = (a[1] - o[1]).toFloat()
        from.visibility = View.INVISIBLE
        val tx = (b[0] - o[0] + target.width / 2 - from.width / 2).toFloat()
        val ty = (b[1] - o[1] + target.height - from.height - dp(8)).toFloat()
        val hop = ObjectAnimator.ofFloat(walker, "rotation", -6f, 6f).apply {
            duration = 160
            repeatCount = 7
            repeatMode = android.animation.ValueAnimator.REVERSE
        }
        hop.start()
        walker.animate().x(tx).y(ty).setDuration(1200).withEndAction {
            hop.cancel()
            ov.removeView(walker)
            then()
        }.start()
    }

    /** فقاعة كلام فوق رأس الشخصية */
    private fun bubble(anchor: View, line: String, ms: Long = 2600) {
        val ov = overlay ?: return
        if (!anchor.isAttachedToWindow) return
        val o = IntArray(2); ov.getLocationInWindow(o)
        val a = IntArray(2); anchor.getLocationInWindow(a)
        val t = text(line, 17f, 0xFF212529.toInt(), true).apply {
            background = rounded(Color.WHITE, dp(16).toFloat(), dp(2), 0xFF343A40.toInt())
            setPadding(dp(10), dp(6), dp(10), dp(6))
            maxWidth = dp(230)
            alpha = 0f
        }
        ov.addView(t, FrameLayout.LayoutParams(WRAP, WRAP))
        t.post {
            val x = (a[0] - o[0] + anchor.width / 2 - t.width / 2).toFloat()
            t.x = x.coerceIn(0f, maxOf(0f, (ov.width - t.width).toFloat()))
            t.y = maxOf(0f, (a[1] - o[1] - t.height + dp(6)).toFloat())
            t.animate().alpha(1f).setDuration(150).start()
        }
        main.postDelayed({ t.animate().alpha(0f).setDuration(200).withEndAction { ov.removeView(t) }.start() }, ms)
    }

    /** كل شوي الأطفال يسولفون مع بعض حسب مين في نفس الغرفة */
    private fun startTalk() {
        stopTalk()
        val r = object : Runnable {
            override fun run() {
                talkOnce()
                main.postDelayed(this, 7000)
            }
        }
        talkLoop = r
        main.postDelayed(r, 2500)
    }

    private fun talkOnce() {
        if (!Settings.autoTalk) return
        val busy = roomScene?.activeIds.orEmpty()
        val together = Talk.convos.filter {
            charViews.containsKey(it.a) && charViews.containsKey(it.b) && store.kidRoom(it.a) == store.kidRoom(it.b) &&
                it.a !in busy && it.b !in busy && convoFits(it)
        }
        if (System.currentTimeMillis() < talkBusyUntil) return
        if (together.isNotEmpty() && Math.random() < 0.75) {
            // حوار متناوب: كل سطر بعد اللي قبله، ولا يبدأ حوار جديد قبل نهايته
            val c = together.random()
            val gen = talkGen
            c.lines.forEachIndexed { i, line ->
                main.postDelayed({ if (gen == talkGen) charViews[c.speaker(i)]?.let { bubble(it, line); speak(line, c.speaker(i)) } }, i * 2600L)
            }
            talkBusyUntil = System.currentTimeMillis() + c.lines.size * 2600L + 500
        } else {
            val kid = Family.kids.filter { charViews.containsKey(it.id) && it.id !in busy }.randomOrNull() ?: return
            val v = charViews[kid.id] ?: return
            val line = soloLine(kid, store.kidRoom(kid.id))
            bubble(v, line); speak(line, kid.id)
        }
    }

    /** الحوار اللي يذكر احتياج (أكل، نظافة، صحة، نوم) ما يصير إلا لما يكون مؤشره تحت الحد عند واحد منهم */
    private fun convoFits(c: Convo): Boolean =
        c.lines.flatMap { Acts.needsOf(it) }.distinct().all { m -> minOf(store.need(c.a, m), store.need(c.b, m)) < Needs.LOW }

    /** عبارة عشوائية تناسب الغرفة، بدون عبارات احتياج ما لها داعي */
    private fun soloLine(kid: Kid, room: String): String =
        Talk.solo(kid, room).filter { Acts.fits(store, kid.id, it) }.randomOrNull()
            ?: if (kid.toddler) listOf("هييه! 🎉", "سلام! 👋", "يلا!").random() else "😊"

    private var roomScene: RoomScene? = null

    // ---------------- داخل الغرفة ----------------

    private fun showRoom(room: Room) {
        stage({ showHome() }) { frame ->
            val here = Family.kids.filter { store.kidRoom(it.id) == room.id }
            // المشهد بكامل الشاشة تحت الأزرار، والخلفية جزء منه حتى تتطابق الأماكن مع الرسم
            val scene = RoomScene(
                this, room.id, room.bg, here, store,
                say = { v, s -> bubble(v, s) },
                speak = { text, who -> speak(text, who) },
                onKidTap = { kid, v -> val line = soloLine(kid, room.id); bubble(v, line); speak(line, kid.id) },
            )
            roomScene = scene
            frame.addView(scene, FrameLayout.LayoutParams(MATCH, MATCH))
            scene.post { charViews.putAll(scene.chars) }
            main.postDelayed({ charViews.putAll(scene.chars) }, 400)

            val root = vbox()
            frame.addView(root, FrameLayout.LayoutParams(MATCH, MATCH))
            val top = hbox().apply { setPadding(dp(12), dp(10), dp(12), dp(6)) }
            top.addView(button("🏠 البيت", orange, 18f) { showHome() })
            top.addView(text("  ${room.emoji} ${room.name}", 26f, Color.WHITE, true).apply {
                setShadowLayer(6f, 0f, 2f, Color.BLACK)
            }, lp(0, WRAP, 1f))
            // نداء طفل غير موجود في الغرفة
            for (kid in Family.kids.filter { store.kidRoom(it.id) != room.id }) {
                val call = hbox().apply {
                    background = rounded(0xEEFFFFFF.toInt(), dp(26).toFloat(), dp(2), kid.color)
                    setPadding(dp(4), dp(2), dp(10), dp(2))
                    setOnClickListener {
                        store.setKidRoom(kid.id, room.id)
                        speak("جاي!", kid.id)
                        showRoom(room)
                    }
                }
                call.addView(avatar(kid, 40))
                call.addView(text(" تعال", 16f, kid.color, true))
                top.addView(call, lp(WRAP, WRAP, margin = dp(3)))
            }
            root.addView(top, lp(MATCH, WRAP))

            if (here.isEmpty()) root.addView(hbox().apply {
                setPadding(dp(8), 0, dp(8), 0)
                addView(note("ما فيه أحد هنا! اضغط «تعال» عشان تنادي أحد 👆"))
            }, lp(MATCH, WRAP))

        }
        if (Family.kids.any { store.kidRoom(it.id) == room.id }) startTalk()
    }

    private fun note(s: String): TextView = text(s, 22f, 0xFF212529.toInt(), true).apply {
        background = rounded(0xEEFFFFFF.toInt(), dp(18).toFloat())
        setPadding(dp(16), dp(10), dp(16), dp(10))
    }

    private fun finishChore(room: Room, kid: Kid, ch: Chore) {
        if (store.taskState(kid.id, ch.id) != TaskState.TODO) return
        store.setTaskState(kid.id, ch.id, TaskState.PENDING)
        store.addRequest(Request(store.newRequestId(), kid.id, "chore", ch.id, ch.title, ch.points))
        sound?.success()
        speak(if (kid.toddler) "شاطر يا ${kid.name}!" else "أحسنت يا ${kid.name}! بانتظار اعتماد الأهل")
        celebrate(kid) { showRoom(room) }
    }

    private fun progress(done: Int, total: Int, color: Int): ProgressBar =
        ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = maxOf(1, total)
            this.progress = done
            progressTintList = android.content.res.ColorStateList.valueOf(color)
        }

    private fun familyGoal(total: Int): Int = maxOf(1, (total * 7) / 10)

    private fun checkFamilyBonus() {
        if (store.bonusGivenToday()) return
        if (store.familyDoneToday() >= familyGoal(store.familyTotalToday())) {
            Family.kids.forEach { store.addPoints(it.id, 10) }
            store.markBonusGiven()
            cheerSound()
            Toast.makeText(this, "🎉 العيلة كلها فازت بتحدي اليوم! +10 لكل واحد", Toast.LENGTH_LONG).show()
        }
    }

    // ---------------- ملف الطفل ----------------

    private fun showKid(kid: Kid, greet: Boolean = true) {
        stopTalk()
        screen("${kid.name} البطل", { showHome() }) { c ->
            val head = hbox().apply {
                background = rounded(Color.WHITE, dp(28).toFloat(), dp(3), kid.color)
                setPadding(dp(16), dp(12), dp(16), dp(12))
            }
            head.addView(CharacterView(this, kid), LinearLayout.LayoutParams(dp(110), dp(170)))
            val info = vbox().apply { setPadding(dp(16), 0, dp(16), 0) }
            info.addView(text("⭐ ${store.points(kid.id)}", 34f, kid.color, true))
            info.addView(text("🏅 المستوى ${store.level(kid.id)}", 22f, 0xFF495057.toInt(), true))
            info.addView(text("👨‍👩‍👧‍👦 ${kid.relation}", 17f, gray))
            info.addView(text("📍 الحين في ${Rooms.byId(store.kidRoom(kid.id)).name}", 17f, gray))
            head.addView(info, lp(0, WRAP, 1f))
            head.addView(button("🎁 المكافآت", orange, 20f) { showRewards(kid) })
            c.addView(head, lp(MATCH, WRAP))

            val chores = store.chores(kid.id)
            val done = store.doneToday(kid.id)
            c.addView(progress(done, chores.size, kid.color), lp(MATCH, dp(18), margin = dp(10)))
            if (done == chores.size && chores.isNotEmpty()) {
                c.addView(text("🏆 خلّصت كل مهام اليوم! بطل!", 24f, green, true).apply { gravity = Gravity.CENTER }, lp(MATCH, WRAP))
            }
            for (room in Rooms.all) {
                val list = chores.filter { it.room == room.id }
                if (list.isEmpty()) continue
                val rowBox = hbox().apply {
                    background = rounded(Color.WHITE, dp(20).toFloat(), dp(2), kid.color)
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                    setOnClickListener {
                        store.setKidRoom(kid.id, room.id)
                        showRoom(room)
                    }
                }
                rowBox.addView(text("${room.emoji} ${room.name}", 20f, orange, true), lp(dp(150), WRAP))
                val marks = list.joinToString("   ") { ch ->
                    val s = when (store.taskState(kid.id, ch.id)) {
                        TaskState.TODO -> ""
                        TaskState.PENDING -> "⏳"
                        TaskState.DONE -> "✅"
                    }
                    "${ch.emoji}$s"
                }
                rowBox.addView(text(marks, 24f), lp(0, WRAP, 1f))
                rowBox.addView(text("روح ←", 17f, kid.color, true))
                c.addView(rowBox, lp(MATCH, WRAP, margin = dp(5)))
            }
        }
        if (kid.autoSpeak && greet) speak("هلا ${kid.name}!")
    }

    private fun bounce(v: View, then: () -> Unit) {
        val sx = ObjectAnimator.ofFloat(v, "scaleX", 1f, 0.9f, 1f)
        val sy = ObjectAnimator.ofFloat(v, "scaleY", 1f, 0.9f, 1f)
        AnimatorSet().apply {
            playTogether(sx, sy)
            duration = 180
            start()
        }
        main.postDelayed({ then() }, 190)
    }

    /** احتفال: نجوم تطير والشخصية تكبر في النص */
    private fun celebrate(kid: Kid, then: () -> Unit) {
        val root = window.decorView as FrameLayout
        val layer = FrameLayout(this)
        root.addView(layer, FrameLayout.LayoutParams(MATCH, MATCH))
        val w = resources.displayMetrics.widthPixels
        val h = resources.displayMetrics.heightPixels
        val emojis = listOf("⭐", "🌟", "🎉", "✨", "💛")
        repeat(18) { i ->
            val t = TextView(this).apply {
                text = emojis[i % emojis.size]
                textSize = 40f
                x = (w * ((i * 37) % 100) / 100f)
                y = h.toFloat()
            }
            layer.addView(t, FrameLayout.LayoutParams(WRAP, WRAP))
            t.animate().translationY(-h * 0.2f - (i % 5) * 60f).rotation(360f).alpha(0.2f)
                .setStartDelay((i * 30).toLong()).setDuration(1100).start()
        }
        val big = CharacterView(this, kid).apply {
            scaleX = 0.2f
            scaleY = 0.2f
        }
        layer.addView(big, FrameLayout.LayoutParams(dp(190), dp(300), Gravity.CENTER))
        big.animate().scaleX(1f).scaleY(1f).setDuration(500).start()
        main.postDelayed({
            root.removeView(layer)
            then()
        }, 1700)
    }

    // ---------------- المكافآت ----------------

    private fun showRewards(kid: Kid) {
        screen("🎁 مكافآت ${kid.name}", { showKid(kid, false) }) { c ->
            c.addView(text("عندك ⭐ ${store.points(kid.id)}", 28f, kid.color, true).apply { gravity = Gravity.CENTER }, lp(MATCH, WRAP))
            val pendingIds = store.requests().filter { it.kidId == kid.id && it.kind == "reward" }.map { it.refId }
            for (r in store.rewards()) {
                val row = hbox().apply {
                    background = rounded(Color.WHITE, dp(22).toFloat(), dp(2), kid.color)
                    setPadding(dp(14), dp(10), dp(14), dp(10))
                    setOnClickListener { speak(r.title) }
                }
                row.addView(text(r.emoji, 44f))
                val col = vbox().apply { setPadding(dp(12), 0, dp(12), 0) }
                col.addView(text(r.title, 22f, bold = true))
                col.addView(text("⭐ ${r.cost}", 18f, kid.color, true))
                row.addView(col, lp(0, WRAP, 1f))
                val can = store.points(kid.id) >= r.cost
                when {
                    r.id in pendingIds -> row.addView(text("⏳", 34f))
                    can -> row.addView(button("أبغاها!", green) {
                        store.addRequest(Request(store.newRequestId(), kid.id, "reward", r.id, r.title, r.cost))
                        cheerSound()
                        speak("طلبت ${r.title}. بانتظار موافقة الأهل")
                        showRewards(kid)
                    })
                    else -> row.addView(text("باقي ${r.cost - store.points(kid.id)}", 17f, gray))
                }
                c.addView(row, lp(MATCH, WRAP, margin = dp(6)))
            }
        }
    }

    // ---------------- منطقة الأهل ----------------

    private fun numberInput(hint: String, password: Boolean = true): EditText = EditText(this).apply {
        this.hint = hint
        inputType = InputType.TYPE_CLASS_NUMBER or (if (password) InputType.TYPE_NUMBER_VARIATION_PASSWORD else 0)
        textSize = 22f
        gravity = Gravity.CENTER
    }

    private fun parentGate() {
        val current = store.pin
        val input = numberInput(if (current == null) "رمز جديد (4 أرقام أو أكثر)" else "الرمز السري")
        AlertDialog.Builder(this)
            .setTitle(if (current == null) "إنشاء رمز منطقة الأهل" else "منطقة الأهل")
            .setView(input)
            .setPositiveButton("دخول") { _, _ ->
                val v = input.text.toString()
                if (current == null) {
                    if (v.length >= 4) {
                        store.pin = v
                        showParent()
                    } else toast("الرمز لازم يكون 4 أرقام أو أكثر")
                } else if (v == current) showParent() else toast("الرمز غلط")
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun showParent() {
        screen("🔒 منطقة الأهل", { showHome() }) { c ->
            val reqs = store.requests()
            c.addView(text("بانتظار الاعتماد (${reqs.size})", 22f, orange, true))
            if (reqs.isEmpty()) c.addView(text("لا توجد طلبات حاليًا.", 18f, gray))
            for (r in reqs) {
                val kid = Family.byId(r.kidId)
                val row = hbox().apply {
                    background = rounded(Color.WHITE, dp(18).toFloat(), dp(2), kid.color)
                    setPadding(dp(10), dp(8), dp(10), dp(8))
                }
                row.addView(avatar(kid, 56))
                val label = if (r.kind == "chore") "مهمة: ${r.title} (+${r.points})" else "مكافأة: ${r.title} (−${r.points})"
                row.addView(text("${kid.name}\n$label", 18f).apply { setPadding(dp(10), 0, dp(10), 0) }, lp(0, WRAP, 1f))
                row.addView(button("✔", green, 22f) { approve(r) })
                row.addView(button("✖", red, 22f) { reject(r) }, lp(WRAP, WRAP, margin = dp(4)))
                c.addView(row, lp(MATCH, WRAP, margin = dp(4)))
            }
            if (reqs.size > 1) c.addView(button("اعتماد الكل", green) { reqs.forEach { approveSilently(it) }; showParent() }, lp(MATCH, WRAP, margin = dp(6)))

            c.addView(text("الإدارة", 22f, orange, true).apply { setPadding(0, dp(16), 0, 0) })
            for (kid in Family.kids) {
                c.addView(button("✏️ مهام ${kid.name}", kid.color) { editChores(kid) }, lp(MATCH, WRAP, margin = dp(4)))
            }
            c.addView(button("🎁 تعديل المكافآت", orange) { editRewards() }, lp(MATCH, WRAP, margin = dp(4)))
            c.addView(button("➕➖ تعديل النقاط يدويًا", blue) { adjustPoints() }, lp(MATCH, WRAP, margin = dp(4)))
            c.addView(button("⚙️ الإعدادات", gray) { showSettings { showParent() } }, lp(MATCH, WRAP, margin = dp(4)))
            c.addView(button("🔑 تغيير الرمز السري", gray) { changePin() }, lp(MATCH, WRAP, margin = dp(4)))
            c.addView(button("🗑️ تصفير النقاط", red) { resetAll() }, lp(MATCH, WRAP, margin = dp(4)))
        }
    }

    private fun approveSilently(r: Request) {
        if (r.kind == "chore") {
            store.setTaskState(r.kidId, r.refId, TaskState.DONE)
            store.addPoints(r.kidId, r.points)
        } else {
            if (store.points(r.kidId) < r.points) {
                toast("نقاط ${Family.byId(r.kidId).name} لا تكفي لهذه المكافأة")
                return
            }
            store.addPoints(r.kidId, -r.points)
        }
        store.removeRequest(r.id)
    }

    private fun approve(r: Request) {
        approveSilently(r)
        checkFamilyBonus()
        showParent()
    }

    private fun reject(r: Request) {
        if (r.kind == "chore") store.setTaskState(r.kidId, r.refId, TaskState.TODO)
        store.removeRequest(r.id)
        showParent()
    }

    private fun editChores(kid: Kid) {
        screen("✏️ مهام ${kid.name}", { showParent() }) { c ->
            val list = store.chores(kid.id)
            for (ch in list) {
                val row = hbox().apply {
                    background = rounded(Color.WHITE, dp(16).toFloat())
                    setPadding(dp(10), dp(6), dp(10), dp(6))
                }
                row.addView(text("${ch.emoji}  ${ch.title}  (⭐ ${ch.points})", 19f), lp(0, WRAP, 1f))
                val rm = Rooms.byId(ch.room)
                row.addView(button("${rm.emoji} ${rm.name}", orange, 16f) {
                    val i = Rooms.all.indexOf(rm)
                    ch.room = Rooms.all[(i + 1) % Rooms.all.size].id
                    store.saveChores(kid.id, list)
                    editChores(kid)
                }, lp(WRAP, WRAP, margin = dp(4)))
                row.addView(button("✏️", blue, 18f) { choreDialog(kid, ch) })
                row.addView(button("🗑️", red, 18f) {
                    store.saveChores(kid.id, list.filter { it.id != ch.id })
                    editChores(kid)
                }, lp(WRAP, WRAP, margin = dp(4)))
                c.addView(row, lp(MATCH, WRAP, margin = dp(4)))
            }
            c.addView(text("اضغط زر الغرفة لتغيير مكان المهمة في البيت.", 15f, gray))
            c.addView(button("➕ إضافة مهمة", green) { choreDialog(kid, null) }, lp(MATCH, WRAP, margin = dp(8)))
            c.addView(button("↩️ إرجاع المهام الافتراضية", gray) {
                store.saveChores(kid.id, Family.defaultChores(kid.id))
                editChores(kid)
            }, lp(MATCH, WRAP, margin = dp(4)))
        }
    }

    private fun itemDialog(title: String, emoji: String, name: String, value: Int, valueHint: String, onSave: (String, String, Int) -> Unit) {
        val box = vbox().apply { setPadding(dp(20), dp(8), dp(20), dp(8)) }
        val e = EditText(this).apply { hint = "إيموجي"; setText(emoji); textSize = 26f }
        val n = EditText(this).apply { hint = "الاسم"; setText(name); textSize = 20f }
        val p = numberInput(valueHint, password = false).apply { setText(value.toString()) }
        box.addView(e); box.addView(n); box.addView(p)
        AlertDialog.Builder(this).setTitle(title).setView(box)
            .setPositiveButton("حفظ") { _, _ ->
                val nm = n.text.toString().trim()
                val pts = p.text.toString().toIntOrNull() ?: 0
                if (nm.isEmpty() || pts <= 0) toast("اكتب اسمًا ورقمًا أكبر من صفر")
                else onSave(e.text.toString().trim().ifEmpty { "⭐" }, nm, pts)
            }
            .setNegativeButton("إلغاء", null).show()
    }

    private fun choreDialog(kid: Kid, ch: Chore?) {
        itemDialog(if (ch == null) "مهمة جديدة" else "تعديل مهمة", ch?.emoji ?: "⭐", ch?.title ?: "", ch?.points ?: 10, "النقاط") { e, n, p ->
            val list = store.chores(kid.id)
            if (ch == null) list.add(Chore("${kid.id}_${System.currentTimeMillis()}", e, n, p, store.kidRoom(kid.id)))
            else list.find { it.id == ch.id }?.let { it.emoji = e; it.title = n; it.points = p }
            store.saveChores(kid.id, list)
            editChores(kid)
        }
    }

    private fun editRewards() {
        screen("🎁 المكافآت", { showParent() }) { c ->
            val list = store.rewards()
            for (r in list) {
                val row = hbox().apply {
                    background = rounded(Color.WHITE, dp(16).toFloat())
                    setPadding(dp(10), dp(6), dp(10), dp(6))
                }
                row.addView(text("${r.emoji}  ${r.title}  (⭐ ${r.cost})", 19f), lp(0, WRAP, 1f))
                row.addView(button("✏️", blue, 18f) { rewardDialog(r) })
                row.addView(button("🗑️", red, 18f) {
                    store.saveRewards(list.filter { it.id != r.id })
                    editRewards()
                }, lp(WRAP, WRAP, margin = dp(4)))
                c.addView(row, lp(MATCH, WRAP, margin = dp(4)))
            }
            c.addView(button("➕ إضافة مكافأة", green) { rewardDialog(null) }, lp(MATCH, WRAP, margin = dp(8)))
        }
    }

    private fun rewardDialog(r: Reward?) {
        itemDialog(if (r == null) "مكافأة جديدة" else "تعديل مكافأة", r?.emoji ?: "🎁", r?.title ?: "", r?.cost ?: 50, "السعر بالنجوم") { e, n, p ->
            val list = store.rewards()
            if (r == null) list.add(Reward("r_${System.currentTimeMillis()}", e, n, p))
            else list.find { it.id == r.id }?.let { it.emoji = e; it.title = n; it.cost = p }
            store.saveRewards(list)
            editRewards()
        }
    }

    private fun adjustPoints() {
        val names = Family.kids.map { "${it.name} (⭐ ${store.points(it.id)})" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("اختر الطفل").setItems(names) { _, i ->
            val kid = Family.kids[i]
            val input = EditText(this).apply {
                hint = "مثال: 20 أو -10"
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
                textSize = 22f
            }
            AlertDialog.Builder(this).setTitle("نقاط ${kid.name}").setView(input)
                .setPositiveButton("حفظ") { _, _ ->
                    val d = input.text.toString().toIntOrNull()
                    if (d == null) toast("رقم غير صالح") else {
                        store.addPoints(kid.id, d)
                        showParent()
                    }
                }.setNegativeButton("إلغاء", null).show()
        }.show()
    }

    private fun changePin() {
        val input = numberInput("الرمز الجديد")
        AlertDialog.Builder(this).setTitle("تغيير الرمز السري").setView(input)
            .setPositiveButton("حفظ") { _, _ ->
                val v = input.text.toString()
                if (v.length >= 4) { store.pin = v; toast("تم حفظ الرمز") } else toast("الرمز لازم يكون 4 أرقام أو أكثر")
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun resetAll() {
        AlertDialog.Builder(this).setTitle("تصفير النقاط؟")
            .setMessage("تُحذف نقاط ومستويات الجميع وطلبات الاعتماد. لا يمكن التراجع.")
            .setPositiveButton("تصفير") { _, _ ->
                Family.kids.forEach {
                    store.addPoints(it.id, -store.points(it.id))
                }
                getSharedPreferences("home_heroes", MODE_PRIVATE).edit().apply {
                    Family.kids.forEach { remove("earned_${it.id}") }
                    remove("requests")
                    apply()
                }
                showParent()
            }.setNegativeButton("إلغاء", null).show()
    }

    // ---------------- التحديثات ----------------

    private fun localVersion(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "0"
    } catch (_: Exception) { "0" }

    private fun localBuild(): Long = try {
        val pi = packageManager.getPackageInfo(packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
    } catch (_: Exception) { 0L }

    private fun checkForUpdate(manual: Boolean) {
        Thread {
            val (info, err) = Updater.fetchLatest()
            main.post {
                if (isFinishing) return@post
                if (info == null) {
                    if (manual) toast("تعذّر التحقق: $err")
                    return@post
                }
                val newer = Updater.isNewer(info.version, localVersion())
                if (!newer) {
                    if (manual) toast("الإصدار الحالي هو الأحدث (${localVersion()})")
                    return@post
                }
                if (!manual && store.skippedVersion == info.version) return@post
                if (!manual) notifyUpdate(info)
                AlertDialog.Builder(this)
                    .setTitle("تحديث جديد متوفر")
                    .setMessage("الإصدار ${info.version}\n\nما الجديد:\n${info.notes}")
                    .setNegativeButton("لاحقًا") { _, _ -> store.skippedVersion = info.version }
                    .setPositiveButton("تحديث") { _, _ -> startUpdate(info) }
                    .show()
            }
        }.start()
    }

    /** إشعار في شريط الإشعارات عند توفر إصدار جديد */
    private fun notifyUpdate(info: ReleaseInfo) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
            val nm = getSystemService(android.app.NotificationManager::class.java) ?: return
            nm.createNotificationChannel(android.app.NotificationChannel("updates", "التحديثات", android.app.NotificationManager.IMPORTANCE_DEFAULT))
            val open = android.app.PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
            val n = android.app.Notification.Builder(this, "updates")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("تحديث جديد متوفر")
                .setContentText("أبطال البيت ${info.version}")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            nm.notify(1, n)
        } catch (_: Exception) {
        }
    }

    /** تنزيل التحديث وفتح شاشة التثبيت من داخل التطبيق */
    private fun startUpdate(info: ReleaseInfo) {
        val apkUrl = info.apkUrl
        if (apkUrl == null) {
            toast("لا يحتوي الإصدار على ملف APK")
            return
        }
        if (!packageManager.canRequestPackageInstalls()) {
            toast("اسمح للتطبيق بتثبيت التحديثات، ثم اضغط «تحديث» مرة ثانية")
            try {
                startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            } catch (_: Exception) {
            }
            return
        }
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        val label = text("جاري التنزيل… 0%", 16f)
        val box = vbox().apply { setPadding(dp(20), dp(12), dp(20), dp(12)) }
        box.addView(label)
        box.addView(bar, lp(MATCH, WRAP))
        val dlg = AlertDialog.Builder(this).setTitle("تحديث ${info.version}").setView(box).setCancelable(false).show()
        Thread {
            try {
                val file = Updater.download(this, apkUrl) { p -> main.post { bar.progress = p; label.text = "جاري التنزيل… $p%" } }
                main.post {
                    dlg.dismiss()
                    try { Updater.install(this, file) } catch (e: Exception) { toast("فشل التثبيت: ${e.message}") }
                }
            } catch (e: Exception) {
                main.post {
                    dlg.dismiss()
                    toast("فشل التنزيل: ${e.message ?: e.javaClass.simpleName}")
                }
            }
        }.start()
    }

    // ---------------- الإعدادات ----------------

    private var settingsBack: () -> Unit = { showHome() }

    private fun showSettings(onBack: () -> Unit) {
        settingsBack = onBack
        screen("⚙️ الإعدادات", onBack) { c ->
            c.addView(text("الصوت", 22f, orange, true))
            c.addView(switchRow("🎵 موسيقى الخلفية", sound?.musicOn ?: true) { sound?.musicOn = it }, lp(MATCH, WRAP, margin = dp(4)))
            c.addView(sliderRow("مستوى الموسيقى", Settings.musicVol) { Settings.musicVol = it; sound?.applyVolumes() }, lp(MATCH, WRAP, margin = dp(4)))
            c.addView(switchRow("🔔 أصوات المؤثرات", Settings.fxOn) { Settings.fxOn = it }, lp(MATCH, WRAP, margin = dp(4)))
            c.addView(sliderRow("مستوى المؤثرات", Settings.fxVol) { Settings.fxVol = it; sound?.applyVolumes() }, lp(MATCH, WRAP, margin = dp(4)))
            c.addView(switchRow("🗣️ أصوات الشخصيات", Settings.voiceOn) { Settings.voiceOn = it; if (!it) voice?.stop() }, lp(MATCH, WRAP, margin = dp(4)))
            c.addView(sliderRow("مستوى أصوات الشخصيات", Settings.voiceVol) { Settings.voiceVol = it }, lp(MATCH, WRAP, margin = dp(4)))
            c.addView(text("اللعب", 22f, orange, true).apply { setPadding(0, dp(16), 0, 0) })
            c.addView(switchRow("💬 حوارات تلقائية بين الشخصيات", Settings.autoTalk) { Settings.autoTalk = it }, lp(MATCH, WRAP, margin = dp(4)))
            c.addView(text("التطبيق", 22f, orange, true).apply { setPadding(0, dp(16), 0, 0) })
            c.addView(button("🔄 البحث عن تحديث", blue) { checkForUpdate(manual = true) }, lp(MATCH, WRAP, margin = dp(4)))
            c.addView(button("ℹ️ حول التطبيق", gray) { showAbout() }, lp(MATCH, WRAP, margin = dp(4)))
            c.addView(text("الإصدار ${localVersion()} (البناء ${localBuild()})", 16f, gray).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0) }, lp(MATCH, WRAP))
        }
    }

    private fun switchRow(label: String, on: Boolean, change: (Boolean) -> Unit): View = android.widget.Switch(this).apply {
        text = label
        textSize = 20f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        isChecked = on
        background = rounded(Color.WHITE, dp(18).toFloat())
        setPadding(dp(16), dp(10), dp(16), dp(10))
        setOnCheckedChangeListener { _, v -> Sound.current?.tap(); change(v) }
    }

    private fun sliderRow(label: String, value: Int, change: (Int) -> Unit): View {
        val row = vbox().apply {
            background = rounded(Color.WHITE, dp(18).toFloat())
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        val t = text("$label: $value%", 17f, 0xFF495057.toInt(), true)
        row.addView(t)
        row.addView(android.widget.SeekBar(this).apply {
            max = 100
            progress = value
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) { t.text = "$label: $p%"; change(p) }
                }
                override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(sb: android.widget.SeekBar?) { Sound.current?.tap() }
            })
        }, lp(MATCH, WRAP))
        return row
    }

    // ---------------- حول التطبيق ----------------

    private fun showAbout() {
        screen("ℹ️ حول التطبيق", { showSettings(settingsBack) }) { c ->
            c.addView(text("أبطال البيت", 30f, orange, true).apply { gravity = Gravity.CENTER }, lp(MATCH, WRAP))
            c.addView(text("الإصدار ${localVersion()} (البناء ${localBuild()})", 20f).apply { gravity = Gravity.CENTER }, lp(MATCH, WRAP))
            c.addView(text("لعبة مهام منزلية للأطفال: مهام يومية، نجوم ومستويات، مكافآت، واعتماد من الأهل. تعمل دون إنترنت.", 18f).apply { setPadding(0, dp(12), 0, dp(12)) })
            c.addView(text("المطوّر: za7ef", 18f, bold = true))
            c.addView(text("https://github.com/za7ef", 18f, blue).apply {
                setOnClickListener {
                    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/za7ef"))) } catch (_: Exception) {}
                }
            })
            c.addView(text("شكر وتراخيص", 20f, orange, true).apply { setPadding(0, dp(16), 0, 0) })
            c.addView(text("الرموز التعبيرية من خط النظام في الجهاز. الشخصيات الكرتونية رُسمت خصيصًا لهذا التطبيق. التطبيق مبني بـ Kotlin وAndroid SDK (Apache License 2.0).\n\nCopyright © 2026 za7ef", 16f))
            c.addView(button("📜 سياسة الاستخدام", gray) { showText("سياسة الاستخدام", Texts.TERMS) }, lp(MATCH, WRAP, margin = dp(6)))
            c.addView(button("🔐 سياسة الخصوصية", gray) { showText("سياسة الخصوصية", Texts.PRIVACY) }, lp(MATCH, WRAP, margin = dp(6)))
        }
    }

    private fun showText(title: String, body: String) {
        screen(title, { showAbout() }) { c ->
            c.addView(text(body, 18f).apply { setLineSpacing(0f, 1.3f) })
        }
    }
}
