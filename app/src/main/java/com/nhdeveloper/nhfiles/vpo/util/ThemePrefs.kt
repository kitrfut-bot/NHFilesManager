package com.nhdeveloper.nhfiles.vpo.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/** App theme choice. Default is LIGHT. Stored in SharedPreferences so it can be read synchronously at startup. */
object ThemePrefs {
    const val LIGHT = 0
    const val DARK = 1
    const val SYSTEM = 2
    val LABELS = arrayOf("Light", "Dark", "System default")

    fun get(ctx: Context): Int = ctx.getSharedPreferences("ui", Context.MODE_PRIVATE).getInt("theme", LIGHT)

    fun set(ctx: Context, value: Int) {
        ctx.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().putInt("theme", value).apply()
        apply(value)
    }

    fun apply(value: Int) {
        AppCompatDelegate.setDefaultNightMode(
            when (value) {
                DARK -> AppCompatDelegate.MODE_NIGHT_YES
                SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                else -> AppCompatDelegate.MODE_NIGHT_NO
            }
        )
    }
}
