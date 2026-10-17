# Private Media Converter

**Private Media Converter** is an offline-first, native Android application engineered for Android 16 (and backward compatible to Android 7.0 / API 24+, optimized for Motorola Moto G85 5G). It converts Apple iPhone photos (`HEIC`/`HEIF`) and videos (`MOV`/QuickTime) received as WhatsApp document attachments into universally compatible **JPEG** photos and **MP4** videos.

---

## 1. Direct WhatsApp Forwarding Integration

* **Native Intent Filters (`ACTION_SEND` & `ACTION_SEND_MULTIPLE`):** Users can share or forward one or multiple iPhone `.HEIC` photos and `.MOV` videos directly from a WhatsApp chat to **Private Media Converter**.
* **Zero Manual Path Copying:** WhatsApp stream URIs are handled via Android's `ContentResolver`.
* **Automatic Queue Detection:** When shared from WhatsApp, the app alerts the user with an incoming media banner and offers 1-tap conversion.
* **1-Tap Share Back:** Completed conversions feature a direct "Share / Forward" button to send the converted JPEG or MP4 right back to WhatsApp.

---

## 2. Privacy & Security Guarantees

* **Zero Network Permissions:** `AndroidManifest.xml` does **not** declare `android.permission.INTERNET`. The app is physically incapable of connecting to servers, making HTTP calls, sending telemetry, or uploading media.
* **No Cloud / No Paid APIs:** All decoding and transcoding is executed 100% on-device utilizing Android platform hardware codecs.
* **No Broad Storage Access:** Does **not** request `MANAGE_EXTERNAL_STORAGE`, `READ_EXTERNAL_STORAGE`, or `WRITE_EXTERNAL_STORAGE`. Access is restricted strictly to files and directories explicitly selected by the user via the Android Storage Access Framework (SAF).
* **Zero Metadata Logging:** Filenames, geolocation data, and media content are never sent to logs or tracking services.
* **EXIF Location Stripping:** Strips GPS coordinates and location metadata from converted photos while preserving visual orientation.
* **Temporary Cache Auto-Cleanup:** Temporary transcoding streams in `cacheDir` are wiped immediately upon job completion or user cancellation.
* **Original File Protection:** Source files are opened strictly in read-only mode via SAF `ContentResolver` streams; originals are never altered or deleted.

---

## 2. Supported Formats & Architecture

### Photo Conversion: HEIC/HEIF to JPEG
* **Input Formats:** Apple `.HEIC`, `.HEIF`, `.JPEG`, `.PNG`, `.WEBP`.
* **Output Format:** Valid `.JPEG` (`image/jpeg`) with verified `0xFF 0xD8` SOI and `0xFF 0xD9` EOI markers.
* **Decoders:** Native `android.graphics.ImageDecoder` (Android 9+) with resilient `BitmapFactory` fallback.
* **Orientation Handling:** Uses `androidx.exifinterface:exifinterface` to detect EXIF orientation (0°, 90°, 180°, 270°, horizontal/vertical flip) and applies Matrix rotation so images are never sideways.
* **Quality Control:** Adjustable compression quality (default 95%, configurable from 50% to 100%).

### Video Conversion: MOV to MP4
* **Input Formats:** Apple QuickTime `.MOV`, `.MP4`, `.M4V`.
* **Output Format:** Universal `.MP4` container with H.264/AVC video and AAC audio (`MimeTypes.VIDEO_H264`, `MimeTypes.AUDIO_AAC`).
* **Engine:** Jetpack **Media3 Transformer** (`androidx.media3:media3-transformer:1.5.1`).
* **Why Media3 Transformer over FFmpeg:**
  * **Zero Binary Bloat:** Native FFmpeg builds add 30–60 MB of C/C++ `.so` libraries across multiple ABIs (`arm64-v8a`, `armeabi-v7a`, `x86_64`). Media3 uses Moto G85's onboard hardware media decoders and encoders directly with an APK size under 20 MB.
  * **Licensing & Compliance:** Avoids GPL/LGPL distribution complications (Media3 is Apache 2.0).
  * **Android 16 Compatibility:** Tested and verified for API 36 targetSdk.
* **Quality Presets:**
  * **Balanced 1080p (Default):** Target bitrate ~4.5 Mbps, ideal for WhatsApp sharing.
  * **Original Resolution:** Preserves full source dimensions.
  * **Compact 720p:** Target bitrate ~2.2 Mbps for lightweight messaging.

---

## 3. Automated Unit Tests

Automated JVM tests are implemented using **Robolectric** in `app/src/test/java/com/example/PrivateMediaConverterTest.kt`:

1. `privacy check - verify AndroidManifest has no internet and no broad storage permissions`: Asserts that `INTERNET`, `MANAGE_EXTERNAL_STORAGE`, `READ_EXTERNAL_STORAGE`, and `WRITE_EXTERNAL_STORAGE` are absent.
2. `photo conversion - produces genuine JPEG with SOI and EOI markers`: Verifies true JPEG byte stream output.
3. `corrupted media - returns graceful error instead of crashing`: Validates container signatures and tests error handling when non-image bytes are supplied.
4. `saf file manager - resolves unique filenames for duplicate inputs`: Tests conflict resolution (e.g. `IMG_1234 (1).jpg`).
5. `file size formatting - displays human readable units accurately`: Tests `B`, `KB`, `MB`, `GB` boundary formatting.
6. `conversion job - size savings calculation`: Tests percentage calculation for space savings.
7. `settings presets - verify labels and parameters`: Verifies bitrate and dimension presets.
8. `whatsapp forwarding - verify SEND and SEND_MULTIPLE intent filters exist for images and videos`: Verifies system intent resolution for direct WhatsApp sharing.

### Running Automated Tests
```bash
gradle :app:testDebugUnitTest
```

---

## 4. Manual Verification Plan (iPhone Media & WhatsApp)

1. **HEIC Photo from WhatsApp:**
   * In WhatsApp, receive an iPhone photo sent as an uncompressed document (`IMG_XXXX.HEIC`).
   * In Private Media Converter, tap **Select Photos** and choose the HEIC file.
   * Verify the file appears in the queue as `HEIC ➔ JPEG`.
   * Tap **Start** (or observe auto-start).
   * Verify progress reaches 100%, the output filename is `IMG_XXXX.jpg`, and the file opens in Google Photos without orientation distortion.
2. **MOV Video from WhatsApp:**
   * Receive an iPhone 4K/1080p 60fps MOV video sent as a document.
   * Tap **Select Videos** and select the `.MOV` file.
   * Observe queue item showing `MOV ➔ MP4` with source file size.
   * Verify live transcoding progress and elapsed time.
   * Once finished, tap **Open** to verify smooth playback with AAC audio and correct rotation.
   * Tap **Share** to send the converted MP4 back via WhatsApp or any messaging app.
3. **Cancellation & Temp Cleanup:**
   * Start converting a large video.
   * Tap **Cancel** during the transcoding process.
   * Verify conversion stops immediately and `cacheDir/transcode_*.mp4` is deleted.
4. **Custom Output Folder:**
   * Tap **Choose Folder** in Settings and select `Pictures/Converted`.
   * Convert an item and verify the output is saved directly into `Pictures/Converted/`.

---

## 5. Building the Debug APK

### In Android Studio:
1. Open Android Studio and choose **File > Open**, then select the project root folder.
2. Allow Gradle to sync.
3. In the menu, select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
4. When notified that the APK built successfully, click **locate** in the popup.

### Command Line:
```bash
gradle :app:assembleDebug
```

### APK Output Path:
```
app/build/outputs/apk/debug/app-debug.apk
```
