package com.za7ef.homeheroes

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import kotlin.math.sin

/** أبعاد رسم الشخصية بالبكسل الأصلي: العرض، الارتفاع، ومستوى الورك (بداية الرجلين) */
data class Rig(val w: Float, val h: Float, val legY: Float)

/** ملامح الوجه في رسم الجسم (بالبكسل الأصلي): العينان والفم ولون البشرة، لأنيميشن الأدوات */
data class Face(val eyeLX: Float, val eyeRX: Float, val eyeY: Float, val eyeR: Float,
                val mouthX: Float, val mouthY: Float, val mouthW: Float, val skin: Int)

object Faces {
    fun of(kidId: String): Face = when (kidId) {
        "ayan" -> Face(102f, 172f, 142f, 22f, 140f, 210f, 50f, 0xFFF9CA92.toInt())
        "layan" -> Face(83f, 148f, 107f, 17f, 118f, 161f, 44f, 0xFFF8DFC6.toInt())
        "rayan" -> Face(83f, 148f, 117f, 16f, 118f, 179f, 42f, 0xFFF4D5B7.toInt())
        else -> Face(78f, 146f, 122f, 16f, 111f, 191f, 42f, 0xFFEACCB1.toInt())
    }
}

object Rigs {
    fun of(kidId: String): Rig = when (kidId) {
        "ayan" -> Rig(267f, 640f, 475f)
        "layan" -> Rig(235f, 640f, 455f)
        "rayan" -> Rig(232f, 640f, 460f)
        else -> Rig(235f, 640f, 458f)
    }
}

/**
 * شخصية مرسومة بأسلوب واحد: الوجه من الصورة المرجعية للطفل والجسم بنفس الأسلوب
 * وبملابس الإصدار 1.0. الرجلان منفصلتان حتى تتحركا أثناء المشي، والجسم ينحني عند الالتقاط.
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

    /** وضعية الرسم: front أمام، side جانب (أثناء المشي يمين ويسار)، back ظهر، crawl حبو (أيان) */
    var pose = "front"
        set(v) { if (field != v) { field = v; invalidate() } }

    /** اتجاه الوجه في الوضعية الجانبية: 1 يمين، -1 يسار */
    var facing = 1f
        set(v) { if (field != v) { field = v; invalidate() } }

    /** مرفوع بالإصبع: يتأرجح شوي */
    var held = false
        set(v) { field = v; invalidate() }

    /** تعبير الوجه أثناء استخدام أداة: teeth، chew، open، sleep أو null */
    var face: String? = null
        set(v) { if (field != v) { field = v; invalidate() } }

    private val fc = Faces.of(kid.id)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

    /** موضع نقطة من الرسم الأصلي داخل حدود العرض (للفم والرأس) */
    fun pointOf(rx: Float, ry: Float): Pair<Float, Float> {
        val vw = width.toFloat(); val vh = height.toFloat()
        val s = minOf(vh / rig.h, vw / rig.w)
        val ox = (vw - rig.w * s) / 2f
        val oy = vh - rig.h * s
        return (ox + rx * s) to (oy + ry * s)
    }

    fun mouth(): Pair<Float, Float> = pointOf(fc.mouthX, fc.mouthY)
    fun head(): Pair<Float, Float> = pointOf(rig.w / 2f, fc.eyeY - 70f)
    fun chest(): Pair<Float, Float> = pointOf(rig.w / 2f, rig.legY * 0.72f)
    fun scale(): Float = if (width > 0) minOf(height / rig.h, width / rig.w) else 1f

    private fun bmp(name: String): Bitmap? = try {
        val id = resources.getIdentifier("${name}_${kid.id}", "drawable", context.packageName)
        if (id != 0) BitmapFactory.decodeResource(resources, id) else null
    } catch (_: Exception) { null }

    private val body = bmp("body")
    private val legL = bmp("legl")
    private val legR = bmp("legr")
    private val rig = Rigs.of(kid.id)
    private val side = bmp("side")
    private val backImg = bmp("back")
    private val crawl = bmp("crawl")
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
        val vw = width.toFloat()
        val vh = height.toFloat()
        if (vw <= 0f || vh <= 0f) return
        val t = (phase * 2 * Math.PI).toFloat()
        // مقياس الرسم: الشخصية بكامل ارتفاع العرض، في المنتصف، وقدماها على الحافة السفلية
        val s = minOf(vh / rig.h, vw / rig.w)
        val ox = (vw - rig.w * s) / 2f
        val oy = vh - rig.h * s
        when {
            pose == "crawl" && crawl != null -> { drawCrawl(c, vw, vh, t); return }
            pose == "side" && side != null -> { drawFlat(c, side, vw, vh, t, facing < 0); return }
            pose == "back" && backImg != null -> { drawFlat(c, backImg, vw, vh, t, false); return }
        }
        val swing = if (walking) sin(t) else 0f
        val bob = if (walking) -abs(sin(t)) * rig.h * 0.02f else sin(t) * rig.h * 0.004f

        c.save()
        c.translate(ox, oy)
        c.scale(s, s)

        // ---- الرجلان: كل رجل ترتفع بالتناوب أثناء المشي ----
        val hipY = rig.legY
        val legH = rig.h - rig.legY
        for ((img, side) in listOf(legL to -1f, legR to 1f)) {
            img ?: continue
            val x0 = if (side < 0) 0f else rig.w / 2f
            val lift = if (walking) maxOf(0f, side * swing) * legH * 0.16f else 0f
            c.save()
            c.rotate(swing * 5f, x0 + rig.w / 4f, hipY)
            rect.set(x0, hipY - lift, x0 + rig.w / 2f, rig.h - lift)
            c.drawBitmap(img, null, rect, bmpPaint)
            c.restore()
        }

        // ---- الجسم والرأس: يهتز مع المشي، وينحني للأمام عند الالتقاط ----
        c.save()
        c.translate(0f, bob + bend * rig.h * 0.05f)
        c.rotate(bend * 10f + (if (walking) swing * 2f else 0f) + (if (held) sin(t) * 5f else 0f), rig.w / 2f, hipY)
        body?.let {
            rect.set(0f, 0f, rig.w, rig.h)
            c.drawBitmap(it, null, rect, bmpPaint)
        }
        // ---- تعبير الوجه أثناء استخدام الأداة ----
        face?.let { drawFace(c, it, t) }
        // ---- الغرض المحمول أمام الصدر ----
        carrying?.let {
            emojiPaint.textSize = rig.w * 0.55f
            c.drawText(it, rig.w / 2f, rig.legY * 0.78f, emojiPaint)
        }
        c.restore()
        c.restore()
    }

    /** صورة جانبية أو خلفية كاملة: المشي بهزة ونطة خفيفة بدل تحريك الرجلين */
    private fun drawFlat(c: Canvas, img: Bitmap, vw: Float, vh: Float, t: Float, flip: Boolean) {
        val s = minOf(vh / img.height, vw / img.width)
        val w = img.width * s; val h = img.height * s
        val bob = if (walking) -abs(sin(t * 2f)) * h * 0.025f else sin(t) * h * 0.004f
        val squash = if (walking) 1f - abs(sin(t * 2f)) * 0.03f else 1f
        c.save()
        c.translate(vw / 2f, vh)
        if (flip) c.scale(-1f, 1f)
        if (held) c.rotate(sin(t) * 6f, 0f, -h)
        c.scale(1f, squash)
        rect.set(-w / 2f, -h + bob, w / 2f, bob)
        c.drawBitmap(img, null, rect, bmpPaint)
        c.restore()
        carrying?.let {
            emojiPaint.textSize = w * 0.5f
            c.drawText(it, vw / 2f + (if (flip) -w else w) * 0.3f, vh - h * 0.38f, emojiPaint)
        }
    }

    /** أيان يحبو ويدف سيارته: الصورة أعرض من إطار الشخصية فترسم خارجه قليلًا */
    private fun drawCrawl(c: Canvas, vw: Float, vh: Float, t: Float) {
        val img = crawl ?: return
        val h = vh * 0.5f
        val w = h * img.width / img.height
        val rock = if (walking) sin(t * 2f) * 2.5f else 0f
        c.save()
        c.translate(vw / 2f, vh)
        if (facing < 0) c.scale(-1f, 1f)
        c.rotate(rock, 0f, 0f)
        rect.set(-w / 2f, -h - (if (walking) abs(sin(t * 2f)) * h * 0.03f else 0f), w / 2f, 0f)
        c.drawBitmap(img, null, rect, bmpPaint)
        c.restore()
    }

    private fun drawFace(c: Canvas, mode: String, t: Float) {
        val mx = fc.mouthX; val my = fc.mouthY; val mw = fc.mouthW
        when (mode) {
            "teeth" -> {
                // ابتسامة عريضة تظهر فيها الأسنان
                val h = mw * 0.42f
                rect.set(mx - mw * 0.62f, my - h * 0.55f, mx + mw * 0.62f, my + h * 0.55f)
                fill.color = 0xFF8C2F39.toInt(); c.drawRoundRect(rect, h * 0.5f, h * 0.5f, fill)
                rect.inset(mw * 0.06f, h * 0.12f)
                fill.color = Color.WHITE; c.drawRoundRect(rect, h * 0.4f, h * 0.4f, fill)
                line.color = 0xFFADB5BD.toInt(); line.strokeWidth = mw * 0.03f
                c.drawLine(rect.left, rect.centerY(), rect.right, rect.centerY(), line)
                for (i in 1..5) {
                    val x = rect.left + rect.width() * i / 6f
                    c.drawLine(x, rect.top + 2f, x, rect.bottom - 2f, line)
                }
                line.color = 0xFFC2555F.toInt(); line.strokeWidth = mw * 0.07f
                rect.set(mx - mw * 0.62f, my - h * 0.55f, mx + mw * 0.62f, my + h * 0.55f)
                c.drawRoundRect(rect, h * 0.5f, h * 0.5f, line)
            }
            "chew", "open" -> {
                val open = if (mode == "chew") 0.08f + 0.30f * abs(sin(t * 2f)) else 0.34f
                val w = mw * 0.34f; val h = mw * open
                rect.set(mx - w, my - h * 0.5f, mx + w, my + h * 0.5f)
                fill.color = 0xFF7A1F2B.toInt(); c.drawOval(rect, fill)
                if (h > mw * 0.15f) {
                    rect.set(mx - w * 0.6f, my, mx + w * 0.6f, my + h * 0.5f)
                    fill.color = 0xFFE8737F.toInt(); c.drawOval(rect, fill)
                }
                line.color = 0xFFC2555F.toInt(); line.strokeWidth = mw * 0.06f
                rect.set(mx - w, my - h * 0.5f, mx + w, my + h * 0.5f)
                c.drawOval(rect, line)
            }
            "sleep" -> {
                // جفون مغمضة بلون البشرة وخط نوم
                for (ex in floatArrayOf(fc.eyeLX, fc.eyeRX)) {
                    val r = fc.eyeR
                    rect.set(ex - r * 1.35f, fc.eyeY - r * 1.05f, ex + r * 1.35f, fc.eyeY + r * 1.0f)
                    fill.color = fc.skin; c.drawOval(rect, fill)
                    line.color = 0xFF4A3426.toInt(); line.strokeWidth = r * 0.28f
                    rect.set(ex - r * 1.05f, fc.eyeY - r * 0.7f, ex + r * 1.05f, fc.eyeY + r * 0.5f)
                    c.drawArc(rect, 20f, 140f, false, line)
                }
            }
        }
    }
}
