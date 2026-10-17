package com.example

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.converter.PhotoConverter
import com.example.model.ConversionJob
import com.example.model.ConversionSettings
import com.example.model.JobStatus
import com.example.model.MediaSourceItem
import com.example.model.MediaType
import com.example.model.VideoQualityPreset
import com.example.storage.SafFileManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrivateMediaConverterTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `privacy check - verify AndroidManifest has no internet and no broad storage permissions`() {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            android.content.pm.PackageManager.GET_PERMISSIONS
        )
        val permissions = packageInfo.requestedPermissions ?: emptyArray()

        // 1. Strict zero INTERNET permission
        assertFalse(
            "Security assertion failed: android.permission.INTERNET must NOT be requested",
            permissions.contains("android.permission.INTERNET")
        )

        // 2. Strict zero broad storage access permissions
        assertFalse(
            "Security assertion failed: MANAGE_EXTERNAL_STORAGE must NOT be requested",
            permissions.contains("android.permission.MANAGE_EXTERNAL_STORAGE")
        )
        assertFalse(
            "Security assertion failed: READ_EXTERNAL_STORAGE must NOT be requested",
            permissions.contains("android.permission.READ_EXTERNAL_STORAGE")
        )
        assertFalse(
            "Security assertion failed: WRITE_EXTERNAL_STORAGE must NOT be requested",
            permissions.contains("android.permission.WRITE_EXTERNAL_STORAGE")
        )
    }

    @Test
    fun `whatsapp forwarding - verify SEND and SEND_MULTIPLE intent filters exist for images and videos`() {
        val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/heic"
        }
        val resolveSendList = context.packageManager.queryIntentActivities(sendIntent, 0)
        assertTrue(
            "MainActivity must resolve ACTION_SEND for direct WhatsApp image forwarding",
            resolveSendList.any { it.activityInfo.packageName == context.packageName }
        )

        val sendMultipleIntent = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
            type = "video/quicktime"
        }
        val resolveMultipleList = context.packageManager.queryIntentActivities(sendMultipleIntent, 0)
        assertTrue(
            "MainActivity must resolve ACTION_SEND_MULTIPLE for batch WhatsApp video forwarding",
            resolveMultipleList.any { it.activityInfo.packageName == context.packageName }
        )
    }

    @Test
    fun `photo conversion - produces genuine JPEG with SOI and EOI markers`() {
        runBlocking {
            // Create a test bitmap
            val testBitmap = Bitmap.createBitmap(120, 160, Bitmap.Config.ARGB_8888)
            val tempInput = File(context.cacheDir, "test_input_${UUID.randomUUID()}.png")
            FileOutputStream(tempInput).use { out ->
                testBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val source = MediaSourceItem(
                uri = Uri.fromFile(tempInput),
                displayName = "IMG_4021.HEIC",
                sizeBytes = tempInput.length(),
                mimeType = "image/heic",
                mediaType = MediaType.PHOTO
            )

            val settings = ConversionSettings(jpegQuality = 95, stripLocationMetadata = true)
            val result = PhotoConverter.convert(
                context = context,
                source = source,
                settings = settings,
                targetFolderUri = null,
                onProgress = {}
            )

            assertTrue("Photo conversion should succeed", result.isSuccess)
            val photoResult = result.getOrThrow()
            assertNotNull(photoResult.outputUri)
            assertTrue(photoResult.outputFileName.endsWith(".jpg"))

            // Read output bytes and verify standard JPEG headers:
            // Byte 0-1: FF D8 (SOI - Start of Image)
            // Last 2 bytes: FF D9 (EOI - End of Image)
            val outputFile = File(photoResult.outputUri.path!!)
            assertTrue("Output file must exist", outputFile.exists())
            assertTrue("Output size must be greater than 0", outputFile.length() > 0)

            val bytes = outputFile.readBytes()
            assertEquals(0xFF.toByte(), bytes[0])
            assertEquals(0xD8.toByte(), bytes[1])
            assertEquals(0xFF.toByte(), bytes[bytes.size - 2])
            assertEquals(0xD9.toByte(), bytes[bytes.size - 1])

            // Cleanup
            tempInput.delete()
            outputFile.delete()
        }
    }

    @Test
    fun `corrupted media - returns graceful error instead of crashing`() {
        runBlocking {
            // Create a corrupt pseudo-HEIC with random junk bytes
            val corruptFile = File(context.cacheDir, "corrupted_${UUID.randomUUID()}.heic")
            FileOutputStream(corruptFile).use { out ->
                out.write("This is not a real image stream".toByteArray())
            }

            val source = MediaSourceItem(
                uri = Uri.fromFile(corruptFile),
                displayName = "corrupted_whatsapp_doc.heic",
                sizeBytes = corruptFile.length(),
                mimeType = "image/heic",
                mediaType = MediaType.PHOTO
            )

            val settings = ConversionSettings(jpegQuality = 90)
            val result = PhotoConverter.convert(
                context = context,
                source = source,
                settings = settings,
                targetFolderUri = null,
                onProgress = {}
            )

            assertTrue("Corrupted image should return failure", result.isFailure)
            val errorMsg = result.exceptionOrNull()?.message ?: ""
            assertTrue(
                "Error message should explain corrupted or unsupported format",
                errorMsg.contains("corrupted", ignoreCase = true) || errorMsg.contains("decode", ignoreCase = true)
            )

            corruptFile.delete()
        }
    }

    @Test
    fun `saf file manager - resolves unique filenames for duplicate inputs`() {
        val testDir = File(context.cacheDir, "duplicate_test_${UUID.randomUUID()}").apply { mkdirs() }
        File(testDir, "IMG_1234.jpg").createNewFile()
        File(testDir, "IMG_1234 (1).jpg").createNewFile()

        val (uri, uniqueName) = SafFileManager.prepareOutputFile(
            context = context,
            treeUri = null,
            baseName = "IMG_1234.HEIC",
            targetExtension = "jpg",
            targetMimeType = "image/jpeg"
        )

        assertNotNull(uri)
        assertTrue(uniqueName.startsWith("IMG_1234"))
        assertTrue(uniqueName.endsWith(".jpg"))

        testDir.deleteRecursively()
    }

    @Test
    fun `file size formatting - displays human readable units accurately`() {
        assertEquals("0 B", SafFileManager.formatFileSize(0))
        assertEquals("512 B", SafFileManager.formatFileSize(512))
        assertEquals("1 KB", SafFileManager.formatFileSize(1024))
        assertEquals("1.5 MB", SafFileManager.formatFileSize((1.5 * 1024 * 1024).toLong()))
        assertEquals("2.4 GB", SafFileManager.formatFileSize((2.4 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun `conversion job - size savings calculation`() {
        val sourceItem = MediaSourceItem(
            uri = Uri.EMPTY,
            displayName = "video.mov",
            sizeBytes = 10_000_000L,
            mimeType = "video/quicktime",
            mediaType = MediaType.VIDEO
        )

        val completedJob = ConversionJob(
            id = "test-1",
            source = sourceItem,
            status = JobStatus.COMPLETED,
            outputSizeBytes = 6_000_000L
        )

        assertEquals(40, completedJob.sizeSavingsPercent)

        val uncompletedJob = ConversionJob(
            id = "test-2",
            source = sourceItem,
            status = JobStatus.QUEUED
        )
        assertNull(uncompletedJob.sizeSavingsPercent)
    }

    @Test
    fun `settings presets - verify labels and parameters`() {
        assertEquals("Balanced 1080p", VideoQualityPreset.BALANCED_1080P.label)
        assertEquals(4500, VideoQualityPreset.BALANCED_1080P.targetBitrateKbps)
        assertEquals("Original Resolution", VideoQualityPreset.ORIGINAL.label)
        assertNull(VideoQualityPreset.ORIGINAL.targetBitrateKbps)
        assertEquals("Compact 720p", VideoQualityPreset.COMPACT_720P.label)
        assertEquals(2200, VideoQualityPreset.COMPACT_720P.targetBitrateKbps)
    }
}
