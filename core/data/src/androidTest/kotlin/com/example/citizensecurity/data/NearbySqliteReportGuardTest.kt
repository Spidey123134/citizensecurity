package com.example.citizensecurity.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.citizensecurity.domain.DeviceLocationFix
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.InvalidReportException
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.ReportField
import com.example.citizensecurity.domain.ReportLocation
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NearbySqliteReportGuardTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val now = Instant.parse("2026-10-03T03:00:00Z")
    private val elapsed = 300_000_000_000L
    private val fix = DeviceLocationFix(19.4326077, -99.1332088, 20.0, elapsed, false)

    @Test
    fun missingEvidenceIsRejectedBeforeCreatingADatabaseFile(): Unit = runBlocking {
        val name = "citizensecurity_test_${UUID.randomUUID()}.db"
        val file = context.getDatabasePath(name)
        assertFalse(file.exists())
        val guard = NearbySqliteReportGuard({ null }, { elapsed })
        SqliteReportRepository(context, name, clock(), createGuard = guard::check).use { repository ->
            expectRejected { repository.create(draft()) }
        }
        assertFalse("Un rechazo inicial no debe abrir la base de reportes.", file.exists())
    }

    @Test
    fun aNearbyPointIsSavedWithItsActualCoordinates(): Unit = runBlocking {
        val guard = NearbySqliteReportGuard({ fix }, { elapsed })
        SqliteReportRepository(context, null, clock(), createGuard = guard::check).use { repository ->
            val saved = repository.create(draft())
            assertEquals(draft().location, repository.findById(saved.id)!!.location)
            assertEquals(1, repository.list().size)
        }
    }

    @Test
    fun gpsDisabledBeforeSavingRejectsUntilEvidenceIsExplicitlyPublishedAgain(): Unit = runBlocking {
        var gpsEnabled = true
        var observedTime = elapsed
        val evidence = PhoneLocationEvidence({ true }, { gpsEnabled })
        assertTrue(evidence.updateFix(fix))
        val guard = NearbySqliteReportGuard(evidence::currentFix, { observedTime })
        SqliteReportRepository(context, null, clock(), createGuard = guard::check).use { repository ->
            gpsEnabled = false
            expectRejected { repository.create(draft()) }
            assertTrue(repository.list().isEmpty())

            gpsEnabled = true
            assertNull(evidence.currentFix())
            expectRejected { repository.create(draft()) }
            assertTrue(repository.list().isEmpty())

            observedTime++
            assertTrue(evidence.updateFix(fix.copy(elapsedRealtimeNanos = observedTime)))
            val saved = repository.create(draft())
            assertEquals(draft().location, repository.findById(saved.id)!!.location)
            assertEquals(1, repository.list().size)
        }
    }

    @Test
    fun gpsDisabledBeforeCommitRollsBackAndCannotReuseEvidenceAfterReenabling(): Unit = runBlocking {
        var gpsEnabled = true
        var observedTime = elapsed
        var reads = 0
        val evidence = PhoneLocationEvidence({ true }, { gpsEnabled })
        assertTrue(evidence.updateFix(fix))
        val guard = NearbySqliteReportGuard(
            {
                if (++reads == 3) gpsEnabled = false
                evidence.currentFix()
            },
            { observedTime },
        )
        SqliteReportRepository(context, null, clock(), createGuard = guard::check).use { repository ->
            expectRejected { repository.create(draft()) }
            assertEquals(3, reads)
            assertTrue("Apagar GPS antes del commit debe revertir la fila insertada.", repository.list().isEmpty())

            gpsEnabled = true
            assertNull(evidence.currentFix())
            expectRejected { repository.create(draft()) }
            assertTrue(repository.list().isEmpty())

            observedTime++
            assertTrue(evidence.updateFix(fix.copy(elapsedRealtimeNanos = observedTime)))
            val saved = repository.create(draft())
            assertEquals(draft().location, repository.findById(saved.id)!!.location)
            assertEquals(1, repository.list().size)
        }
    }

    @Test
    fun changingAnApprovedPointToFranceDoesNotInsertAnything(): Unit = runBlocking {
        val guard = NearbySqliteReportGuard({ fix }, { elapsed })
        SqliteReportRepository(context, null, clock(), createGuard = guard::check).use { repository ->
            val tampered = draft().copy(location = ReportLocation("París de ejemplo", 48.8566, 2.3522))
            expectRejected { repository.create(tampered) }
            assertTrue(repository.list().isEmpty())
        }
    }

    @Test
    fun anExpiredFixAndAMockedFixBothRejectTheSave(): Unit = runBlocking {
        for (evidence in listOf(fix.copy(elapsedRealtimeNanos = elapsed - 120_000_000_001L), fix.copy(isMock = true))) {
            val guard = NearbySqliteReportGuard({ evidence }, { elapsed })
            SqliteReportRepository(context, null, clock(), createGuard = guard::check).use { repository ->
                expectRejected { repository.create(draft()) }
                assertTrue(repository.list().isEmpty())
            }
        }
    }

    @Test
    fun evidenceBecomingMockedBeforeCommitRollsBackTheInsertedRow(): Unit = runBlocking {
        var reads = 0
        val guard = NearbySqliteReportGuard(
            { if (++reads >= 3) fix.copy(isMock = true) else fix },
            { elapsed },
        )
        SqliteReportRepository(context, null, clock(), createGuard = guard::check).use { repository ->
            expectRejected { repository.create(draft()) }
            assertTrue("La segunda comprobación de transacción debe deshacer la fila.", repository.list().isEmpty())
        }
    }

    @Test
    fun evidenceExpiringBeforeCommitRollsBackTheInsertedRow(): Unit = runBlocking {
        var checks = 0
        val guard = NearbySqliteReportGuard(
            { fix },
            { if (++checks >= 3) elapsed + 120_000_000_001L else elapsed },
        )
        SqliteReportRepository(context, null, clock(), createGuard = guard::check).use { repository ->
            expectRejected { repository.create(draft()) }
            assertTrue("Una lectura caducada no debe dejar la inserción sin confirmar.", repository.list().isEmpty())
        }
    }

    @Test
    fun expiryWhileTheRepositoryIsObtainingItsTimestampIsCheckedBeforeWriting(): Unit = runBlocking {
        val observedTime = AtomicLong(elapsed)
        val blockingClock = BlockingClock(now)
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val guard = NearbySqliteReportGuard({ fix }, observedTime::get)
        val repository = SqliteReportRepository(context, null, blockingClock, dispatcher, guard::check)
        try {
            val saving = async(dispatcher) { runCatching { repository.create(draft()) } }
            assertTrue(blockingClock.entered.await(10, TimeUnit.SECONDS))
            observedTime.set(elapsed + 120_000_000_001L)
            blockingClock.release.countDown()
            assertTrue(saving.await().exceptionOrNull() is InvalidReportException)
            assertTrue(repository.list().isEmpty())
        } finally {
            blockingClock.release.countDown()
            repository.close()
            dispatcher.close()
        }
    }

    @Test
    fun referenceOnlyReportsAreNotAcceptedByTheProtectedRepository(): Unit = runBlocking {
        val guard = NearbySqliteReportGuard({ fix }, { elapsed })
        SqliteReportRepository(context, null, clock(), createGuard = guard::check).use { repository ->
            expectRejected { repository.create(draft().copy(location = ReportLocation("Parque de ejemplo"))) }
            assertTrue(repository.list().isEmpty())
        }
    }

    private suspend fun expectRejected(action: suspend () -> Unit) {
        try {
            action()
            fail("El guardado debía rechazarse.")
        } catch (invalid: InvalidReportException) {
            assertEquals(setOf(ReportField.LOCATION_COORDINATES), invalid.errors.errors.keys)
        }
    }

    private fun clock() = Clock.fixed(now, ZoneOffset.UTC)
    private fun draft() = NewReport(
        IncidentType.RISK, Priority.MEDIUM, "Ejemplo ficticio de un incidente cercano.",
        now.minusSeconds(30), ReportLocation("Parque de ejemplo", fix.latitude, fix.longitude),
    )

    private class BlockingClock(private val now: Instant) : Clock() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(now, zone)
        override fun instant(): Instant {
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS))
            return now
        }
    }
}
