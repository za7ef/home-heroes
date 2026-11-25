package com.za7ef.homeheroes

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** مظهر ملابس كل شخصية، مأخوذ من صورها المرجعية */
data class Outfit(
    val shirt: Int,
    val shirtTrim: Int,
    val pants: Int,
    val shortSleeves: Boolean,
    val shorts: Boolean,
    val stripes: Boolean = false,
    val hoodie: Boolean = false,
)

object Outfits {
    fun of(kidId: String): Outfit = when (kidId) {
        "abdulrahman" -> Outfit(0xFFD9342B.toInt(), 0xFFA8231C.toInt(), 0xFF2F3E5C.toInt(), shortSleeves = true, shorts = false)
        "rayan" -> Outfit(0xFFF7F3EA.toInt(), 0xFFCFC6B4.toInt(), 0xFF6B5B4B.toInt(), shortSleeves = true, shorts = false)
        "layan" -> Outfit(0xFF4F8FD0.toInt(), 0xFFFFFFFF.toInt(), 0xFF8E6BB8.toInt(), shortSleeves = false, shorts = false, hoodie = true)
        else -> Outfit(0xFFDCEBFA.toInt(), 0xFF5C8FD6.toInt(), 0xFF3B6FB6.toInt(), shortSleeves = true, shorts = true, stripes = true)
    }
}

/**
 * شخصية كرتونية مرسومة: الوجه من صورة الطفل، والجسم مرسوم بالكود
 * حتى تتحرك الأرجل والأيدي أثناء المشي وحمل الأغراض.
 */
class CharacterView(ctx: Context, val kid: Kid) : View(ctx) {

    var walking = false
        set(v) { field = v; invalidate() }

    /** الغرض المحمول (إيموجي) أو null */
    var carrying: String? = null
        set(v) { field = v; invalidate() }

    /** لحظة الانحناء لالتقاط غرض أو وضعه (0..1) */
    var bend = 0f
        set(v) { field = v; invalidate() }

    private val face: Bitmap? = try { BitmapFactory.decodeResource(resources, kid.imageRes) } catch (_: Exception) { null }
    private val outfit = Outfits.of(kid.id)
    private val skin = 0xFFE7B48A.toInt()
    private val line = 0xFF3B2A20.toInt()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = line }
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val rect = RectF()
    private var phase = 0f

    private val ticker = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 700
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!ticker.isStarted) ticker.start()
    }

    override fun onDetachedFromWindow() {
        ticker.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val t = (phase * 2 * Math.PI).toFloat()
        val toddler = kid.toddler
        val sw = maxOf(2f, h * 0.012f)
        stroke.strokeWidth = sw

        val headD = minOf(w * 0.92f, h * if (toddler) 0.5f else 0.42f)
        val legLen = h * if (toddler) 0.17f else 0.23f
        val footY = h - sw * 2
        val bob = if (walking) -abs(sin(t)) * h * 0.035f else sin(t) * h * 0.006f
        val hipY = footY - legLen + bob
        val torsoH = (h - headD * 0.9f - legLen - sw * 2) + bend * h * 0.06f
        val shoulderY = hipY - torsoH + bend * h * 0.08f
        val cx = w / 2f
        val torsoW = headD * if (toddler) 0.78f else 0.72f
        val legW = torsoW * 0.32f
        val armW = legW * 0.8f

        // ---- الأرجل ----
        val swing = if (walking) sin(t) else 0f
        for (side in listOf(-1, 1)) {
            val lift = if (walking) maxOf(0f, side * swing) * legLen * 0.22f else 0f
            val lx = cx + side * torsoW * 0.22f
            val top = hipY - sw
            val bottom = footY - lift
            // بنطلون أو شورت
            val pantsEnd = if (outfit.shorts) top + (bottom - top) * 0.4f else bottom - legW * 0.3f
            if (outfit.shorts) {
                fill.color = skin
                rect.set(lx - legW / 2, top, lx + legW / 2, bottom)
                c.drawRoundRect(rect, legW / 2, legW / 2, fill)
                c.drawRoundRect(rect, legW / 2, legW / 2, stroke)
            }
            fill.color = outfit.pants
            rect.set(lx - legW / 2 - sw, top, lx + legW / 2 + sw, pantsEnd)
            c.drawRoundRect(rect, legW / 3, legW / 3, fill)
            c.drawRoundRect(rect, legW / 3, legW / 3, stroke)
            // الحذاء
            fill.color = 0xFF343A40.toInt()
            rect.set(lx - legW * 0.75f, bottom - legW * 0.55f, lx + legW * 0.75f, bottom + sw)
            c.drawOval(rect, fill)
        }

        // ---- الجسم ----
        fill.color = outfit.shirt
        rect.set(cx - torsoW / 2, shoulderY, cx + torsoW / 2, hipY + sw)
        val rr = torsoW * 0.28f
        c.drawRoundRect(rect, rr, rr, fill)
        if (outfit.stripes) {
            fill.color = outfit.shirtTrim
            var y = shoulderY + torsoH * 0.18f
            while (y < hipY - sw) {
                c.drawRect(cx - torsoW / 2 + sw, y, cx + torsoW / 2 - sw, y + torsoH * 0.06f, fill)
                y += torsoH * 0.18f
            }
        }
        if (outfit.hoodie) {
            fill.color = outfit.shirtTrim
            rect.set(cx - torsoW * 0.2f, shoulderY, cx + torsoW * 0.2f, hipY)
            c.drawRect(rect, fill)
            stroke.strokeWidth = sw * 0.8f
            c.drawLine(cx - torsoW * 0.2f, shoulderY, cx - torsoW * 0.2f, hipY, stroke)
            c.drawLine(cx + torsoW * 0.2f, shoulderY, cx + torsoW * 0.2f, hipY, stroke)
            stroke.strokeWidth = sw
        }
        rect.set(cx - torsoW / 2, shoulderY, cx + torsoW / 2, hipY + sw)
        c.drawRoundRect(rect, rr, rr, stroke)
        if (outfit.shirtTrim != outfit.shirt && !outfit.stripes && !outfit.hoodie) {
            // ياقة
            fill.color = outfit.shirtTrim
            c.drawCircle(cx, shoulderY + sw, torsoW * 0.16f, fill)
        }

        // ---- الأيدي ----
        val armLen = torsoH * 0.9f
        val holding = carrying != null
        for (side in listOf(-1, 1)) {
            val sx = cx + side * (torsoW / 2 - armW * 0.2f)
            val sy = shoulderY + armW * 0.6f
            // الزاوية من الاتجاه العمودي للأسفل
            val ang = when {
                holding -> side * -0.95f
                walking -> side * 0.15f + swing * 0.55f * side
                else -> side * (0.12f + sin(t) * 0.03f)
            }
            val ex = sx + sin(ang) * armLen * if (holding) 0.85f else 1f
            val ey = sy + cos(ang) * armLen * if (holding) 0.7f else 1f
            // كم
            val sleeveColor = if (outfit.shortSleeves) skin else outfit.shirt
            fill.color = sleeveColor
            stroke.strokeCap = Paint.Cap.ROUND
            fill.strokeCap = Paint.Cap.ROUND
            fill.style = Paint.Style.STROKE
            fill.strokeWidth = armW
            stroke.strokeWidth = armW + sw * 2
            c.drawLine(sx, sy, ex, ey, stroke)
            c.drawLine(sx, sy, ex, ey, fill)
            if (outfit.shortSleeves) {
                fill.color = outfit.shirt
                c.drawLine(sx, sy, sx + (ex - sx) * 0.3f, sy + (ey - sy) * 0.3f, fill)
            }
            fill.style = Paint.Style.FILL
            stroke.strokeWidth = sw
            fill.color = skin
            c.drawCircle(ex, ey, armW * 0.62f, fill)
        }

        // ---- الرأس (صورة الطفل) ----
        val headCy = shoulderY - headD * 0.42f + bend * h * 0.03f
        val hx = cx + if (walking) sin(t) * w * 0.01f else 0f
        fill.color = kid.color
        c.drawCircle(hx, headCy, headD / 2 + sw * 1.6f, fill)
        val f = face
        if (f != null) {
            rect.set(hx - headD / 2, headCy - headD / 2, hx + headD / 2, headCy + headD / 2)
            c.drawBitmap(f, null, rect, bmpPaint)
        } else {
            fill.color = skin
            c.drawCircle(hx, headCy, headD / 2, fill)
        }

        // ---- الغرض المحمول ----
        val item = carrying
        if (item != null) {
            emojiPaint.textSize = torsoW * 0.95f
            c.drawText(item, cx, shoulderY + torsoH * 0.45f, emojiPaint)
        }
    }
}
