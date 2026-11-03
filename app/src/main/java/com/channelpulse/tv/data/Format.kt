package com.channelpulse.tv.data

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

object Fmt {
    private val grouping: NumberFormat = NumberFormat.getIntegerInstance()

    fun full(n: Long): String = synchronized(grouping) { grouping.format(n) }

    fun signed(n: Long): String = if (n > 0) "+" + full(n) else full(n)

    fun compact(n: Long): String {
        val a = abs(n)
        val sign = if (n < 0) "-" else ""
        return sign + when {
            a >= 1_000_000_000L -> trim(a / 1_000_000_000.0) + "B"
            a >= 1_000_000L -> trim(a / 1_000_000.0) + "M"
            a >= 1_000L -> trim(a / 1_000.0) + "K"
            else -> a.toString()
        }
    }

    private fun trim(v: Double): String {
        val s = if (v >= 100) String.format(Locale.US, "%.0f", v) else String.format(Locale.US, "%.1f", v)
        return s.removeSuffix(".0")
    }

    fun duration(ms: Long): String {
        val s = ms / 1000
        return when {
            s < 60 -> "${s}s"
            s < 3600 -> "${s / 60}m"
            s < 86_400 -> {
                val h = s / 3600
                val m = (s % 3600) / 60
                if (m == 0L || h >= 10) "${h}h" else "${h}h ${m}m"
            }
            else -> "${s / 86_400}d"
        }
    }

    fun ago(t: Long, now: Long): String {
        if (t <= 0) return "never"
        val d = (now - t).coerceAtLeast(0)
        return if (d < 5_000) "just now" else duration(d) + " ago"
    }

    fun clock(now: Long): String = SimpleDateFormat("h:mm", Locale.getDefault()).format(Date(now))

    fun timeOfDay(t: Long): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(t))

    /** Masks an API key for display: AIza…x1Yz */
    fun maskKey(key: String): String =
        if (key.length <= 8) "••••" else key.take(4) + "…" + key.takeLast(4)

    /** Parses YouTube timestamps such as 2026-11-01T12:34:56Z or 2026-11-01T12:34:56.123Z. */
    fun parseIsoUtc(s: String?): Long {
        if (s.isNullOrBlank() || s.length < 19) return 0L
        return try {
            val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            f.timeZone = TimeZone.getTimeZone("UTC")
            f.parse(s.substring(0, 19))?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}

/** YouTube API quotas reset at midnight Pacific time. */
object QuotaClock {
    private val pacific: TimeZone = TimeZone.getTimeZone("America/Los_Angeles")

    fun day(now: Long = System.currentTimeMillis()): String {
        val c = Calendar.getInstance(pacific)
        c.timeInMillis = now
        return String.format(
            Locale.US, "%04d-%02d-%02d",
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun nextReset(now: Long = System.currentTimeMillis()): Long {
        val c = Calendar.getInstance(pacific)
        c.timeInMillis = now
        c.add(Calendar.DAY_OF_MONTH, 1)
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }
}

/** Change of a value over a time window, read from recorded history. */
data class Delta(val change: Long, val spanMs: Long)

object Stats {
    /**
     * Change between the oldest reading inside [windowMs] and the newest reading.
     * When history doesn't cover the whole window yet, [Delta.spanMs] says how much it covers.
     */
    fun <T> delta(
        points: List<T>,
        now: Long,
        windowMs: Long,
        time: (T) -> Long,
        value: (T) -> Long,
    ): Delta? {
        if (points.size < 2) return null
        val from = now - windowMs
        val base = points.firstOrNull { time(it) >= from } ?: return null
        val last = points.last()
        val span = time(last) - time(base)
        if (span < 30_000L) return null
        return Delta(value(last) - value(base), span)
    }

    private fun <T> valueAt(points: List<T>, t: Long, time: (T) -> Long, value: (T) -> Long): Long? {
        var result: Long? = null
        for (p in points) {
            if (time(p) <= t) result = value(p) else break
        }
        return result
    }

    /**
     * Splits the last [count] × [bucketMs] into buckets and returns the gain in each one.
     * Buckets with no recorded data are -1, so charts can draw them as empty.
     */
    fun <T> buckets(
        points: List<T>,
        now: Long,
        bucketMs: Long,
        count: Int,
        time: (T) -> Long,
        value: (T) -> Long,
    ): List<Long> {
        if (points.isEmpty()) return List(count) { -1L }
        val firstT = time(points.first())
        val lastT = time(points.last())
        return List(count) { i ->
            val start = now - (count - i).toLong() * bucketMs
            val end = start + bucketMs
            if (end <= firstT || start > lastT) {
                -1L
            } else {
                val a = valueAt(points, start, time, value) ?: value(points.first())
                val b = valueAt(points, end, time, value) ?: value(points.first())
                (b - a).coerceAtLeast(0L)
            }
        }
    }
}
