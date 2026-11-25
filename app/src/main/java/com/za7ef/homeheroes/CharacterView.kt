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
import kotlin.math.sin

/** أبعاد رسم الشخصية بالبكسل الأصلي: العرض، الارتفاع، ومستوى الورك (بداية الرجلين) */
data class Rig(val w: Float, val h: Float, val legY: Float)

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

    private fun bmp(name: String): Bitmap? = try {
        val id = resources.getIdentifier("${name}_${kid.id}", "drawable", context.packageName)
        if (id != 0) BitmapFactory.decodeResource(resources, id) else null
    } catch (_: Exception) { null }

    private val body = bmp("body")
    private val legL = bmp("legl")
    private val legR = bmp("legr")
    private val rig = Rigs.of(kid.id)
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
        c.rotate(bend * 10f + (if (walking) swing * 2f else 0f), rig.w / 2f, hipY)
        body?.let {
            rect.set(0f, 0f, rig.w, rig.h)
            c.drawBitmap(it, null, rect, bmpPaint)
        }
        // ---- الغرض المحمول أمام الصدر ----
        carrying?.let {
            emojiPaint.textSize = rig.w * 0.55f
            c.drawText(it, rig.w / 2f, rig.legY * 0.78f, emojiPaint)
        }
        c.restore()
        c.restore()
    }
}
