package com.tabdeck.app.bridge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.tabdeck.app.MainActivity
import com.tabdeck.app.R
import com.tabdeck.app.TabDeckApplication
import com.tabdeck.app.model.BridgeScope
import com.tabdeck.app.model.BridgeSession
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.BufferedInputStream
import java.net.InetAddress
import java.net.URI
import java.net.ServerSocket
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class LocalBridgeService : Service() {
    private val running = AtomicBoolean(false)
    private val executor = Executors.newFixedThreadPool(BRIDGE_THREADS)
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val requestWindows = ConcurrentHashMap<String, ArrayDeque<Long>>()
    private var serverSocket: ServerSocket? = null
    private var expiryTask: ScheduledFuture<*>? = null
    private var sessionScope: BridgeScope = BridgeScope.THIS_DEVICE
    private var sessionExpiry: Long = 0

    private val repository get() = (application as TabDeckApplication).repository

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopBridge()
            return START_NOT_STICKY
        }
        startForegroundBridge(getString(R.string.bridge_notification_starting))
        startBridge()
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopBridge()
    }

    override fun onDestroy() {
        stopBridge()
        executor.shutdownNow()
        scheduler.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundBridge(content: String) {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = Intent(this, LocalBridgeService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.bridge_notification_title))
            .setContentText(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .addAction(0, getString(R.string.stop_bridge), stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun startBridge() {
        if (!running.compareAndSet(false, true)) return
        executor.execute {
            try {
                val state = runBlocking { repository.currentState() }
                sessionScope = BridgeScope.THIS_DEVICE
                val now = System.currentTimeMillis()
                sessionExpiry = now + state.settings.bridgeSessionMinutes.coerceAtLeast(1) * 60_000L
                runBlocking {
                    repository.setBridgeSession(
                        BridgeSession(
                            enabled = true,
                            startedAtEpochMs = now,
                            expiresAtEpochMs = sessionExpiry,
                        ),
                    )
                }
                expiryTask?.cancel(false)
                expiryTask = scheduler.schedule({ stopBridge() }, sessionExpiry - now, TimeUnit.MILLISECONDS)
                startForegroundBridge(
                    getString(
                        R.string.bridge_notification_active,
                        state.settings.bridgeSessionMinutes,
                    ),
                )

                val bindAddress = InetAddress.getLoopbackAddress()
                ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(bindAddress, BridgeNetwork.PORT), SOCKET_BACKLOG)
                }.also { serverSocket = it }.use { server ->
                    while (running.get()) {
                        val socket = runCatching { server.accept() }.getOrNull() ?: break
                        executor.execute { handle(socket) }
                    }
                }
            } catch (_: Exception) {
                // Surface the stopped state in the app; callers can restart after resolving a port/network conflict.
            } finally {
                running.set(false)
                runBlocking { repository.setBridgeSession(BridgeSession()) }
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun stopBridge() {
        if (!running.getAndSet(false) && serverSocket == null) return
        expiryTask?.cancel(false)
        expiryTask = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        runBlocking { repository.setBridgeSession(BridgeSession()) }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun handle(socket: Socket) {
        socket.use {
            it.soTimeout = SOCKET_TIMEOUT_MS
            val requestId = UUID.randomUUID().toString().take(8)
            if (System.currentTimeMillis() >= sessionExpiry) {
                writeResponse(it, HttpResponse(410, error("Bridge session expired", requestId)), null, requestId)
                stopBridge()
                return
            }
            val remote = it.inetAddress
            if (!isAllowedClient(remote)) {
                runBlocking { repository.recordBridgeRequest(false) }
                writeResponse(it, HttpResponse(403, error("Client is outside the active bridge scope", requestId)), null, requestId)
                return
            }
            if (!withinRateLimit(remote.hostAddress.orEmpty())) {
                runBlocking { repository.recordBridgeRequest(false) }
                writeResponse(it, HttpResponse(429, error("Too many requests", requestId)), null, requestId)
                return
            }

            val request = try {
                readRequest(it)
            } catch (problem: HttpProblem) {
                runBlocking { repository.recordBridgeRequest(false) }
                writeResponse(it, HttpResponse(problem.status, error(problem.message.orEmpty(), requestId)), null, requestId)
                return
            }
            val origin = request.headers["origin"]
            if (!originAllowed(origin)) {
                runBlocking { repository.recordBridgeRequest(false) }
                writeResponse(it, HttpResponse(403, error("Origin is not allowed", requestId)), null, requestId)
                return
            }

            val response = when {
                request.method == "OPTIONS" -> HttpResponse(204, "")
                request.method == "GET" && request.path == "/health" -> HttpResponse(
                    200,
                    JSONObject()
                        .put("ok", true)
                        .put("service", "TabDeck Bridge")
                        .put("version", 3)
                        .put("scope", sessionScope.name)
                        .put("expiresAtEpochMs", sessionExpiry)
                        .put("requestId", requestId)
                        .toString(),
                )
                request.method == "POST" && request.path == "/api/v3/import" -> importPayload(request, requestId)
                request.method == "POST" && request.path == "/api/v2/import" -> importPayload(request, requestId)
                request.method == "POST" && request.path == "/api/v1/import" -> importPayload(request, requestId)
                else -> HttpResponse(404, error("Not found", requestId))
            }
            writeResponse(it, response, origin, requestId)
        }
    }

    private fun importPayload(request: HttpRequest, requestId: String): HttpResponse {
        if (!request.headers["content-type"].orEmpty().lowercase().startsWith("application/json")) {
            runBlocking { repository.recordBridgeRequest(false) }
            return HttpResponse(415, error("Content-Type must be application/json", requestId))
        }
        val expectedToken = runBlocking { repository.currentState().settings.bridgeToken }
        val providedToken = request.headers["x-tabdeck-token"].orEmpty()
        val tokenMatches = expectedToken.isNotBlank() && MessageDigest.isEqual(
            providedToken.toByteArray(StandardCharsets.UTF_8),
            expectedToken.toByteArray(StandardCharsets.UTF_8),
        )
        if (!tokenMatches) {
            runBlocking { repository.recordBridgeRequest(false) }
            return HttpResponse(401, error("Invalid bridge token", requestId))
        }
        val parsed = runCatching { BridgePayloadParser.parse(request.body) }.getOrElse {
            runBlocking { repository.recordBridgeRequest(false) }
            return HttpResponse(400, error("Invalid JSON payload", requestId))
        }
        val imported = runBlocking {
            repository.importTabs(
                parsed.tabs,
                sourceLabel = parsed.sourceLabel,
                deviceName = parsed.deviceName,
                completeSnapshot = parsed.completeSnapshot,
                snapshotBrowser = parsed.sourceBrowser,
                snapshotDeviceName = parsed.deviceName,
            )
        }
        runBlocking { repository.recordBridgeRequest(true) }
        return HttpResponse(
            200,
            JSONObject()
                .put("ok", true)
                .put("received", parsed.tabs.size)
                .put("imported", imported)
                .put("completeSnapshot", parsed.completeSnapshot)
                .put("requestId", requestId)
                .toString(),
        )
    }

    private fun readRequest(socket: Socket): HttpRequest {
        val input = BufferedInputStream(socket.getInputStream())
        val headerBytes = ArrayList<Byte>()
        var state = 0
        while (headerBytes.size < MAX_HEADER_BYTES) {
            val value = input.read()
            if (value == -1) break
            headerBytes += value.toByte()
            state = when {
                state == 0 && value == '\r'.code -> 1
                state == 1 && value == '\n'.code -> 2
                state == 2 && value == '\r'.code -> 3
                state == 3 && value == '\n'.code -> 4
                else -> 0
            }
            if (state == 4) break
        }
        if (state != 4) throw HttpProblem(400, "Incomplete request headers")
        val headerText = decodeUtf8Strict(headerBytes.toByteArray(), "request headers")
        val lines = headerText.split("\r\n")
        val requestLine = lines.firstOrNull().orEmpty().split(' ')
        if (requestLine.size != 3) throw HttpProblem(400, "Malformed request line")
        val method = requestLine[0].uppercase(Locale.ROOT)
        val rawTarget = requestLine[1]
        val protocol = requestLine[2]
        if (method !in setOf("GET", "POST", "OPTIONS")) throw HttpProblem(400, "Unsupported request method")
        if (protocol != "HTTP/1.1" && protocol != "HTTP/1.0") throw HttpProblem(400, "Unsupported HTTP version")
        if (!rawTarget.startsWith('/') || rawTarget.length > MAX_REQUEST_TARGET_LENGTH || rawTarget.any { it.isISOControl() }) {
            throw HttpProblem(400, "Invalid request target")
        }

        val headerPairs = mutableListOf<Pair<String, String>>()
        lines.drop(1).filter(String::isNotEmpty).forEach { line ->
            if (line.firstOrNull()?.isWhitespace() == true || ':' !in line) throw HttpProblem(400, "Malformed request header")
            val name = line.substringBefore(':').trim().lowercase(Locale.ROOT)
            val value = line.substringAfter(':').trim()
            if (!HEADER_NAME.matches(name) || value.any { it == '\u0000' || it == '\r' || it == '\n' }) {
                throw HttpProblem(400, "Malformed request header")
            }
            headerPairs += name to value
        }
        SENSITIVE_SINGLETON_HEADERS.forEach { name ->
            if (headerPairs.count { it.first == name } > 1) throw HttpProblem(400, "Duplicate $name header")
        }
        val headers = headerPairs.toMap()
        if (headers.containsKey("transfer-encoding")) throw HttpProblem(400, "Chunked requests are not supported")
        if (protocol == "HTTP/1.1" && headers["host"].isNullOrBlank()) throw HttpProblem(400, "Host header is required")
        val rawLength = headers["content-length"]?.toLongOrNull() ?: 0L
        if (rawLength < 0 || rawLength > MAX_BODY_BYTES) throw HttpProblem(413, "Request body is too large")
        val contentLength = rawLength.toInt()
        if (method == "POST" && contentLength == 0) throw HttpProblem(400, "Request body is required")
        val bodyBytes = ByteArray(contentLength)
        var offset = 0
        while (offset < contentLength) {
            val read = input.read(bodyBytes, offset, contentLength - offset)
            if (read <= 0) break
            offset += read
        }
        if (offset != contentLength) throw HttpProblem(400, "Incomplete request body")
        return HttpRequest(
            method = method,
            path = rawTarget.substringBefore('?'),
            headers = headers,
            body = decodeUtf8Strict(bodyBytes, "request body"),
        )
    }

    private fun decodeUtf8Strict(bytes: ByteArray, label: String): String = try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: Exception) {
        throw HttpProblem(400, "Invalid UTF-8 $label")
    }

    private fun writeResponse(socket: Socket, response: HttpResponse, origin: String?, requestId: String) {
        val bytes = response.body.toByteArray(StandardCharsets.UTF_8)
        val reason = when (response.status) {
            200 -> "OK"
            204 -> "No Content"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            410 -> "Gone"
            413 -> "Payload Too Large"
            415 -> "Unsupported Media Type"
            429 -> "Too Many Requests"
            else -> "Error"
        }
        val headers = buildString {
            append("HTTP/1.1 ${response.status} $reason\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Cache-Control: no-store\r\n")
            append("X-Content-Type-Options: nosniff\r\n")
            append("X-TabDeck-Request-Id: $requestId\r\n")
            if (originAllowed(origin) && origin != null) {
                append("Access-Control-Allow-Origin: $origin\r\n")
            }
            append("Access-Control-Allow-Headers: Content-Type, X-TabDeck-Token, X-TabDeck-Request-Id\r\n")
            append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
            append("Connection: close\r\n\r\n")
        }.toByteArray(StandardCharsets.UTF_8)
        socket.getOutputStream().apply {
            write(headers)
            write(bytes)
            flush()
        }
    }

    private fun isAllowedClient(address: InetAddress): Boolean = address.isLoopbackAddress

    private fun originAllowed(origin: String?): Boolean {
        if (origin == null) return true
        val uri = runCatching { URI(origin) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return false
        val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
        if (uri.userInfo != null || uri.fragment != null || uri.query != null) return false
        return when (scheme) {
            "chrome-extension", "moz-extension" -> host.isNotBlank()
            "http" -> host == "127.0.0.1" || host == "localhost" || host == "[::1]" || host == "::1"
            else -> false
        }
    }

    private fun withinRateLimit(client: String): Boolean {
        val now = System.currentTimeMillis()
        if (requestWindows.size > MAX_TRACKED_CLIENTS) {
            requestWindows.entries.removeIf { (_, requests) ->
                synchronized(requests) {
                    requests.removeIf { now - it > RATE_WINDOW_MS }
                    requests.isEmpty()
                }
            }
        }
        val queue = requestWindows.computeIfAbsent(client) { ArrayDeque() }
        synchronized(queue) {
            while (queue.isNotEmpty() && now - queue.first() > RATE_WINDOW_MS) queue.removeFirst()
            if (queue.size >= MAX_REQUESTS_PER_WINDOW) return false
            queue.addLast(now)
            return true
        }
    }

    private fun error(message: String, requestId: String): String = JSONObject()
        .put("ok", false)
        .put("error", message)
        .put("requestId", requestId)
        .toString()

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.bridge_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = getString(R.string.bridge_channel_description) },
        )
    }

    private data class HttpRequest(
        val method: String,
        val path: String,
        val headers: Map<String, String>,
        val body: String,
    )

    private data class HttpResponse(val status: Int, val body: String)
    private class HttpProblem(val status: Int, message: String) : Exception(message)

    companion object {
        const val ACTION_START = "com.tabdeck.app.bridge.START"
        const val ACTION_STOP = "com.tabdeck.app.bridge.STOP"
        private const val CHANNEL_ID = "tabdeck_bridge"
        private const val NOTIFICATION_ID = 48721
        private const val MAX_HEADER_BYTES = 32 * 1024
        private const val MAX_REQUEST_TARGET_LENGTH = 2_048
        private const val MAX_BODY_BYTES = 8L * 1024 * 1024
        private val HEADER_NAME = Regex("^[a-z0-9!#$%&'*+.^_`|~-]+$")
        private val SENSITIVE_SINGLETON_HEADERS = setOf("host", "content-length", "transfer-encoding", "origin", "x-tabdeck-token")
        private const val SOCKET_TIMEOUT_MS = 8_000
        private const val SOCKET_BACKLOG = 12
        private const val BRIDGE_THREADS = 6
        private const val RATE_WINDOW_MS = 60_000L
        private const val MAX_REQUESTS_PER_WINDOW = 30
        private const val MAX_TRACKED_CLIENTS = 256
    }
}
