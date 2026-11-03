package com.channelpulse.tv.data

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Spreads requests across all saved API keys. Each key from a separate Google Cloud
 * project has its own 10,000 units/day, so more keys allow faster refreshes.
 * Tracks an estimate of units used today (resets at midnight Pacific, like Google's quota).
 */
class KeyPool(private val prefs: SharedPreferences) {

    private var day: String = QuotaClock.day()
    private val used = HashMap<String, Int>()
    private val exhausted = HashSet<String>()
    private val invalid = HashMap<String, String>()

    private val _state = MutableStateFlow<Map<String, KeyState>>(emptyMap())
    val state: StateFlow<Map<String, KeyState>> = _state.asStateFlow()

    init {
        try {
            val o = JSONObject(prefs.getString("usage", "{}") ?: "{}")
            if (o.optString("day") == day) {
                o.optJSONObject("used")?.let { u -> u.keys().forEach { k -> used[k] = u.optInt(k) } }
                o.optJSONObject("exhausted")?.let { e -> e.keys().forEach { k -> exhausted += k } }
            }
        } catch (ignored: Exception) {
        }
        publish()
    }

    @Synchronized
    private fun rollDay() {
        val today = QuotaClock.day()
        if (today != day) {
            day = today
            used.clear()
            exhausted.clear()
            persist()
        }
    }

    /** Picks the healthy key with the least use today, or null if none is usable. */
    @Synchronized
    fun next(keys: List<String>): String? {
        rollDay()
        return keys.filter { it !in exhausted && it !in invalid }.minByOrNull { used[it] ?: 0 }
    }

    @Synchronized
    fun healthyCount(keys: List<String>): Int {
        rollDay()
        return keys.count { it !in exhausted && it !in invalid }
    }

    @Synchronized
    fun charge(key: String, units: Int) {
        used[key] = (used[key] ?: 0) + units
        persist()
    }

    @Synchronized
    fun markExhausted(key: String) {
        exhausted += key
        persist()
    }

    @Synchronized
    fun markInvalid(key: String, reason: String) {
        invalid[key] = reason
        publish()
    }

    /** Called when keys change, so a fixed key gets another chance. */
    @Synchronized
    fun forgetProblems(key: String) {
        invalid.remove(key)
        publish()
    }

    /** Gives rejected keys another try (e.g. after enabling the API in Cloud Console). */
    @Synchronized
    fun forgetAllProblems() {
        invalid.clear()
        publish()
    }

    private fun persist() {
        val u = JSONObject()
        used.forEach { (k, v) -> u.put(k, v) }
        val e = JSONObject()
        exhausted.forEach { e.put(it, true) }
        prefs.edit().putString("usage", JSONObject().put("day", day).put("used", u).put("exhausted", e).toString()).apply()
        publish()
    }

    private fun publish() {
        val all = used.keys + exhausted + invalid.keys
        _state.value = all.associateWith { KeyState(used[it] ?: 0, it in exhausted, invalid[it]) }
    }
}
