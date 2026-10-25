/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local cache of a room chat message.
 *
 * The relay only keeps a short window of history, so a member that was already
 * inside the room would lose the conversation as soon as it leaves the server
 * window. Every client therefore keeps its own copy of the messages it has
 * seen, keyed by room, and drops the copy once it is older than
 * [LOCAL_CHAT_RETENTION_MS].
 */
@Entity(
    tableName = "chat_cache",
    indices = [
        Index(value = ["roomCode"]),
        Index(value = ["timestamp"]),
    ],
)
data class ChatCacheEntity(
    @PrimaryKey val id: String,
    val roomCode: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val timestamp: Long,
)

/** How long a locally cached chat message is kept. */
const val LOCAL_CHAT_RETENTION_MS = 6L * 60 * 60 * 1000
