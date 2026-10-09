package com.example.citizensecurity.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.citizensecurity.domain.DeviceLocationFix
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.InvalidReportException
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.domain.ReportRequestConflictException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** Bases aisladas. Ningún caso abre el repositorio productivo ni utiliza ubicaciones del usuario. */
@RunWith(AndroidJUnit4::class)
class SqliteReportIdempotencyTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val now = Instant.parse("2026-10-09T03:00:00Z")
    private val elapsed = 300_000_000_000L
    private val fix = DeviceLocationFix(19.4326077, -99.1332088, 20.0, elapsed, false)
    private val requestId = "1e345678-1234-4567-89ab-0123456789ab"

    @Test
    fun guardedRetryReturnsTheOriginalReportWithoutGpsOrAnotherInsert(): Unit = runBlocking {
        val evidence = AtomicReference<DeviceLocationFix?>(fix)
        val calls = AtomicInteger()
        val guard = NearbySqliteReportGuard(evidence::get, { elapsed })
        SqliteReportRepository(context, null, clock(), createGuard = {
            calls.incrementAndGet()
            guard.check(it)
        }).use { repository ->
            val requested = draft().copy(description = "  ${draft().description}  ")
            val saved = repository.createOnce(requestId, requested)
            assertEquals(requestId, saved.id)
            assertEquals(3, calls.get())
            evidence.set(null)

            assertEquals(saved, repository.createOnce(requestId, draft()))
            assertEquals(3, calls.get())
            assertEquals(listOf(saved), repository.list())
        }
    }

    @Test
    fun reusingTheIdentityWithDifferentInputRejectsWithoutChangingTheSavedReport(): Unit = runBlocking {
        SqliteReportRepository(context, null, clock()).use { repository ->
            val original = draft()
            val saved = repository.createOnce(requestId, original)
            val changed = listOf(
                original.copy(type = IncidentType.THEFT),
                original.copy(priority = Priority.HIGH),
                original.copy(description = "Hay otra luminaria dañada junto al mercado."),
                original.copy(occurredAt = original.occurredAt.minusSeconds(1)),
                original.copy(location = original.location.copy(reference = "Junto al mercado central")),
                original.copy(location = original.location.copy(latitude = 48.8566, longitude = 2.3522)),
            )
            changed.forEach { other ->
                try {
                    repository.createOnce(requestId, other)
                    fail("La identidad no debe admitir un contenido diferente.")
                } catch (conflict: ReportRequestConflictException) {
                    assertEquals(requestId, conflict.requestId)
                }
                assertEquals(saved, repository.findById(requestId))
                assertEquals(listOf(saved), repository.list())
            }
        }
    }

    @Test
    fun aDifferentIdentityCanCreateAnotherReportWithTheSameContent(): Unit = runBlocking {
        SqliteReportRepository(context, null, clock()).use { repository ->
            val first = repository.createOnce(requestId, draft())
            val anotherId = UUID.randomUUID().toString()
            val second = repository.createOnce(anotherId, draft())
            assertEquals(requestId, first.id)
            assertEquals(anotherId, second.id)
            assertEquals(setOf(first, second), repository.list().toSet())
            assertEquals(2, repository.list().size)
        }
    }

    @Test
    fun twoRepositoryInstancesWithTheSameIdentityCommitOnlyOneReport(): Unit = runBlocking {
        val name = temporaryDatabaseName()
        val workers = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
        val bothStarted = CountDownLatch(2)
        fun firstGuard(): (NewReport) -> Unit {
            var checks = 0
            return {
                if (++checks == 1) {
                    bothStarted.countDown()
                    check(bothStarted.await(10, TimeUnit.SECONDS)) { "No iniciaron las dos solicitudes." }
                }
            }
        }
        val firstRepository = SqliteReportRepository(context, name, clock(), workers, firstGuard())
        val secondRepository = SqliteReportRepository(context, name, clock(), workers, firstGuard())
        try {
            val first = async { firstRepository.createOnce(requestId, draft()) }
            val second = async { secondRepository.createOnce(requestId, draft()) }
            val saved = first.await()
            assertEquals(saved, second.await())
            assertEquals(listOf(saved), firstRepository.list())
            assertEquals(listOf(saved), secondRepository.list())
        } finally {
            firstRepository.close()
            secondRepository.close()
            workers.close()
            deleteOnlyTemporaryDatabase(name)
        }
    }

    @Test
    fun cancellationAfterCommitCanRecoverTheOriginalFolioOnRetry(): Unit = runBlocking {
        val caller = QueuedDispatcher()
        SqliteReportRepository(context, null, clock()).use { repository ->
            var responseReceived = false
            val pending = launch(caller) {
                repository.createOnce(requestId, draft())
                responseReceived = true
            }
            caller.awaitTask().run()
            // SQLite terminó y withContext ya encoló el resultado para su dueño. Cancelar
            // antes de ejecutar ese retorno comprueba la cancelación posterior al commit.
            val returnToCaller = caller.awaitTask()
            pending.cancel()
            returnToCaller.run()
            pending.join()
            assertTrue(pending.isCancelled)
            assertFalse(responseReceived)

            val stored = repository.findById(requestId)
            assertTrue("La transacción ya debía estar confirmada.", stored != null)
            assertEquals(stored, repository.createOnce(requestId, draft()))
            assertEquals(listOf(stored), repository.list())
        }
    }

    @Test
    fun invalidRequestIdentitiesDoNotOpenADatabase(): Unit = runBlocking {
        val name = temporaryDatabaseName()
        val file = context.getDatabasePath(name)
        assertFalse(file.exists())
        SqliteReportRepository(context, name, clock()).use { repository ->
            listOf("", "1234", "1-2-3-4-5", requestId.uppercase()).forEach { invalid ->
                try {
                    repository.createOnce(invalid, draft())
                    fail("La identidad necesita un UUID canónico completo.")
                } catch (_: IllegalArgumentException) {
                    assertFalse(file.exists())
                }
            }
        }
        assertFalse(file.exists())
    }

    @Test
    fun aNewIdentityStillRequiresFreshEvidenceBeforeOpeningTheDatabase(): Unit = runBlocking {
        val name = temporaryDatabaseName()
        val file = context.getDatabasePath(name)
        val guard = NearbySqliteReportGuard({ fix }, { elapsed + 120_000_000_001L })
        SqliteReportRepository(context, name, clock(), createGuard = guard::check).use { repository ->
            try {
                repository.createOnce(requestId, draft())
                fail("Una identidad nueva no sustituye la evidencia GPS reciente.")
            } catch (_: InvalidReportException) {
                assertFalse(file.exists())
            }
        }
        assertFalse(file.exists())
    }

    @Test
    fun reopeningTheDatabaseRecoversTheSameIdentityWithoutConsultingTheGuard(): Unit = runBlocking {
        val name = temporaryDatabaseName()
        try {
            val saved = SqliteReportRepository(context, name, clock()).use { repository ->
                repository.createOnce(requestId, draft())
            }
            SqliteReportRepository(context, name, clock(), createGuard = {
                error("Recuperar una fila existente no solicita autorización para otra inserción.")
            }).use { reopened ->
                assertEquals(saved, reopened.createOnce(requestId, draft()))
                assertEquals(listOf(saved), reopened.list())
                assertNull(reopened.findById(UUID.randomUUID().toString()))
            }
        } finally {
            deleteOnlyTemporaryDatabase(name)
        }
    }

    private fun draft() = NewReport(
        IncidentType.RISK, Priority.LOW, "Hay una luminaria dañada junto al parque.",
        now.minusSeconds(10), ReportLocation("Frente al parque central", fix.latitude, fix.longitude),
    )

    private fun clock(): Clock = Clock.fixed(now, ZoneOffset.UTC)

    private fun temporaryDatabaseName() = "citizensecurity_idempotency_test_${UUID.randomUUID()}.db"

    private fun deleteOnlyTemporaryDatabase(name: String) {
        check(name.matches(Regex("citizensecurity_idempotency_test_[a-f0-9-]{36}\\.db")))
        context.deleteDatabase(name)
    }

    private class QueuedDispatcher : CoroutineDispatcher() {
        private val tasks = LinkedBlockingQueue<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.add(block) }
        fun awaitTask(): Runnable = checkNotNull(tasks.poll(10, TimeUnit.SECONDS)) {
            "No llegó el retorno de la corrutina dentro del límite."
        }
    }
}
