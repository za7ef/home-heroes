package com.za7ef.homeheroes

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(val version: String, val title: String, val notes: String, val url: String, val apkUrl: String?)

object Updater {
    const val REPO = "za7ef/home-heroes"
    const val ACTION_INSTALL = "com.za7ef.homeheroes.INSTALL_STATUS"

    /** يجلب آخر إصدار من GitHub (المستودع عام، دون رمز وصول). يرجع null مع سبب الفشل. */
    fun fetchLatest(): Pair<ReleaseInfo?, String?> {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            val code = conn.responseCode
            if (code != 200) return null to "رد GitHub برمز $code"
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val o = JSONObject(body)
            val tag = o.optString("tag_name", "").removePrefix("v")
            var apk: String? = null
            val assets = o.optJSONArray("assets")
            if (assets != null) for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) { apk = a.optString("browser_download_url"); break }
            }
            ReleaseInfo(tag, o.optString("name", tag), o.optString("body", ""), o.optString("html_url", ""), apk) to null
        } catch (e: Exception) {
            null to (e.message ?: e.javaClass.simpleName)
        } finally {
            conn?.disconnect()
        }
    }

    /** ينزّل ملف APK إلى ذاكرة التطبيق المؤقتة، ويبلّغ عن التقدم بالنسبة المئوية */
    fun download(ctx: Context, url: String, progress: (Int) -> Unit): File {
        val out = File(ctx.cacheDir, "update.apk")
        var link = url
        var conn: HttpURLConnection
        var hops = 0
        while (true) {
            conn = URL(link).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            val code = conn.responseCode
            if (code in 300..399 && hops < 5) {
                link = conn.getHeaderField("Location") ?: throw IllegalStateException("تحويل دون عنوان")
                conn.disconnect(); hops++
                continue
            }
            if (code != 200) throw IllegalStateException("فشل التنزيل برمز $code")
            break
        }
        val total = conn.contentLengthLong
        conn.inputStream.use { input ->
            out.outputStream().use { o ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                var last = -1
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    o.write(buf, 0, n)
                    done += n
                    if (total > 0) {
                        val p = (done * 100 / total).toInt()
                        if (p != last) { last = p; progress(p) }
                    }
                }
            }
        }
        conn.disconnect()
        return out
    }

    /** يفتح شاشة التثبيت في النظام عبر PackageInstaller */
    fun install(ctx: Context, apk: File) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(ctx.packageName)
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("update.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val intent = Intent(ctx, InstallReceiver::class.java).setAction(ACTION_INSTALL)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val pi = PendingIntent.getBroadcast(ctx, id, intent, flags)
            session.commit(pi.intentSender)
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

/** يستقبل حالة التثبيت: لو يحتاج موافقة المستخدم يفتح شاشة النظام، ولو فشل يعرض السبب */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> {}
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "سبب غير معروف"
                android.widget.Toast.makeText(ctx, "فشل التثبيت: $msg", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
}
