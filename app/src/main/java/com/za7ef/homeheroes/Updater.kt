package com.za7ef.homeheroes

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(val version: String, val title: String, val notes: String, val url: String)

object Updater {
    const val REPO = "za7ef/home-heroes"

    /** يجلب آخر إصدار من GitHub. المستودع خاص، لذلك يلزم رمز وصول للقراءة. يرجع null مع سبب الفشل. */
    fun fetchLatest(token: String): Pair<ReleaseInfo?, String?> {
        if (token.isBlank()) return null to "لا يوجد رمز GitHub محفوظ في إعدادات الأهل"
        var conn: HttpURLConnection? = null
        return try {
            conn = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("Authorization", "Bearer $token")
            val code = conn.responseCode
            if (code != 200) return null to "رد GitHub برمز $code"
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val o = JSONObject(body)
            val tag = o.optString("tag_name", "").removePrefix("v")
            ReleaseInfo(tag, o.optString("name", tag), o.optString("body", ""), o.optString("html_url", "")) to null
        } catch (e: Exception) {
            null to (e.message ?: e.javaClass.simpleName)
        } finally {
            conn?.disconnect()
        }
    }

    /** مقارنة أرقام الإصدارات مثل 1.2 و 1.10 */
    fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val l = local.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
