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
        "kitchen" -> listOf("🍎", "🥤")
        "bedroom" -> listOf("📕", "🎈")
        "bath" -> listOf("🦆", "🧴")
        else -> listOf("⚽", "🪀")
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
            else -> return null
        }
        val t = targets(roomId).firstOrNull { it.id == targetId } ?: return null
        return DragTask(kid, ch, obj, t)
    }

    /** أماكن ظهور الأغراض: نسبة أفقية من العرض، ونسبة من عمق الأرضية */
    private val spots = listOf(
        0.30f to 0.45f, 0.55f to 0.70f, 0.78f to 0.40f, 0.42f to 0.85f,
        0.66f to 0.88f, 0.20f to 0.75f, 0.50f to 0.30f, 0.88f to 0.70f,
    )

    fun spot(i: Int): Pair<Float, Float> = spots[i % spots.size]
}

/**
 * الغرفة التفاعلية: الخلفية مرسومة بنفس مقياس الإحداثيات، والأغراض تنسحب،
 * والشخصية تمشي على الأرضية فقط، تمسك الغرض وتوديه لمكانه.
 */
@SuppressLint("ViewConstructor")
class RoomScene(
    ctx: Context,
    private val roomId: String,
    private val bgRes: Int,
    private val kids: List<Kid>,
    private val tasks: List<DragTask>,
    private val onTaskDone: (DragTask) -> Unit,
    private val say: (View, String) -> Unit,
    private val speak: (String, String?) -> Unit,
    private val onKidTap: (Kid, CharacterView) -> Unit,
) : FrameLayout(ctx) {

    val chars = mutableMapOf<String, CharacterView>()
    private val busy = mutableSetOf<String>()
    private val art = RoomItems.art(roomId)
    private val targetViews = mutableMapOf<String, View>()
    private var built = false
    private var selected: String? = kids.firstOrNull()?.id
    private val bg = ImageView(ctx)

    // تحويل إحداثيات الصورة إلى إحداثيات الشاشة (عرض كامل، والصورة ملتصقة بالأسفل)
    private var scale = 1f
    private var offY = 0f
    private var floorY = 0f

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
    private fun sx(fx: Float) = fx * art.iw * scale
    private fun sy(fy: Float) = offY + fy * art.ih * scale

    /** كل الأبناء بإحداثيات من اليسار للأعلى حتى لا تنعكس الحركة في واجهة عربية */
    private fun lp(w: Int, h: Int) = LayoutParams(w, h, Gravity.TOP or Gravity.LEFT)

    init {
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        setBackgroundColor(art.wall)
        clipChildren = false
        bg.setImageResource(bgRes)
        bg.scaleType = ImageView.ScaleType.MATRIX
        addView(bg, lp(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (w <= 0 || h <= 0) return
        scale = w / art.iw
        offY = h - art.ih * scale
        floorY = sy(art.floorTop).coerceIn(h * 0.35f, h * 0.88f)
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

    private fun floorPoint(x: Float, depth: Float): Pair<Float, Float> =
        x.coerceIn(dp(30f), width - dp(30f)) to (floorY + (height - floorY) * depth.coerceIn(0f, 1f))

    private fun clampFloorY(y: Float): Float = y.coerceIn(floorY + dp(4f), height - dp(2f))

    /** مكان وقوف الشخصية أمام المكان (على الأرض دائمًا) */
    private fun standFor(t: Target): Pair<Float, Float> {
        val x = sx(t.fx)
        return if (t.prop) {
            val side = if (x > width / 2f) -1f else 1f
            (x + side * dp(70f)).coerceIn(dp(40f), width - dp(40f)) to clampFloorY(sy(t.fy) + dp(10f))
        } else {
            x.coerceIn(dp(40f), width - dp(40f)) to clampFloorY(floorY + (height - floorY) * 0.3f)
        }
    }

    private fun build() {
        val w = width.toFloat()
        val h = height.toFloat()

        // الأماكن: علامة خفيفة فوق الأثاث المرسوم، أو غرض مرسوم على الأرض
        for (t in art.targets) {
            val box = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            if (t.prop) {
                box.addView(TextView(context).apply { text = t.emoji; textSize = 46f; gravity = Gravity.CENTER })
            } else {
                box.addView(View(context).apply {
                    background = rounded(0x33FFFFFF, dp(30f), dp(3f).toInt(), 0xCCFFFFFF.toInt())
                }, LinearLayout.LayoutParams(dp(60f).toInt(), dp(60f).toInt()))
            }
            box.addView(TextView(context).apply {
                text = t.name; textSize = 13f; setTextColor(0xFF5C3D2E.toInt()); gravity = Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                background = rounded(0xE6FFFFFF.toInt(), dp(12f))
                setPadding(dp(8f).toInt(), dp(1f).toInt(), dp(8f).toInt(), dp(2f).toInt())
            })
            box.setOnClickListener { speak(t.name, null) }
            addView(box, lp(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            targetViews[t.id] = box
            box.post {
                box.x = (sx(t.fx) - box.width / 2f).coerceIn(0f, w - box.width)
                val cy = if (t.prop) sy(t.fy) - box.height * 0.6f else sy(t.fy) - dp(30f)
                box.y = cy.coerceIn(0f, h - box.height)
            }
        }

        // الأغراض على الأرضية: المهام أولًا ثم أغراض اللعب
        var i = 0
        for (dt in tasks) addObject(dt.obj, RoomItems.spot(i++), dt)
        for (d in RoomItems.decor(roomId)) addObject(d, RoomItems.spot(i++), null)

        // الشخصيات على الأرضية
        val n = kids.size
        kids.forEachIndexed { idx, kid ->
            val ch = h * when (kid.id) { "ayan" -> 0.30f; "layan" -> 0.36f; "rayan" -> 0.41f; else -> 0.45f }
            val cw = ch * 0.45f
            val v = CharacterView(context, kid)
            addView(v, lp(cw.toInt(), ch.toInt()))
            val fx = (idx + 1f) / (n + 1f)
            v.x = fx * w - cw / 2f
            v.y = h - dp(4f) - ch
            v.setOnClickListener {
                Sound.current?.tap()
                selected = kid.id
                onKidTap(kid, v)
            }
            chars[kid.id] = v
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addObject(emoji: String, spot: Pair<Float, Float>, task: DragTask?) {
        val v = TextView(context).apply {
            text = emoji
            textSize = if (task != null) 44f else 36f
            gravity = Gravity.CENTER
            if (task != null) {
                background = rounded(0x55FFFFFF, dp(40f), dp(3f).toInt(), task.kid.color)
                setPadding(dp(6f).toInt(), 0, dp(6f).toInt(), 0)
            }
        }
        addView(v, lp(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        v.post {
            val (px, py) = floorPoint(spot.first * width, spot.second)
            v.x = px - v.width / 2f
            v.y = py - v.height
            if (task != null) wiggle(v)
        }

        var ghost: TextView? = null
        var dx = 0f
        var dy = 0f
        v.setOnTouchListener { _, ev ->
            // إحداثيات اللمس داخل المشهد = مكان الغرض + مكان اللمس داخله
            val px = v.x + ev.x
            val py = v.y + ev.y
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (v.tag == "moving") return@setOnTouchListener false
                    parent?.requestDisallowInterceptTouchEvent(true)
                    dx = ev.x
                    dy = ev.y
                    val g = TextView(context).apply {
                        text = emoji; textSize = v.textSize / resources.displayMetrics.scaledDensity * 1.15f
                        alpha = 0.8f
                    }
                    addView(g, lp(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
                    g.x = v.x; g.y = v.y
                    ghost = g
                    Sound.current?.tap()
                    if (task != null) speak(if (task.kid.toddler) task.target.name else "ودّها ${task.target.name}", null)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    ghost?.let {
                        it.x = px - dx
                        it.y = py - dy
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val g = ghost ?: return@setOnTouchListener true
                    ghost = null
                    removeView(g)
                    if (ev.actionMasked == MotionEvent.ACTION_CANCEL) return@setOnTouchListener true
                    if (hypot(ev.x - dx, ev.y - dy) < dp(24f)) return@setOnTouchListener true
                    onDrop(v, emoji, task, px, py)
                    true
                }
                else -> false
            }
        }
    }

    private fun targetAt(x: Float, y: Float): Target? {
        val r = dp(70f)
        var best: Target? = null
        var bestD = Float.MAX_VALUE
        for (t in art.targets) {
            val tv = targetViews[t.id] ?: continue
            val cx = tv.x + tv.width / 2f
            val cy = tv.y + tv.height / 2f
            val d = hypot(x - cx, y - cy)
            if (d < r + maxOf(tv.width, tv.height) / 2f && d < bestD) { best = t; bestD = d }
        }
        return best
    }

    private fun onDrop(obj: TextView, emoji: String, task: DragTask?, x: Float, y: Float) {
        val hit = targetAt(x, y)
        val kidId = task?.kid?.id ?: selected ?: kids.firstOrNull()?.id
        val cv = kidId?.let { chars[it] }
        if (cv == null) {
            speak("نادِ أحد أول", null)
            return
        }
        if (busy.contains(cv.kid.id)) {
            say(cv, "لحظة! 🙂")
            speak("لحظة! 🙂", cv.kid.id)
            return
        }
        val correct = task != null && hit != null && hit.id == task.target.id
        val (destX, destY) = if (hit != null) standFor(hit) else (x.coerceIn(dp(30f), width - dp(30f)) to clampFloorY(y))
        obj.tag = "moving"
        busy.add(cv.kid.id)
        cv.bringToFront()
        // ١) يمشي للغرض على الأرض
        walkTo(cv, obj.x + obj.width / 2f, obj.y + obj.height) {
            // ٢) ينحني ويمسكه
            pick(cv) {
                cv.carrying = emoji
                Sound.current?.pickUp()
                obj.visibility = View.INVISIBLE
                // ٣) يمشي على الأرض إلى المكان
                walkTo(cv, destX, destY) {
                    pick(cv) {
                        cv.carrying = null
                        busy.remove(cv.kid.id)
                        val chestX = cv.x + cv.width / 2f - obj.width / 2f
                        val chestY = cv.y + cv.height * 0.45f - obj.height / 2f
                        obj.x = chestX
                        obj.y = chestY
                        obj.visibility = View.VISIBLE
                        obj.bringToFront()
                        if (hit != null) {
                            val tv = targetViews[hit.id]
                            val tx = (tv?.let { it.x + it.width / 2f } ?: sx(hit.fx)) - obj.width / 2f
                            val ty = (tv?.let { it.y + it.height / 2f } ?: sy(hit.fy)) - obj.height / 2f
                            // يرمي/يحط الغرض في المكان
                            obj.animate().x(tx).y(ty).setDuration(320).withEndAction {
                                if (task == null || correct) {
                                    obj.animate().scaleX(0.2f).scaleY(0.2f).alpha(0f).setDuration(250).withEndAction {
                                        removeView(obj)
                                    }.start()
                                    tv?.animate()?.scaleX(1.15f)?.scaleY(1.15f)?.setDuration(150)?.withEndAction {
                                        tv.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                                    }?.start()
                                } else {
                                    // مكان غلط: يطيح على الأرض جنب الشخصية
                                    obj.animate().x(cv.x + cv.width - obj.width / 2f)
                                        .y(clampFloorY(cv.y + cv.height) - obj.height).setDuration(350)
                                        .withEndAction { obj.tag = null }.start()
                                }
                            }.start()
                        } else {
                            obj.animate().x(destX - obj.width / 2f).y(destY - obj.height).setDuration(300)
                                .withEndAction { obj.tag = null }.start()
                        }
                        when {
                            correct -> onTaskDone(task!!)
                            task != null -> {
                                Sound.current?.wrong()
                                val msg = "مو هنا! مكانه ${task.target.emoji} ${task.target.name}"
                                say(cv, msg)
                                speak(if (cv.kid.toddler) task.target.name else msg, cv.kid.id)
                            }
                        }
                    }
                }
            }
        }
    }

    /** المشي على الأرضية فقط: القدمان لا تطلعان فوق خط الأرضية */
    private fun walkTo(cv: CharacterView, footX: Float, footY: Float, then: () -> Unit) {
        val tx = (footX - cv.width / 2f).coerceIn(0f, width - cv.width.toFloat())
        val ty = clampFloorY(footY) - cv.height
        val sx0 = cv.x
        val sy0 = cv.y
        val dist = hypot(tx - sx0, ty - sy0)
        if (dist < 4f) return then()
        cv.walking = true
        cv.scaleX = if (tx < sx0) -1f else 1f
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = (dist / (width * 0.35f) * 1000f).toLong().coerceIn(350L, 2600L)
            interpolator = LinearInterpolator()
            addUpdateListener {
                val f = it.animatedValue as Float
                cv.x = sx0 + (tx - sx0) * f
                cv.y = sy0 + (ty - sy0) * f
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    cv.walking = false
                    cv.scaleX = 1f
                    then()
                }
            })
            start()
        }
    }

    /** انحناء قصير لالتقاط الغرض أو وضعه */
    private fun pick(cv: CharacterView, then: () -> Unit) {
        ValueAnimator.ofFloat(0f, 1f, 0f).apply {
            duration = 450
            addUpdateListener { cv.bend = it.animatedValue as Float }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    cv.bend = 0f
                    then()
                }
            })
            start()
        }
    }

    private fun wiggle(v: View) {
        v.animate().rotation(8f).setDuration(500).withEndAction {
            v.animate().rotation(-8f).setDuration(500).withEndAction {
                if (v.isAttachedToWindow && v.tag == null) wiggle(v) else v.rotation = 0f
            }.start()
        }.start()
    }

    @Suppress("unused")
    private val transparent = Color.TRANSPARENT
}
