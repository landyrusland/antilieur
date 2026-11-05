# Anti lieur

Aplikasi Android yang menampilkan **titik isyarat gerak** (motion cues) di tepi kiri dan kanan layar.
Titik bergeser mengikuti percepatan, pengereman, dan belokan kendaraan, sehingga mata ikut "melihat"
gerakan yang dirasakan tubuh. Ini membantu mengurangi mabuk perjalanan (lieur) saat melihat layar HP.

- Berjalan di atas semua aplikasi (YouTube, chat, browser, dll.) dan tidak menghalangi sentuhan
- Bisa diatur: sensitivitas, ukuran titik, jumlah titik, dan kepekatan
- Tanpa internet, tanpa iklan, tanpa library tambahan (APK hanya puluhan KB)
- Android 8.0 ke atas

---

## Cara 1: Build lewat GitHub Actions (tanpa instal apa pun di laptop)

1. Buat akun di github.com, lalu klik **New repository** (beri nama misalnya `anti-lieur`).
2. Klik **uploading an existing file**, lalu seret **seluruh isi** folder `AntiLieur`
   (termasuk folder `.github` dan `gradle`). Klik **Commit changes**.
   - Folder `.github` kadang tersembunyi di Windows Explorer. Aktifkan
     *View → Show → Hidden items*, atau gunakan GitHub Desktop.
3. Buka tab **Actions**. Build berjalan otomatis (sekitar 3–6 menit).
   Jika belum jalan, pilih **Build APK → Run workflow**.
4. Setelah centang hijau, buka hasil build, unduh **anti-lieur-apk** (file .zip),
   ekstrak, lalu kirim `app-release.apk` ke HP dan pasang.
   HP akan meminta izin *Instal aplikasi tidak dikenal*: izinkan untuk aplikasi pengelola file Anda.

Catatan: setiap build di GitHub memakai kunci tanda tangan sementara yang berbeda.
Jika pemasangan versi baru gagal, hapus dulu versi lama dari HP.

---

## Cara 2: Build lokal dengan Android command-line tools (Windows 10)

Total ruang yang dibutuhkan sekitar 3–4 GB (tanpa Android Studio dan tanpa emulator).

### a. Pasang JDK 17
Unduh **Eclipse Temurin JDK 17 (Windows x64, .msi)** dari adoptium.net, lalu pasang.
Saat instalasi, aktifkan opsi *Set JAVA_HOME variable*.

### b. Pasang Android command-line tools
1. Dari developer.android.com/studio, gulir ke bagian **Command line tools only**,
   unduh versi Windows.
2. Buat folder `C:\Android\cmdline-tools\latest`, lalu ekstrak isi zip sehingga
   strukturnya menjadi `C:\Android\cmdline-tools\latest\bin\sdkmanager.bat`.
3. Buka **Command Prompt** dan jalankan:

```bat
setx ANDROID_HOME C:\Android
```

4. Tutup lalu buka lagi Command Prompt, kemudian:

```bat
cd C:\Android\cmdline-tools\latest\bin
sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

Ketik `y` untuk menyetujui lisensi.

### c. Build APK

```bat
cd C:\lokasi\folder\AntiLieur
gradlew.bat assembleRelease
```

Build pertama mengunduh Gradle dan plugin Android (butuh internet, beberapa menit).
Hasilnya ada di:

```
app\build\outputs\apk\release\app-release.apk
```

### d. Pasang ke HP lewat kabel USB (opsional)
1. Di HP: *Pengaturan → Tentang ponsel →* ketuk **Nomor build** 7 kali,
   lalu aktifkan **USB debugging** di *Opsi pengembang*.
2. Sambungkan kabel, izinkan debugging di HP, lalu:

```bat
C:\Android\platform-tools\adb install -r app\build\outputs\apk\release\app-release.apk
```

---

## Cara pakai
1. Buka **Anti lieur**, ketuk **Mulai isyarat gerak**.
2. Pertama kali, aktifkan izin **Tampilkan di atas aplikasi lain**, lalu kembali ke aplikasi.
3. Buka aplikasi apa pun. Titik akan bergerak saat kendaraan mengerem, menambah kecepatan, atau berbelok.
4. Hentikan dari aplikasi atau tombol **Hentikan** di notifikasi.

Tips: duduk menghadap ke depan kendaraan. Mulai dari sensitivitas 5, naikkan jika gerakan titik terlalu kecil.

---

## Struktur kode

| File | Fungsi |
|---|---|
| `MainActivity.kt` | Layar utama: tombol mulai/berhenti, izin, dan pengaturan |
| `MotionCueService.kt` | Layanan latar depan: membaca sensor dan menampilkan overlay |
| `MotionCueView.kt` | Menggambar titik dan menganimasikannya dengan model pegas |
| `CueSettings.kt` | Menyimpan pengaturan pengguna |
| `.github/workflows/build.yml` | Resep build otomatis di GitHub |

### Cara kerja sensor
Layanan memakai sensor **gravitasi** dan **percepatan linear** (dengan cadangan akselerometer biasa).
Arah gravitasi dipakai untuk menentukan bidang datar, sehingga percepatan bisa dipisah menjadi
komponen **ke depan** dan **ke samping** tidak peduli HP dipegang tegak, miring, atau mendatar,
dan dalam mode potret maupun lanskap.

- Kendaraan menambah kecepatan → titik turun (pemandangan seolah mendekat)
- Kendaraan mengerem → titik naik
- Belok kanan → titik bergeser ke kiri, dan sebaliknya
