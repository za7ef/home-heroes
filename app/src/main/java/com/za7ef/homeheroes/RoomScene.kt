package com.za7ef.homeheroes

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.hypot

/**
 * مكان تُوضع فيه الأغراض. الإحداثيات نسبة من صورة الغرفة نفسها (لا من الشاشة)
 * حتى يقع المكان فوق قطعة الأثاث المرسومة. prop = true يعني أن المكان غير مرسوم في الصورة
 * فيُرسم كغرض على الأرض (مثل سلة المهملات).
 */
data class Target(
    val id: String, val emoji: String, val name: String,
    val fx: Float, val fy: Float, val prop: Boolean = false,
)

/** وصف صورة الغرفة: مقاسها، وبداية الأرضية فيها، ولون الجدار لملء أي فراغ أعلاها */
data class RoomArt(val iw: Float, val ih: Float, val floorTop: Float, val wall: Int, val targets: List<Target>)

/** مهمة سحب: غرض في الغرفة لازم يوصل لمكانه */
data class DragTask(val kid: Kid, val chore: Chore, val obj: String, val target: Target)

object RoomItems {
    fun art(roomId: String): RoomArt = when (roomId) {
        "bedroom" -> RoomArt(1280f, 714f, 0.74f, 0xFFF2B33D.toInt(), listOf(
            Target("bed", "🛏️", "السرير", 0.12f, 0.60f),
            Target("desk", "🪑", "المكتب", 0.38f, 0.50f),
            Target("wardrobe", "🚪", "الدولاب", 0.58f, 0.52f),
            Target("basket", "🧺", "سلة الملابس", 0.71f, 0.62f),
        ))
        "kitchen" -> RoomArt(1367f, 784f, 0.85f, 0xFFF6B73C.toInt(), listOf(
            Target("sink", "🚰", "المغسلة", 0.64f, 0.50f),
            Target("cupboard", "🗄️", "الدولاب", 0.80f, 0.66f),
            Target("bin", "🗑️", "الزبالة", 0.93f, 0.90f, prop = true),
        ))
        "bath" -> RoomArt(1280f, 714f, 0.80f, 0xFFF9D27A.toInt(), listOf(
            Target("sink", "🚰", "المغسلة", 0.84f, 0.52f),
            Target("basket", "🧺", "سلة الملابس", 0.40f, 0.90f, prop = true),
        ))
        else -> RoomArt(1280f, 484f, 0.66f, 0xFFF2C46B.toInt(), listOf(
            Target("toybox", "📦", "صندوق الألعاب", 0.16f, 0.80f),
            Target("shoes", "👞", "رف الجزم", 0.87f, 0.69f),
        ))
    }

    fun targets(roomId: String): List<Target> = art(roomId).targets

    /** أغراض للعب فقط (بدون مهمة) يقدر الطفل يسحبها لأي مكان */
    fun decor(roomId: String): List<String> = when (roomId) {
        "kitchen" -> listOf("🍎", "🍌", "🥄", "🧃")
        "bedroom" -> listOf("📕", "🎈", "🧩", "🖍️")
        "bath" -> listOf("🦆", "🧽", "🪮", "🫧")
        else -> listOf("🪀", "🚗", "🎲", "🪁")
    }

    /** يحوّل المهمة لغرض وهدف لو كانت مهمة لها غرض ملموس في هذه الغرفة */
    fun dragFor(kid: Kid, ch: Chore, roomId: String): DragTask? {
        val (obj, targetId) = when (ch.emoji) {
            "👕" -> "👕" to "basket"
            "🧸" -> "🧸" to "toybox"
            "🧺" -> "👚" to "wardrobe"
            "👟" -> "👟" to "shoes"
            "🍽️" -> "🍽️" to "sink"
            "🗑️" -> "🛍️" to "bin"
            "🛒" -> "🥫" to "cupboard"
            "🎒" -> "🎒" to "desk"
            "🛏️" -> "🧣" to "bed"
            "🪥" -> "🪥" to "sink"
            "🧼" -> "🧼" to "sink"
            "🧹" -> "🧦" to "basket"
            "🚿" -> "🧽" to "sink"
            "🧦" -> "🧦" to "basket"
            "⚽" -> "⚽" to "toybox"
            "🥤" -> "🥤" to "sink"
            "🧴" -> "🧴" to "sink"
            "📘" -> "📘" to "desk"
            else -> return null
        }
        val t = targets(roomId).firstOrNull { it.id == targetId } ?: return null
        return DragTask(kid, ch, obj, t)
    }

    /** أماكن ظهور الأغراض: نسبة أفقية من العرض، ونسبة من عمق الأرضية */
    private val spots = listOf(
        0.30f to 0.45f, 0.55f to 0.70f, 0.78f to 0.40f, 0.42f to 0.85f,
        0.66f to 0.88f, 0.20f to 0.75f, 0.50f to 0.30f, 0.88f to 0.70f,
        0.12f to 0.40f, 0.36f to 0.62f, 0.60f to 0.50f, 0.82f to 0.92f, 0.26f to 0.95f, 0.72f to 0.62f,
    )

    fun spot(i: Int): Pair<Float, Float> = spots[i % spots.size]
}

/**
 * الغرفة التفاعلية على طريقة ألعاب الحيوان الأليف: الشخصيات واقفة على الأرضية،
 * وأدوات الغرفة في شريط تحت، تُسحب الأداة على الشخصية فتستخدمها (أكل، تنظيف، نوم، لعب)
 * ويرتفع مؤشر احتياجها. فوق رأس كل شخصية أيقونة احتياجها لما يكون ناقص.
 */
@SuppressLint("ViewConstructor")
class RoomScene(
    ctx: Context,
    private val roomId: String,
    private val bgRes: Int,
    private val kids: List<Kid>,
    private val store: Store,
    private val say: (View, String) -> Unit,
    private val speak: (String, String?) -> Unit,
    private val onKidTap: (Kid, CharacterView) -> Unit,
) : FrameLayout(ctx) {

    val chars = mutableMapOf<String, CharacterView>()
    private val needIcons = mutableMapOf<String, TextView>()
    private val busy = mutableSetOf<String>()
    private val art = RoomItems.art(roomId)
    private var built = false
    private var selected: String? = kids.firstOrNull()?.id
    private val bg = ImageView(ctx)
    private var meterBox: LinearLayout? = null

    private var scale = 1f
    private var offY = 0f

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    /** كل الأبناء بإحداثيات من اليسار للأعلى حتى لا تنعكس الحركة في واجهة عربية */
    private fun lp(w: Int, h: Int) = LayoutParams(w, h, Gravity.TOP or Gravity.LEFT)

    private val ticker = object : Runnable {
        override fun run() {
            refreshNeeds()
            postDelayed(this, 4000)
        }
    }

    init {
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        setBackgroundColor(art.wall)
        clipChildren = false
        bg.setImageResource(bgRes)
        bg.scaleType = ImageView.ScaleType.MATRIX
        addView(bg, lp(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); postDelayed(ticker, 4000) }
    override fun onDetachedFromWindow() { removeCallbacks(ticker); super.onDetachedFromWindow() }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (w <= 0 || h <= 0) return
        scale = w / art.iw
        offY = h - art.ih * scale
        val d = bg.drawable
        if (d != null && d.intrinsicWidth > 0) {
            val s = w.toFloat() / d.intrinsicWidth
            bg.imageMatrix = Matrix().apply { setScale(s, s); postTranslate(0f, h - d.intrinsicHeight * s) }
        }
        if (!built) {
            built = true
            post { build() }
        }
    }

    private fun build() {
        val w = width.toFloat()
        val h = height.toFloat()
        val trayH = dp(78f)

        // الشخصيات على الأرضية فوق شريط الأدوات
        val n = kids.size
        kids.forEachIndexed { idx, kid ->
            val ch = h * when (kid.id) { "ayan" -> 0.30f; "layan" -> 0.36f; "rayan" -> 0.41f; else -> 0.45f }
            val cw = ch * 0.45f
            val v = CharacterView(context, kid)
            addView(v, lp(cw.toInt(), ch.toInt()))
            val fx = (idx + 1f) / (n + 1f)
            v.x = fx * w - cw / 2f
            v.y = h - trayH - dp(2f) - ch
            v.setOnClickListener {
                Sound.current?.tap()
                selected = kid.id
                refreshMeters()
                bounce(v)
                val low = Needs.lowest(store, kid.id)
                if (low != null) {
                    val line = Needs.needLine(store, kid, low)
                    say(v, line); speak(line, kid.id)
                } else onKidTap(kid, v)
            }
            chars[kid.id] = v

            val icon = TextView(context).apply {
                textSize = 26f; gravity = Gravity.CENTER
                background = rounded(Color.WHITE, dp(24f), dp(3f).toInt(), 0xFFE03131.toInt())
                visibility = View.INVISIBLE
                elevation = dp(4f)
            }
            addView(icon, lp(dp(48f).toInt(), dp(48f).toInt()))
            icon.x = v.x + cw / 2f - dp(24f)
            icon.y = (v.y - dp(52f)).coerceAtLeast(dp(60f))
            icon.setOnClickListener { v.performClick() }
            needIcons[kid.id] = icon
        }

        // شريط الأدوات ومؤشرات الطفل المختار
        val tray = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(0xEEFFFFFF.toInt(), dp(22f), dp(3f).toInt(), 0xFFE8590C.toInt())
            setPadding(dp(10f).toInt(), dp(4f).toInt(), dp(10f).toInt(), dp(4f).toInt())
            elevation = dp(6f)
        }
        val mb = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        meterBox = mb
        tray.addView(mb, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(12f).toInt() })
        for (tool in Needs.tools(roomId)) tray.addView(toolView(tool), LinearLayout.LayoutParams(dp(62f).toInt(), dp(62f).toInt()).apply {
            leftMargin = dp(4f).toInt(); rightMargin = dp(4f).toInt()
        })
        addView(tray, LayoutParams(LayoutParams.WRAP_CONTENT, trayH.toInt(), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(2f).toInt()
        })
        refreshNeeds()
    }

    /** مؤشرات الطفل المختار: أربعة أشرطة صغيرة */
    private fun refreshMeters() {
        val mb = meterBox ?: return
        mb.removeAllViews()
        val kid = kids.firstOrNull { it.id == selected } ?: return
        val grid = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val cols = List(3) { LinearLayout(context).apply { orientation = LinearLayout.VERTICAL } }
        Needs.meters.forEachIndexed { i, m ->
            val v = store.need(kid.id, m.id)
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(context).apply { text = m.emoji; textSize = 15f })
            val track = FrameLayout(context).apply { background = rounded(0xFFDEE2E6.toInt(), dp(5f)) }
            val color = if (v < Needs.LOW) 0xFFE03131.toInt() else if (v < 65f) 0xFFF59F00.toInt() else 0xFF2F9E44.toInt()
            track.addView(View(context).apply { background = rounded(color, dp(5f)) },
                LayoutParams((dp(46f) * (v / 100f)).toInt().coerceAtLeast(dp(4f).toInt()), LayoutParams.MATCH_PARENT, Gravity.LEFT))
            row.addView(track, LinearLayout.LayoutParams(dp(46f).toInt(), dp(10f).toInt()).apply { leftMargin = dp(4f).toInt() })
            cols[i / 2].addView(row, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(1f).toInt(); bottomMargin = dp(1f).toInt(); rightMargin = dp(8f).toInt()
            })
        }
        cols.forEach { grid.addView(it) }
        mb.addView(TextView(context).apply {
            text = kid.name; textSize = 13f; setTextColor(kid.color); setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        mb.addView(grid)
    }

    /** أيقونة الاحتياج فوق رأس كل طفل */
    private fun refreshNeeds() {
        for (kid in kids) {
            val icon = needIcons[kid.id] ?: continue
            val low = Needs.lowest(store, kid.id)
            if (low == null) {
                icon.visibility = View.INVISIBLE
                icon.animate().cancel(); icon.rotation = 0f
            } else {
                icon.text = low.emoji
                if (icon.visibility != View.VISIBLE) { icon.visibility = View.VISIBLE; wiggle(icon) }
            }
        }
        refreshMeters()
    }

    // ---------------- استخدام الأدوات بالإصبع ----------------

    /** تقدّم الاستخدام غير المكتمل لكل طفل وأداة، حتى يكمل من حيث وقف */
    private val partial = mutableMapOf<String, Float>()
    private val bars = mutableMapOf<String, Pair<FrameLayout, View>>()

    /** حالة الإمساك الحالية بالأداة */
    private inner class Grip(val tool: Tool, val ghost: TextView) {
        var kid: CharacterView? = null
        var progress = 0f
        var lastX = 0f
        var lastY = 0f
        var lastT = 0L
        var moved = 0f
        var fxDist = 0f
        var fxTime = 0f
        var lastDy = 0f
        var steps = 0
        var done = false
        var warned = false
        var engaged = false
        var label: TextView? = null
    }

    private var grip: Grip? = null

    @SuppressLint("ClickableViewAccessibility")
    private fun toolView(tool: Tool): View {
        val v = TextView(context).apply {
            text = tool.emoji; textSize = 34f; gravity = Gravity.CENTER
            background = rounded(0xFFFFF4D6.toInt(), dp(31f), dp(2f).toInt(), 0xFFFFC078.toInt())
        }
        var downAt = 0L
        v.setOnTouchListener { _, ev ->
            // إحداثيات اللمس داخل المشهد نفسه (من اليسار)
            val loc = IntArray(2); getLocationOnScreen(loc)
            val px = ev.rawX - loc[0]
            val py = ev.rawY - loc[1]
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    downAt = System.currentTimeMillis()
                    val g = TextView(context).apply { text = tool.emoji; textSize = 54f; elevation = dp(12f); gravity = Gravity.CENTER }
                    addView(g, lp(dp(84f).toInt(), dp(84f).toInt()))
                    val gr = Grip(tool, g)
                    gr.lastX = px; gr.lastY = py; gr.lastT = System.currentTimeMillis()
                    grip = gr
                    placeGhost(gr, px, py)
                    Sound.current?.pickUp()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    grip?.let { onGripMove(it, px, py) }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val gr = grip ?: return@setOnTouchListener true
                    grip = null
                    releaseGrip(gr)
                    if (ev.actionMasked == MotionEvent.ACTION_UP && !gr.done) {
                        val quick = System.currentTimeMillis() - downAt < 350
                        if (gr.moved < dp(20f) && quick) speak(tool.name, null)
                        else if (!gr.engaged) speak("اسحبها على واحد من العيال", null)
                    }
                    true
                }
                else -> false
            }
        }
        return v
    }

    /** الأداة تظهر فوق الإصبع قليلًا حتى يراها الطفل */
    private fun placeGhost(gr: Grip, px: Float, py: Float) {
        gr.ghost.x = px - dp(42f)
        gr.ghost.y = py - dp(100f)
    }

    /** رأس الأداة (النقطة التي تلمس الشخصية) */
    private fun tip(gr: Grip): Pair<Float, Float> = (gr.ghost.x + dp(42f)) to (gr.ghost.y + dp(42f))

    /** هل رأس الأداة على المكان الصحيح من الشخصية؟ */
    private fun zoneHit(cv: CharacterView, tool: Tool, x: Float, y: Float): Boolean {
        return when (tool.spot) {
            "mouth" -> {
                val (mx, my) = cv.mouth()
                val r = maxOf(cv.width * 0.32f, dp(38f))
                hypot(x - (cv.x + mx), y - (cv.y + my)) < r
            }
            "head" -> {
                val (hx, hy) = cv.head()
                x > cv.x - dp(20f) && x < cv.x + cv.width + dp(20f) &&
                    y > cv.y + hy - dp(140f) && y < cv.y + hy + cv.height * 0.18f && hypot(x - (cv.x + hx), 0f) < cv.width * 0.8f
            }
            else -> x > cv.x - dp(16f) && x < cv.x + cv.width + dp(16f) && y > cv.y && y < cv.y + cv.height
        }
    }

    private fun onGripMove(gr: Grip, px: Float, py: Float) {
        val now = System.currentTimeMillis()
        val dx = px - gr.lastX
        val dy = py - gr.lastY
        val dist = hypot(dx, dy)
        val dt = ((now - gr.lastT) / 1000f).coerceIn(0f, 0.12f)
        gr.lastX = px; gr.lastY = py; gr.lastT = now
        gr.moved += dist
        placeGhost(gr, px, py)
        if (gr.done) return
        val tool = gr.tool
        val (tx, ty) = tip(gr)
        val hit = chars.values.firstOrNull { zoneHit(it, tool, tx, ty) }

        // ترك الشخصية السابقة
        val prev = gr.kid
        if (prev != null && prev !== hit) leaveKid(gr, prev)
        if (hit == null) {
            idleGhost(gr, dx)
            return
        }
        if (prev !== hit) {
            // بداية الاستخدام على هذه الشخصية
            gr.kid = hit
            gr.engaged = true
            selected = hit.kid.id
            refreshMeters()
            if (busy.contains(hit.kid.id)) return
            if (store.need(hit.kid.id, tool.meter) >= 95f) {
                if (!gr.warned) {
                    gr.warned = true
                    Sound.current?.wrong()
                    val l = Needs.full(hit.kid)
                    say(hit, l); speak(l, hit.kid.id)
                }
                return
            }
            gr.progress = partial[key(hit, tool)] ?: 0f
            hit.face = Needs.use(tool.kind).face
            showBar(hit, gr.progress)
        }
        if (busy.contains(hit.kid.id) || store.need(hit.kid.id, tool.meter) >= 95f) return

        val use = Needs.use(tool.kind)
        val dDp = dist / resources.displayMetrics.density
        gr.progress += dDp / use.rub + dt / use.hold
        animateTool(gr, hit, dx, dy, dist, dt)
        showBar(hit, gr.progress)
        if (gr.progress >= 1f) completeGrip(gr, hit)
    }

    private fun key(cv: CharacterView, tool: Tool) = cv.kid.id + "|" + tool.emoji

    private fun idleGhost(gr: Grip, dx: Float) {
        if (gr.tool.kind == "car") { if (dx > 1f) gr.ghost.scaleX = -1f else if (dx < -1f) gr.ghost.scaleX = 1f }
        else if (gr.tool.kind != "ball") gr.ghost.rotation = 0f
        gr.label?.let { removeView(it) }; gr.label = null
    }

    private fun leaveKid(gr: Grip, cv: CharacterView) {
        if (!gr.done && gr.progress > 0f && gr.progress < 1f) partial[key(cv, gr.tool)] = gr.progress
        cv.face = null
        hideBar(cv)
        gr.kid = null
        gr.label?.let { removeView(it) }; gr.label = null
        removeDoor(cv)
    }

    private fun releaseGrip(gr: Grip) {
        gr.kid?.let { leaveKid(gr, it) }
        val g = gr.ghost
        g.animate().alpha(0f).scaleX(0.4f).scaleY(0.4f).setDuration(160).withEndAction { removeView(g) }.start()
    }

    private fun completeGrip(gr: Grip, cv: CharacterView) {
        gr.done = true
        partial.remove(key(cv, gr.tool))
        cv.face = null
        hideBar(cv)
        gr.label?.let { removeView(it) }; gr.label = null
        removeDoor(cv)
        when (gr.tool.kind) {
            "toilet" -> Sound.current?.effect("flush")
            "pill", "drink" -> Sound.current?.effect("gulp")
        }
        val g = gr.ghost
        g.animate().alpha(0f).scaleX(0.3f).scaleY(0.3f).setDuration(220).withEndAction { removeView(g) }.start()
        finishCare(cv, gr.tool)
    }

    /** أنيميشن خاص لكل أداة أثناء استخدامها */
    private fun animateTool(gr: Grip, cv: CharacterView, dx: Float, dy: Float, dist: Float, dt: Float) {
        val g = gr.ghost
        val (tx, ty) = tip(gr)
        gr.fxDist += dist
        gr.fxTime += dt
        val (mx0, my0) = cv.mouth()
        val mx = cv.x + mx0
        val my = cv.y + my0
        when (gr.tool.kind) {
            "brush" -> {
                // الفرشاة تميل مع اتجاه الحركة، ورغوة تتجمع عند الفم
                g.rotation = if (dx > 0) 18f else if (dx < 0) -18f else g.rotation
                if (gr.fxDist > dp(26f)) {
                    gr.fxDist = 0f
                    particle("🫧", mx + rnd(-dp(26f), dp(26f)), my + rnd(-dp(14f), dp(14f)), 16f + rnd(0f, 10f), rnd(-dp(10f), dp(10f)), -dp(18f), 900)
                    if ((++gr.steps) % 2 == 0) Sound.current?.effect("brush")
                }
            }
            "eat" -> {
                // قضمات: الأكل يصغر مع كل قضمة وتتطاير فتافيت
                val bites = (gr.progress * 4).toInt()
                if (bites > gr.steps) {
                    gr.steps = bites
                    Sound.current?.effect("bite")
                    val sc = 1f - 0.18f * bites
                    g.scaleX = sc; g.scaleY = sc
                    repeat(3) { particle(gr.tool.emoji, mx + rnd(-dp(16f), dp(16f)), my + dp(6f), 10f, rnd(-dp(40f), dp(40f)), dp(70f), 600) }
                }
                g.rotation = (Math.sin(System.currentTimeMillis() / 90.0) * 8).toFloat()
            }
            "drink" -> {
                g.rotation = -55f
                if (gr.fxTime > 0.55f) {
                    gr.fxTime = 0f
                    Sound.current?.effect("gulp")
                    particle("💧", mx + rnd(-dp(10f), dp(10f)), my, 14f, 0f, dp(30f), 500)
                }
            }
            "pill" -> { g.rotation = -20f }
            "thermo" -> {
                g.rotation = -35f
                val t = 36.0f + 1.0f * gr.progress.coerceAtMost(1f)
                val lab = gr.label ?: TextView(context).apply {
                    textSize = 18f; setTextColor(0xFFE03131.toInt()); setTypeface(typeface, android.graphics.Typeface.BOLD)
                    background = rounded(Color.WHITE, dp(10f), dp(2f).toInt(), 0xFFE03131.toInt())
                    setPadding(dp(8f).toInt(), dp(2f).toInt(), dp(8f).toInt(), dp(2f).toInt())
                    elevation = dp(12f)
                }.also { addView(it, lp(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)); gr.label = it }
                lab.text = String.format(java.util.Locale.US, "%.1f°", t)
                lab.x = tx + dp(30f); lab.y = ty - dp(60f)
            }
            "scrub" -> {
                g.rotation = if (dx > 0) 12f else if (dx < 0) -12f else g.rotation
                if (gr.fxDist > dp(24f)) {
                    gr.fxDist = 0f
                    particle("🫧", tx + rnd(-dp(20f), dp(20f)), ty + rnd(-dp(20f), dp(20f)), 18f + rnd(0f, 12f), rnd(-dp(12f), dp(12f)), -dp(30f), 1000)
                    if ((++gr.steps) % 2 == 0) Sound.current?.effect("bubble")
                }
            }
            "shower" -> {
                g.rotation = 0f
                if (gr.fxTime > 0.09f) {
                    gr.fxTime = 0f
                    particle("💧", tx + rnd(-dp(26f), dp(26f)), ty + dp(20f), 16f, rnd(-dp(6f), dp(6f)), dp(160f), 650)
                    if ((++gr.steps) % 5 == 0) Sound.current?.effect("splash")
                }
            }
            "toilet" -> {
                // باب يغطي الشخصية حتى ينتهي
                val door = doorFor(cv)
                door.alpha = (0.3f + gr.progress).coerceAtMost(1f)
            }
            "sleep" -> {
                g.rotation = (Math.sin(System.currentTimeMillis() / 400.0) * 10).toFloat()
                if (gr.fxTime > 0.5f) {
                    gr.fxTime = 0f
                    val (hx, hy) = cv.head()
                    particle("💤", cv.x + hx + dp(20f), cv.y + hy, 22f, dp(30f), -dp(50f), 1200)
                }
            }
            "hug" -> {
                g.rotation = (Math.sin(System.currentTimeMillis() / 160.0) * 12).toFloat()
                if (gr.fxTime > 0.35f) {
                    gr.fxTime = 0f
                    particle("💕", tx + rnd(-dp(20f), dp(20f)), ty - dp(20f), 20f, rnd(-dp(10f), dp(10f)), -dp(60f), 1000)
                }
            }
            "story" -> {
                // صفحات تتقلب ونجوم خيال
                if (gr.fxTime > 0.5f) {
                    gr.fxTime = 0f
                    g.scaleX = -g.scaleX.let { if (it == 0f) 1f else it }
                    Sound.current?.tap()
                    particle(listOf("⭐", "🌈", "🦄", "🏰").random(), tx + rnd(-dp(30f), dp(30f)), ty - dp(30f), 20f, rnd(-dp(20f), dp(20f)), -dp(50f), 1100)
                }
            }
            "ball" -> {
                // كل ما ينعكس اتجاه الحركة العمودية تنط الكورة
                if (dy != 0f && gr.lastDy != 0f && (dy > 0) != (gr.lastDy > 0) && gr.fxDist > dp(30f)) {
                    gr.fxDist = 0f
                    Sound.current?.effect("boing")
                    particle("✨", tx, ty + dp(30f), 18f, 0f, dp(10f), 400)
                }
                if (dy != 0f) gr.lastDy = dy
                g.rotation += dist * 0.8f
            }
            "balloon" -> {
                g.rotation = (Math.sin(System.currentTimeMillis() / 200.0) * 15).toFloat()
                if (gr.fxDist > dp(60f)) {
                    gr.fxDist = 0f
                    Sound.current?.effect("bubble")
                    particle(listOf("🎊", "🎉", "✨").random(), tx + rnd(-dp(30f), dp(30f)), ty - dp(30f), 18f, rnd(-dp(30f), dp(30f)), -dp(50f), 900)
                }
            }
            "puzzle" -> {
                val pieces = (gr.progress * 5).toInt()
                if (pieces > gr.steps) {
                    gr.steps = pieces
                    Sound.current?.tap()
                    val (cx, cy) = cv.chest()
                    particle("🧩", tx, ty, 22f, cv.x + cx - tx, cv.y + cy - ty, 500)
                }
                g.rotation = if (dx > 0) 10f else if (dx < 0) -10f else g.rotation
            }
            "car" -> {
                // السيارة تلتفت لجهة الحركة ويطلع غبار وراها
                if (dx > 1f) g.scaleX = -1f else if (dx < -1f) g.scaleX = 1f
                if (gr.fxDist > dp(50f)) {
                    gr.fxDist = 0f
                    if ((++gr.steps) % 2 == 0) Sound.current?.effect("zoom")
                    particle("💨", tx - (if (g.scaleX < 0) dp(40f) else -dp(40f)), ty + dp(10f), 18f, if (g.scaleX < 0) -dp(30f) else dp(30f), 0f, 600)
                }
            }
        }
    }

    private fun rnd(a: Float, b: Float): Float = a + (b - a) * kotlin.random.Random.nextFloat()

    /** جزيء صغير يطير ويختفي */
    private fun particle(e: String, x: Float, y: Float, size: Float, dx: Float, dy: Float, ms: Long) {
        val t = TextView(context).apply { text = e; textSize = size; elevation = dp(11f) }
        addView(t, lp(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        t.x = x - dp(size * 0.6f); t.y = y - dp(size * 0.6f)
        t.animate().translationXBy(dx).translationYBy(dy).alpha(0f).setDuration(ms).withEndAction { removeView(t) }.start()
    }

    private val doors = mutableMapOf<String, TextView>()

    private fun doorFor(cv: CharacterView): TextView = doors[cv.kid.id] ?: TextView(context).apply {
        text = "🚪"; gravity = Gravity.CENTER; elevation = dp(9f); alpha = 0f
        textSize = (cv.height / resources.displayMetrics.scaledDensity) * 0.7f
    }.also {
        addView(it, lp(cv.width + dp(30f).toInt(), cv.height))
        it.x = cv.x - dp(15f); it.y = cv.y
        doors[cv.kid.id] = it
    }

    private fun removeDoor(cv: CharacterView) {
        doors.remove(cv.kid.id)?.let { d -> d.animate().alpha(0f).setDuration(200).withEndAction { removeView(d) }.start() }
    }

    /** شريط تقدم صغير فوق رأس الشخصية أثناء الاستخدام */
    private fun showBar(cv: CharacterView, p: Float) {
        val (track, fillV) = bars[cv.kid.id] ?: run {
            val tr = FrameLayout(context).apply {
                background = rounded(Color.WHITE, dp(8f), dp(2f).toInt(), 0xFF2F9E44.toInt()); elevation = dp(12f)
            }
            val f = View(context).apply { background = rounded(0xFF51CF66.toInt(), dp(6f)) }
            tr.addView(f, LayoutParams(0, LayoutParams.MATCH_PARENT, Gravity.LEFT))
            addView(tr, lp(dp(84f).toInt(), dp(14f).toInt()))
            (tr to f).also { bars[cv.kid.id] = it }
        }
        track.visibility = View.VISIBLE
        track.x = cv.x + cv.width / 2f - dp(42f)
        track.y = (cv.y - dp(18f)).coerceAtLeast(dp(4f))
        val lpF = fillV.layoutParams
        lpF.width = (dp(80f) * p.coerceIn(0f, 1f)).toInt()
        fillV.layoutParams = lpF
    }

    private fun hideBar(cv: CharacterView) { bars[cv.kid.id]?.first?.visibility = View.GONE }

    private fun finishCare(cv: CharacterView, tool: Tool) {
        val kid = cv.kid
        store.addNeed(kid.id, tool.meter, tool.amount)
        busy.remove(kid.id)
        Sound.current?.success()
        val line = Needs.react(kid, tool.meter)
        say(cv, line); speak(line, kid.id)
        // أثر فوق الرأس
        repeat(3) { i ->
            val e = TextView(context).apply { text = Needs.effect(tool.meter); textSize = 26f; elevation = dp(8f) }
            addView(e, lp(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            e.x = cv.x + cv.width * (0.25f + 0.25f * i)
            e.y = cv.y + cv.height * 0.1f
            e.animate().translationYBy(-dp(70f)).alpha(0f).setStartDelay(i * 120L).setDuration(900).withEndAction { removeView(e) }.start()
        }
        refreshNeeds()
    }

    private fun bounce(v: View) {
        v.pivotX = v.width / 2f; v.pivotY = v.height.toFloat()
        v.animate().scaleY(1.06f).scaleX(0.97f).setDuration(120).withEndAction {
            v.animate().scaleY(1f).scaleX(1f).setDuration(160).start()
        }.start()
    }

    private fun wiggle(v: View) {
        v.animate().rotation(10f).setDuration(450).withEndAction {
            v.animate().rotation(-10f).setDuration(450).withEndAction {
                if (v.isAttachedToWindow && v.visibility == View.VISIBLE) wiggle(v) else v.rotation = 0f
            }.start()
        }.start()
    }
}
