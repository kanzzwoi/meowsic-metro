/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ChatCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: com.metrolist.music.db.entities.ChatCacheEntity)

    @Query("SELECT * FROM chat_cache WHERE roomCode = :roomCode ORDER BY timestamp ASC")
    suspend fun getRoomMessages(roomCode: String): List<com.metrolist.music.db.entities.ChatCacheEntity>

    @Query("DELETE FROM chat_cache WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("DELETE FROM chat_cache WHERE roomCode = :roomCode")
    suspend fun clearRoom(roomCode: String)

    @Query("DELETE FROM chat_cache")
    suspend fun clearAll()
}
