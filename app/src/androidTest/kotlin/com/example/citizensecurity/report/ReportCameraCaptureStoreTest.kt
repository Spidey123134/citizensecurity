package com.example.citizensecurity.report

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.Os
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Native filesystem/provider checks in a unique fixture folder; no production cache cleanup. */
class ReportCameraCaptureStoreTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val authority get() = "${app.packageName}.camera_capture"
    private lateinit var cacheRoot: File
    private lateinit var context: Context
    private lateinit var store: ReportCameraCaptureStore
    private val directory get() = File(cacheRoot, ReportCameraCaptureStore.DIRECTORY_NAME)
    private val outsideFiles = mutableListOf<File>()
    private val symlinks = mutableListOf<File>()

    @Before fun ownCache() {
        val providerRoot = File(app.cacheDir.canonicalFile, ReportCameraCaptureStore.DIRECTORY_NAME)
        if (!providerRoot.exists()) assertTrue(providerRoot.mkdir())
        require(providerRoot.canonicalFile == providerRoot.absoluteFile)
        cacheRoot = File(providerRoot, "camera_store_test_${UUID.randomUUID()}")
        assertTrue(cacheRoot.mkdir())
        context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getCacheDir(): File = cacheRoot
        }
        store = ReportCameraCaptureStore(context)
    }

    @After fun clearOnlyOwnFixture() {
        symlinks.forEach { assertTrue(!it.exists() || it.delete()) }
        outsideFiles.forEach { file ->
            require(file.name.startsWith("camera_store_test_"))
            require(file.canonicalFile == file.absoluteFile)
            assertTrue(!file.exists() || file.delete())
        }
        require(cacheRoot.name.startsWith("camera_store_test_"))
        require(cacheRoot.canonicalFile.parentFile == File(app.cacheDir, ReportCameraCaptureStore.DIRECTORY_NAME).canonicalFile)
        assertTrue(cacheRoot.deleteRecursively())
    }

    @Test fun createsUniqueEmptyFilesAndContentUrisWithoutIdentityOrOtherPermissions() = runBlocking {
        val first = store.create()
        val second = store.create()
        assertNotEquals(first.id, second.id)
        listOf(first, second).forEach { capture ->
            assertEquals(UUID.fromString(capture.id).toString(), capture.id)
            val uri = Uri.parse(capture.source)
            assertEquals("content", uri.scheme)
            assertEquals(authority, uri.authority)
            assertEquals("report_camera", uri.pathSegments.first())
            assertEquals("${capture.id}.jpg", uri.lastPathSegment)
            assertNull(uri.query)
            assertNull(uri.fragment)
            val file = file(capture)
            assertTrue(file.isFile)
            assertEquals(0L, file.length())
            assertEquals(file.absoluteFile, file.canonicalFile)
        }
        val permissions = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().toSet()
        assertFalse(Manifest.permission.CAMERA in permissions)
        assertFalse(Manifest.permission.READ_EXTERNAL_STORAGE in permissions)
        assertFalse(Manifest.permission.WRITE_EXTERNAL_STORAGE in permissions)
        assertFalse(Manifest.permission.READ_MEDIA_IMAGES in permissions)
    }

    @Test fun providerIsNonExportedAndGrantsOnlyTheCapturePath() {
        val provider = checkNotNull(app.packageManager.resolveContentProvider(authority, PackageManager.GET_META_DATA))
        assertEquals(ReportCameraFileProvider::class.java.name, provider.name)
        assertFalse(provider.exported)
        assertTrue(provider.grantUriPermissions)
        val resource = provider.metaData.getInt("android.support.FILE_PROVIDER_PATHS")
        assertTrue(resource != 0)
        val parser = app.resources.getXml(resource)
        val configured = mutableListOf<Pair<String, String>>()
        parser.use {
            while (it.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (it.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && it.name != "paths") {
                    assertEquals("cache-path", it.name)
                    assertEquals("report_camera", it.getAttributeValue(null, "name"))
                    configured += it.name to it.getAttributeValue(null, "path")
                }
            }
        }
        assertEquals(listOf("cache-path" to "report_camera/"), configured)
    }

    @Test fun providerCanWriteFullCaptureAndReleaseRemovesOnlyThatTemporary() = runBlocking {
        val capture = store.create()
        val previous = File(directory, "previous_photo.jpg").apply { writeText("Keep the previous photo") }
        val uri = Uri.parse(capture.source)
        val bytes = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 1, 2, 3, 0xff.toByte(), 0xd9.toByte())
        ParcelFileDescriptor.AutoCloseOutputStream(checkNotNull(app.contentResolver.openFileDescriptor(uri, "w")))
            .use { it.write(bytes) }
        assertArrayEquals(bytes, file(capture).readBytes())
        store.release(capture)
        assertFalse(file(capture).exists())
        assertEquals("Keep the previous photo", previous.readText())
        assertEquals(listOf(previous.name), directory.list()!!.toList())
    }

    @Test fun releaseRevokesBothReadAndWriteGrants() = runBlocking {
        val capture = store.create()
        val uri = Uri.parse(capture.source)
        val recipient = InstrumentationRegistry.getInstrumentation().context.packageName
        val recipientUid = app.packageManager.getApplicationInfo(recipient, 0).uid
        assertNotEquals(app.applicationInfo.uid, recipientUid)
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        app.grantUriPermission(recipient, uri, flags)
        assertEquals(PackageManager.PERMISSION_GRANTED, app.checkUriPermission(uri, -1, recipientUid, flags))
        store.release(capture)
        assertEquals(PackageManager.PERMISSION_DENIED, app.checkUriPermission(uri, -1, recipientUid, flags))
        assertFalse(file(capture).exists())
    }

    @Test fun cancellationReleaseIsIdempotentAcrossStoreInstances() = runBlocking {
        val first = store.create()
        val second = store.create()
        val recreated = ReportCameraCaptureStore(context)
        recreated.release(first)
        recreated.release(first)
        assertFalse(file(first).exists())
        assertTrue(file(second).isFile)
        assertEquals(listOf("${second.id}.jpg"), directory.list()!!.toList())
    }

    @Test fun swappedIdSourceCannotDeleteEitherCapture() = runBlocking {
        val first = store.create()
        val second = store.create()
        expectIOException { store.release(first.copy(source = second.source)) }
        assertTrue(file(first).isFile)
        assertTrue(file(second).isFile)
    }

    @Test fun traversalNonCanonicalIdAndUriAlterationsAreRejected() = runBlocking {
        val capture = store.create()
        val changed = listOf(
            capture.copy(id = "../${capture.id}"),
            capture.copy(id = "A" + capture.id.drop(1)),
            capture.copy(id = "1-1-1-1-1"),
            capture.copy(source = "file://${file(capture).absolutePath}"),
            capture.copy(source = capture.source + "?other=photo"),
            capture.copy(source = capture.source + "#photo"),
            capture.copy(source = capture.source.replace(authority, "${app.packageName}.evidence")),
        )
        changed.forEach { forged -> expectIOException { store.release(forged) } }
        assertTrue(file(capture).isFile)
    }

    @Test fun countLimitIncludesUnknownResidualsAndNeverSweepsThem() = runBlocking {
        val capture = store.create()
        val remaining = (1 until ReportCameraCaptureStore.MAX_PENDING_FILES).map {
            File(directory, "residual_$it.jpg").apply { writeText("Residual $it") }
        }
        val before = directory.listFiles()!!.associate { it.name to it.readBytes().toList() }
        expectCapacity { ReportCameraCaptureStore(context).create() }
        assertEquals(before, directory.listFiles()!!.associate { it.name to it.readBytes().toList() })
        store.release(capture)
        assertEquals(remaining.map { it.name }.toSet(), directory.list()!!.toSet())
        val replacement = store.create()
        assertTrue(file(replacement).isFile)
        assertEquals(ReportCameraCaptureStore.MAX_PENDING_FILES, directory.list()!!.size)
    }

    @Test fun byteLimitPreservesLargeResidualAndOtherCaptures() = runBlocking {
        val capture = store.create()
        val residual = File(directory, "large_residual.jpg")
        RandomAccessFile(residual, "rw").use { it.setLength(ReportCameraCaptureStore.MAX_CACHE_BYTES) }
        expectCapacity { store.create() }
        assertEquals(ReportCameraCaptureStore.MAX_CACHE_BYTES, residual.length())
        assertTrue(file(capture).isFile)
        assertEquals(2, directory.list()!!.size)
    }

    @Test fun concurrentStoresRespectOneSharedFourFileLimit() = runBlocking {
        val results = (1..10).map {
            async { try { ReportCameraCaptureStore(context).create() } catch (_: ReportCameraCapacityException) { null } }
        }.awaitAll()
        assertEquals(4, results.filterNotNull().size)
        assertEquals(6, results.count { it == null })
        assertEquals(4, results.filterNotNull().map { it.id }.toSet().size)
        assertEquals(4, directory.list()!!.size)
    }

    @Test fun unexpectedSubdirectoryFailsWithoutRemovingItsContents() = runBlocking {
        val capture = store.create()
        val residual = File(directory, "unknown_folder").apply { assertTrue(mkdir()) }
        val original = File(residual, "photo.jpg").apply { writeText("Must remain") }
        expectIOException { store.create() }
        assertTrue(file(capture).isFile)
        assertEquals("Must remain", original.readText())
    }

    @Test fun directorySymlinkCannotRedirectCreationOrDeleteOutsideFiles() = runBlocking {
        val outside = File(cacheRoot, "outside").apply { assertTrue(mkdir()) }
        val sentinel = File(outside, "sentinel.jpg").apply { writeText("Must remain outside") }
        Os.symlink(outside.absolutePath, directory.absolutePath)
        symlinks += directory
        expectIOException { store.create() }
        assertEquals("Must remain outside", sentinel.readText())
        assertEquals(listOf("sentinel.jpg"), outside.list()!!.toList())
    }

    @Test fun fileSymlinkCannotBeReleasedOrUsedToExpandCapacity() = runBlocking {
        val capture = store.create()
        assertTrue(file(capture).delete())
        val sentinel = File(cacheRoot, "sentinel.jpg").apply { writeText("Must remain outside") }
        Os.symlink(sentinel.absolutePath, file(capture).absolutePath)
        symlinks += file(capture)
        expectIOException { store.release(capture) }
        expectIOException { store.create() }
        assertEquals("Must remain outside", sentinel.readText())
    }

    @Test fun providerDoesNotExposePrivatePhotosDatabaseEvidenceOrGeneralCache() {
        val outside = listOf(app.filesDir, app.getDatabasePath("camera_store_test_${UUID.randomUUID()}.db").parentFile!!,
            app.cacheDir, File(app.cacheDir, "evidence_shared"))
        outside.forEach { parent ->
            if (!parent.exists()) assertTrue(parent.mkdir())
            // Android may expose /data/user/0 through a valid /data/data alias. Use its
            // canonical parent before creating the fixture; cleanup still rejects links.
            val sentinel = File(parent.canonicalFile, "camera_store_test_${UUID.randomUUID()}.jpg")
                .apply { writeText("Private") }
            outsideFiles += sentinel
            try {
                FileProvider.getUriForFile(app, authority, sentinel)
                fail("Camera provider exposed ${parent.name}")
            } catch (_: IllegalArgumentException) { }
            assertEquals("Private", sentinel.readText())
        }
    }

    @Test fun maliciousProviderTraversalCannotReadGeneralCache() = runBlocking {
        val capture = store.create()
        val sentinel = File(app.cacheDir.canonicalFile, "camera_store_test_${UUID.randomUUID()}.jpg")
            .apply { writeText("Private cache") }
        outsideFiles += sentinel
        val traversal = Uri.parse("content://$authority/report_camera/%2E%2E/${sentinel.name}")
        try {
            app.contentResolver.openFileDescriptor(traversal, "r")?.use { }
            fail("Provider traversal opened private cache")
        } catch (_: SecurityException) { }
        assertEquals("Private cache", sentinel.readText())
        assertTrue(file(capture).isFile)
    }

    private fun file(capture: ReportCameraCapture) = File(directory, "${capture.id}.jpg")
    private suspend fun expectIOException(block: suspend () -> Unit) {
        try { block(); fail("Invalid camera path or handle was accepted") } catch (_: IOException) { }
    }
    private suspend fun expectCapacity(block: suspend () -> Unit) {
        try { block(); fail("Camera temporary quota was exceeded") } catch (_: ReportCameraCapacityException) { }
    }
}
