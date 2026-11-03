package com.channelpulse.tv.data

import java.net.URLDecoder

/** What the user typed to identify a channel. */
sealed interface ChannelRef {
    data class Id(val id: String) : ChannelRef
    data class Handle(val handle: String) : ChannelRef
    data class Username(val name: String) : ChannelRef

    companion object {
        private val channelId = Regex("^UC[A-Za-z0-9_-]{22}$")

        /**
         * Accepts @handle, handle, a channel ID (UC…), or any youtube.com channel URL:
         * /@handle, /channel/UC…, /user/name, /c/name.
         */
        fun parse(input: String): ChannelRef? {
            var s = input.trim()
            if (s.isEmpty()) return null
            s = s.substringBefore('?').substringBefore('#').trimEnd('/')

            val marker = "youtube.com/"
            val idx = s.lowercase().indexOf(marker)
            if (idx >= 0) {
                val parts = s.substring(idx + marker.length).split('/').filter { it.isNotEmpty() }.map { decode(it) }
                if (parts.isEmpty()) return null
                val first = parts[0]
                return when {
                    first.startsWith("@") -> Handle(first)
                    first.equals("channel", ignoreCase = true) && parts.size > 1 -> Id(parts[1])
                    first.equals("user", ignoreCase = true) && parts.size > 1 -> Username(parts[1])
                    first.equals("c", ignoreCase = true) && parts.size > 1 -> Handle("@" + parts[1])
                    else -> Handle("@$first")
                }
            }
            if (channelId.matches(s)) return Id(s)
            val handle = decode(s).replace(" ", "")
            return Handle(if (handle.startsWith("@")) handle else "@$handle")
        }

        private fun decode(s: String): String =
            try { URLDecoder.decode(s, "UTF-8") } catch (e: Exception) { s }
    }
}
