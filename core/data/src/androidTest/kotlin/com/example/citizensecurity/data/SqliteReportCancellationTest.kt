package com.example.citizensecurity.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportLocation
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SqliteReportCancellationTest {

    @Test
    fun cancelarMientrasEsperaNoGuardaOtroReporte() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "citizensecurity_test_${UUID.randomUUID()}.db"
        val workers = CopyOnWriteArrayList<Thread>()
        val dispatcher = Executors.newFixedThreadPool(2) { action ->
            Thread(action, "persistencia-reporte-${workers.size + 1}").also(workers::add)
        }.asCoroutineDispatcher()
        val clock = BlockingClock(NOW)
        val repository = SqliteReportRepository(context, databaseName, clock, dispatcher)
        val first = async(Dispatchers.Default) { repository.create(draft()) }
        var cancelled: Deferred<Report>? = null

        try {
            assertTrue(
                "La primera solicitud debe llegar al reloj antes de iniciar la segunda.",
                clock.firstRequested.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )
            val second = async(Dispatchers.Default) {
                repository.create(draft().copy(description = "Hay un poste dañado frente al mercado."))
            }
            cancelled = second
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS)
            while (workers.none { it.isWaitingForRepository() } &&
                System.nanoTime() < deadline
            ) {
                delay(1)
            }
            assertTrue(
                "La segunda solicitud debe estar realmente bloqueada mientras la primera espera.",
                workers.any { it.isWaitingForRepository() },
            )

            second.cancel()
            clock.releaseFirst.countDown()
            val saved = first.await()
            second.join()
            assertTrue(second.isCancelled)
            repository.close()

            SqliteReportRepository(
                context,
                databaseName,
                Clock.fixed(NOW, ZoneOffset.UTC),
            ).use { reopened ->
                assertEquals(
                    "La solicitud cancelada durante la espera no debe insertar otro reporte.",
                    listOf(saved),
                    reopened.list(),
                )
            }
        } finally {
            clock.releaseFirst.countDown()
            withContext(NonCancellable) {
                cancelled?.cancelAndJoin()
                first.cancelAndJoin()
            }
            repository.close()
            dispatcher.close()
            check(databaseName.matches(Regex("citizensecurity_test_[a-f0-9-]{36}\\.db")))
            context.deleteDatabase(databaseName)
        }
    }

    private fun draft() = NewReport(
        type = IncidentType.RISK,
        priority = Priority.MEDIUM,
        description = "Hay una luminaria dañada frente al parque.",
        occurredAt = NOW.minusSeconds(60),
        location = ReportLocation("Frente al parque central"),
    )

    private fun Thread.isWaitingForRepository(): Boolean =
        state == Thread.State.BLOCKED && stackTrace.any { frame ->
            frame.className.startsWith(SqliteReportRepository::class.java.name)
        }

    /** Retiene únicamente la primera creación hasta observar y cancelar la segunda solicitud. */
    private class BlockingClock(private val now: Instant) : Clock() {
        val firstRequested = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        private val calls = AtomicInteger()

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = Clock.fixed(now, zone)

        override fun instant(): Instant {
            if (calls.incrementAndGet() == 1) {
                firstRequested.countDown()
                check(releaseFirst.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    "La prueba no liberó la primera solicitud dentro del plazo."
                }
            }
            return now
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-03T03:00:00.987654321Z")
        const val TIMEOUT_SECONDS = 10L
    }
}
