package com.metrolist.music.listentogether

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.ConnectionSpec
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Room chat backed by ntfy.sh (free, no account, no server of our own).
 *
 * The official Listen Together server has no chat support, so each room maps to an
 * ntfy topic named after the room code. Members publish to the topic and subscribe
 * over WebSocket to receive messages in real time.
 */
class RoomChatClient(
    private val client: OkHttpClient =
        OkHttpClient
            .Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            // okhttp's ping deadline equals the interval; on slow mobile networks a
            // 20s window was regularly missed, which killed the socket mid-session.
            .pingInterval(60, TimeUnit.SECONDS)
            // A self-hosted relay is usually plain http:// (ws://) on a private host.
            .apply {
                try {
                    connectionSpecs(
                        listOf(ConnectionSpec.CLEARTEXT, ConnectionSpec.MODERN_TLS),
                    )
                } catch (_: Throwable) {
                    // keep defaults
                }
            }
            .build(),
) {
    companion object {
        private const val TAG = "RoomChat"
        private const val NTFY_BASE = "https://ntfy.sh"
        private const val TOPIC_PREFIX = "meowsic-room-"

        /**
         * Self-hosted relay (Pterodactyl). Falls back to ntfy when unreachable.
         *
         * Null means "use the default relay". The string "ntfy" forces the built-in
         * free service, anything else is treated as a custom relay address.
         */
        var relayUrl: String? = null

        /** The community relay that ships with Meowsic. */
        const val DEFAULT_RELAY = "http://31.57.178.188:6130"

        /** Maximum number of characters allowed in a single chat message. */
        const val MAX_CHAT_LENGTH = 500

        /** Resolve the relay that should actually be used right now. */
        fun activeRelay(): String? {
            val configured = relayUrl
            return when {
                configured == null -> DEFAULT_RELAY
                configured.equals("ntfy", ignoreCase = true) -> null
                configured.isBlank() -> DEFAULT_RELAY
                else -> configured
            }
        }

        /** Human readable name of the transport currently in use. */
        fun transportLabel(): String = if (activeRelay() != null) "Relay server" else "Layanan gratis"

        /** Optional token required by the relay (read from the URL query string). */
        var relayToken: String? = null

        /**
         * When true the free service (ntfy) is also used as a safety net, so members
         * running an older build still see each other. When false only the relay is used.
         */
        var useFreeServiceFallback: Boolean = true

        /**
         * Topic name derived from the room code. The code is hashed so the ntfy topic
         * cannot be guessed from the room code alone (ntfy topics are world-readable).
         */
        fun topicFor(roomCode: String): String {
            val digest =
                java.security.MessageDigest
                    .getInstance("SHA-256")
                    .digest(roomCode.lowercase().toByteArray())
            val hash = digest.take(8).joinToString("") { "%02x".format(it) }
            return TOPIC_PREFIX + hash
        }

        private val json = Json { ignoreUnknownKeys = true }
    }

    data class ChatMessage(
        val id: String,
        val senderId: String,
        val senderName: String,
        val text: String,
        val timestamp: Long,
    )

    @Serializable
    private data class ChatEnvelope(
        val kind: String = "chat",
        val id: String = "",
        val sender_id: String = "",
        val sender_name: String = "",
        val message: String = "",
        val timestamp: Long = 0L,
        val emoji: String = "",
        val color: String = "",
    )

    /** Avatar details a member picked for themselves. */
    data class UserProfile(
        val userId: String,
        val username: String,
        val emoji: String,
        val color: String,
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var webSocket: WebSocket? = null
    private var currentTopic: String? = null
    private var userId: String = ""
    private var relayDisabled = false
    private var historyJob: Job? = null
    private var pollJob: Job? = null
    private val seenMessageIds = LinkedHashSet<String>()
    private var lastPollCursor: String? = null

    @Volatile
    private var onMessage: ((ChatMessage) -> Unit)? = null

    @Volatile
    private var onError: ((String) -> Unit)? = null

    @Volatile
    private var onProfile: ((UserProfile) -> Unit)? = null

    /**
     * Subscribe to a room topic.
     *
     * Messages are published to BOTH the self-hosted relay and ntfy, and both
     * transports are subscribed to. That way two members who have different (or no)
     * relay settings still see each other's messages: whichever transport the other
     * member happens to use, the message arrives on at least one of them.
     */
    fun connect(
        roomCode: String,
        onMessage: (ChatMessage) -> Unit,
        onError: ((String) -> Unit)? = null,
        onProfile: ((UserProfile) -> Unit)? = null,
    ) {
        this.onMessage = onMessage
        this.onError = onError
        this.onProfile = onProfile
        val topic = topicFor(roomCode)
        if (currentTopic == topic && webSocket != null) return
        disconnect()
        currentTopic = topic
        relayDisabled = false

        if (activeRelay() != null) {
            // Own server: the only transport we talk to.
            openSocket(topic)
            // The free service is only a safety net for members on an older build.
            if (useFreeServiceFallback) {
                ensureTopic(topic)
                openNtfySocket(topic)
            }
        } else {
            // No relay configured: the free service is the transport.
            ensureTopic(topic)
            openNtfySocket(topic)
            loadHistory(topic)
            startPolling(topic)
        }
    }

    /** Second socket to the free service, used alongside the relay. */
    private var ntfySocket: WebSocket? = null

    /** Opens the socket for the given transport. */
    private fun openSocket(topic: String) {
        val relay = activeRelay()?.takeIf { !relayDisabled }
        if (relay == null) {
            // No relay: rely on the free service only.
            openNtfySocket(topic)
            return
        }
        val wsUrl =
            relay.trimEnd('/').replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") +
                "/chat/$topic"
        val request =
            Request
                .Builder()
                .url(wsUrl)
                .build()

        webSocket =
            client.newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response,
                    ) {
                        Timber.tag(TAG).d("Relay socket open for $topic")
                        webSocket.send(
                            json.encodeToString(
                                JsonObject.serializer(),
                                buildJsonObject {
                                    put("action", JsonPrimitive("subscribe"))
                                    put("topic", JsonPrimitive(topic))
                                    put("user_id", JsonPrimitive(userId.orEmpty()))
                                    relayToken?.takeIf { it.isNotBlank() }?.let {
                                        put("token", JsonPrimitive(it))
                                    }
                                },
                            ),
                        )
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String,
                    ) {
                        handleIncoming(text)
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?,
                    ) {
                        Timber.tag(TAG).w(t, "Relay socket failed for $topic")
                        onError?.invoke("Koneksi chat terputus, mencoba lagi...")
                        // Retry the SAME transport. Switching transports mid-session
                        // would silently isolate this member from the rest of the room.
                        scope.launch {
                            delay(3000)
                            if (currentTopic == topic && activeRelay() != null) openSocket(topic)
                        }
                    }
                },
            )
    }

    /** Socket for the free service, used as a second channel. */
    private fun openNtfySocket(topic: String) {
        val request =
            Request
                .Builder()
                .url("$NTFY_BASE/$topic/ws")
                .build()

        ntfySocket =
            client.newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response,
                    ) {
                        Timber.tag(TAG).d("Free-service socket open for $topic")
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String,
                    ) {
                        handleIncoming(text)
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?,
                    ) {
                        Timber.tag(TAG).d(t, "Free-service socket failed")
                        scope.launch {
                            delay(5000)
                            if (currentTopic == topic) openNtfySocket(topic)
                        }
                    }
                },
            )
    }

    /**
     * ntfy only starts delivering to a topic a short moment after the first subscriber
     * attaches. Publishing a seed message registers the topic right away so the very
     * first real chat message is not missed.
     */
    private fun ensureTopic(topic: String) {
        scope.launch {
            try {
                val payload =
                    buildJsonObject {
                        put("topic", JsonPrimitive(topic))
                        put("kind", JsonPrimitive("seed"))
                        put("id", JsonPrimitive("seed"))
                        put("timestamp", JsonPrimitive(System.currentTimeMillis()))
                    }
                val request =
                    Request
                        .Builder()
                        .url("$NTFY_BASE/$topic")
                        .post(payload.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                client.newCall(request).execute().use { }
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to seed chat topic")
            }
        }
    }

    /** Load recent messages so history survives a join or reconnect. */
    private fun loadHistory(topic: String) {
        historyJob?.cancel()
        historyJob =
            scope.launch {
                try {
                    val request =
                        Request
                            .Builder()
                            .url("$NTFY_BASE/$topic/json?poll=1&since=12h")
                            .build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@use
                        val body = response.body?.string().orEmpty()
                        body.lineSequence()
                            .filter { it.isNotBlank() }
                            .forEach { line ->
                                extractOuterId(line)?.let { lastPollCursor = it }
                                val id = extractMessageId(line)
                                if (id != null && seenMessageIds.add(id)) {
                                    handleIncoming(line)
                                }
                            }
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "Failed to load chat history")
                }
            }
    }

    private fun handleIncoming(text: String) {
        val outer =
            try {
                json.parseToJsonElement(text).jsonObject
            } catch (e: Exception) {
                return
            }

        // Relay sends the payload directly; ntfy wraps it in {"event":"message","message":"<json>"}.
        val isRelayFrame = outer["event"] == null && outer["kind"] != null
        val raw =
            if (isRelayFrame) {
                outer.toString()
            } else {
                if (outer["event"]?.jsonPrimitive?.content != "message") return
                outer["message"]?.jsonPrimitive?.content.orEmpty()
            }
        if (raw.isBlank() || raw.length > 8192) return

        // Our payload is JSON; ntfy messages from other apps are plain text and are ignored.
        val payload =
            try {
                json.decodeFromString<ChatEnvelope>(raw)
            } catch (e: Exception) {
                return
            }

        val messageKey = payload.id.ifBlank { outer["id"]?.jsonPrimitive?.content.orEmpty() }
        if (messageKey.isNotBlank() && !seenMessageIds.add(messageKey)) return

        val timestamp =
            payload.timestamp.takeIf { it > 0L }
                ?: outer["time"]?.jsonPrimitive?.content?.toLongOrNull()?.times(1000)
                ?: System.currentTimeMillis()

        if (payload.kind == "seed") return

        if (payload.kind == "profile") {
            if (payload.sender_id.isBlank()) return
            onProfile?.invoke(
                UserProfile(
                    userId = payload.sender_id,
                    username = payload.sender_name,
                    emoji = payload.emoji,
                    color = payload.color,
                ),
            )
            return
        }

        val body = payload.message.trim()
        if (body.isBlank()) return

        onMessage?.invoke(
            ChatMessage(
                id = payload.id.ifBlank { outer["id"]?.jsonPrimitive?.content.orEmpty() },
                senderId = payload.sender_id,
                senderName = payload.sender_name.ifBlank { "Teman" },
                text = body,
                timestamp = timestamp,
            ),
        )
    }

    /** Publish a message to the room topic. */
    fun send(
        roomCode: String,
        senderId: String,
        senderName: String,
        message: String,
        onSent: ((ChatMessage) -> Unit)? = null,
        onError: ((String) -> Unit)? = null,
    ) {
        // Chat messages are capped so a long paste cannot break the transport.
        val text = message.trim().take(MAX_CHAT_LENGTH)
        if (text.isBlank()) return
        val topic = topicFor(roomCode)
        val id = System.currentTimeMillis().toString() + "-" + senderId.hashCode().toString().replace("-", "")
        val timestamp = System.currentTimeMillis()

        val payload =
            buildJsonObject {
                put("kind", JsonPrimitive("chat"))
                put("topic", JsonPrimitive(topic))
                put("id", JsonPrimitive(id))
                put("sender_id", JsonPrimitive(senderId))
                put("sender_name", JsonPrimitive(senderName))
                put("message", JsonPrimitive(text))
                put("timestamp", JsonPrimitive(timestamp))
                // Kode room asli, hanya dipakai server untuk log owner.
                put("room_code", JsonPrimitive(roomCode))
            }

        val chatMessage =
            ChatMessage(
                id = id,
                senderId = senderId,
                senderName = senderName,
                text = text,
                timestamp = timestamp,
            )

        // 1) Self-hosted relay first: it is our own server, has no quota and is the
        //    transport every member should end up on.
        var delivered = false
        val relaySocket = webSocket
        if (activeRelay() != null && !relayDisabled && relaySocket != null) {
            delivered =
                try {
                    relaySocket.send(
                        json.encodeToString(
                            JsonObject.serializer(),
                            buildJsonObject {
                                put("action", JsonPrimitive("publish"))
                                put("payload", payload)
                            },
                        ),
                    )
                    true
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "Relay publish failed")
                    false
                }
        }

        if (!useFreeServiceFallback && activeRelay() != null) {
            // Relay only: the message is already on our own server, nothing else to do.
            if (delivered) {
                onSent?.invoke(chatMessage)
            } else {
                onError?.invoke("Gagal kirim pesan, cek koneksi")
            }
            return
        }

        if (delivered) {
            // The message is on the relay already, so show it right away. The free
            // service still receives it in the background for members that have not
            // switched to the relay yet.
            onSent?.invoke(chatMessage)
        }

        scope.launch {
            // The free service also gets the message, so members that have not
            // switched to the relay yet still receive it.
            val accepted =
                try {
                    val request =
                        Request
                            .Builder()
                            .url("$NTFY_BASE/$topic")
                            .post(payload.toString().toRequestBody("application/json".toMediaType()))
                            .build()
                    client.newCall(request).execute().use { it.isSuccessful }
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "Failed to publish chat message to free service")
                    false
                }

            // Only complain when neither transport accepted the message.
            if (!delivered && accepted) {
                onSent?.invoke(chatMessage)
            } else if (!delivered) {
                onError?.invoke("Gagal kirim pesan, cek koneksi")
            }
        }
    }

    /** Broadcast the member's chosen avatar so others can render it. */
    fun publishProfile(
        roomCode: String,
        userId: String,
        username: String,
        emoji: String,
        color: String,
    ) {
        val topic = topicFor(roomCode)
        val payload =
            buildJsonObject {
                put("topic", JsonPrimitive(topic))
                put("kind", JsonPrimitive("profile"))
                put("id", JsonPrimitive("profile-$userId"))
                put("sender_id", JsonPrimitive(userId))
                put("sender_name", JsonPrimitive(username))
                put("emoji", JsonPrimitive(emoji))
                put("color", JsonPrimitive(color))
                put("timestamp", JsonPrimitive(System.currentTimeMillis()))
            }

        // Publish to every available transport.
        val live = webSocket
        if (activeRelay() != null && !relayDisabled && live != null) {
            try {
                live.send(
                    json.encodeToString(
                        JsonObject.serializer(),
                        buildJsonObject {
                            put("action", JsonPrimitive("publish"))
                            put("payload", payload)
                        },
                    ),
                )
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Relay profile publish failed")
            }
        }

        if (!useFreeServiceFallback && activeRelay() != null) return

        scope.launch {
            try {
                val request =
                    Request
                        .Builder()
                        .url("$NTFY_BASE/$topic")
                        .post(payload.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                client.newCall(request).execute().use { }
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to publish profile")
            }
        }
    }

    /**
     * Safety net: poll recent messages every few seconds so nothing is lost even if a
     * WebSocket event is dropped by the relay.
     */
    private fun startPolling(topic: String) {
        pollJob?.cancel()
        pollJob =
            scope.launch {
                while (currentTopic == topic) {
                    kotlinx.coroutines.delay(4000)
                    try {
                        val since = lastPollCursor?.let { "&since=$it" } ?: "&since=10m"
                        val request =
                            Request
                                .Builder()
                                .url("$NTFY_BASE/$topic/json?poll=1$since")
                                .build()
                        client.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) return@use
                            val body = response.body?.string().orEmpty()
                            body.lineSequence()
                                .filter { it.isNotBlank() }
                                .forEach { line ->
                                    // Advance the cursor with ntfy's own message id
                                    extractOuterId(line)?.let { lastPollCursor = it }
                                    val id = extractMessageId(line)
                                    if (id != null && seenMessageIds.add(id)) {
                                        handleIncoming(line)
                                    }
                                }
                        }
                    } catch (e: Exception) {
                        Timber.tag(TAG).w(e, "Chat poll failed")
                    }
                }
            }
    }

    private fun extractOuterId(line: String): String? =
        try {
            json.parseToJsonElement(line).jsonObject["id"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }

    private fun extractMessageId(line: String): String? =
        try {
            val obj = json.parseToJsonElement(line).jsonObject
            val inner = obj["message"]?.jsonPrimitive?.content
            if (inner.isNullOrBlank()) {
                obj["id"]?.jsonPrimitive?.content
            } else {
                json.parseToJsonElement(inner).jsonObject["id"]?.jsonPrimitive?.content
            }
        } catch (e: Exception) {
            null
        }

    fun disconnect() {
        historyJob?.cancel()
        historyJob = null
        pollJob?.cancel()
        pollJob = null
        seenMessageIds.clear()
        lastPollCursor = null
        webSocket?.close(1000, "bye")
        webSocket = null
        ntfySocket?.close(1000, "bye")
        ntfySocket = null
        currentTopic = null
    }
}
