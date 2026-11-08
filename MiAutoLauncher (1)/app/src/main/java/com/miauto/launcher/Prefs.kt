package com.miauto.launcher

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import java.util.Calendar

object Prefs {
    private fun p(c: Context) = c.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    /** 0 = automático, 1 = día, 2 = noche */
    fun mode(c: Context) = p(c).getInt("mode", 0)
    fun setMode(c: Context, m: Int) = p(c).edit().putInt("mode", m).apply()

    fun rules(c: Context) = p(c).getString("rules", "") ?: ""
    fun cameraPkg(c: Context) = p(c).getString("cameraPkg", "") ?: ""
    fun radioPkg(c: Context) = p(c).getString("radioPkg", "") ?: ""
    fun elmType(c: Context) = p(c).getString("elmType", "bt") ?: "bt"
    fun elmTarget(c: Context) = p(c).getString("elmTarget", "") ?: ""
    fun pids(c: Context) = p(c).getString("pids", null) ?: Obd.DEFAULT_PIDS
    fun save(c: Context, key: String, value: String) = p(c).edit().putString(key, value).apply()

    // Día de 6:30 a 18:30. En automático, si el canbus reporta LUCES encendidas, pasa a noche.
    fun appCompatMode(c: Context): Int = when (mode(c)) {
        1 -> AppCompatDelegate.MODE_NIGHT_NO
        2 -> AppCompatDelegate.MODE_NIGHT_YES
        else -> {
            val cal = Calendar.getInstance()
            val m = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            val night = m < 6 * 60 + 30 || m >= 18 * 60 + 30 || CanbusBridge.state["LUCES"] == true
            if (night) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        }
    }
}
