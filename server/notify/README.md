# instagaram-notify

Server kecil di **AWS Lambda** yang mengirim **notifikasi push** aplikasi Instagaram lewat **Firebase Cloud Messaging (FCM)**, untuk tiga kejadian:

- 💬 **Pesan baru** di chat
- ❤️ **Suka baru** pada postingan
- 👤 **Pengikut baru**

---

## Cara kerjanya

1. Aplikasi menyimpan data seperti biasa ke Firestore, misalnya pesan chat baru.
2. Aplikasi lalu memanggil server ini dengan **jenis** kejadian dan **ID dokumennya** saja:
   ```json
   { "type": "chat", "id": "<id dokumen>" }
   ```
   `type` bisa `chat`, `like`, atau `follow`.
3. Server **membaca sendiri** dokumen itu dari Firestore, mencari siapa penerimanya, lalu mengirim notifikasi ke HP penerima.

Kenapa tidak dikirim langsung dari aplikasi? Mengirim notifikasi butuh **kunci server Firebase**, dan kunci itu tidak boleh ada di aplikasi karena bisa dibongkar siapa saja. Jadi kunci hanya disimpan di server ini.

**Pengaman yang sudah ada:**
- Isi request tidak dipercaya. Server selalu membaca data asli dari Firestore.
- Dokumen yang umurnya lebih dari **10 menit** diabaikan.
- Setiap dokumen hanya dinotifikasi **satu kali** (dicatat di koleksi `notifySent`).
- Tidak ada notifikasi untuk diri sendiri, misalnya menyukai postingan sendiri.

---

## Yang dibutuhkan

- **Node.js** versi 20 atau lebih baru di komputer
- Akun **AWS**
- Akses ke **Firebase Console** proyek Instagaram

---

## 1. Siapkan kunci service account Firebase

Kunci ini yang memberi server izin membaca Firestore dan mengirim notifikasi.

1. Buka **Firebase Console** → ikon ⚙️ **Project settings** → tab **Service accounts**.
2. Klik **Generate new private key** → **Generate key**. Sebuah file `.json` akan terunduh.
3. Simpan file itu di tempat aman, **di luar** folder proyek.

> ⚠️ **Jangan pernah meng-commit atau membagikan file ini.** Siapa pun yang memegangnya punya akses penuh ke Firebase Anda. Semua file `.json` di folder ini (kecuali `package.json` dan `package-lock.json`) sudah diabaikan Git sebagai pengaman.

---

## 2. Install dan tes

Jalankan dari folder `server/notify`:

```bash
npm install --omit=dev
```
```bash
npm test
```

Semua tes harus lolos (`pass`). Tes berjalan tanpa internet dan tanpa Firebase asli.

---

## 3. Buat file zip

Lambda menerima kode dalam bentuk satu file zip yang berisi:

- `index.js`
- `package.json`
- folder `node_modules/`

Folder `test/` tidak perlu dimasukkan.

**Di Windows**, gunakan `tar` bawaan Windows 10/11:

```bash
tar -a -c -f notify-lambda.zip index.js package.json node_modules
```

> Jangan memakai **Compress-Archive** dari Windows PowerShell 5.1. Perintah itu menulis path dengan garis miring terbalik (`\`), sehingga Lambda gagal menemukan modulnya. 7-Zip juga aman dipakai.

**Di macOS / Linux:**

```bash
zip -r notify-lambda.zip index.js package.json node_modules
```

File `notify-lambda.zip` sudah diabaikan Git, jadi aman dibuat di folder ini.

---

## 4. Deploy ke AWS Lambda (pertama kali)

1. Masuk ke **AWS Console**. Di kanan atas, pilih region **Asia Pacific (Jakarta) `ap-southeast-3`**, atau Singapore kalau Lambda tidak tersedia.
2. Buka **Lambda** → **Create function** → **Author from scratch**, lalu isi:
   - Function name: `instagaram-notify`
   - Runtime: **Node.js 24.x**

   Lalu klik **Create function**.
3. Tab **Code** → **Upload from** → **.zip file** → pilih `notify-lambda.zip` → **Save**.
   Pastikan di **Runtime settings**, Handler bernilai `index.handler`.
4. **Configuration → General configuration** → **Edit**:
   - Memory: **256 MB**
   - Timeout: **15 detik**
5. **Configuration → Environment variables** → **Edit** → **Add environment variable**:
   - Key: `FIREBASE_SERVICE_ACCOUNT`
   - Value: **seluruh isi** file `.json` dari langkah 1 (buka dengan Notepad, salin semua, tempel)
6. **Configuration → Function URL** → **Create function URL** → Auth type **NONE** → **Save**.
   Salin URL yang muncul, misalnya `https://xxxx.lambda-url.ap-southeast-3.on.aws/`.

---

## 5. Hubungkan ke aplikasi Android

Di folder utama proyek (bukan di `server/notify`):

1. Salin `notify.properties.example` menjadi `notify.properties`.
2. Isi URL dari langkah 4:
   ```properties
   NOTIFY_URL=https://xxxx.lambda-url.ap-southeast-3.on.aws/
   ```
3. Build ulang lalu pasang aplikasinya.

Kalau `NOTIFY_URL` dikosongkan, aplikasi tetap berjalan normal, hanya tanpa notifikasi push.

---

## Memperbarui kode (update)

Kalau `index.js` berubah, **tidak perlu membuat function baru**:

1. Ulangi langkah **2** (install & tes) dan **3** (buat zip).
2. Di Lambda `instagaram-notify` → tab **Code** → **Upload from** → **.zip file** → pilih zip yang baru → **Save**.

Function URL, environment variable, dan pengaturan lain tetap sama, jadi aplikasi tidak perlu diubah.

---

## Cek apakah server berjalan

Perintah ini aman dan **tidak mengirim notifikasi apa pun**. Ganti `<FUNCTION_URL>` dengan URL dari langkah 4:

```bash
curl -s -X POST "<FUNCTION_URL>" -H "Content-Type: application/json" -d '{"type":"chat","id":"tidak-ada"}'
```

| Hasil | Artinya |
|---|---|
| `{"sent":false,"reason":"nothing to notify"}` | ✅ Server berjalan normal |
| `{"error":"expected {type: chat\|like\|follow, id}"}` | Request salah format, atau yang aktif masih versi lama |
| `{"error":"internal error"}` / HTTP 500 | Biasanya `FIREBASE_SERVICE_ACCOUNT` salah atau tidak lengkap |

---

## Kalau notifikasi tidak muncul

1. **Cek log Lambda:** tab **Monitor** → **View CloudWatch logs**. Error terbaru ada di sana.
2. **Cek `NOTIFY_URL`** di `notify.properties`, lalu build ulang aplikasinya.
3. **Cek izin notifikasi** di HP: Pengaturan → Aplikasi → Instagaram → Notifikasi harus aktif.
4. **Penerima harus sudah pernah login** di HP-nya, supaya token FCM-nya tersimpan di Firestore. Setelah logout, token dihapus dan notifikasi tidak dikirim.
5. **Notifikasi chat sengaja tidak muncul** kalau penerima sedang membuka ruang chat dengan pengirim tersebut.

---

## Saran keamanan di AWS

Function URL bersifat publik, jadi siapa pun bisa memanggilnya. Server sudah menolak request palsu, tapi sebaiknya batasi juga biayanya:

- **Configuration → Concurrency** → atur *Reserved concurrency* ke angka kecil, misalnya **5**, supaya lonjakan request tidak membengkak.
- **AWS Budgets** → buat alarm anggaran, misalnya **$1**, supaya Anda diberi tahu bila ada biaya tak terduga.
