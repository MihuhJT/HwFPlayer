package com.hwf.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Playlist(val name: String, val ids: List<Long>)

/**
 * Everything the app keeps on the player: user playlists and listening stats.
 * Plain JSON files in the app's private folder. Shared by the UI and the playback service.
 */
object Store {
    private var dir: File? = null

    val playlists = mutableStateListOf<Playlist>()

    /** Name of what's playing now, shown top-left ("Случайные 20", a playlist, "Поиск: …"). */
    var source by mutableStateOf("Случайные 20")

    // Stats: seconds per day ("2026-10-17"), seconds per artist, plays per track id
    val days = HashMap<String, Long>()
    val artists = HashMap<String, Long>()
    val plays = HashMap<Long, Int>()
    private var unsavedSeconds = 0

    @Synchronized
    fun init(context: Context) {
        if (dir != null) return
        dir = context.filesDir
        loadPlaylists()
        loadStats()
    }

    // ---- playlists ----

    fun addPlaylist(name: String): Int {
        playlists += Playlist(name, emptyList())
        savePlaylists()
        return playlists.lastIndex
    }

    fun toggleTrack(index: Int, id: Long) {
        val p = playlists[index]
        playlists[index] = p.copy(ids = if (id in p.ids) p.ids - id else p.ids + id)
        savePlaylists()
    }

    private fun loadPlaylists() {
        val f = File(dir, "playlists.json")
        if (!f.exists()) return
        runCatching {
            val arr = JSONArray(f.readText())
            for (k in 0 until arr.length()) {
                val o = arr.getJSONObject(k)
                val ids = o.getJSONArray("ids")
                playlists += Playlist(o.getString("name"), List(ids.length()) { ids.getLong(it) })
            }
        }
    }

    private fun savePlaylists() {
        val arr = JSONArray()
        playlists.forEach { p -> arr.put(JSONObject().put("name", p.name).put("ids", JSONArray(p.ids))) }
        writeAtomic("playlists.json", arr.toString())
    }

    // ---- stats ----

    fun dayKey(d: Date = Date()): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(d)

    /** Called once a second by the service while music plays. */
    @Synchronized
    fun logSecond(artist: String) {
        val k = dayKey()
        days[k] = (days[k] ?: 0) + 1
        artists[artist] = (artists[artist] ?: 0) + 1
        if (++unsavedSeconds >= 15) saveStats()
    }

    @Synchronized
    fun countPlay(id: Long) {
        plays[id] = (plays[id] ?: 0) + 1
    }

    @Synchronized
    fun saveStats() {
        unsavedSeconds = 0
        val o = JSONObject()
        o.put("days", JSONObject(days.mapValues { it.value }))
        o.put("artists", JSONObject(artists.mapValues { it.value }))
        o.put("plays", JSONObject(plays.mapKeys { it.key.toString() }.mapValues { it.value }))
        writeAtomic("stats.json", o.toString())
    }

    private fun loadStats() {
        val f = File(dir, "stats.json")
        if (!f.exists()) return
        runCatching {
            val o = JSONObject(f.readText())
            o.optJSONObject("days")?.let { d -> d.keys().forEach { days[it] = d.getLong(it) } }
            o.optJSONObject("artists")?.let { a -> a.keys().forEach { artists[it] = a.getLong(it) } }
            o.optJSONObject("plays")?.let { p -> p.keys().forEach { plays[it.toLong()] = p.getInt(it) } }
        }
    }

    private fun writeAtomic(name: String, text: String) {
        val d = dir ?: return
        val tmp = File(d, "$name.tmp")
        tmp.writeText(text)
        tmp.renameTo(File(d, name))
    }
}
