package com.channelpulse.tv.ui

import com.channelpulse.tv.data.ChannelUi
import com.channelpulse.tv.data.Delta
import com.channelpulse.tv.data.Fmt
import com.channelpulse.tv.data.Stats
import com.channelpulse.tv.data.VideoSample

/** Numbers derived from the readings this TV has recorded. */
object Metrics {
    const val HOUR = 3_600_000L
    const val DAY = 24 * HOUR

    fun views(c: ChannelUi, now: Long, windowMs: Long): Delta? =
        Stats.delta(c.history, now, windowMs, { it.t }, { it.views })

    fun subs(c: ChannelUi, now: Long, windowMs: Long): Delta? =
        Stats.delta(c.history, now, windowMs, { it.t }, { it.subs })

    fun video(points: List<VideoSample>, now: Long, windowMs: Long): Delta? =
        Stats.delta(points, now, windowMs, { it.t }, { it.views })

    /**
     * Views gained across the channel's newest uploads. YouTube refreshes video view counts far more
     * often than the channel's total, so this moves when the channel total sits still.
     */
    fun uploadViews(c: ChannelUi, now: Long, windowMs: Long): Delta? {
        var change = 0L
        var span = 0L
        var found = false
        for (v in c.videos) {
            val d = video(c.videoHistory[v.id].orEmpty(), now, windowMs) ?: continue
            change += d.change
            span = maxOf(span, d.spanMs)
            found = true
        }
        return if (found) Delta(change, span) else null
    }

    /** Views per 2 minutes over the last hour, summed across the newest uploads (30 bars). */
    fun uploadBars(c: ChannelUi, now: Long): List<Long> {
        val perVideo = c.videos.map { v ->
            Stats.buckets(c.videoHistory[v.id].orEmpty(), now, 2 * 60_000L, 30, { it.t }, { it.views })
        }
        return List(30) { i ->
            val values = perVideo.map { it[i] }.filter { it >= 0 }
            if (values.isEmpty()) -1L else values.sum()
        }
    }

    /** "+1,234", or "+1,234 in 23m" while history doesn't yet cover the whole window. */
    fun label(d: Delta?, windowMs: Long): String? {
        if (d == null) return null
        val s = Fmt.signed(d.change)
        return if (d.spanMs >= windowMs * 0.9) s else "$s in ${Fmt.duration(d.spanMs)}"
    }

    fun positive(d: Delta?): Boolean? = d?.let { if (it.change == 0L) null else it.change > 0 }

    /** Views per hour over the last 48 hours (48 bars). */
    fun last48hBars(c: ChannelUi, now: Long): List<Long> =
        Stats.buckets(c.history, now, HOUR, 48, { it.t }, { it.views })
}
