package com.za7ef.homeheroes

import android.content.Context
import android.media.MediaPlayer
import org.json.JSONArray

/**
 * أصوات مسجّلة مسبقًا للعبارات الثابتة (لكل شخصية وللراوي).
 * البحث بنص العبارة بعد حذف الرموز والإيموجي، ويُفضَّل صوت المتحدث نفسه.
 * إذا لم توجد العبارة (أو كانت الأصوات مطفأة) يُرجع false فتظهر العبارة نصًا بلا صوت.
 */
class Voice(private val ctx: Context) {
    private val byKey = mutableMapOf<String, MutableMap<String, String>>()
    private var player: MediaPlayer? = null

    init {
        try {
            val arr = JSONArray(ctx.assets.open("voices/voices.json").bufferedReader().readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                byKey.getOrPut(key(o.getString("t"))) { mutableMapOf() }[o.getString("s")] = o.getString("f")
            }
        } catch (_: Exception) {
        }
    }

    /** يُبقي الحروف والأرقام فقط */
    private fun key(s: String): String = s.filter { it.isLetterOrDigit() }

    fun play(text: String, speaker: String?): Boolean {
        if (!Settings.voiceOn) return false
        val m = byKey[key(text)] ?: return false
        val file = (speaker?.let { m[it] }) ?: m["narrator"] ?: m.values.first()
        return try {
            stop()
            val afd = ctx.assets.openFd("voices/$file")
            val p = MediaPlayer()
            p.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            p.setOnCompletionListener { it.release(); if (player === it) player = null }
            p.prepare()
            val vol = Settings.voiceVol / 100f
            p.setVolume(vol, vol)
            p.start()
            player = p
            true
        } catch (_: Exception) {
            false
        }
    }

    fun stop() {
        try { player?.stop() } catch (_: Exception) {}
        player?.release()
        player = null
    }
}
