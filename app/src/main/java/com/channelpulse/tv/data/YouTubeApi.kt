package com.channelpulse.tv.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class ApiException(val code: Int, val reason: String, message: String) : Exception(message) {
    /** The key's daily quota is used up (resets at midnight Pacific). */
    val isQuota: Boolean
        get() = reason == "quotaExceeded" || reason == "dailyLimitExceeded"

    /** The key itself is wrong, restricted or the API isn't enabled on its project. */
    val isKeyProblem: Boolean
        get() = (code == 400 || code == 403) && !isQuota && (
            reason in setOf("keyInvalid", "keyExpired", "accessNotConfigured", "ipRefererBlocked", "forbidden") ||
                (message ?: "").contains("API key", ignoreCase = true)
            )
}

/**
 * Thin client for the public YouTube Data API v3. Every method here costs 1 quota unit.
 * Calls are blocking; run them on Dispatchers.IO.
 */
class YouTubeApi(private val http: OkHttpClient) {

    private val base = "https://www.googleapis.com/youtube/v3"

    fun channelsById(ids: List<String>, key: String): List<ChannelStats> {
        if (ids.isEmpty()) return emptyList()
        val json = get(
            "channels",
            mapOf("part" to "snippet,statistics,contentDetails", "id" to ids.joinToString(","), "maxResults" to "50"),
            key
        )
        return items(json).map { parseChannel(it) }
    }

    fun channelByHandle(handle: String, key: String): ChannelStats? {
        val json = get("channels", mapOf("part" to "snippet,statistics,contentDetails", "forHandle" to handle), key)
        return items(json).firstOrNull()?.let { parseChannel(it) }
    }

    fun channelByUsername(name: String, key: String): ChannelStats? {
        val json = get("channels", mapOf("part" to "snippet,statistics,contentDetails", "forUsername" to name), key)
        return items(json).firstOrNull()?.let { parseChannel(it) }
    }

    /** Newest video IDs from a channel's uploads playlist. */
    fun recentUploads(playlistId: String, max: Int, key: String): List<String> {
        val json = get(
            "playlistItems",
            mapOf("part" to "contentDetails", "playlistId" to playlistId, "maxResults" to max.toString()),
            key
        )
        return items(json).mapNotNull { it.optJSONObject("contentDetails")?.optString("videoId")?.takeIf { id -> id.isNotEmpty() } }
    }

    fun videos(ids: List<String>, key: String): List<VideoStats> {
        if (ids.isEmpty()) return emptyList()
        val json = get(
            "videos",
            mapOf("part" to "snippet,statistics,liveStreamingDetails", "id" to ids.joinToString(","), "maxResults" to "50"),
            key
        )
        return items(json).map { parseVideo(it) }
    }

    private fun get(path: String, params: Map<String, String>, key: String): JSONObject {
        val url = "$base/$path".toHttpUrl().newBuilder().apply {
            params.forEach { (k, v) -> addQueryParameter(k, v) }
            addQueryParameter("key", key)
        }.build()
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                var reason = ""
                var message = "HTTP ${response.code}"
                try {
                    val err = JSONObject(body).getJSONObject("error")
                    message = err.optString("message", message)
                    val errors = err.optJSONArray("errors")
                    if (errors != null && errors.length() > 0) reason = errors.getJSONObject(0).optString("reason")
                } catch (ignored: Exception) {
                }
                throw ApiException(response.code, reason, message)
            }
            return JSONObject(body)
        }
    }

    private fun items(json: JSONObject): List<JSONObject> {
        val arr: JSONArray = json.optJSONArray("items") ?: return emptyList()
        return List(arr.length()) { arr.getJSONObject(it) }
    }

    private fun thumb(snippet: JSONObject): String {
        val t = snippet.optJSONObject("thumbnails") ?: return ""
        for (size in listOf("medium", "high", "default")) {
            val url = t.optJSONObject(size)?.optString("url").orEmpty()
            if (url.isNotEmpty()) return url
        }
        return ""
    }

    private fun num(o: JSONObject?, name: String): Long =
        o?.optString(name, "0")?.toLongOrNull() ?: 0L

    private fun parseChannel(o: JSONObject): ChannelStats {
        val snippet = o.optJSONObject("snippet") ?: JSONObject()
        val stats = o.optJSONObject("statistics")
        val uploads = o.optJSONObject("contentDetails")?.optJSONObject("relatedPlaylists")?.optString("uploads").orEmpty()
        return ChannelStats(
            id = o.optString("id"),
            title = snippet.optString("title"),
            handle = snippet.optString("customUrl"),
            thumb = thumb(snippet),
            subscribers = num(stats, "subscriberCount"),
            subscribersHidden = stats?.optBoolean("hiddenSubscriberCount", false) ?: false,
            views = num(stats, "viewCount"),
            videoCount = num(stats, "videoCount"),
            uploadsPlaylist = uploads,
            fetchedAt = System.currentTimeMillis(),
        )
    }

    private fun parseVideo(o: JSONObject): VideoStats {
        val snippet = o.optJSONObject("snippet") ?: JSONObject()
        val stats = o.optJSONObject("statistics")
        val live = o.optJSONObject("liveStreamingDetails")
        val broadcast = snippet.optString("liveBroadcastContent")
        val viewers = live?.optString("concurrentViewers", "")?.toLongOrNull()
        return VideoStats(
            id = o.optString("id"),
            channelId = snippet.optString("channelId"),
            title = snippet.optString("title"),
            thumb = thumb(snippet),
            publishedAt = Fmt.parseIsoUtc(snippet.optString("publishedAt")),
            views = num(stats, "viewCount"),
            likes = num(stats, "likeCount"),
            comments = num(stats, "commentCount"),
            isLive = broadcast == "live",
            isUpcoming = broadcast == "upcoming",
            concurrentViewers = viewers,
        )
    }
}
