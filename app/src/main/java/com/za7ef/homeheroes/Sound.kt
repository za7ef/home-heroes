package com.za7ef.homeheroes

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * أصوات اللعبة مولّدة داخل التطبيق (بدون ملفات صوت خارجية):
 * موسيقى خلفية هادئة تتكرر، وأصوات قصيرة للضغط والنجاح والالتقاط والخطأ.
 * لا تؤثر على صوت النطق العربي.
 */
class Sound(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("hh_sound", Context.MODE_PRIVATE)
    private val rate = 22050
    private var music: AudioTrack? = null
    private val fx = mutableMapOf<String, AudioTrack>()
    @Volatile private var released = false
    @Volatile private var wantMusic = true

    companion object {
        /** النسخة الحالية حتى تستخدمها أي شاشة */
        var current: Sound? = null
    }

    var musicOn: Boolean
        get() = prefs.getBoolean("music_on", true)
        set(v) {
            prefs.edit().putBoolean("music_on", v).apply()
            if (v) startMusic() else pauseMusic()
        }

    init {
        Thread {
            try {
                val m = track(makeMusic(), loop = true)
                m.setVolume(musicLevel())
                val made = mapOf(
                    "tap" to track(makeNotes(listOf(1046.5 to 0.06), 0)),
                    "pick" to track(makeNotes(listOf(659.3 to 0.06, 880.0 to 0.07), 0)),
                    "success" to track(makeNotes(listOf(523.3 to 0.10, 659.3 to 0.10, 784.0 to 0.10, 1046.5 to 0.28), 1)),
                    "wrong" to track(makeNotes(listOf(392.0 to 0.14, 311.1 to 0.22), 1)),
                    "brush" to track(makeNoise(0.14, 18.0, 0.55, 0.35)),
                    "bite" to track(makeNoise(0.08, 40.0, 0.25, 0.6)),
                    "gulp" to track(makeSweep(320.0, 140.0, 0.12, 0.5)),
                    "bubble" to track(makeSweep(500.0, 1300.0, 0.07, 0.35)),
                    "splash" to track(makeNoise(0.35, 7.0, 0.7, 0.3)),
                    "flush" to track(makeNoise(0.9, 3.0, 0.85, 0.35)),
                    "boing" to track(makeSweep(180.0, 520.0, 0.18, 0.5)),
                    "zoom" to track(makeSweep(260.0, 620.0, 0.22, 0.3)),
                )
                made.values.forEach { it.setVolume(fxLevel()) }
                synchronized(this) {
                    if (released) {
                        m.release(); made.values.forEach { it.release() }
                    } else {
                        music = m
                        fx.putAll(made)
                    }
                }
                if (!released && musicOn && wantMusic) startMusic()
            } catch (_: Exception) {
            }
        }.start()
    }

    private fun track(data: ShortArray, loop: Boolean = false): AudioTrack {
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(rate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(data.size * 2)
            .build()
        t.write(data, 0, data.size)
        if (loop) t.setLoopPoints(0, data.size, -1)
        return t
    }

    private fun musicLevel(): Float = 0.36f * Settings.musicVol / 100f
    private fun fxLevel(): Float = Settings.fxVol / 100f

    /** يطبّق مستويات الصوت بعد تغييرها من الإعدادات */
    fun applyVolumes() {
        try { music?.setVolume(musicLevel()) } catch (_: Exception) {}
        fx.values.forEach { try { it.setVolume(fxLevel()) } catch (_: Exception) {} }
    }

    /** ضجيج مُنعَّم (فرشاة، ماء، قضمة): decay سرعة التلاشي، smooth بين 0 و1 كلما زاد صار الصوت أنعم */
    private fun makeNoise(dur: Double, decay: Double, smooth: Double, amp: Double): ShortArray {
        val n = (dur * rate).toInt()
        val out = ShortArray(n)
        val rnd = java.util.Random(7)
        var y = 0.0
        for (k in 0 until n) {
            val t = k.toDouble() / rate
            y = smooth * y + (1 - smooth) * (rnd.nextDouble() * 2 - 1)
            val env = min(1.0, t / 0.01) * exp(-decay * t)
            out[k] = (y * env * amp * 2.2 * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    /** نغمة تنزلق من تردد لآخر (فقاعة، بلعة، نطة) */
    private fun makeSweep(f0: Double, f1: Double, dur: Double, amp: Double): ShortArray {
        val n = (dur * rate).toInt()
        val out = ShortArray(n)
        var ph = 0.0
        for (k in 0 until n) {
            val t = k.toDouble() / rate
            val f = f0 + (f1 - f0) * (t / dur)
            ph += 2 * PI * f / rate
            val env = min(1.0, t / 0.005) * exp(-4.0 * t / dur)
            out[k] = (sin(ph) * env * amp * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    /** نغمات متتالية: wave 0 = جيبية ناعمة، 1 = مثلثية خفيفة */
    private fun makeNotes(notes: List<Pair<Double, Double>>, wave: Int, gap: Double = 0.01): ShortArray {
        val total = notes.sumOf { it.second + gap }
        val out = ShortArray((total * rate).toInt())
        var i = 0
        for ((f, d) in notes) {
            val n = (d * rate).toInt()
            for (k in 0 until n) {
                if (i + k >= out.size) break
                val t = k.toDouble() / rate
                val env = min(1.0, t / 0.008) * exp(-3.5 * t / d)
                val ph = 2 * PI * f * t
                val v = if (wave == 0) sin(ph) else (2 / PI) * asin(sin(ph))
                out[i + k] = (v * env * 0.5 * Short.MAX_VALUE).toInt().toShort()
            }
            i += n + (gap * rate).toInt()
        }
        return out
    }

    /** لحن خلفية بسيط (8 مازورات) مع باص ناعم، يتكرر بدون توقف */
    private fun makeMusic(): ShortArray {
        val beat = 0.32
        val melody = doubleArrayOf(
            523.3, 659.3, 784.0, 659.3, 587.3, 698.5, 880.0, 698.5,
            523.3, 659.3, 784.0, 1046.5, 987.8, 784.0, 659.3, 587.3,
            440.0, 523.3, 659.3, 523.3, 493.9, 587.3, 784.0, 587.3,
            523.3, 659.3, 587.3, 493.9, 523.3, 0.0, 523.3, 0.0,
        )
        val bass = doubleArrayOf(130.8, 146.8, 130.8, 196.0, 110.0, 123.5, 130.8, 130.8)
        val n = (beat * rate).toInt()
        val out = ShortArray(melody.size * n)
        for ((bi, f) in melody.withIndex()) {
            val bf = bass[(bi / 4) % bass.size]
            for (k in 0 until n) {
                val t = k.toDouble() / rate
                val env = min(1.0, t / 0.01) * exp(-4 * t)
                var v = if (f > 0) sin(2 * PI * f * t) * env * 0.35 else 0.0
                val tb = ((bi % 4) * n + k).toDouble() / rate
                v += sin(2 * PI * bf * tb) * 0.18 * exp(-1.2 * tb)
                out[bi * n + k] = (v * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
            }
        }
        return out
    }

    fun startMusic() {
        wantMusic = true
        if (!musicOn) return
        val m = music ?: return
        try { if (m.playState != AudioTrack.PLAYSTATE_PLAYING) m.play() } catch (_: Exception) {}
    }

    fun pauseMusic() {
        wantMusic = false
        try { music?.pause() } catch (_: Exception) {}
    }

    private fun play(name: String) {
        if (!Settings.fxOn) return
        val t = fx[name] ?: return
        try {
            if (t.playState == AudioTrack.PLAYSTATE_PLAYING) t.stop()
            t.reloadStaticData()
            t.play()
        } catch (_: Exception) {
        }
    }

    fun tap() = play("tap")
    fun pickUp() = play("pick")
    fun success() = play("success")
    fun wrong() = play("wrong")
    /** صوت مؤثر باسمه: brush، bite، gulp، bubble، splash، flush، boing، zoom */
    fun effect(name: String) = play(name)

    fun release() {
        synchronized(this) {
            released = true
            music?.release(); music = null
            fx.values.forEach { it.release() }
            fx.clear()
        }
    }
}
