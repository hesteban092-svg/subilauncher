package com.miauto.launcher

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class AppsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pm = packageManager
        val d = resources.displayMetrics.density
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
        val grid = GridView(this)
        grid.numColumns = 6
        grid.verticalSpacing = (16 * d).toInt()
        grid.setPadding((16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt())
        grid.setBackgroundColor(ContextCompat.getColor(this, R.color.bg))
        grid.adapter = object : BaseAdapter() {
            override fun getCount() = apps.size
            override fun getItem(p: Int) = apps[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, v: View?, parent: ViewGroup?): View {
                val ri = apps[p]
                val box = LinearLayout(this@AppsActivity)
                box.orientation = LinearLayout.VERTICAL
                box.gravity = Gravity.CENTER
                val icon = ImageView(this@AppsActivity)
                icon.setImageDrawable(ri.loadIcon(pm))
                box.addView(icon, LinearLayout.LayoutParams((64 * d).toInt(), (64 * d).toInt()))
                val tv = TextView(this@AppsActivity)
                tv.text = ri.loadLabel(pm)
                tv.maxLines = 1
                tv.gravity = Gravity.CENTER
                tv.textSize = 14f
                tv.setTextColor(ContextCompat.getColor(this@AppsActivity, R.color.text))
                box.addView(tv)
                return box
            }
        }
        grid.setOnItemClickListener { _, _, pos, _ ->
            val i = pm.getLaunchIntentForPackage(apps[pos].activityInfo.packageName)
            if (i != null) startActivity(i)
        }
        setContentView(grid)
    }
}
