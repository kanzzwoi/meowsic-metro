---v0.6.5
# Tampilan baru dan perbaikan notifikasi
- Latar Beranda memakai gambar gelombang biru statis (bukan animasi),
  sehingga tidak lagi berat dan tidak patah-patah.
- Banner Beranda memakai gambar transparan dengan latar kaca putih tipis,
  tanpa blok gelap di belakangnya.
- Chip filter dan bilah atas dibuat transparan agar latar terlihat.
- Pindah tab kini memakai transisi fade singkat, terasa lebih cepat dan halus.
- Kerangka pemuatan (skeleton) muncul sejak awal saat Beranda dibuka.
- Halaman Cari, Bersama, dan Pustaka tidak lagi menutup latar dengan warna
  pekat; kotak di Pustaka memakai kaca putih tipis dengan garis tepi.
- Tombol putar/jeda dan tutup pada mini player dibuat lebih jelas.
- Menutup lagu dari mini player tidak lagi memunculkan notifikasi pemutar
  kosong yang rusak.
- Ukuran unduhan APK lebih kecil karena gambar latar dan banner dipadatkan.

---v0.6.4
# Nav bar dan logo
- Nav bar dinaikkan sehingga lebih dekat dengan mini player, dan ikon di
  dalamnya kini sejajar dengan label.
- Logo Meowsic pada kartu bagikan lirik diperbesar agar seimbang dengan
  teks di sampingnya.
- Tombol acak melayang di Beranda dihapus karena muncul walaupun tidak ada
  lagu yang bisa diputar.

---v0.6.3
# Logo, latar buram, dan ikon ulang
- Logo Meowsic di kartu bagikan lirik memakai gambar asli dengan latar
  transparan, tidak lagi diwarnai satu warna sehingga bentuknya utuh.
- Latar Buram pada gambar bagikan lirik tidak lagi tampak pekat; sampul
  kini diburamkan pada resolusi yang cukup sehingga warna dan bentuknya
  masih terlihat.
- Ikon Ulang tidak lagi punya kotak kecil di belakang panah.

---v0.6.2
# Logo Meowsic
- Logo di kartu bagikan lirik memakai PNG transparan asli, tanpa diwarnai
  satu warna, sehingga bentuk kucingnya utuh.

---v0.6.1
# Perbaikan build
- Kembalikan tampilan nav bar, logo, dan layout ke kondisi stabil 0.5.10
  untuk memastikan APK bisa dibangun kembali.

---v0.5.10
# Nav bar diperbaiki
- Ikon dan label nav bar kembali terlihat (sebelumnya hilang karena ruang
  pil termakan padding).
- Isi nav bar kini rata tengah, tidak menempel ke dasar pil.
- Mini player didekatkan ke nav bar.

---v0.5.9
# Nav bar mengambang
- Pil nav bar tidak lagi terpotong di bagian bawah dan kini punya jarak
  nyata dari tombol navigasi HP.
- Mini player dan nav bar menjadi dua elemen mengambang yang terpisah,
  dengan celah di antaranya.
- Warna pil disesuaikan: abu-abu gelap transparan dengan border tipis.

---v0.5.8
# Wallpaper liquid & kartu lirik
- Wallpaper gradien abstrak akhirnya tampil di belakang aplikasi. Selama
  ini tidak pernah muncul karena tertutup lapisan latar layar.
- Kartu bagikan lirik tidak lagi punya pita putih di tepi; latar buram
  kini memakai blur penuh dari sampul.

---v0.5.7
# Tombol Ulang disederhanakan
- Kini hanya satu tombol Ulang: tekan untuk mengulang lagu terus-menerus,
  tekan lagi untuk mematikan. Tidak ada lagi mode "Ulang 1" yang membuat
  perilakunya membingungkan.
- Ikon repeat tidak lagi memakai angka 1.
- Seluruh logika ulang yang saling bertabrakan dihapus; pengulangan kini
  ditangani langsung oleh mesin pemutar sehingga jauh lebih andal.

---v0.5.6
# Penyesuaian nav bar
- Pil nav bar diangkat agar tidak menabrak tombol navigasi HP.
- Ikon digeser turun sedikit supaya seimbang di dalam pil.
- Warna kaca memakai putih 20% sehingga tidak terlalu transparan.

---v0.5.5
# Mini player tidak lagi tertutup nav bar
- Tinggi nav bar dan ruang yang disediakan induk kini memakai satu angka
  yang sama, sehingga mini player berhenti tepat di atas pil nav bar.
  Sebelumnya ada selisih 22dp yang membuat keduanya bertumpuk.

---v0.5.4
# Latar gelombang lembut & nav bar lebih ringkas
- Latar liquid digambar ulang: kini pita gelombang lebar yang tenang
  (biru tua ke hitam, sedikit biru muda di puncak), bukan pola ramai.
  Gerakannya lambat sehingga tidak mengganggu.
- Nav bar dipendekkan (80dp -> 58dp) dan pilnya dibuat lebih ringkas.

---v0.5.3
# Wallpaper liquid terlihat & nav bar kembali standar
- Latar liquid glass kini benar-benar tampil. Sebelumnya Scaffold Material3
  punya warna latar bawaan yang menutupi wallpaper di belakangnya; kini
  transparan saat opsi liquid aktif.
- Bottom navigation kembali memakai NavigationBar Material3 sesuai panduan
  Android, sehingga ukuran dan jaraknya standar — tidak lagi kegedean.
  Efek kaca hanya pada warna kontainer dan pembungkus pil.
- Indikator item aktif memakai primaryContainer sesuai peran warna tema.

---v0.5.2
# Nav bar, tombol Ulang, dan kartu lirik
- Nav bar kaca tidak lagi tenggelam: pil kini lebih pendek dari ruangnya
  sehingga terlihat mengapung dan tidak menempel ke tombol navigasi.
- Tombol Ulang diperbaiki: pencet pertama = Ulang (lagu mengulang terus),
  kedua = Ulang 1 (sekali lalu lanjut), ketiga = mati. Sebelumnya pencet
  pertama langsung masuk Ulang 1 karena flag disimpan tidak sinkron.
- Kartu bagikan lirik: latar Buram kini benar-benar tampil (sebelumnya
  hanya warna solid pekat), dan logo tidak lagi menutupi seluruh lingkaran.
- Latar liquid memakai palet biru + putih + hitam, sama untuk mode terang
  maupun gelap.

---v0.5.1
# Liquid glass langsung aktif
- Latar liquid glass kini menyala secara default, jadi bisa langsung
  terlihat tanpa perlu mencarinya di pengaturan.
- Tetap bisa dimatikan di Tema & Warna bila tidak diinginkan.

---v0.5.0
# Liquid glass, perbaikan Ulang, dan pembaruan kecil
- Latar liquid glass: wallpaper gradien biru tua yang bergerak pelan di
  belakang aplikasi. Sangat ringan (tanpa bitmap, tanpa blur per-frame),
  dan warnanya mengikuti warna tema. Aktifkan di Tema & Warna.
- Bottom navigation bergaya kaca saat liquid aktif: pil mengapung,
  semi-transparan, border tipis terang, dan sorotan pada item aktif.
- Tombol Ulang diperbaiki: pencet 1x = lagu mengulang terus, 2x = Ulang 1
  (sekali lalu lanjut), 3x = mati. Mode ulang ikut mati saat ganti lagu.
- Ikon notifikasi / lock screen memakai logo Meowsic baru.
- Kartu bagikan lirik default berlatar buram, dan judulnya mengecil
  otomatis sampai maksimal 2 baris sebelum dipotong elipsis.
- Tombol Unduh pada dialog bagikan lirik: menyimpan kartu langsung ke
  galeri tanpa membuka dialog bagikan.
- Pilihan warna tema baru: Biru tua (#071B85).

---v0.4.4
# Latar buram benar-benar tampil & tombol Ulang diperbaiki
- Akar masalah latar buram ditemukan: fungsi stackBlur sendiri menghasilkan
  data rusak (akumulator jendela geser memakai pembagi yang berubah sehingga
  nilai meluber keluar 0..255). Itu sebabnya tidak ada blur yang pernah
  tampil sejak awal. Algoritma ditulis ulang dengan pembagi tetap dan sudah
  diuji: piksel menyebar benar dan semua kanal valid.
- Tombol Ulang tidak lagi kebalik. Sebelumnya dua jalur (onMediaItemTransition
  dan onPlaybackStateChanged) saling bertabrakan, sehingga "Ulang" berperilaku
  seperti "Ulang 1". Kini hanya satu jalur yang mengatur: Ulang = mengulang
  terus, Ulang 1 = mengulang sekali lalu lanjut.
- Mode ulang kini ikut mati saat lagu berganti (next/prev/pilih lagu).

---v0.4.3
# Latar buram: tulis ulang sebagai komponen mandiri
- Blur dipindah ke komponen `BlurBackground` yang memegang state-nya sendiri,
  bukan state di dalam blok `when`. State di dalam cabang `when` mudah
  dibuang saat recomposition sehingga bitmap tidak pernah tampil.
- Tetap blur di CPU (stackBlur) untuk 100x100, tidak bergantung pada
  RenderEffect maupun Android 12+.

---v0.4.2
# Perbaikan latar buram & tombol Ulang
- Latar buram kini dirender di ruang bitmap (CPU) alih-alih RenderEffect
  GPU: Modifier.blur()/RenderEffect diabaikan saat kontennya AsyncImage
  (Coil), sehingga latar tampil polos. Jalur bitmap + stackBlur
  deterministik di semua perangkat.
- Tombol Ulang disesuaikan: pencet 1x = lagu mengulang terus sampai
  dimatikan atau pindah lagu; pencet 2x = Ulang 1 (mengulang sekali lalu
  lanjut); pencet 3x = mati. Ikon: repeat_on / repeat_one_on.

---v0.4.1
# Perbaikan latar buram
- Latar buram (blur) mini-player dan full player kini benar-benar tampil.
  Sebelumnya Modifier.blur() diabaikan saat memuat gambar via Coil,
  sehingga latar tetap berwarna polos (bug juga ada di upstream).
- Ganti ke RenderEffect langsung di graphicsLayer + overscale 1.5x
  agar blur pasti dirender dan tepi gambar tidak berkerut.

---v13.7.0
# KMP status update
Metrolist-KMP remains in alpha, but tester feedback has been very positive. To try it, join our Discord and donate at least $1 to support development.

## Highlights
- Migrated networking to InnerTubeX with automatic client fallback (@nyxiereal)
- Reduced memory use and background work in large libraries, History, Cache, Android Auto, and audio processing (@makro17 @nyxiereal)
- Repaired older-version upgrades, Android Auto browsing, artist metadata, and YouTube channel switching (@nyxiereal)
- Improved login, account loading, uploads, downloads, caching, metadata editing, and playlist sync (@nyxiereal)
- Restored the classic app icon (@nyxiereal)

## New features
- Choose whether songs are added to the start or end of playlists (@nyxiereal)
- Added Zemer lyrics for Jewish music (@alltechdev)
- Added detailed, copyable playback error reports (@nyxiereal)
- Added a unified update prompt for standalone and KMP releases (@nyxiereal)
- Added Inception AI models and improved lyric translation output (@nyxiereal)
- Restored universal x86 and x86_64 support (@nyxiereal)

## Fixes and improvements
- Fixed missing or cropped artwork and improved timed and Cyrillic lyrics (@RizkLee @GameOn223 @Cocoa2219 @nyxiereal)
- Preserved manual metadata edits during refreshes and corrected Cache Playlist contents (@nyxiereal)
- Fixed repeat and shuffle with crossfade, Listen Together stutter, and paused queues restarting at the end (@nyxiereal)
- Fixed per-song volume normalization and improved playback recovery (@nyxiereal)
- Fixed dismissed media controls reappearing and several startup crashes (@nyxiereal)
- Protected local likes from incomplete sync responses and improved uploaded-song handling (@nyxiereal)
- Updated dependencies and CI (@nyxiereal)

New contributors: @RizkLee (#4277), @GameOn223 (#4269), and @makro17 (#4270)

**Full changelog**: https://github.com/MetrolistGroup/Metrolist/compare/v13.6.3...v13.7.0

---v13.6.3

This is a hotfix release to fix borked lyrics and media controller. We apologize for the inconvenience.  

~ MetrolistGroup

---v13.6.2
# THE FUTURE OF METROLIST
Metrolist KMP is almost ready. We are ironing out the remaining bugs and preparing for release, but it is still at least a couple of weeks away.

# Major changes
- Fixed playback issues caused by changes that also broke official apps (@nyxiereal)
- Fixed playlist sync duplication and out-of-memory crashes (@kairosci)
- Improved automatic player configuration updates for future YouTube changes (@mostafaalagamy @nyxiereal)

## Notable new features
- Added handling for KMP updates and migrations (@nyxiereal)

## Other improvements
- Improved Android Auto voice search matching and radio queue generation (@FireLion137)
- Added an Android Auto search limit and optimized local searches to prevent out-of-memory crashes (@FireLion137)
- Fixed podcast playback errors (@kairosci)
- Fixed crossfade timing at non-default playback speeds (@kairosci)
- Fixed the app lingering in the background after it was closed (@kairosci)
- Fixed sleep timer dialog layouts and menus containing long translated text (@kairosci)
- Fixed the persistent shuffle setting not working (@SimoneFelici)
- Dimmed the repeat button when repeat is disabled (@arpitagarwal1301)
- Rounded the corners of exported lyrics images (@arpitagarwal1301)
- Updated dependencies (@nyxiereal)

## New Contributors
* @SimoneFelici made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/4102
* @arpitagarwal1301 made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/4178

**Full Changelog**: https://github.com/MetrolistGroup/Metrolist/compare/v13.6.1...v13.6.2

---v13.6.1
# THE FUTURE OF METROLIST
The new Kotlin Multiplatform version of Metrolist is now in a good state, and we are aiming to release it within the next month. Until then, the current app will remain in maintenance mode and receive bug fixes and minor improvements.

# Major changes
- Improved playback reliability and recovery from YouTube player failures (@alltechdev @JASK625 @kairosci @mostafaalagamy @nyxiereal)
- Fixed black screens, startup crashes, and playback freezes (@kairosci @mostafaalagamy @nyxiereal)
- Improved Listen Together synchronization (@nyxiereal)

## Notable new features
- Added refreshed branding and an optional dynamic app icon (@mostafaalagamy)
- Added predictive back support and improved landscape scaling (@HansHolz09 @kairosci)

## Other improvements
- Fixed login, logout, and backup restoration issues (@mostafaalagamy @kairosci @nyxiereal)
- Fixed incorrect artist names, song durations, podcast metadata, and missing artwork (@alltechdev @kairosci @mostafaalagamy @nyxiereal)
- Fixed Android Auto freezes and playback delays (@kairosci)
- Fixed foreground service ANRs and multiple other crashes (@kairosci @mostafaalagamy)
- Fixed sleep timer crashes and restored saved defaults (@johannesbrauer @mostafaalagamy)
- Improved library sync performance and YouTube player compatibility (@mostafaalagamy @nyxiereal)

## New Contributors
* @HansHolz09 made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3837

**Full Changelog**: https://github.com/MetrolistGroup/Metrolist/compare/v13.6.0...v13.6.1

---v13.5.0
# MAINTENANCE MODE
Metrolist is currently in maintenance mode. This means we will only be fixing bugs and making minor improvements. Please do not submit PRs for new features or major changes, as they will not be accepted.

# Major changes
- Rewrote the Discord RPC integration again (@adrielGGmotion @nyxiereal)
- Fixed random playback issues and pauses (@DanielSchmerber @isotjs)
- Fixed liked songs, playlists, albums, search results, etc. not displaying properly (@adrielGGmotion @nyxiereal)

## Notable new features
- Added a toggle for automatic radio queue generation (@FireLion137)
- Added automatic tablet UI scaling (@kairosci)
- Toggle from repeat(1) to repeat(all) after song change (@sunjeetkajla)

## Other improvements
- Fixe covers not loading sometimes (@Arjuanto)
- Artist names are now split properly, and are now clickable (@kairosci)
- Fixed multiple crashes (@kairosci @nyxiereal)
- Improved audio normalization (@kairosci)
- Fixed search results not being combined properly (@kairosci)
- Fixed 'High quality' option not choosing the highest quality option (@kairosci)
- Fixed history sync not working (@kairosci)

## New Contributors
- @DanielSchmerber made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3777
- @Arjuanto made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3780

---v13.4.3
# MAINTENANCE MODE
Metrolist is currently in maintenance mode. This means we will only be fixing bugs and making minor improvements. Please do not submit PRs for new features or major changes, as they will not be accepted.

# Major changes
- Rewrote the Discord RPC integration (@adrielGGmotion)
- Improved the look of playlist screens (@adrielGGmotion)
- Added a new playlist widget (@David-2765 @AntonioDionisio05)

## Notable new features
- Added proper apple music lyrics support (@adrielGGmotion)
- Added a normalization level selector (@Jeff0945)
- Added the ability to hide monthly/weekly most playlists (@isotjs)

## Other improvements
- Improve overall performance and stability (@adrielGGmotion @nyxiereal)
- Fixed devnagari lyrics not being displayed properly (@cloud-zip)
- Improve lyrics fetching speed (@nyxiereal)
- Fixed crashes and some memory leaks (@nyxiereal)
- Fixed images being low resolution for some users (@adrielGGmotion)
- Handle playlist paging properly (@kairosci)
- Multiple smaller improvements by @kairosci <3

## New Contributors
- @Jeff0945 made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3358

---v13.4.2
# MAINTENANCE MODE
Metrolist is currently in maintenance mode. This means we will only be fixing bugs and making minor improvements. Please do not submit PRs for new features or major changes, as they will not be accepted.

# Major changes
- Fixed random crashes and some memory leaks (@nyxiereal)
- Fixed issues with uploading songs to YouTube (@kairosci)
- Fixed playback for uploaded songs (@punkscience)

## Notable new features
- EQ screen redesign and guided AutoEQ profile import (@ndellagrotte)
- Automatically create database backups before updates (@nyxiereal)

## Other improvements
- Improved support for Android Auto (@cmeka)
- Brought back the copy lyrics button for experimental lyrics (@nyxiereal)
- Fixed the re-sync button for experimental lyrics (@nyxiereal)
- Fixed listen together not working (@nyxiereal)
- Added back support for choosing an account upon login (@nyxiereal)
- Implemented concurrent fetching, fix lyrics fetch ordering, and optimize LyricsPlus server selection (@ibratabian17)
- Corrected the play-next shuffle order (@johannesbrauer)
- Improved the Android Auto icon (@ThatOneCalculator)

## New Contributors
- @ndellagrotte made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3487
- @cmeka made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3534
- @punkscience made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3517

---v13.4.1
# MAINTENANCE MODE
Metrolist is currently in maintenance mode. This means we will only be fixing bugs and making minor improvements. Please do not submit PRs for new features or major changes, as they will not be accepted.

# Major changes
- Fixed cached songs showing up in the downloads playlist (@nyxiereal)
- Fixed multiple playback issues and prepared for YouTube's player changes (@mostafaalagamy @nyxiereal)

## Notable new features
- Added the ability to paste URLs to the search to play them directly (@nyxiereal)
- Added a search bar to the Library screen (@isotjs)
- Added a setting to bind pitch and speed together (@sasha-melech)
- Added support for Gemini voice playback (@FireLion137)
- Added an option choose the highest possible audio quality (@nyxiereal @kairosci)
- Added a button to create a playlist from the Library screen (@SunjeetKajla)

## Other improvements
- Moved the resync button to the lyrics menu (@nyxiereal)
- Properly reset player on IO errors (@kairosci)
- Multiple improvements to lyrics fetching and parsing (@kairosci @nyxiereal @ibratabian17)
- Made autoplay disablable from the settings (@kairosci)
- Fixed foreground/background service crashes (@kairosci)
- Fixed Play next not working (@johannesbrauer)
- Properly handle database updates on download removal (@kairosci)
- Use lyricsplus caching to lower server load (@binimum)
- Performance optimizations (@stopper2408)
- Prefetch lyrics for the next song if currently viewing lyrics (@nyxiereal)
- Fixed multiple issues with Listen Together (@nyxiereal)
- Fixed multiple issues with the experimental lyrics (@nyxiereal)
- Fixed pause music on task clear not working (@nyxiereal)

## New Contributors
* @ibratabian17 made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3474
* @sasha-melech made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3301
* @FireLion137 made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3500
* @binimum made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3493
* @stopper2408 made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3506
* @SunjeetKajla made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3505

**Full Changelog**: https://github.com/MetrolistGroup/Metrolist/compare/v13.4.0...v13.4.1
---v13.4.0
# MAINTENANCE MODE
Metrolist is currently in maintenance mode. This means we will only be fixing bugs and making minor improvements. Please do not submit PRs for new features or major changes, as they will not be accepted.

No, this is not an April Fools joke, even though this update is being released on April 1st.

We are working on something big for the future of Metrolist - this is not the end of the project.

# Major changes
- Multiple playback fixes and reliability improvements (@alltechdev)
- Revamped the entire Lyrics engine, improving lyric accuracy and usability (@adrielGGmotion)
- Fixed multiple crash issues (@kairosci, @nyxiereal)
- Multiple improvements to Android Auto support (@andker87)
- Fixed multiple grammar and text inconsistency issues in the project (@TheRebo)

## Notable new features
- Added support for treating cached songs as offline songs (@kairosci)
- Added music alarm scheduling (@0xarchit)
- Added miniplayer styles (@johannesbrauer)
- Added a button to copy all song lyrics to the clipboard (@kairosci)
- Added a time transfer feature to move listening time between songs in the stats page (@finley-webber)
- Added customization support for the AI prompt used for translations (@nyxiereal)
- Added a notification-based music recognition for the QS tile shortcut (@isotjs)

## Other improvements
- Fixed incorrect artist order for multi-artist songs (@AntonioDionisio05)
- Fixed playtime in the stats page not being fully visible (@David-2765)
- Improved radio to start seamlessly when initiated from the currently playing track (@luigiwwmf)
- Improved the UI for tablets (@adrielGGmotion)
- Improved the About Screen layout (@adrielGGmotion)
- Fixed ghost adds on playlists (@johannesbrauer)
- Improved search focus and navigation behavior (@saivijaychandan)
- Added album navigation on song title click regardless of play source (@gergesh)
- Prevented UI state reset when switching apps (@mostafaalagamy)
- Restored the Daily Discover title in the Home screen (@mostafaalagamy)
- Fixed listen together audio choppiness (@nyxiereal)
- Redesigned romanization and account settings (@omardotdev)
- Improved the design of the sleep timer dialog (@johannesbrauer)
- Redesigned some components to use Material 3 Expressive (@johannesbrauer)
- Fixed links in the README (@Lolen10 @nyxiereal)

## New Contributors
* @AntonioDionisio05 made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3255
* @David-2765 made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3271
* @luigiwwmf made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3293
* @gergesh made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3300
* @Lolen10 made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3328

**Full Changelog**: https://github.com/MetrolistGroup/Metrolist/compare/v13.3.0...v13.3.1
---v13.3.0
# Major changes
- Implemented song upload and delete functionality (@alltechdev)
- Multiple playback fixes and reliability improvements (@alltechdev, @mostafaalagamy)
- Fixed proguard rules causing issues with Reproducible Builds (@nyxiereal)
- Fixed proguard rules removing Listen Together protobuf classes (@mostafaalagamy)
- Added a playlist export option to the playlist context menu (@nyxiereal)

## Notable new features
- Added a Play all action for the stats page (@isotjs)
- Added a quick settings tile for recognizing music (@nyxiereal)
- Added automatic sleep timer options and integrated fade-out volume handling (@isotjs)
- Added a profile search filter (@alltechdev)
- Added channel subscriptions for podcasts and artists (@alltechdev)

## Other improvements
- Fixed cached images not clearing properly and cached covers not showing when offline (@nyxiereal)
- Removed useless and stale strings from the codebase (@nyxiereal)
- Refined the song details view (@omardotdev)
- Added support for Mistral AI models (@nyxiereal)
- Redesigned the lastfm integration settings (@omardotdev)
- Fixed importing csv files crashing the app (@nyxiereal)
- Prevent guest playback while in listen together (@nyxiereal)
- Fixed podcasts not working for logged-out users (@alltechdev)
- Updated dependencies (@nyxiereal)

## New Contributors
* @isotjs made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/3090

**Full Changelog**: https://github.com/MetrolistGroup/Metrolist/compare/v13.2.1...v13.3.0
---v13.2.1
>[!WARNING]
>Listen Together doesn't work in v13.2.1! Use v13.2.0 if you need it.

## Hot Fixes
- Fix interface lag issue
- Fix navigate local playlists pinned in speed dial
- Removed "cache songs only after playback has started" option

**Full Changelog**: https://github.com/MetrolistGroup/Metrolist/compare/v13.2.0...v13.2.1
---v13.2.0
# Major changes
- Fixed playback breaking due to YouTube's February 2026 n-transform changes (@alltechdev)
- Added full podcast library support (@mostafaalagamy & @alltechdev)
- Redesigned loading, Changelog, and About screens (@adrielGGmotion)
- Improved app startup time via parallelized home screen loading (@mostafaalagamy)

## Notable new features
- Added an option to cache songs only after playback has started (@kairosci)
- Added a music recognizer home screen widget (@mostafaalagamy)
- Rewrote music recognizer in pure Kotlin, removing NDK dependency and reducing APK size (@mostafaalagamy)
- Overhauled lyrics: added LyricsPlus provider, AI lyric fixes, untranslation support, and provider priority settings (@nyxiereal)
- Changed listen together to use protobuf, lowering latency and improving reliability (@nyxiereal)
- Added auto-approve setting for listen together song requests (@nyxiereal)
- Added an option to persist the sleep timer default value (@johannesbrauer)
- Added a dialog on logout to keep or clear library data (@alltechdev)

## Other improvements
- Fixed backup restore causing playback errors due to stale auth credentials (@alltechdev)
- The CSV import dialog is now scrollable (@kairosci)
- Fixed Android 15 foreground service crashes (@kairosci)
- Fixed a crash on the About screen on some devices (@mostafaalagamy)
- Fixed home screen playlist navigation routing to wrong screen (@mostafaalagamy)
- Fixed crash when creating local playlists (@mostafaalagamy)

## New Contributors
* @johannesbrauer made their first contribution in https://github.com/MetrolistGroup/Metrolist/pull/2991

**Full Changelog**: https://github.com/MetrolistGroup/Metrolist/compare/v13.1.1...v13.2.0
