package com.miauto.launcher

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Context
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

/** Conexión a un ELM327 por Bluetooth (SPP) o WiFi (TCP). */
class ElmLink {
    private var bt: BluetoothSocket? = null
    private var tcp: Socket? = null
    private var ins: InputStream? = null
    private var outs: OutputStream? = null

    @SuppressLint("MissingPermission")
    fun open(type: String, target: String) {
        if (type == "wifi") {
            val host = target.substringBefore(":").ifBlank { "192.168.0.10" }
            val port = target.substringAfter(":", "35000").toIntOrNull() ?: 35000
            val s = Socket()
            s.connect(InetSocketAddress(host, port), 4000)
            tcp = s; ins = s.getInputStream(); outs = s.getOutputStream()
        } else {
            val ad = BluetoothAdapter.getDefaultAdapter() ?: throw IOException("Sin Bluetooth")
            val bonded = ad.bondedDevices
            val dev = bonded.firstOrNull { it.name.equals(target, true) || it.address.equals(target, true) }
                ?: bonded.firstOrNull { val n = it.name ?: ""; n.contains("OBD", true) || n.contains("ELM", true) || n.contains("V-LINK", true) }
                ?: throw IOException("ELM no emparejado")
            ad.cancelDiscovery()
            val s = dev.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
            s.connect()
            bt = s; ins = s.inputStream; outs = s.outputStream
        }
    }

    /** Envía un comando y espera hasta el prompt '>' (máx. 4 s). */
    fun send(cmd: String): String {
        val o = outs ?: throw IOException("sin conexión")
        val i = ins ?: throw IOException("sin conexión")
        while (i.available() > 0) i.read()
        o.write((cmd + "\r").toByteArray()); o.flush()
        val sb = StringBuilder()
        val end = System.currentTimeMillis() + 4000
        while (System.currentTimeMillis() < end) {
            if (i.available() > 0) {
                val ch = i.read().toChar()
                if (ch == '>') break
                sb.append(ch)
            } else Thread.sleep(15)
        }
        return sb.toString().replace('\r', '\n').trim()
    }

    fun close() {
        try { bt?.close() } catch (_: Exception) {}
        try { tcp?.close() } catch (_: Exception) {}
        bt = null; tcp = null; ins = null; outs = null
    }
}

/** Evalúa fórmulas tipo Torque: A-40, (A*256+B)/4, etc. */
object Formula {
    fun eval(f: String, vars: Map<Char, Double>): Double = P(f.replace(" ", ""), vars).expr()

    private class P(val s: String, val vars: Map<Char, Double>) {
        var i = 0
        fun expr(): Double {
            var v = term()
            while (i < s.length && (s[i] == '+' || s[i] == '-')) {
                val op = s[i++]; val t = term(); v = if (op == '+') v + t else v - t
            }
            return v
        }
        fun term(): Double {
            var v = factor()
            while (i < s.length && (s[i] == '*' || s[i] == '/')) {
                val op = s[i++]; val t = factor(); v = if (op == '*') v * t else v / t
            }
            return v
        }
        fun factor(): Double {
            if (i >= s.length) throw IllegalArgumentException("fórmula")
            val c = s[i]
            if (c == '-') { i++; return -factor() }
            if (c == '(') { i++; val v = expr(); i++; return v }
            if (c.isLetter()) { i++; return vars[c.uppercaseChar()] ?: throw IllegalArgumentException("byte") }
            val st = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            return s.substring(st, i).toDouble()
        }
    }
}

/**
 * Lee PIDs en segundo plano. Reglas (una por línea):
 *   NOMBRE|header|comando|fórmula     (NOMBRE: OIL, COOLANT, CVT)
 * Ejemplo: COOLANT||0105|A-40      CVT|7E1|2210D2|A-40
 */
object Obd {
    data class Pid(val name: String, val header: String, val cmd: String, val formula: String)

    val values = HashMap<String, Double?>()
    @Volatile var status = "ELM: sin iniciar"
    val listeners = ArrayList<() -> Unit>()
    @Volatile private var running = false
    private var thread: Thread? = null

    val DEFAULT_PIDS = "COOLANT||0105|A-40\nOIL||015C|A-40\n" +
        "# CVT: pruébalo en el terminal de VEHICLE y quita el # cuando funcione\n" +
        "# CVT|7E1|2210D2|A-40"

    fun parse(text: String): List<Pid> = text.lines().map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull {
            val p = it.split("|").map { s -> s.trim() }
            if (p.size >= 4) Pid(p[0].uppercase(), p[1].uppercase(), p[2].uppercase(), p[3]) else null
        }

    private fun notifyAll() = listeners.toList().forEach { it() }
    private fun setStatus(s: String) { status = s; notifyAll() }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
    }

    fun start(ctx: Context) {
        stop()
        val app = ctx.applicationContext
        val type = Prefs.elmType(app)
        val target = Prefs.elmTarget(app)
        val pids = parse(Prefs.pids(app))
        if (pids.isEmpty()) { setStatus("ELM: sin PIDs"); return }
        if (type == "wifi" && target.isBlank()) { setStatus("ELM: sin configurar"); return }
        running = true
        thread = Thread {
            while (running) {
                val link = ElmLink()
                try {
                    setStatus("ELM: conectando…")
                    link.open(type, target)
                    for (c in listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0")) link.send(c)
                    setStatus("ELM: conectado")
                    var header = ""
                    while (running) {
                        for (p in pids) {
                            if (p.header != header) {
                                link.send(if (p.header.isEmpty()) "ATSH7DF" else "ATSH" + p.header)
                                header = p.header
                            }
                            values[p.name] = decode(p, link.send(p.cmd))
                        }
                        notifyAll()
                        Thread.sleep(1500)
                    }
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    setStatus("ELM: sin conexión (reintentando)")
                    try { Thread.sleep(5000) } catch (_: InterruptedException) { break }
                } finally {
                    link.close()
                }
            }
        }.also { it.isDaemon = true; it.start() }
    }

    fun decode(p: Pid, resp: String): Double? {
        try {
            val svc = p.cmd.substring(0, 2).toInt(16) + 0x40
            val expected = String.format("%02X", svc) + p.cmd.substring(2)
            val hex = StringBuilder()
            for (raw in resp.uppercase().lines()) {
                val line = raw.substringAfter(':').replace(" ", "")
                if (line.isNotEmpty() && line.all { it in "0123456789ABCDEF" }) hex.append(line)
            }
            val idx = hex.indexOf(expected)
            if (idx < 0) return null
            val data = hex.substring(idx + expected.length)
            val vars = HashMap<Char, Double>()
            var n = 0
            while (n * 2 + 2 <= data.length && n < 8) {
                vars['A' + n] = data.substring(n * 2, n * 2 + 2).toInt(16).toDouble(); n++
            }
            return Formula.eval(p.formula, vars)
        } catch (_: Exception) { return null }
    }
}
