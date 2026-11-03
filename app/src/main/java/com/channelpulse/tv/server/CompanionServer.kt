package com.channelpulse.tv.server

import com.channelpulse.tv.data.KeyPool
import com.channelpulse.tv.data.PulseRepository
import com.channelpulse.tv.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.BindException
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * A tiny web server on the TV, reached from a phone on the same network by scanning a QR code.
 * Lets you paste API keys and add channels with a phone keyboard instead of the TV remote.
 * Every request must carry a random token from the QR code, so other devices on the network can't use it.
 */
class CompanionServer(
    private val repo: PulseRepository,
    private val settings: SettingsStore,
    private val keyPool: KeyPool,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: ServerSocket? = null

    val token: String = buildString {
        val chars = "abcdefghjkmnpqrstuvwxyz23456789"
        val rnd = SecureRandom()
        repeat(8) { append(chars[rnd.nextInt(chars.length)]) }
    }

    private val _url = MutableStateFlow<String?>(null)
    /** Address to open on the phone, or null if the TV has no local network address. */
    val url: StateFlow<String?> = _url.asStateFlow()

    private var job: Job? = null

    /** Starts listening in the background (sockets are never opened on the main thread). */
    @Synchronized
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            val socket = bind() ?: return@launch
            synchronized(this@CompanionServer) { server = socket }
            // The TV may join Wi-Fi after the app opens, or switch networks, so keep the QR address current.
            val watcher = launch {
                while (isActive) {
                    val url = localIp()?.let { "http://$it:${socket.localPort}/?t=$token" }
                    if (isActive) _url.value = url
                    delay(10_000)
                }
            }
            try {
                while (isActive) {
                    val client = socket.accept()
                    launch { handle(client) }
                }
            } catch (ignored: Exception) {
                // Socket closed by stop().
            } finally {
                watcher.cancel()
                try { socket.close() } catch (ignored: Exception) {}
            }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        try { server?.close() } catch (ignored: Exception) {}
        server = null
        _url.value = null
    }

    private fun bind(): ServerSocket? {
        for (port in 8642..8660) {
            try {
                return ServerSocket(port)
            } catch (e: BindException) {
                continue
            } catch (e: Exception) {
                return null
            }
        }
        return try { ServerSocket(0) } catch (e: Exception) { null }
    }

    /** The TV's LAN address, preferring Wi-Fi/Ethernet over VPN or other virtual interfaces. */
    private fun localIp(): String? = try {
        val candidates = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { nif -> nif.inetAddresses.toList().filterIsInstance<Inet4Address>().map { nif.name to it } }
        val lan = candidates.filter { (_, addr) -> addr.isSiteLocalAddress }
        val preferred = lan.firstOrNull { (name, _) -> name.startsWith("wlan") || name.startsWith("eth") }
        (preferred ?: lan.firstOrNull() ?: candidates.firstOrNull())?.second?.hostAddress
    } catch (e: Exception) {
        null
    }

    // ---- HTTP handling ------------------------------------------------------------------

    private class Response(val status: Int, val text: String, val type: String, val body: ByteArray, val location: String? = null)

    private suspend fun handle(socket: Socket) {
        socket.use { s ->
            s.soTimeout = 15_000
            val input = BufferedInputStream(s.getInputStream())
            val requestLine = readLine(input) ?: return
            val headers = HashMap<String, String>()
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val i = line.indexOf(':')
                if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
            }
            val parts = requestLine.split(' ')
            if (parts.size < 2) return
            val method = parts[0].uppercase()
            val target = parts[1]
            val path = target.substringBefore('?')
            val query = parseForm(target.substringAfter('?', ""))

            val length = headers["content-length"]?.toIntOrNull()?.coerceIn(0, 64_000) ?: 0
            val body = ByteArray(length)
            var read = 0
            while (read < length) {
                val n = input.read(body, read, length - read)
                if (n < 0) break
                read += n
            }
            val form = if (method == "POST") parseForm(String(body, 0, read, Charsets.UTF_8)) else emptyMap()

            val response = try {
                route(method, path, query, form)
            } catch (e: Exception) {
                html(500, "Error", CompanionPage.message("Something went wrong: ${e.message}"))
            }
            write(s.getOutputStream(), response)
        }
    }

    private suspend fun route(method: String, path: String, query: Map<String, String>, form: Map<String, String>): Response {
        if (path == "/favicon.ico") return Response(404, "Not Found", "text/plain", ByteArray(0))
        val givenToken = (form["t"] ?: query["t"]).orEmpty()
        if (!MessageDigest.isEqual(givenToken.toByteArray(), token.toByteArray())) {
            return html(403, "Forbidden", CompanionPage.message("This link has expired. Scan the QR code on your TV again."))
        }
        if (method == "GET" && path == "/") {
            val page = CompanionPage.render(
                token = token,
                settings = settings.state.value,
                channels = repo.channels.value,
                keyStates = keyPool.state.value,
                message = query["msg"],
                plan = repo.plan(),
            )
            return html(200, "OK", page)
        }
        if (method != "POST") return html(404, "Not Found", CompanionPage.message("Page not found."))

        val msg: String = when (path) {
            "/key/add" -> {
                val key = form["key"].orEmpty().trim()
                when {
                    key.isEmpty() -> "Paste an API key first."
                    repo.addApiKey(key) -> "API key added. Refreshing…".also { repo.refreshNow() }
                    else -> "That key is already saved."
                }
            }
            "/key/remove" -> {
                val key = form["key"].orEmpty()
                settings.removeApiKey(key)
                "API key removed."
            }
            "/channel/add" -> {
                val q = form["q"].orEmpty()
                val lines = q.split('\n', ',').map { it.trim() }.filter { it.isNotEmpty() }
                if (lines.isEmpty()) {
                    "Type a @handle, channel URL or channel ID."
                } else {
                    val results = ArrayList<String>()
                    for (line in lines.take(20)) {
                        val result = repo.addChannel(line)
                        results += result.fold(
                            onSuccess = { "Added ${it.title}." },
                            onFailure = { it.message ?: "Couldn't add $line." },
                        )
                    }
                    results.joinToString("  ")
                }
            }
            "/channel/remove" -> {
                repo.removeChannel(form["id"].orEmpty())
                "Channel removed."
            }
            "/channel/move" -> {
                settings.moveChannel(form["id"].orEmpty(), if (form["dir"] == "up") -1 else 1)
                "Order updated."
            }
            "/refresh" -> {
                settings.setRefreshSeconds(form["seconds"]?.toIntOrNull() ?: 0)
                "Refresh speed updated."
            }
            "/wall" -> {
                settings.setWallCycleSeconds(form["seconds"]?.toIntOrNull() ?: 20)
                "Live wall timing updated."
            }
            else -> return html(404, "Not Found", CompanionPage.message("Page not found."))
        }
        val location = "/?t=$token&msg=" + URLEncoder.encode(msg, "UTF-8")
        return Response(303, "See Other", "text/plain", ByteArray(0), location)
    }

    private fun html(status: Int, text: String, page: String) =
        Response(status, text, "text/html; charset=utf-8", page.toByteArray(Charsets.UTF_8))

    private fun write(out: OutputStream, r: Response) {
        val head = StringBuilder()
            .append("HTTP/1.1 ").append(r.status).append(' ').append(r.text).append("\r\n")
            .append("Content-Type: ").append(r.type).append("\r\n")
            .append("Content-Length: ").append(r.body.size).append("\r\n")
            .append("Cache-Control: no-store\r\n")
            .append("Connection: close\r\n")
        if (r.location != null) head.append("Location: ").append(r.location).append("\r\n")
        head.append("\r\n")
        out.write(head.toString().toByteArray(Charsets.UTF_8))
        out.write(r.body)
        out.flush()
    }

    private fun readLine(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (buf.size() == 0) null else buf.toString("UTF-8")
            if (b == '\n'.code) break
            if (b != '\r'.code) buf.write(b)
            if (buf.size() > 8192) return null
        }
        return buf.toString("UTF-8")
    }

    private fun parseForm(s: String): Map<String, String> {
        if (s.isEmpty()) return emptyMap()
        return s.split('&').mapNotNull { pair ->
            if (pair.isEmpty()) return@mapNotNull null
            val k = pair.substringBefore('=')
            val v = pair.substringAfter('=', "")
            try {
                URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
            } catch (e: Exception) {
                null
            }
        }.toMap()
    }
}
