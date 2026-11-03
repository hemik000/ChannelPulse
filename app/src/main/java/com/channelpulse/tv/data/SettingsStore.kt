package com.channelpulse.tv.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Saved settings: API keys, tracked channels and refresh options. */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    private fun load(): AppSettings {
        val keys = try {
            val arr = JSONArray(prefs.getString("apiKeys", null) ?: "[]")
            List(arr.length()) { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
        val channels = try {
            val arr = JSONArray(prefs.getString("channels", null) ?: "[]")
            List(arr.length()) {
                val o = arr.getJSONObject(it)
                TrackedChannel(o.getString("id"), o.optString("title"), o.optString("handle"), o.optString("thumb"))
            }
        } catch (e: Exception) {
            emptyList()
        }
        return AppSettings(
            apiKeys = keys,
            channels = channels,
            refreshSeconds = prefs.getInt("refreshSeconds", 0),
            wallCycleSeconds = prefs.getInt("wallCycleSeconds", 20),
            phoneSetup = prefs.getBoolean("phoneSetup", true),
            pixelShift = prefs.getBoolean("pixelShift", true),
            nightDimStart = prefs.getInt("nightDimStart", -1),
            nightDimEnd = prefs.getInt("nightDimEnd", 7),
        )
    }

    @Synchronized
    private fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_state.value)
        if (next == _state.value) return
        _state.value = next
        val channels = JSONArray()
        next.channels.forEach {
            channels.put(JSONObject().put("id", it.id).put("title", it.title).put("handle", it.handle).put("thumb", it.thumb))
        }
        prefs.edit()
            .putString("apiKeys", JSONArray(next.apiKeys).toString())
            .putString("channels", channels.toString())
            .putInt("refreshSeconds", next.refreshSeconds)
            .putInt("wallCycleSeconds", next.wallCycleSeconds)
            .putBoolean("phoneSetup", next.phoneSetup)
            .putBoolean("pixelShift", next.pixelShift)
            .putInt("nightDimStart", next.nightDimStart)
            .putInt("nightDimEnd", next.nightDimEnd)
            .apply()
    }

    /** Returns false if the key was already saved or is empty. */
    fun addApiKey(raw: String): Boolean {
        val key = raw.trim().replace(" ", "")
        if (key.isEmpty() || key in _state.value.apiKeys) return false
        update { it.copy(apiKeys = it.apiKeys + key) }
        return true
    }

    fun removeApiKey(key: String) = update { it.copy(apiKeys = it.apiKeys - key) }

    /** Returns false if the channel is already tracked. */
    fun addChannel(channel: TrackedChannel): Boolean {
        if (_state.value.channels.any { it.id == channel.id }) return false
        update { it.copy(channels = it.channels + channel) }
        return true
    }

    fun removeChannel(id: String) = update { s -> s.copy(channels = s.channels.filterNot { it.id == id }) }

    fun moveChannel(id: String, by: Int) = update { s ->
        val list = s.channels.toMutableList()
        val from = list.indexOfFirst { it.id == id }
        if (from < 0) return@update s
        val to = (from + by).coerceIn(0, list.lastIndex)
        if (to == from) return@update s
        list.add(to, list.removeAt(from))
        s.copy(channels = list)
    }

    /** Keeps saved titles/avatars in step with what the API returns. */
    fun refreshChannelInfo(stats: Collection<ChannelStats>) = update { s ->
        val byId = stats.associateBy { it.id }
        s.copy(channels = s.channels.map { c ->
            val st = byId[c.id] ?: return@map c
            if (st.title == c.title && st.handle == c.handle && st.thumb == c.thumb) c
            else c.copy(title = st.title, handle = st.handle, thumb = st.thumb)
        })
    }

    fun setRefreshSeconds(seconds: Int) = update { it.copy(refreshSeconds = seconds.coerceAtLeast(0)) }

    fun setWallCycleSeconds(seconds: Int) = update { it.copy(wallCycleSeconds = seconds.coerceAtLeast(0)) }

    fun setPhoneSetup(on: Boolean) = update { it.copy(phoneSetup = on) }

    fun setPixelShift(on: Boolean) = update { it.copy(pixelShift = on) }

    /** [start] -1 turns night dimming off. */
    fun setNightDim(start: Int, end: Int) = update { it.copy(nightDimStart = start, nightDimEnd = end) }
}
