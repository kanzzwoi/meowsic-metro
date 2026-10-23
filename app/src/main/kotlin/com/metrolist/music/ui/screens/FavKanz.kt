/**
 * Meowsic - daftar favorit pribadi "Fav Kanz" (hardcoded, YouTube Music videoId)
 */
package com.metrolist.music.ui.screens

import com.metrolist.innertube.models.Album
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint

data class FavKanzTrack(
    val videoId: String,
    val title: String,
    val artist: String,
    val cover: String,
)

object FavKanz {
    const val TITLE = "Fav Kanz"

    val tracks = listOf(
        FavKanzTrack("aMqOtlVwQYM", "Merry Christmas, Please Don't Call", "Bleachers", "https://yt3.googleusercontent.com/77ea97F29oHMbNi5x0Y-oiXblciolt1cMeDimlp63AICdq2zk6JXFWPYdUzLFplt2OPid6xVcb1bSsON=w120-h120-l90-rj"),
        FavKanzTrack("F2PsSZKweTc", "What If I Call", "Alex Crichton", "https://yt3.googleusercontent.com/eQ8b5BbMQnN1eJwDn8kh0x9NDfjH2KwpOjo7UeW5ZogFw8J1P2NpvsHyf7iEseeu6ngpqZ79Jw8tDIk=w120-h120-l90-rj"),
        FavKanzTrack("M3dWa4Odr8I", "HEARTACHE (SHALL NOT SURVIVE)", "Bin Idris", "https://yt3.googleusercontent.com/0t5A_x6OTm8Wotp_5nIgec0QTSLwQvkfZhFsCCzHZPcGCRBOYJHhUkGwFyjfF_64eYT6mMneB-vUcvKQaQ=w120-h120-l90-rj"),
        FavKanzTrack("-TVknpWcCIo", "Membasuh (feat. Rara Sekar)", "Hindia", "https://yt3.googleusercontent.com/1GDRLU9dU_dZPHmwuf6-IcbH5orM7A5z18VLBpbowSD6iDL0RvKKC9EuuwOUxo6Als7uXMXBqlaw8bUh=w120-h120-l90-rj"),
        FavKanzTrack("NcbxQom0Jpk", "1000X", "Ghea Indrawari", "https://yt3.googleusercontent.com/XRjz6gom3U3nnqugJAD9YZM6KWup4F3F79U0e_mK1hgZ-MfUrONb17HFjSEczVd758HDsfPJd6AOdnM=w120-h120-l90-rj"),
        FavKanzTrack("KJcsD8LgzTs", "Lalu Biru", "Eleanor Whisper", "https://yt3.googleusercontent.com/KI1CGqQ6UTTmkM2IgdcwKK4jrB72SQc86c1jV-T7FZAY4BzrREunNdSByBqdCJWm1X2Z4XTE1H8pOIfk=w120-h120-l90-rj"),
        FavKanzTrack("th86Wf76WhU", "Mystery Of Love (Demo)", "Sufjan Stevens", "https://yt3.googleusercontent.com/ckicrxYA1Nfrtz81LzOpw92YvETQTEmuHrQqKdtzWFkty_lPVM6fia66cZZJWdNVCoCCpq5I3LOpk4wyoA=w120-h120-l90-rj"),
        FavKanzTrack("dONTSATGKvI", "Pijaraya", "Soegi Bornean", "https://yt3.googleusercontent.com/t8ageYTgE8v2rDz85ZVY33z3l7YAAkhFp42KFD6Uxuav2ML1qdPad8x-z79MEmDv2EF91RqE0fhXMTUYCw=w120-h120-l90-rj"),
        FavKanzTrack("m9K4u9r1cCw", "Surabaya", "Crayon Case", "https://yt3.googleusercontent.com/j0L8KpbqRKtNlPTVcDPJ77QyV5ooyUUD_ENJbAL7Y6Wwb2YkW1pg3r1mte3pSmr_kbmGZAacwzC4Poc=w120-h120-l90-rj"),
        FavKanzTrack("hvYVZqbBq4w", "No Other Heart", "Mac DeMarco", "https://yt3.googleusercontent.com/-tn6_fq2oxfuB85ZRsZRDmcLdd4dJKPCENrmQLL_Iz7DcvNoN_2TYVrq9M6FQjSD1dbglptQbll30IMV=w120-h120-l90-rj"),
        FavKanzTrack("Wy5HTaOZ6EA", "There Is a Light That Never Goes Out (2017 Master)", "The Smiths", "https://yt3.googleusercontent.com/TsiwDZL1y5PWawEN3aozCx23IkTXFYiHxc8dB_bZc1LR22s0PJrp7pcNU0fOdniSxRwYm7-JG9wt17E=w120-h120-l90-rj"),
        FavKanzTrack("xF1Yu-amFfk", "Sofia", "Clairo", "https://yt3.googleusercontent.com/O7tPf_tdS29Re6crTsMO-vCwJmX8_TkSXDwAfKpi17vFkJoVyQZUQRovFIkIlEqPKFsMSz7cb-a0UCM=w120-h120-l90-rj"),
        FavKanzTrack("e7yg0A-PCTI", "Love Story (Taylor's Version)", "Taylor Swift", "https://yt3.googleusercontent.com/huIYugJjpimikk-tSDJyDU09Jr8vDVJuLOh5uGG6T45c5MEtYvFW_9NlqkHHSaLl231kLBgAS8rtDfQ=w120-h120-l90-rj"),
        FavKanzTrack("8UY5BGFLtK0", "Payphone", "Maroon 5", "https://yt3.googleusercontent.com/kT4YnGXbEuzj6yHtv7Wf4vZSdawtaVufBWs3zAMRfM4f95ujvZjzzmNaewhpDv5sM4rHNkpZ470X9nw=w120-h120-l90-rj"),
        FavKanzTrack("MMvlxSupSVE", "Best Friend", "Rex Orange County", "https://yt3.googleusercontent.com/c95uKWCafaUKEC_vVTJ6PaAxONPAIdaP_7ZX2qRkqTTLS-nCcXo-wX9Ksz8Gp02CS2Lhs-N6CgRHrGbz=w120-h120-l90-rj"),
        FavKanzTrack("D1JpkgXxMR8", "Hotel Room", "Ax and the Hatchetmen", "https://yt3.googleusercontent.com/_05EiodBVO9Gt86llxnd-knVfisoqlzqCWU07-qGOoDrUayvPUta3vWGvwI8Wqp-iNrLG7w6uYv-CgfX9A=w120-h120-l90-rj"),
        FavKanzTrack("tGgPcLsqN2c", "Lemon Tree", "Gustixa", "https://yt3.googleusercontent.com/0k7ElOkKmpvjVIVp5dky_oT1tha-vkYf9sR8hpzF1Ssa9ODdNv-afdv-gUeUsV0Hrhg6Lo4A9iuRAg1s=w120-h120-l90-rj"),
        FavKanzTrack("6Kqrcm4RPW8", "Asmalibrasi", "Soegi Bornean", "https://yt3.googleusercontent.com/Qz7KFHDsw_Z4awE-e8epkotIYXhZYPptSLEeLNFsT4AzOLYm575BpiE3I_h3kH5Ygzn6yVP4G58siX6G=w120-h120-l90-rj"),
        FavKanzTrack("Hz0pIAG6SCE", "Dinda (Versi Sped Up)", "Masdo", "https://yt3.googleusercontent.com/doV2wvPmVDpldv3EUtszi67H1kqfGYJFuQwCXIAoysbVSjAQTReYLDp2E9zLqlNc9198HwyjmyVV5o4=w120-h120-l90-rj"),
        FavKanzTrack("_womM8X0ti0", "Lampu Merah", "The Lantis", "https://yt3.googleusercontent.com/tbQyC3UAiZdK8JcBdxKhhCZhBVE1cqYPZw3hsG56fPHpoj8ObAJHGZ154jCJSUieKz1nsvFF5yZ6ywE5=w120-h120-l90-rj"),
    )

    fun toSongItem(t: FavKanzTrack): SongItem =
        SongItem(
            id = t.videoId,
            title = t.title,
            artists = listOf(Artist(name = t.artist, id = null)),
            thumbnail = t.cover,
            endpoint = WatchEndpoint(videoId = t.videoId),
        )
}
