# Senza Sound

Aplikasi equalizer & audio spasial untuk Android (Kotlin + Jetpack Compose), minimal Android 9 (API 28).
Bukan produk Dolby; memakai efek audio Android sendiri (`android.media.audiofx`).

## Fitur
- Equalizer 10 band (31 Hz–16 kHz, ±12 dB) dengan grafik respons frekuensi, 13 preset + preset buatan sendiri
- Profil: Film, Musik, Game, Suara, Kustom (masing-masing menyimpan pengaturannya)
- Surround virtualizer (mode binaural untuk headphone, transaural untuk speaker – otomatis)
- Penguat dialog, bass enhancer, Intelligent EQ (Seimbang/Detail/Hangat + intensitas)
- Volume leveler (kompresor 3 band), loudness boost, limiter, "Volume aman"
- Deteksi output: Speaker / Headphone / Bluetooth
- Pemutar musik internal + penguji suara (sweep, pink noise, tes kiri/kanan) + analyzer spektrum
- Service latar belakang + penerima sesi audio dari aplikasi pemutar lain
- Halaman diagnostik: efek mana yang didukung perangkat

## Cara membuat APK
**Opsi A – GitHub (tanpa PC):**
1. Buat repo baru di GitHub, upload seluruh isi folder ini (termasuk folder `.github`).
2. Buka tab **Actions → Build APK → Run workflow**.
3. Setelah selesai, unduh artifact **SenzaSound-APK** → pasang `app-release.apk`.

**Opsi B – Android Studio:** buka folder ini, tunggu sync, lalu *Build → Build APK(s)*.

## Catatan
- Efek global (sesi 0) hanya diizinkan sebagian ROM; pemutar internal selalu bekerja.
- Kode belum pernah dikompilasi di lingkungan pembuatnya; jika build gagal, kirim log error-nya.
