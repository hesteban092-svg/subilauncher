package com.miauto.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

/**
 * Puente genérico del canbus. Cada regla (una por línea) dice qué broadcast escuchar:
 *   EVENTO|acción|clave_extra|valor_que_significa_activo
 * Eventos: REVERSA, DOOR_FL, DOOR_FR, DOOR_RL, DOOR_RR, TRUNK, LUCES
 */
object CanbusBridge {
    data class Rule(val event: String, val action: String, val key: String, val value: String)

    val state = HashMap<String, Boolean>()
    val listeners = ArrayList<() -> Unit>()
    private var receiver: BroadcastReceiver? = null

    fun parse(text: String): List<Rule> = text.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull {
            val p = it.split("|").map { s -> s.trim() }
            if (p.size >= 4) Rule(p[0].uppercase(), p[1], p[2], p[3]) else null
        }

    fun start(ctx: Context) {
        val app = ctx.applicationContext
        receiver?.let { try { app.unregisterReceiver(it) } catch (_: Exception) {} }
        receiver = null
        val rules = parse(Prefs.rules(app))
        if (rules.isEmpty()) return
        val filter = IntentFilter()
        rules.map { it.action }.distinct().forEach { filter.addAction(it) }
        val r = object : BroadcastReceiver() {
            @Suppress("DEPRECATION")
            override fun onReceive(c: Context, i: Intent) {
                var changed = false
                for (rule in rules) {
                    if (rule.action != i.action) continue
                    val v = i.extras?.get(rule.key)?.toString() ?: continue
                    val active = v.equals(rule.value, ignoreCase = true)
                    if (state[rule.event] != active) {
                        state[rule.event] = active
                        changed = true
                        if (rule.event == "REVERSA" && active) openCamera(c)
                    }
                }
                if (changed) listeners.toList().forEach { it() }
            }
        }
        app.registerReceiver(r, filter)
        receiver = r
    }

    private fun openCamera(c: Context) {
        val pkg = Prefs.cameraPkg(c)
        if (pkg.isBlank()) return
        c.packageManager.getLaunchIntentForPackage(pkg)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { c.startActivity(it) } catch (_: Exception) {}
        }
    }
}
