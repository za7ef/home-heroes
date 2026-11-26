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

    @SuppressLint("ClickableViewAccessibility")
    private fun toolView(tool: Tool): View {
        val v = TextView(context).apply {
            text = tool.emoji; textSize = 34f; gravity = Gravity.CENTER
            background = rounded(0xFFFFF4D6.toInt(), dp(31f), dp(2f).toInt(), 0xFFFFC078.toInt())
        }
        var ghost: TextView? = null
        var sx0 = 0f
        var sy0 = 0f
        v.setOnTouchListener { _, ev ->
            // إحداثيات اللمس داخل المشهد نفسه
            val loc = IntArray(2); getLocationOnScreen(loc)
            val px = ev.rawX - loc[0]
            val py = ev.rawY - loc[1]
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    sx0 = px; sy0 = py
                    val g = TextView(context).apply { text = tool.emoji; textSize = 46f; alpha = 0.9f; elevation = dp(10f) }
                    addView(g, lp(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
                    g.x = px - dp(28f); g.y = py - dp(70f)
                    ghost = g
                    Sound.current?.tap()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    ghost?.let { it.x = px - dp(28f); it.y = py - dp(70f) }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val g = ghost ?: return@setOnTouchListener true
                    ghost = null
                    removeView(g)
                    if (ev.actionMasked == MotionEvent.ACTION_CANCEL) return@setOnTouchListener true
                    if (hypot(px - sx0, py - sy0) < dp(20f)) {
                        speak(tool.name, null)
                        return@setOnTouchListener true
                    }
                    val hit = kidAt(px, py - dp(42f))
                    if (hit != null) care(hit, tool) else speak("اسحبها على واحد من العيال", null)
                    true
                }
                else -> false
            }
        }
        return v
    }

    private fun kidAt(x: Float, y: Float): CharacterView? {
        val pad = dp(24f)
        return chars.values.filter { x >= it.x - pad && x <= it.x + it.width + pad && y >= it.y - pad && y <= it.y + it.height + pad }
            .minByOrNull { hypot(x - (it.x + it.width / 2f), y - (it.y + it.height / 2f)) }
    }

    /** استخدام الأداة على الشخصية: الأداة تتحرك عند الفم أو الرأس أو اليد، ثم يرتفع المؤشر */
    private fun care(cv: CharacterView, tool: Tool) {
        val kid = cv.kid
        if (busy.contains(kid.id)) return
        selected = kid.id
        if (store.need(kid.id, tool.meter) >= 95f) {
            Sound.current?.wrong()
            val l = Needs.full(kid)
            say(cv, l); speak(l, kid.id)
            refreshMeters()
            return
        }
        busy.add(kid.id)
        val t = TextView(context).apply { text = tool.emoji; textSize = 40f; elevation = dp(10f) }
        addView(t, lp(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        val fy = when (tool.spot) { "mouth" -> 0.17f; "head" -> 0.02f; else -> 0.45f }
        t.post {
            t.x = cv.x + cv.width * (if (tool.spot == "hand") 0.85f else 0.5f) - t.width / 2f
            t.y = cv.y + cv.height * fy - t.height / 2f
            t.animate().rotation(25f).translationXBy(dp(8f)).setDuration(220).withEndAction {
                t.animate().rotation(-25f).translationXBy(-dp(16f)).setDuration(220).withEndAction {
                    t.animate().rotation(25f).translationXBy(dp(16f)).setDuration(220).withEndAction {
                        t.animate().rotation(0f).alpha(0f).scaleX(0.3f).scaleY(0.3f).setDuration(250).withEndAction {
                            removeView(t)
                            finishCare(cv, tool)
                        }.start()
                    }.start()
                }.start()
            }.start()
        }
        bounce(cv)
    }

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
