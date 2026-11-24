package com.axter.opu

import android.content.Context

/** Tiny settings store. Everything stays on this tablet. */
object Store {
    private fun p(c: Context) = c.getSharedPreferences("opu", Context.MODE_PRIVATE)
    fun get(c: Context, k: String, d: String = "") = p(c).getString(k, d) ?: d
    fun put(c: Context, k: String, v: String) = p(c).edit().putString(k, v).apply()

    fun geminiKey(c: Context) = get(c, "gemini_key")
    fun model(c: Context) = get(c, "gemini_model", "gemini-3.8-flash")
    fun userName(c: Context) = get(c, "user_name", "Axter")
    fun opuName(c: Context) = get(c, "opu_name", "Opu")
}
