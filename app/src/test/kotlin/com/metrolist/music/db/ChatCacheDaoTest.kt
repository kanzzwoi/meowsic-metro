package com.metrolist.music.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.metrolist.music.db.daos.ChatCacheDao
import com.metrolist.music.db.entities.ChatCacheEntity
import com.metrolist.music.db.entities.LOCAL_CHAT_RETENTION_MS
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The relay only keeps a short window of history, so a member that is already inside
 * the room must still be able to read what was said earlier. The local cache covers
 * that gap, and it must expire on its own so it never grows without bound.
 */
@RunWith(RobolectricTestRunner::class)
class ChatCacheDaoTest {

    private lateinit var database: InternalDatabase
    private lateinit var dao: ChatCacheDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            InternalDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.chatCacheDao
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a message is stored and read back for the same room`() = runBlocking {
        dao.insert(
            ChatCacheEntity(
                id = "m1",
                roomCode = "ROOM1",
                senderId = "a",
                senderName = "A",
                text = "halo",
                timestamp = 1_000L,
            ),
        )

        val stored = dao.getRoomMessages("ROOM1")
        assertEquals(1, stored.size)
        assertEquals("halo", stored[0].text)
    }

    @Test
    fun `messages of another room are not mixed in`() = runBlocking {
        dao.insert(ChatCacheEntity("m1", "ROOM1", "a", "A", "halo", 1_000L))
        dao.insert(ChatCacheEntity("m2", "ROOM2", "b", "B", "hei", 1_000L))

        assertEquals(1, dao.getRoomMessages("ROOM1").size)
        assertEquals("halo", dao.getRoomMessages("ROOM1")[0].text)
        assertEquals(1, dao.getRoomMessages("ROOM2").size)
    }

    @Test
    fun `messages are returned in chronological order`() = runBlocking {
        dao.insert(ChatCacheEntity("m2", "ROOM1", "a", "A", "kedua", 2_000L))
        dao.insert(ChatCacheEntity("m1", "ROOM1", "a", "A", "pertama", 1_000L))

        val stored = dao.getRoomMessages("ROOM1")
        assertEquals("pertama", stored[0].text)
        assertEquals("kedua", stored[1].text)
    }

    @Test
    fun `messages older than the retention window are dropped`() = runBlocking {
        val now = System.currentTimeMillis()
        dao.insert(ChatCacheEntity("old", "ROOM1", "a", "A", "lama", now - LOCAL_CHAT_RETENTION_MS - 1_000))
        dao.insert(ChatCacheEntity("fresh", "ROOM1", "a", "A", "baru", now - 1_000))

        val removed = dao.deleteOlderThan(now - LOCAL_CHAT_RETENTION_MS)

        assertEquals(1, removed)
        val remaining = dao.getRoomMessages("ROOM1")
        assertEquals(1, remaining.size)
        assertEquals("baru", remaining[0].text)
    }

    @Test
    fun `the retention window is six hours`() {
        assertEquals(6L * 60 * 60 * 1000, LOCAL_CHAT_RETENTION_MS)
    }

    @Test
    fun `storing the same message twice does not duplicate it`() = runBlocking {
        val row = ChatCacheEntity("m1", "ROOM1", "a", "A", "halo", 1_000L)
        dao.insert(row)
        dao.insert(row)

        assertEquals(1, dao.getRoomMessages("ROOM1").size)
    }

    @Test
    fun `clearing a room removes only that room`() = runBlocking {
        dao.insert(ChatCacheEntity("m1", "ROOM1", "a", "A", "halo", 1_000L))
        dao.insert(ChatCacheEntity("m2", "ROOM2", "b", "B", "hei", 1_000L))

        dao.clearRoom("ROOM1")

        assertTrue(dao.getRoomMessages("ROOM1").isEmpty())
        assertEquals(1, dao.getRoomMessages("ROOM2").size)
    }
}
