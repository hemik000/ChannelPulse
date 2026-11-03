package com.channelpulse.tv.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil

/**
 * Polls the YouTube Data API for every tracked channel and their newest uploads.
 *
 * Cost per refresh: 1 unit per 50 channels + 1 unit per 50 videos (all batched).
 * Upload lists are refreshed every [UPLOADS_TTL_MS] at 1 unit per channel.
 * In automatic mode the interval is the shortest one that fits today's quota across all keys.
 */
class PulseRepository(
    private val api: YouTubeApi,
    private val settings: SettingsStore,
    private val keys: KeyPool,
    private val history: HistoryStore,
) {
    companion object {
        const val DAILY_QUOTA = 10_000
        const val VIDEOS_PER_CHANNEL = 5
        const val UPLOADS_TTL_MS = 10L * 60_000L
        const val MIN_INTERVAL_SEC = 10
        const val MAX_INTERVAL_SEC = 600
        private const val SAVE_EVERY_MS = 5L * 60_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stats = MutableStateFlow<Map<String, ChannelStats>>(emptyMap())
    private val videos = MutableStateFlow<Map<String, List<VideoStats>>>(emptyMap())
    // Written by the polling loop and by removeChannel (UI or phone-server thread).
    private val uploads = ConcurrentHashMap<String, Pair<Long, List<String>>>()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var job: Job? = null
    private var lastSave = 0L

    private val _status = MutableStateFlow(PollStatus())
    val status: StateFlow<PollStatus> = _status.asStateFlow()

    val channels: StateFlow<List<ChannelUi>> =
        combine(settings.state, stats, videos, history.version) { s, st, vids, _ ->
            s.channels.map { c -> ChannelUi(c, st[c.id], vids[c.id].orEmpty(), history.channel(c.id)) }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        // Refresh straight away when keys, channels or the interval change.
        scope.launch {
            settings.state
                .map { Triple(it.apiKeys, it.channels.map { c -> c.id }, it.refreshSeconds) }
                .distinctUntilChanged()
                .drop(1)
                .collect { wake.trySend(Unit) }
        }
    }

    fun videoHistory(videoId: String): List<VideoSample> = history.video(videoId)

    fun start() {
        if (job?.isActive == true) return
        keys.forgetAllProblems()
        job = scope.launch {
            _status.update { it.copy(polling = true) }
            while (isActive) {
                val waitMs = try {
                    pollOnce()
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _status.update { it.copy(error = describe(e), lastAttemptAt = System.currentTimeMillis()) }
                    30_000L
                }
                withTimeoutOrNull(waitMs) { wake.receive() }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _status.update { it.copy(polling = false) }
        scope.launch(Dispatchers.IO) { history.save() }
    }

    fun refreshNow() {
        keys.forgetAllProblems()
        wake.trySend(Unit)
    }

    fun addApiKey(raw: String): Boolean {
        val ok = settings.addApiKey(raw)
        if (ok) keys.forgetProblems(raw.trim().replace(" ", ""))
        return ok
    }

    /** Looks up a channel by @handle, URL or ID (1 quota unit) and starts tracking it. */
    suspend fun addChannel(input: String): Result<TrackedChannel> = runCatching {
        val ref = ChannelRef.parse(input) ?: error("Type a @handle, channel URL or channel ID.")
        val found = when (ref) {
            is ChannelRef.Id -> call { key -> api.channelsById(listOf(ref.id), key) }.firstOrNull()
            is ChannelRef.Handle -> call { key -> api.channelByHandle(ref.handle, key) }
            is ChannelRef.Username -> call { key -> api.channelByUsername(ref.name, key) }
        } ?: error("No channel found for \"${input.trim()}\".")
        val tracked = TrackedChannel(found.id, found.title, found.handle, found.thumb)
        if (!settings.addChannel(tracked)) error("${found.title} is already on your list.")
        stats.update { it + (found.id to found) }
        history.recordChannel(found.id, found.fetchedAt, found.subscribers, found.views)
        history.bump()
        tracked
    }.recoverCatching { e -> throw IllegalStateException(describe(e), e) }

    fun removeChannel(id: String) {
        settings.removeChannel(id)
        uploads.remove(id)
    }

    // ---- polling ----------------------------------------------------------------------

    private suspend fun pollOnce(): Long {
        val s = settings.state.value
        val now = System.currentTimeMillis()
        if (s.apiKeys.isEmpty()) {
            _status.update { it.copy(error = null, healthyKeys = 0) }
            return 60_000L
        }
        if (s.channels.isEmpty()) {
            _status.update { it.copy(error = null) }
            return 60_000L
        }

        // 1. Channel statistics, 50 per request.
        val fresh = HashMap<String, ChannelStats>()
        for (chunk in s.channels.map { it.id }.chunked(50)) {
            call { key -> api.channelsById(chunk, key) }.forEach { fresh[it.id] = it }
        }

        // 2. Newest uploads per channel, refreshed every 10 minutes.
        for (c in fresh.values) {
            val cached = uploads[c.id]
            if (c.uploadsPlaylist.isNotEmpty() && (cached == null || now - cached.first > UPLOADS_TTL_MS)) {
                try {
                    val ids = call { key -> api.recentUploads(c.uploadsPlaylist, VIDEOS_PER_CHANNEL, key) }
                    uploads[c.id] = now to ids
                } catch (e: ApiException) {
                    // Channels without public uploads return 404 for the playlist.
                    if (e.code == 404) uploads[c.id] = now to emptyList() else throw e
                }
            }
        }
        uploads.keys.retainAll(fresh.keys)

        // 3. Video statistics, 50 per request.
        val videoIds = uploads.values.flatMap { it.second }.distinct()
        val freshVideos = ArrayList<VideoStats>()
        for (chunk in videoIds.chunked(50)) {
            freshVideos += call { key -> api.videos(chunk, key) }
        }

        // 4. Record and publish.
        val t = System.currentTimeMillis()
        fresh.values.forEach { history.recordChannel(it.id, t, it.subscribers, it.views) }
        freshVideos.forEach { history.recordVideo(it.id, t, it.views) }
        history.retain(fresh.keys, freshVideos.map { it.id }.toSet())
        history.bump()
        stats.value = fresh
        videos.value = freshVideos.groupBy { it.channelId }.mapValues { (_, list) -> list.sortedByDescending { it.publishedAt } }
        settings.refreshChannelInfo(fresh.values)

        if (t - lastSave > SAVE_EVERY_MS) {
            lastSave = t
            withContext(Dispatchers.IO) { history.save() }
        }

        val plan = plan(s, videoIds.size)
        _status.value = PollStatus(
            lastSuccessAt = t,
            lastAttemptAt = t,
            error = null,
            intervalSec = plan.intervalSec,
            unitsPerDay = plan.unitsPerDay,
            capacityPerDay = plan.capacityPerDay,
            healthyKeys = plan.healthyKeys,
            polling = true,
        )
        return plan.intervalSec * 1000L
    }

    data class Plan(val intervalSec: Int, val unitsPerDay: Int, val capacityPerDay: Int, val healthyKeys: Int)

    /** Works out the refresh interval and daily quota use for the current settings. */
    fun plan(s: AppSettings = settings.state.value, videoCount: Int = s.channels.size * VIDEOS_PER_CHANNEL, fixedSeconds: Int? = null): Plan {
        val channelCount = s.channels.size
        val healthy = keys.healthyCount(s.apiKeys)
        val perRefresh = ceil(channelCount / 50.0).toInt().coerceAtLeast(1) + ceil(videoCount / 50.0).toInt()
        val uploadsPerDay = channelCount * (86_400_000L / UPLOADS_TTL_MS).toInt()
        val capacity = (healthy * DAILY_QUOTA * 0.9).toInt()
        val chosen = fixedSeconds ?: s.refreshSeconds
        val interval = if (chosen > 0) {
            chosen
        } else {
            val budget = capacity - uploadsPerDay
            if (budget <= 0) MAX_INTERVAL_SEC
            else ceil(86_400.0 * perRefresh / budget).toInt().coerceIn(MIN_INTERVAL_SEC, MAX_INTERVAL_SEC)
        }
        val unitsPerDay = (86_400 / interval) * perRefresh + uploadsPerDay
        return Plan(interval, unitsPerDay, capacity, healthy)
    }

    /** Runs one API request with the least-used healthy key, moving to the next key on quota or key errors. */
    private suspend fun <T> call(block: (String) -> T): T {
        var lastError: Exception? = null
        repeat(10) {
            val key = keys.next(settings.state.value.apiKeys) ?: throw (lastError ?: NoUsableKeyException())
            keys.charge(key, 1)
            try {
                return withContext(Dispatchers.IO) { block(key) }
            } catch (e: ApiException) {
                when {
                    e.isQuota -> keys.markExhausted(key)
                    e.isKeyProblem -> keys.markInvalid(key, e.message ?: "Key rejected")
                    else -> throw e
                }
                lastError = e
            }
        }
        throw lastError ?: NoUsableKeyException()
    }

    class NoUsableKeyException : Exception("No usable API key")

    private fun describe(e: Throwable): String {
        val cause = if (e is IllegalStateException && e.cause != null && e.message == null) e.cause!! else e
        return when (cause) {
            is NoUsableKeyException -> {
                val saved = settings.state.value.apiKeys
                val states = keys.state.value
                val rejected = saved.mapNotNull { states[it]?.invalidReason }
                when {
                    saved.isEmpty() -> "Add a YouTube API key in Settings."
                    // Waiting for the quota reset won't fix a key YouTube rejects, so say so.
                    rejected.size == saved.size ->
                        (if (saved.size == 1) "Your API key was rejected: " else "All API keys were rejected: ") +
                            rejected.first() + " Check the key in Settings."
                    else -> "All API keys are out of quota" + (if (rejected.isNotEmpty()) " or rejected" else "") +
                        ". Quota resets at " + Fmt.timeOfDay(QuotaClock.nextReset()) + ". Add another key for more."
                }
            }
            is ApiException -> when {
                cause.isQuota -> "API quota used up for today. Resets at ${Fmt.timeOfDay(QuotaClock.nextReset())}."
                cause.isKeyProblem -> "API key problem: ${cause.message}"
                else -> "YouTube API error (${cause.code}): ${cause.message}"
            }
            is java.net.UnknownHostException, is java.net.SocketTimeoutException, is java.net.ConnectException ->
                "No internet connection. Retrying…"
            else -> cause.message ?: cause.javaClass.simpleName
        }
    }
}
