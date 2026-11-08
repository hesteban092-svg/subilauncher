package com.miauto.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class DiagActivity : AppCompatActivity() {
    private lateinit var log: TextView
    private var rx: BroadcastReceiver? = null
    private var link: ElmLink? = null
    private lateinit var term: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        fun col(id: Int) = ContextCompat.getColor(this, id)
        val pad = (16 * d).toInt()

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(pad, pad, pad, pad)
        root.setBackgroundColor(col(R.color.bg))

        fun label(t: String) = TextView(this).apply {
            text = t; textSize = 16f; setTextColor(col(R.color.text)); setPadding(0, pad / 2, 0, 4)
        }
        fun field(v: String, h: String, lines: Int = 1) = EditText(this).apply {
            setText(v); hint = h; textSize = 15f; minLines = lines; gravity = Gravity.TOP
            setTextColor(col(R.color.text)); setHintTextColor(col(R.color.textSub))
        }

        root.addView(label("VEHÍCULO / DIAGNÓSTICO CANBUS"))
        root.addView(label("Reglas (una por línea):  EVENTO|acción|clave|valor_activo\n" +
            "Eventos: REVERSA, DOOR_FL, DOOR_FR, DOOR_RL, DOOR_RR, TRUNK, LUCES"))
        val rules = field(Prefs.rules(this), "REVERSA|com.xinbu.xxx.REVERSE|state|1", 5)
        root.addView(rules)
        root.addView(label("Paquete de la app de cámara (opcional, se abre al meter reversa)"))
        val cam = field(Prefs.cameraPkg(this), "com.ejemplo.camara")
        root.addView(cam)
        root.addView(label("Paquete de la app de radio FM (para el botón RADIO)"))
        val radio = field(Prefs.radioPkg(this), "com.ejemplo.radio")
        root.addView(radio)
        root.addView(label("ELM327 — tipo: bt (Bluetooth) o wifi"))
        val elmType = field(Prefs.elmType(this), "bt")
        root.addView(elmType)
        root.addView(label("ELM327 — nombre o MAC del Bluetooth, o IP:puerto si es wifi (ej. 192.168.0.10:35000)"))
        val elmTarget = field(Prefs.elmTarget(this), "OBDII")
        root.addView(elmTarget)
        root.addView(label("PIDs (una por línea):  NOMBRE|header|comando|fórmula   (OIL, COOLANT, CVT)"))
        val pids = field(Prefs.pids(this), "COOLANT||0105|A-40", 5)
        root.addView(pids)
        root.addView(Button(this).apply {
            text = "GUARDAR Y APLICAR"
            setOnClickListener {
                Prefs.save(context, "rules", rules.text.toString())
                Prefs.save(context, "cameraPkg", cam.text.toString().trim())
                Prefs.save(context, "radioPkg", radio.text.toString().trim())
                Prefs.save(context, "elmType", elmType.text.toString().trim().lowercase())
                Prefs.save(context, "elmTarget", elmTarget.text.toString().trim())
                Prefs.save(context, "pids", pids.text.toString())
                link?.close(); link = null
                Obd.start(context)
                CanbusBridge.start(context)
                Toast.makeText(context, "Guardado", Toast.LENGTH_SHORT).show()
            }
        })

        root.addView(label("Terminal ELM327 (ej: ATSH7E1, luego 2210D2). Pausa la lectura automática."))
        val cmd = field("", "comando")
        root.addView(cmd)
        root.addView(Button(this).apply {
            text = "ENVIAR"
            setOnClickListener { sendCmd(cmd.text.toString().trim()) }
        })
        term = TextView(this).apply { textSize = 14f; setTextColor(col(R.color.text)); text = "(terminal)" }
        root.addView(term)

        root.addView(label("Escuchar una acción (muestra todos sus extras en vivo)"))
        val act = field("", "acción del broadcast")
        root.addView(act)
        root.addView(Button(this).apply {
            text = "ESCUCHAR"
            setOnClickListener { listen(act.text.toString().trim()) }
        })
        log = TextView(this).apply { textSize = 14f; setTextColor(col(R.color.text)); text = "(sin datos)" }
        root.addView(log)

        root.addView(label("Apps instaladas que parecen del canbus / MCU:"))
        val keys = listOf("canbus", "xinbu", "syu", "fyt", "mcu", "carset", "cardata")
        val found = packageManager.getInstalledApplications(0)
            .filter { a -> keys.any { a.packageName.lowercase().contains(it) } }
            .joinToString("\n") { it.packageName }
        root.addView(TextView(this).apply {
            text = if (found.isEmpty()) "(ninguna encontrada)" else found
            textSize = 14f; setTextColor(col(R.color.textSub))
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    @Suppress("DEPRECATION")
    private fun listen(action: String) {
        if (action.isEmpty()) return
        rx?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val sb = StringBuilder("▶ ${i.action}\n")
                i.extras?.keySet()?.forEach { k ->
                    val v = i.extras?.get(k)
                    sb.append("   $k = $v (${v?.javaClass?.simpleName})\n")
                }
                log.text = sb.toString() + "\n" + log.text
            }
        }
        registerReceiver(r, IntentFilter(action))
        rx = r
        log.text = "Escuchando: $action …"
    }

    private fun sendCmd(cmd: String) {
        if (cmd.isEmpty()) return
        Thread {
            val out = try {
                if (link == null) {
                    Obd.stop()
                    val l = ElmLink()
                    l.open(Prefs.elmType(this), Prefs.elmTarget(this))
                    l.send("ATE0"); l.send("ATH0")
                    link = l
                }
                link!!.send(cmd)
            } catch (e: Exception) {
                link?.close(); link = null
                "Error: ${e.message}"
            }
            runOnUiThread { term.text = "> $cmd\n$out\n\n" + term.text }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        link?.close(); link = null
        Obd.start(this)
        rx?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
    }
}
