package io.shubham0204.smollmandroid.logocaptcha

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

object LogoCaptchaData {

    private const val PREFS = "logocaptcha"
    private const val KEY_IMAGES = "images"
    private const val KEY_ORDER = "order"
    private const val KEY_LAST_VERIFIED = "last_verified"
    private const val KEY_ATTEMPTS = "attempts"
    private const val KEY_LOCKED_UNTIL = "locked_until"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun saveImagePaths(ctx: Context, paths: List<String>) {
        val arr = JSONArray().apply { paths.forEach { put(it) } }
        prefs(ctx).edit().putString(KEY_IMAGES, arr.toString()).apply()
    }

    fun getImagePaths(ctx: Context): List<String> {
        val raw = prefs(ctx).getString(KEY_IMAGES, null) ?: return emptyList()
        val arr = JSONArray(raw)
        return List(arr.length()) { arr.getString(it) }
    }

    fun saveCorrectOrder(ctx: Context, order: List<Int>) {
        val arr = JSONArray().apply { order.forEach { put(it) } }
        prefs(ctx).edit().putString(KEY_ORDER, arr.toString()).apply()
    }

    fun getCorrectOrder(ctx: Context): List<Int> {
        val raw = prefs(ctx).getString(KEY_ORDER, null) ?: return emptyList()
        val arr = JSONArray(raw)
        return List(arr.length()) { arr.getInt(it) }
    }

    fun saveLastVerified(ctx: Context) {
        prefs(ctx).edit().putLong(KEY_LAST_VERIFIED, System.currentTimeMillis()).apply()
    }

    fun getLastVerified(ctx: Context): Long =
        prefs(ctx).getLong(KEY_LAST_VERIFIED, 0L)

    fun saveAttempts(ctx: Context, n: Int) {
        prefs(ctx).edit().putInt(KEY_ATTEMPTS, n).apply()
    }

    fun getAttempts(ctx: Context): Int =
        prefs(ctx).getInt(KEY_ATTEMPTS, 0)

    fun setLockedUntil(ctx: Context, until: Long) {
        prefs(ctx).edit().putLong(KEY_LOCKED_UNTIL, until).apply()
    }

    fun getLockedUntil(ctx: Context): Long =
        prefs(ctx).getLong(KEY_LOCKED_UNTIL, 0L)

    fun resetAll(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }
}