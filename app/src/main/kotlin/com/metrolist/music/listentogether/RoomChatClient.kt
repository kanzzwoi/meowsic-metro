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
            .pingInterval(20, TimeUnit.SECONDS)
            .build(),
) {
    companion object {
        private const val TAG = "RoomChat"
        private const val NTFY_BASE = "https://ntfy.sh"
        private const val TOPIC_PREFIX = "meowsic-room-"

        fun topicFor(roomCode: String): String = TOPIC_PREFIX + roomCode.lowercase()

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
    private var historyJob: Job? = null

    @Volatile
    private var onMessage: ((ChatMessage) -> Unit)? = null

    @Volatile
    private var onError: ((String) -> Unit)? = null

    @Volatile
    private var onProfile: ((UserProfile) -> Unit)? = null

    /** Subscribe to a room topic. Reconnects automatically on drop. */
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
        openSocket(topic)
        loadHistory(topic)
    }

    private fun openSocket(topic: String) {
        val request =
            Request
                .Builder()
                .url("$NTFY_BASE/$topic/ws")
                .build()

        webSocket =
            client.newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response,
                    ) {
                        Timber.tag(TAG).d("Chat socket open for $topic")
                        webSocket.send(
                            json.encodeToString(
                                JsonObject.serializer(),
                                buildJsonObject {
                                    put("action", JsonPrimitive("subscribe"))
                                    put("topic", JsonPrimitive(topic))
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
                        Timber.tag(TAG).w(t, "Chat socket failed for $topic")
                        onError?.invoke(t.message ?: "Chat connection failed")
                        scope.launch {
                            delay(3000)
                            if (currentTopic == topic) openSocket(topic)
                        }
                    }
                },
            )
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
                            .forEach { line -> handleIncoming(line) }
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

        if (outer["event"]?.jsonPrimitive?.content != "message") return

        val raw = outer["message"]?.jsonPrimitive?.content.orEmpty()
        if (raw.isBlank()) return

        // Our payload is JSON; ntfy messages from other apps are plain text and are ignored.
        val payload =
            try {
                json.decodeFromString<ChatEnvelope>(raw)
            } catch (e: Exception) {
                return
            }

        val timestamp =
            payload.timestamp.takeIf { it > 0L }
                ?: outer["time"]?.jsonPrimitive?.content?.toLongOrNull()?.times(1000)
                ?: System.currentTimeMillis()

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
        val text = message.trim()
        if (text.isBlank()) return
        val topic = topicFor(roomCode)
        val id = System.currentTimeMillis().toString() + "-" + senderId.hashCode().toString().replace("-", "")
        val timestamp = System.currentTimeMillis()

        scope.launch {
            try {
                val payload =
                    buildJsonObject {
                        put("topic", JsonPrimitive(topic))
                        put("id", JsonPrimitive(id))
                        put("sender_id", JsonPrimitive(senderId))
                        put("sender_name", JsonPrimitive(senderName))
                        put("message", JsonPrimitive(text))
                        put("timestamp", JsonPrimitive(timestamp))
                    }
                val request =
                    Request
                        .Builder()
                        .url("$NTFY_BASE/$topic")
                        .post(payload.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        onSent?.invoke(
                            ChatMessage(
                                id = id,
                                senderId = senderId,
                                senderName = senderName,
                                text = text,
                                timestamp = timestamp,
                            ),
                        )
                    } else {
                        onError?.invoke("Gagal kirim pesan (${response.code})")
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to publish chat message")
                onError?.invoke("Gagal kirim pesan")
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
        scope.launch {
            try {
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

    fun disconnect() {
        historyJob?.cancel()
        historyJob = null
        webSocket?.close(1000, "bye")
        webSocket = null
        currentTopic = null
    }
}
