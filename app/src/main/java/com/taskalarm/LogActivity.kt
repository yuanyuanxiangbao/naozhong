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
        findViewById<View>(android.R.id.content).applySystemBarsPadding()

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
                .inflate(R.layout.item_log, parent, false)
            val entry = entries[position]
            view.findViewById<TextView>(R.id.logTime).text = entry.timeText()
            view.findViewById<TextView>(R.id.logEvent).text = entry.event
            view.findViewById<TextView>(R.id.logDetail).apply {
                text = entry.detail
                visibility = if (entry.detail.isBlank()) View.GONE else View.VISIBLE
            }
            return view
        }
    }
}
