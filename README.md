# Dynamic Island (Fabric 1.21.11)

Notch / pil di tengah atas layar: music player online + offline, thumbnail, equalizer live,
lirik tersinkron, dan pengingat waktu.

## Build
Butuh JDK 21 + internet (download Minecraft/Fabric dari maven).

    ./gradlew build          # Windows: gradlew.bat build

Hasil: `build/libs/dynamic-island-1.0.0.jar` -> taruh di folder `mods/` bersama Fabric API.

## Pakai
- **J** buka Music Player, **K** play/pause, **]** lagu berikutnya (bisa diubah di Controls).
- Pause menu -> tombol **Island Settings** (kiri tombol Options): bentuk Notch/Pil, posisi X/Y,
  ukuran, opacity, warna aksen, mode animasi (Super Ringan / Ringan / Berat), kecepatan animasi,
  toggle thumbnail/equalizer/lirik, pengingat, jadwal kegiatan, tes notifikasi.
- Pengingat: default jam **20:30** notch melebar "Waktunya selesai main". Tambah kegiatan sendiri
  (mis. "Berangkat sekolah" 06:00) di Island Settings -> Pengingat -> Jadwal kegiatan.
- Lagu: sumber **Jamendo** (stream penuh, legal, boleh di-download). Tombol `DL` menyimpan mp3 + cover + lirik
  ke `.minecraft/dynamicisland/music`, tab **Offline** memutarnya tanpa internet.
- Lirik dari lrclib.net (hanya muncul kalau tersedia).
- Config: `config/dynamicisland.json`, jadwal: `config/dynamicisland-schedule.json`.
  Kalau pencarian selalu kosong, buat client_id gratis di devportal.jamendo.com dan isi `jamendoClientId`.
