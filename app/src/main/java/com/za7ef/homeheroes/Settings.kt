package com.za7ef.homeheroes

import android.content.Context
import android.content.SharedPreferences

/** إعدادات الصوت والعرض، محفوظة على الجهاز فقط */
object Settings {
    private var p: SharedPreferences? = null

    fun init(ctx: Context) {
        if (p == null) p = ctx.applicationContext.getSharedPreferences("hh_sound", Context.MODE_PRIVATE)
    }

    private fun b(k: String, d: Boolean) = p?.getBoolean(k, d) ?: d
    private fun i(k: String, d: Int) = p?.getInt(k, d) ?: d
    private fun put(k: String, v: Boolean) { p?.edit()?.putBoolean(k, v)?.apply() }
    private fun put(k: String, v: Int) { p?.edit()?.putInt(k, v)?.apply() }

    /** أصوات المؤثرات (الضغط، النجاح، الفرشاة، الماء...) */
    var fxOn: Boolean get() = b("fx_on", true); set(v) = put("fx_on", v)
    /** أصوات الشخصيات المسجلة */
    var voiceOn: Boolean get() = b("voice_on", true); set(v) = put("voice_on", v)
    /** الحوارات التلقائية بين الشخصيات */
    var autoTalk: Boolean get() = b("auto_talk", true); set(v) = put("auto_talk", v)
    /** مستويات الصوت من 0 إلى 100 */
    var musicVol: Int get() = i("music_vol", 60); set(v) = put("music_vol", v.coerceIn(0, 100))
    var fxVol: Int get() = i("fx_vol", 80); set(v) = put("fx_vol", v.coerceIn(0, 100))
    var voiceVol: Int get() = i("voice_vol", 100); set(v) = put("voice_vol", v.coerceIn(0, 100))
}
