package com.lain.notes

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Note(
    val id: String,
    val title: String,
    val body: String,
    val ts: Long,
    val pinned: Boolean = false,
)

class NotesStore(context: Context) {

    private val prefs = context.getSharedPreferences("lain_os", Context.MODE_PRIVATE)

    fun load(): MutableList<Note> {
        val out = mutableListOf<Note>()
        try {
            val raw = prefs.getString("notes", "[]") ?: "[]"
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    Note(
                        id = o.getString("id"),
                        title = o.optString("title", ""),
                        body = o.optString("body", ""),
                        ts = o.optLong("ts", 0L),
                        pinned = o.optBoolean("pinned", false),
                    )
                )
            }
        } catch (e: Exception) {
            // corrupted store: start clean instead of crashing
        }
        out.sortWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.ts })
        return out
    }

    private fun saveAll(notes: List<Note>) {
        val arr = JSONArray()
        for (n in notes) {
            val o = JSONObject()
            o.put("id", n.id)
            o.put("title", n.title)
            o.put("body", n.body)
            o.put("ts", n.ts)
            o.put("pinned", n.pinned)
            arr.put(o)
        }
        prefs.edit().putString("notes", arr.toString()).apply()
    }

    fun newNote(): Note =
        Note(id = UUID.randomUUID().toString(), title = "", body = "", ts = System.currentTimeMillis())

    fun upsert(note: Note) {
        val notes = load()
        val idx = notes.indexOfFirst { it.id == note.id }
        val stamped = note.copy(ts = System.currentTimeMillis())
        if (idx >= 0) notes[idx] = stamped else notes.add(0, stamped)
        saveAll(notes)
    }

    fun delete(id: String) {
        saveAll(load().filterNot { it.id == id })
    }
}
