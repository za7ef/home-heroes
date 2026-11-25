package com.za7ef.homeheroes

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.hypot

/** مكان ثابت في الغرفة تُوضع فيه الأغراض (سلة، صندوق، مغسلة...) */
data class Target(val id: String, val emoji: String, val name: String, val fx: Float, val fy: Float)

/** مهمة سحب: غرض في الغرفة لازم يوصل لمكانه */
data class DragTask(val kid: Kid, val chore: Chore, val obj: String, val target: Target)

object RoomItems {
    fun targets(roomId: String): List<Target> = when (roomId) {
        "bedroom" -> listOf(
            Target("basket", "🧺", "سلة الملابس", 0.12f, 0.18f),
            Target("bed", "🛏️", "السرير", 0.36f, 0.16f),
            Target("desk", "🪑", "المكتب", 0.62f, 0.16f),
            Target("wardrobe", "🚪", "الدولاب", 0.88f, 0.18f),
        )
        "kitchen" -> listOf(
            Target("sink", "🚰", "المغسلة", 0.14f, 0.18f),
            Target("cupboard", "🗄️", "الدولاب", 0.5f, 0.16f),
            Target("bin", "🗑️", "الزبالة", 0.86f, 0.2f),
        )
        "bath" -> listOf(
            Target("sink", "🚰", "المغسلة", 0.2f, 0.18f),
            Target("basket", "🧺", "سلة الملابس", 0.8f, 0.2f),
        )
        else -> listOf(
            Target("toybox", "📦", "صندوق الألعاب", 0.14f, 0.2f),
            Target("shoes", "👞", "رف الجزم", 0.86f, 0.2f),
        )
    }

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

    private val spots = listOf(
        0.30f to 0.62f, 0.58f to 0.72f, 0.80f to 0.60f, 0.42f to 0.86f,
        0.70f to 0.88f, 0.18f to 0.80f, 0.50f to 0.55f, 0.90f to 0.78f,
    )

    fun spot(i: Int): Pair<Float, Float> = spots[i % spots.size]
}

/**
 * أرضية الغرفة التفاعلية: أغراض تنسحب، وأماكن تُوضع فيها،
 * والشخصية تمشي لحالها تمسك الغرض وتوديه.
 */
@SuppressLint("ViewConstructor")
class RoomScene(
    ctx: Context,
    private val roomId: String,
    private val kids: List<Kid>,
    private val tasks: List<DragTask>,
    private val onTaskDone: (DragTask) -> Unit,
    private val say: (View, String) -> Unit,
    private val speak: (String) -> Unit,
    private val onKidTap: (Kid, CharacterView) -> Unit,
) : FrameLayout(ctx) {

    val chars = mutableMapOf<String, CharacterView>()
    private val busy = mutableSetOf<String>()
    private val targetViews = mutableMapOf<String, View>()
    private var built = false
    private var selected: String? = kids.firstOrNull()?.id

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (!built && w > 0 && h > 0) {
            built = true
            post { build() }
        }
    }

    private fun build() {
        val w = width.toFloat()
        val h = height.toFloat()

        // الأماكن
        for (t in RoomItems.targets(roomId)) {
            val box = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                background = rounded(0xCCFFFFFF.toInt(), dp(18f), dp(2f).toInt(), 0xFF8D5524.toInt())
                setPadding(dp(8f).toInt(), dp(4f).toInt(), dp(8f).toInt(), dp(4f).toInt())
            }
            box.addView(TextView(context).apply { text = t.emoji; textSize = 44f; gravity = Gravity.CENTER })
            box.addView(TextView(context).apply {
                text = t.name; textSize = 14f; setTextColor(0xFF5C3D2E.toInt()); gravity = Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            box.setOnClickListener { speak(t.name) }
            addView(box, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            targetViews[t.id] = box
            box.post {
                box.x = (t.fx * w - box.width / 2f).coerceIn(0f, w - box.width)
                box.y = (t.fy * h - box.height / 2f).coerceIn(0f, h - box.height)
            }
        }

        // الأغراض: مهام أولًا ثم أغراض اللعب
        var i = 0
        for (dt in tasks) addObject(dt.obj, RoomItems.spot(i++), dt)
        for (d in RoomItems.decor(roomId)) addObject(d, RoomItems.spot(i++), null)

        // الشخصيات
        val n = kids.size
        kids.forEachIndexed { idx, kid ->
            val ch = h * when (kid.id) { "ayan" -> 0.42f; "layan" -> 0.52f; "rayan" -> 0.58f; else -> 0.64f }
            val cw = ch * 0.62f
            val v = CharacterView(context, kid)
            addView(v, LayoutParams(cw.toInt(), ch.toInt()))
            val fx = (idx + 1f) / (n + 1f)
            v.x = fx * w - cw / 2f
            v.y = h - ch
            v.setOnClickListener {
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
            textSize = if (task != null) 52f else 42f
            gravity = Gravity.CENTER
            if (task != null) {
                background = rounded(0x55FFFFFF, dp(40f), dp(3f).toInt(), task.kid.color)
                setPadding(dp(6f).toInt(), 0, dp(6f).toInt(), 0)
            }
        }
        addView(v, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        v.post {
            v.x = spot.first * width - v.width / 2f
            v.y = spot.second * height - v.height / 2f
            if (task != null) wiggle(v)
        }

        var ghost: TextView? = null
        var dx = 0f
        var dy = 0f
        v.setOnTouchListener { _, ev ->
            val loc = IntArray(2)
            getLocationOnScreen(loc)
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (v.tag == "moving") return@setOnTouchListener false
                    dx = ev.rawX - loc[0] - v.x
                    dy = ev.rawY - loc[1] - v.y
                    val g = TextView(context).apply {
                        text = emoji; textSize = v.textSize / resources.displayMetrics.scaledDensity * 1.15f
                        alpha = 0.75f
                    }
                    addView(g, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
                    g.x = v.x; g.y = v.y
                    ghost = g
                    if (task != null) speak(if (task.kid.toddler) task.target.name else "ودّها ${task.target.name}")
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    ghost?.let {
                        it.x = ev.rawX - loc[0] - dx
                        it.y = ev.rawY - loc[1] - dy
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val g = ghost ?: return@setOnTouchListener true
                    ghost = null
                    val gx = g.x + g.width / 2f
                    val gy = g.y + g.height / 2f
                    removeView(g)
                    if (ev.actionMasked == MotionEvent.ACTION_CANCEL) return@setOnTouchListener true
                    if (hypot(gx - (v.x + v.width / 2f), gy - (v.y + v.height / 2f)) < dp(30f)) return@setOnTouchListener true
                    onDrop(v, emoji, task, gx, gy)
                    true
                }
                else -> false
            }
        }
    }

    private fun targetAt(x: Float, y: Float): Pair<String, View>? {
        val pad = dp(24f)
        for ((id, tv) in targetViews) {
            if (x >= tv.x - pad && x <= tv.x + tv.width + pad && y >= tv.y - pad && y <= tv.y + tv.height + pad) return id to tv
        }
        return null
    }

    private fun onDrop(obj: TextView, emoji: String, task: DragTask?, x: Float, y: Float) {
        val hit = targetAt(x, y)
        val kidId = task?.kid?.id ?: selected ?: kids.firstOrNull()?.id
        val cv = kidId?.let { chars[it] }
        if (cv == null) {
            speak("نادِ أحد أول")
            return
        }
        if (busy.contains(cv.kid.id)) {
            say(cv, "لحظة! 🙂")
            return
        }
        val correct = task != null && hit != null && hit.first == task.target.id
        val destX: Float
        val destY: Float
        if (hit != null) {
            destX = hit.second.x + hit.second.width / 2f
            destY = hit.second.y + hit.second.height
        } else {
            destX = x; destY = y
        }
        obj.tag = "moving"
        busy.add(cv.kid.id)
        cv.bringToFront()
        // ١) يمشي للغرض
        walkTo(cv, obj.x + obj.width / 2f, obj.y + obj.height) {
            // ٢) ينحني ويمسكه
            pick(cv) {
                cv.carrying = emoji
                obj.visibility = View.INVISIBLE
                // ٣) يمشي للمكان
                walkTo(cv, destX, destY) {
                    pick(cv) {
                        cv.carrying = null
                        busy.remove(cv.kid.id)
                        obj.tag = null
                        if (hit != null) {
                            // يدخل الغرض في المكان
                            obj.x = hit.second.x + hit.second.width / 2f - obj.width / 2f
                            obj.y = hit.second.y + hit.second.height / 2f - obj.height / 2f
                            obj.visibility = View.VISIBLE
                            obj.animate().scaleX(0.2f).scaleY(0.2f).alpha(0f).setDuration(350).withEndAction {
                                if (task == null || correct) {
                                    removeView(obj)
                                } else {
                                    obj.scaleX = 1f; obj.scaleY = 1f; obj.alpha = 1f
                                    obj.x = (destX - obj.width / 2f).coerceIn(0f, width - obj.width.toFloat())
                                    obj.y = (destY + dp(10f)).coerceIn(0f, height - obj.height.toFloat())
                                }
                            }.start()
                            hit.second.animate().scaleX(1.15f).scaleY(1.15f).setDuration(150).withEndAction {
                                hit.second.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                            }.start()
                        } else {
                            obj.x = (destX - obj.width / 2f).coerceIn(0f, width - obj.width.toFloat())
                            obj.y = (destY - obj.height / 2f).coerceIn(0f, height - obj.height.toFloat())
                            obj.visibility = View.VISIBLE
                        }
                        when {
                            correct -> onTaskDone(task!!)
                            task != null -> {
                                val msg = "مو هنا! مكانه ${task.target.emoji} ${task.target.name}"
                                say(cv, msg)
                                speak(if (cv.kid.toddler) task.target.name else msg)
                            }
                        }
                    }
                }
            }
        }
    }

    /** المشي: حركة الأرجل والأيدي مرسومة داخل الشخصية، وهنا ينتقل مكانها */
    private fun walkTo(cv: CharacterView, footX: Float, footY: Float, then: () -> Unit) {
        val tx = (footX - cv.width / 2f).coerceIn(0f, width - cv.width.toFloat())
        val minY = height * 0.25f - cv.height * 0.2f
        val ty = (footY - cv.height).coerceIn(minY, height - cv.height.toFloat())
        val sx = cv.x
        val sy = cv.y
        val dist = hypot(tx - sx, ty - sy)
        if (dist < 4f) return then()
        cv.walking = true
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = (dist / (width * 0.35f) * 1000f).toLong().coerceIn(350L, 2600L)
            interpolator = LinearInterpolator()
            addUpdateListener {
                val f = it.animatedValue as Float
                cv.x = sx + (tx - sx) * f
                cv.y = sy + (ty - sy) * f
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    cv.walking = false
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

    init {
        setBackgroundColor(Color.TRANSPARENT)
        clipChildren = false
    }
}
