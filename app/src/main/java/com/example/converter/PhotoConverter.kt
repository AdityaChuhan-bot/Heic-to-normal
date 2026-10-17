package com.example.converter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.example.model.ConversionSettings
import com.example.model.MediaSourceItem
import com.example.storage.SafFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

object PhotoConverter {

    data class PhotoResult(
        val outputUri: Uri,
        val outputFileName: String,
        val outputSizeBytes: Long,
        val durationMs: Long,
        val details: String
    )

    suspend fun convert(
        context: Context,
        source: MediaSourceItem,
        settings: ConversionSettings,
        targetFolderUri: Uri?,
        onProgress: (Float) -> Unit
    ): Result<PhotoResult> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            // 1. Validate image format header signature (rejects corrupted/invalid streams)
            if (!isValidImageSignature(context, source.uri)) {
                return@withContext Result.failure(
                    IllegalArgumentException("Unable to decode '${source.displayName}'. The file is corrupted or not a valid image format.")
                )
            }

            // 2. Read EXIF orientation before stream consumption
            val orientation = readExifOrientation(context, source.uri)
            onProgress(0.25f)

            // 3. Decode Bitmap (supports HEIC, HEIF, JPEG, PNG, WEBP natively)
            val decodedBitmap = decodeImageSafely(context, source.uri)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Unable to decode '${source.displayName}'. The file may be corrupted or in an unsupported image format.")
                )

            onProgress(0.55f)

            // 3. Apply orientation correction if rotated/flipped
            val rotatedBitmap = applyOrientation(decodedBitmap, orientation)
            if (rotatedBitmap != decodedBitmap && !decodedBitmap.isRecycled) {
                decodedBitmap.recycle()
            }

            onProgress(0.75f)

            // 4. Prepare SAF Output Target
            val (outputUri, outputName) = SafFileManager.prepareOutputFile(
                context = context,
                treeUri = targetFolderUri,
                baseName = source.displayName,
                targetExtension = "jpg",
                targetMimeType = "image/jpeg"
            )

            // 5. Compress to genuine JPEG with user quality
            val quality = settings.jpegQuality.coerceIn(50, 100)
            val outputStream = SafFileManager.openOutputStream(context, outputUri)
                ?: return@withContext Result.failure(
                    IllegalStateException("Cannot open writable output stream for destination: $outputName")
                )

            var writtenBytes = 0L
            outputStream.use { out ->
                val byteBuffer = ByteArrayOutputStream()
                val compressed = rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, byteBuffer)
                if (!compressed) {
                    return@withContext Result.failure(
                        IllegalStateException("JPEG compression failed for image '${source.displayName}'")
                    )
                }
                val byteArray = byteBuffer.toByteArray()
                out.write(byteArray)
                out.flush()
                writtenBytes = byteArray.size.toLong()
            }

            val width = rotatedBitmap.width
            val height = rotatedBitmap.height
            if (!rotatedBitmap.isRecycled) {
                rotatedBitmap.recycle()
            }

            onProgress(0.90f)

            // 6. Optional EXIF location stripping for privacy
            if (settings.stripLocationMetadata) {
                stripGpsFromOutput(context, outputUri)
            }

            onProgress(1.0f)
            val duration = System.currentTimeMillis() - startTime

            Result.success(
                PhotoResult(
                    outputUri = outputUri,
                    outputFileName = outputName,
                    outputSizeBytes = writtenBytes,
                    durationMs = duration,
                    details = "${width}x${height} • JPEG ${quality}%"
                )
            )
        } catch (e: OutOfMemoryError) {
            Result.failure(Exception("Image too large to process in memory. Please try with smaller dimensions.", e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun readExifOrientation(context: Context, uri: Uri): Int {
        return try {
            SafFileManager.openInputStream(context, uri)?.use { stream ->
                val exif = ExifInterface(stream)
                exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    private fun decodeImageSafely(context: Context, uri: Uri): Bitmap? {
        // Preferred modern path on Android 9+ (API 28+) using ImageDecoder
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    // Downscale gigantic images safely to prevent OOM
                    val maxDim = 8192
                    if (info.size.width > maxDim || info.size.height > maxDim) {
                        val scale = maxDim.toFloat() / maxOf(info.size.width, info.size.height)
                        val targetW = (info.size.width * scale).toInt()
                        val targetH = (info.size.height * scale).toInt()
                        decoder.setTargetSize(targetW, targetH)
                    }
                }
            } catch (_: Exception) {
                // Fallback to BitmapFactory if ImageDecoder throws
            }
        }

        // Robust fallback using BitmapFactory with bounds inspection
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            SafFileManager.openInputStream(context, uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            if (options.outWidth <= 0 || options.outHeight <= 0) {
                return null
            }

            // Downscale if needed
            var sampleSize = 1
            val maxDimension = 8192
            while (options.outWidth / sampleSize > maxDimension || options.outHeight / sampleSize > maxDimension) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            SafFileManager.openInputStream(context, uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }

        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (_: Exception) {
            bitmap
        }
    }

    private fun stripGpsFromOutput(context: Context, outputUri: Uri) {
        try {
            if (outputUri.scheme == "file") {
                outputUri.path?.let { path ->
                    val exif = ExifInterface(path)
                    exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null)
                    exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
                    exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, null)
                    exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, null)
                    exif.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, null)
                    exif.saveAttributes()
                }
            } else {
                context.contentResolver.openFileDescriptor(outputUri, "rw")?.use { pfd ->
                    val exif = ExifInterface(pfd.fileDescriptor)
                    exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null)
                    exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
                    exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, null)
                    exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, null)
                    exif.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, null)
                    exif.saveAttributes()
                }
            }
        } catch (_: Exception) {
            // Non-fatal if specific storage provider doesn't support EXIF writeback
        }
    }

    private fun isValidImageSignature(context: Context, uri: Uri): Boolean {
        return try {
            val header = ByteArray(16)
            val readCount = SafFileManager.openInputStream(context, uri)?.use { stream ->
                stream.read(header)
            } ?: 0
            if (readCount < 8) return false

            // JPEG: FF D8
            if (header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte()) return true

            // PNG: 89 50 4E 47
            if (header[0] == 0x89.toByte() && header[1] == 0x50.toByte() && header[2] == 0x4E.toByte() && header[3] == 0x47.toByte()) return true

            // HEIF / HEIC / AVIF / ISOBMFF: bytes 4..7 are "ftyp"
            if (header[4] == 'f'.code.toByte() && header[5] == 't'.code.toByte() && header[6] == 'y'.code.toByte() && header[7] == 'p'.code.toByte()) return true

            // WEBP: "RIFF"
            if (header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte() && header[2] == 'F'.code.toByte() && header[3] == 'F'.code.toByte()) return true

            // GIF: "GIF8"
            if (header[0] == 'G'.code.toByte() && header[1] == 'I'.code.toByte() && header[2] == 'F'.code.toByte()) return true

            // BMP: "BM"
            if (header[0] == 'B'.code.toByte() && header[1] == 'M'.code.toByte()) return true

            false
        } catch (_: Exception) {
            false
        }
    }
}
