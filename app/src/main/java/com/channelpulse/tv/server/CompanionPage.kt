package com.channelpulse.tv.server

import com.channelpulse.tv.data.AppSettings
import com.channelpulse.tv.data.ChannelUi
import com.channelpulse.tv.data.Fmt
import com.channelpulse.tv.data.KeyState
import com.channelpulse.tv.data.PulseRepository

/** HTML for the phone setup page. Plain HTML forms, no JavaScript needed. */
object CompanionPage {

    private fun esc(s: String): String = buildString(s.length) {
        for (c in s) when (c) {
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '&' -> append("&amp;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(c)
        }
    }

    /** Short non-reversible ID for a key, so the page never sends the full key back to the phone. */
    fun keyId(key: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
            .take(8).joinToString("") { "%02x".format(it) }

    private const val STYLE = """
        :root { color-scheme: dark; }
        * { box-sizing: border-box; }
        body { margin: 0; background: #0e0e14; color: #f2f2f5; font: 16px/1.45 system-ui, -apple-system, Segoe UI, Roboto, sans-serif; }
        main { max-width: 560px; margin: 0 auto; padding: 20px 16px 48px; }
        h1 { font-size: 22px; margin: 4px 0 2px; }
        h1 span { color: #ff3b48; }
        h2 { font-size: 15px; text-transform: uppercase; letter-spacing: .08em; color: #9a9aa8; margin: 28px 0 10px; }
        .sub { color: #9a9aa8; margin: 0 0 8px; font-size: 14px; }
        .msg { background: #1f2a1f; border: 1px solid #2f6b3a; color: #c9f2d2; padding: 10px 12px; border-radius: 10px; margin: 14px 0; }
        .card { background: #16161d; border: 1px solid #262632; border-radius: 14px; padding: 14px; }
        .row { display: flex; align-items: center; gap: 10px; padding: 10px 0; border-top: 1px solid #262632; }
        .row:first-child { border-top: 0; padding-top: 0; }
        .grow { flex: 1; min-width: 0; }
        .name { font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
        .meta { color: #9a9aa8; font-size: 13px; }
        .bad { color: #ff8a8a; }
        img.av { width: 40px; height: 40px; border-radius: 50%; background: #262632; flex: none; }
        textarea, input[type=text] { width: 100%; background: #0e0e14; color: #f2f2f5; border: 1px solid #33333f; border-radius: 10px; padding: 12px; font: inherit; }
        textarea { min-height: 76px; resize: vertical; }
        button { background: #ff3b48; color: #fff; border: 0; border-radius: 10px; padding: 11px 16px; font: inherit; font-weight: 600; }
        button.ghost { background: #262632; color: #f2f2f5; padding: 8px 11px; font-weight: 500; }
        form.inline { display: inline; margin: 0; }
        .stack > * + * { margin-top: 10px; }
        .chips { display: flex; flex-wrap: wrap; gap: 8px; }
        .chips button { background: #262632; color: #f2f2f5; font-weight: 500; }
        .chips button.on { background: #ff3b48; color: #fff; }
        ol { padding-left: 20px; margin: 6px 0 0; color: #c8c8d2; }
        a { color: #ff8a92; }
    """

    fun message(text: String): String =
        "<!doctype html><html><head><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'>" +
            "<title>Channel Pulse</title><style>$STYLE</style></head><body><main><h1>Channel <span>Pulse</span></h1>" +
            "<p class=msg>${esc(text)}</p></main></body></html>"

    fun render(
        token: String,
        settings: AppSettings,
        channels: List<ChannelUi>,
        keyStates: Map<String, KeyState>,
        message: String?,
        plan: PulseRepository.Plan,
    ): String {
        val t = esc(token)
        val hidden = "<input type=hidden name=t value='$t'>"
        val sb = StringBuilder()
        sb.append("<!doctype html><html><head><meta charset=utf-8>")
        sb.append("<meta name=viewport content='width=device-width,initial-scale=1'>")
        sb.append("<title>Channel Pulse setup</title><style>").append(STYLE).append("</style></head><body><main>")
        sb.append("<h1>Channel <span>Pulse</span></h1><p class=sub>Changes appear on your TV right away.</p>")
        if (!message.isNullOrBlank()) sb.append("<p class=msg>").append(esc(message)).append("</p>")

        // Channels
        sb.append("<h2>Add channels</h2><form method=post action='/channel/add' class='card stack'>").append(hidden)
        sb.append("<textarea name=q placeholder='@handle, youtube.com/@handle, or UC… channel ID&#10;One per line to add several'></textarea>")
        sb.append("<button type=submit>Add</button></form>")

        sb.append("<h2>Your channels (").append(channels.size).append(")</h2><div class=card>")
        if (channels.isEmpty()) sb.append("<p class=meta>No channels yet.</p>")
        channels.forEachIndexed { i, c ->
            val st = c.stats
            val subs = when {
                st == null -> "Loading…"
                st.subscribersHidden -> "Subscribers hidden"
                else -> Fmt.full(st.subscribers) + " subscribers"
            }
            val views = st?.let { " · " + Fmt.full(it.views) + " views" }.orEmpty()
            val id = esc(c.id)
            sb.append("<div class=row>")
            if (c.thumb.isNotEmpty()) sb.append("<img class=av src='").append(esc(c.thumb)).append("' alt=''>")
            else sb.append("<div class=av></div>")
            sb.append("<div class=grow><div class=name>").append(esc(c.title)).append("</div>")
            sb.append("<div class=meta>").append(esc(c.handle)).append(" ").append(esc(subs + views)).append("</div></div>")
            if (i > 0) sb.append("<form class=inline method=post action='/channel/move'>$hidden<input type=hidden name=id value='$id'><input type=hidden name=dir value=up><button class=ghost aria-label='Move up'>↑</button></form>")
            if (i < channels.lastIndex) sb.append("<form class=inline method=post action='/channel/move'>$hidden<input type=hidden name=id value='$id'><input type=hidden name=dir value=down><button class=ghost aria-label='Move down'>↓</button></form>")
            sb.append("<form class=inline method=post action='/channel/remove'>$hidden<input type=hidden name=id value='$id'><button class=ghost aria-label='Remove'>✕</button></form>")
            sb.append("</div>")
        }
        sb.append("</div>")

        // API keys
        sb.append("<h2>YouTube API keys</h2><div class='card stack'>")
        if (settings.apiKeys.isEmpty()) {
            sb.append("<p class=meta>You need at least one free API key:</p><ol>")
            sb.append("<li>Open <a href='https://console.cloud.google.com/apis/library/youtube.googleapis.com' target=_blank>Google Cloud Console → YouTube Data API v3</a> and click <b>Enable</b>.</li>")
            sb.append("<li>Go to <b>APIs &amp; Services → Credentials → Create credentials → API key</b>.</li>")
            sb.append("<li>Optional: restrict the key to <b>YouTube Data API v3</b>. Don't add an app restriction.</li>")
            sb.append("<li>Paste the key below.</li></ol>")
        }
        for (key in settings.apiKeys) {
            val ks = keyStates[key] ?: KeyState()
            val status = when {
                ks.invalidReason != null -> "<span class=bad>Rejected: ${esc(ks.invalidReason)}</span>"
                ks.exhausted -> "<span class=bad>Out of quota until midnight Pacific</span>"
                else -> "~${Fmt.full(ks.used.toLong())} of 10,000 units used today"
            }
            sb.append("<div class=row><div class=grow><div class=name>").append(esc(Fmt.maskKey(key))).append("</div>")
            sb.append("<div class=meta>").append(status).append("</div></div>")
            sb.append("<form class=inline method=post action='/key/remove'>$hidden<input type=hidden name=kid value='${keyId(key)}'><button class=ghost>Remove</button></form></div>")
        }
        sb.append("<form method=post action='/key/add' class=stack>").append(hidden)
        sb.append("<input type=text name=key placeholder='AIza…' autocomplete=off autocapitalize=off spellcheck=false>")
        sb.append("<button type=submit>Add key</button></form>")
        sb.append("<p class=meta>Each key from a separate Google Cloud project adds 10,000 units a day, which lets the TV refresh faster.</p></div>")

        // Refresh speed
        sb.append("<h2>Refresh speed</h2><div class='card stack'><form method=post action='/refresh' class=chips>").append(hidden)
        for ((label, sec) in listOf("Auto" to 0, "10s" to 10, "15s" to 15, "30s" to 30, "1m" to 60, "2m" to 120, "5m" to 300)) {
            val on = if (settings.refreshSeconds == sec) " class=on" else ""
            sb.append("<button name=seconds value=$sec$on>$label</button>")
        }
        sb.append("</form><p class=meta>Refreshing every ${plan.intervalSec}s ≈ ${Fmt.full(plan.unitsPerDay.toLong())} units/day")
        sb.append(" of ${Fmt.full(plan.capacityPerDay.toLong())} available (${plan.healthyKeys} working key${if (plan.healthyKeys == 1) "" else "s"}).")
        if (plan.unitsPerDay > plan.capacityPerDay) sb.append(" <span class=bad>That's more than your quota; keys will run out before the day ends.</span>")
        sb.append("</p></div>")

        // Wall
        sb.append("<h2>Live wall</h2><div class='card stack'><p class=meta>Switch to the next channel every:</p><form method=post action='/wall' class=chips>").append(hidden)
        for ((label, sec) in listOf("10s" to 10, "20s" to 20, "30s" to 30, "1m" to 60, "Never" to 0)) {
            val on = if (settings.wallCycleSeconds == sec) " class=on" else ""
            sb.append("<button name=seconds value=$sec$on>$label</button>")
        }
        sb.append("</form></div>")

        sb.append("</main></body></html>")
        return sb.toString()
    }
}
