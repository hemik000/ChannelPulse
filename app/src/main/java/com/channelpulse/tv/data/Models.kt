package com.channelpulse.tv.data

/** A channel the user chose to track. Saved in settings. */
data class TrackedChannel(
    val id: String,
    val title: String,
    val handle: String,
    val thumb: String,
)

/** One reading of a channel from channels.list. */
data class ChannelStats(
    val id: String,
    val title: String,
    val handle: String,
    val thumb: String,
    val subscribers: Long,
    val subscribersHidden: Boolean,
    val views: Long,
    val videoCount: Long,
    val uploadsPlaylist: String,
    val fetchedAt: Long,
)

/** One reading of a video from videos.list. */
data class VideoStats(
    val id: String,
    val channelId: String,
    val title: String,
    val thumb: String,
    val publishedAt: Long,
    val views: Long,
    val likes: Long,
    val comments: Long,
    val isLive: Boolean,
    val isUpcoming: Boolean,
    val concurrentViewers: Long?,
)

/** A point in the locally recorded history of a channel. */
data class Sample(val t: Long, val subs: Long, val views: Long)

/** A point in the in-memory history of one video's views. */
data class VideoSample(val t: Long, val views: Long)

/** Everything the UI needs for one channel. */
data class ChannelUi(
    val channel: TrackedChannel,
    val stats: ChannelStats?,
    val videos: List<VideoStats>,
    val history: List<Sample>,
) {
    val id: String get() = channel.id
    val title: String get() = stats?.title ?: channel.title
    val handle: String get() = stats?.handle ?: channel.handle
    val thumb: String get() = stats?.thumb ?: channel.thumb
    val liveVideo: VideoStats? get() = videos.firstOrNull { it.isLive }
}

data class AppSettings(
    val apiKeys: List<String> = emptyList(),
    val channels: List<TrackedChannel> = emptyList(),
    /** 0 = automatic (fastest rate the quota allows). */
    val refreshSeconds: Int = 0,
    /** 0 = don't auto-cycle on the live wall. */
    val wallCycleSeconds: Int = 20,
    /** Whether the phone setup web page is served while the app is open. */
    val phoneSetup: Boolean = true,
)

data class KeyState(
    val used: Int = 0,
    val exhausted: Boolean = false,
    val invalidReason: String? = null,
)

data class PollStatus(
    val lastSuccessAt: Long = 0L,
    val lastAttemptAt: Long = 0L,
    val error: String? = null,
    val intervalSec: Int = 0,
    val unitsPerDay: Int = 0,
    val capacityPerDay: Int = 0,
    val healthyKeys: Int = 0,
    val polling: Boolean = false,
)
