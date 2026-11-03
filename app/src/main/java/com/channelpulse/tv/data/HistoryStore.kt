package com.channelpulse.tv.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Readings recorded on this TV. Channels keep 48 hours (saved to disk) so the app can
 * draw its own "last 60 minutes" / "last 48 hours" charts; videos keep 2 hours in memory.
 */
class HistoryStore(private val file: File) {

    companion object {
        const val CHANNEL_KEEP_MS = 48L * 3600_000L
        const val VIDEO_KEEP_MS = 2L * 3600_000L
        private const val MIN_GAP_MS = 20_000L
    }

    private val channels = HashMap<String, ArrayList<Sample>>()
    private val videos = HashMap<String, ArrayList<VideoSample>>()
    private val _version = MutableStateFlow(0L)

    /** Bumps whenever new readings are recorded. */
    val version: StateFlow<Long> = _version.asStateFlow()

    init {
        load()
    }

    @Synchronized
    fun recordChannel(id: String, t: Long, subs: Long, views: Long) {
        val list = channels.getOrPut(id) { ArrayList() }
        val last = list.lastOrNull()
        if (last != null && t - last.t < MIN_GAP_MS) {
            list[list.lastIndex] = Sample(last.t, subs, views)
        } else {
            list.add(Sample(t, subs, views))
        }
        val cutoff = t - CHANNEL_KEEP_MS
        while (list.isNotEmpty() && list[0].t < cutoff) list.removeAt(0)
    }

    @Synchronized
    fun recordVideo(id: String, t: Long, views: Long) {
        val list = videos.getOrPut(id) { ArrayList() }
        val last = list.lastOrNull()
        if (last != null && t - last.t < MIN_GAP_MS) {
            list[list.lastIndex] = VideoSample(last.t, views)
        } else {
            list.add(VideoSample(t, views))
        }
        val cutoff = t - VIDEO_KEEP_MS
        while (list.isNotEmpty() && list[0].t < cutoff) list.removeAt(0)
    }

    fun bump() {
        _version.update { it + 1 }
    }

    @Synchronized
    fun channel(id: String): List<Sample> = channels[id]?.toList().orEmpty()

    @Synchronized
    fun video(id: String): List<VideoSample> = videos[id]?.toList().orEmpty()

    @Synchronized
    fun retain(channelIds: Set<String>, videoIds: Set<String>) {
        channels.keys.retainAll(channelIds)
        videos.keys.retainAll(videoIds)
    }

    /** Keeps full detail for the last 2 hours and one reading per 2 minutes before that. */
    @Synchronized
    private fun compact(now: Long) {
        val fineFrom = now - 2L * 3600_000L
        channels.values.forEach { list ->
            if (list.size < 2) return@forEach
            val kept = ArrayList<Sample>(list.size)
            for (s in list) {
                val prev = kept.lastOrNull()
                if (prev == null || s.t >= fineFrom || s.t - prev.t >= 120_000L) kept.add(s)
            }
            list.clear()
            list.addAll(kept)
        }
    }

    @Synchronized
    fun save() {
        compact(System.currentTimeMillis())
        try {
            val root = JSONObject()
            channels.forEach { (id, list) ->
                val arr = JSONArray()
                list.forEach { s -> arr.put(JSONArray().put(s.t).put(s.subs).put(s.views)) }
                root.put(id, arr)
            }
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(root.toString())
            tmp.renameTo(file)
        } catch (ignored: Exception) {
        }
    }

    private fun load() {
        try {
            if (!file.exists()) return
            val root = JSONObject(file.readText())
            val cutoff = System.currentTimeMillis() - CHANNEL_KEEP_MS
            root.keys().forEach { id ->
                val arr = root.getJSONArray(id)
                val list = ArrayList<Sample>(arr.length())
                for (i in 0 until arr.length()) {
                    val p = arr.getJSONArray(i)
                    val s = Sample(p.getLong(0), p.getLong(1), p.getLong(2))
                    if (s.t >= cutoff) list.add(s)
                }
                channels[id] = list
            }
        } catch (ignored: Exception) {
        }
    }
}
