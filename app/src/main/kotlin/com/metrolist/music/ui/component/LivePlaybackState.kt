/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import com.metrolist.music.playback.PlayerConnection
import kotlinx.coroutines.delay

/**
 * Status putar yang dibaca langsung dari objek player tiap 400ms.
 *
 * Alasan: Flow [PlayerConnection.isPlaying] bergantung pada callback Media3.
 * Saat player diganti (service dimatikan-hidupkan) callback bisa terlewat
 * sehingga nilai tersimpan nyangkut dan ikon play/pause di daftar lagu tidak
 * ikut berubah.
 *
 * Penting: kalau player belum siap, JANGAN paksa false — itu membuat ikon
 * selalu tampak "play" walau musik sedang jalan. Pertahankan nilai terakhir
 * atau pakai flow sebagai cadangan.
 */
@Composable
fun rememberIsPlayingLive(playerConnection: PlayerConnection?): Boolean {
    var isPlaying by remember { mutableStateOf(playerConnection?.isPlaying?.value ?: false) }

    LaunchedEffect(playerConnection) {
        if (playerConnection == null) return@LaunchedEffect
        while (true) {
            val player =
                runCatching { playerConnection.player }.getOrNull()
                    ?: runCatching { playerConnection.service.player }.getOrNull()

            val direct =
                player?.let {
                    runCatching {
                        it.playWhenReady && it.playbackState != Player.STATE_ENDED
                    }.getOrNull()
                }

            isPlaying = direct ?: playerConnection.isPlaying.value

            delay(400)
        }
    }

    return isPlaying
}
