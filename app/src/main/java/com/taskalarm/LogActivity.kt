package com.taskalarm

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class LogActivity : AppCompatActivity() {
    private lateinit var listView: ListView
    private lateinit var emptyView: TextView
    private var entries: List<LogEntry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        listView = findViewById(R.id.logList)
        emptyView = findViewById(R.id.logEmpty)
        listView.adapter = LogAdapter()

        findViewById<TextView>(R.id.clearButton).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.log_clear)
                .setMessage(R.string.log_clear_confirm)
                .setPositiveButton(R.string.delete) { _, _ ->
                    DiagLog.clear(this)
                    Toast.makeText(this, R.string.log_cleared, Toast.LENGTH_SHORT).show()
                    refresh()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        entries = DiagLog.all(this)
        (listView.adapter as BaseAdapter).notifyDataSetChanged()
        val empty = entries.isEmpty()
        listView.visibility = if (empty) View.GONE else View.VISIBLE
        emptyView.visibility = if (empty) View.VISIBLE else View.GONE
    }

    private inner class LogAdapter : BaseAdapter() {
        override fun getCount(): Int = entries.size

        override fun getItem(position: Int): LogEntry = entries[position]

        override fun getItemId(position: Int): Long = entries[position].time

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(this@LogActivity)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            val entry = entries[position]
            view.findViewById<TextView>(android.R.id.text1).apply {
                text = "${entry.timeText()}  ${entry.event}"
                setTextColor(getColor(R.color.text_primary))
                textSize = 15f
            }
            view.findViewById<TextView>(android.R.id.text2).apply {
                text = entry.detail
                setTextColor(getColor(R.color.text_secondary))
                textSize = 13f
                visibility = if (entry.detail.isBlank()) View.GONE else View.VISIBLE
            }
            return view
        }
    }
}
